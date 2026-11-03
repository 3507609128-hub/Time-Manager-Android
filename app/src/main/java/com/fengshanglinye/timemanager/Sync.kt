package com.fengshanglinye.timemanager

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * 双端同步引擎（安卓端），对应电脑端的 `TmSync.h`。
 *
 * 协议 TMSYNC/2：
 *  - 6 位配对码 → 房间 `timemanager/room/<CODE>/`
 *  - 主题 `<room>/tasks`、`<room>/settings`、`<room>/hello`
 *  - 信封 `TM2|<devId>|<seq>|<mtime>|<kind>|<len>\n` + 正文
 *  - 传输：MQTT 3.1.1 over WebSocket（broker.emqx.io:8083/mqtt），全部手写，不依赖第三方库
 *  - 合并：与电脑端完全相同的内容指纹 + 三路合并 + id 重排（见 [SyncMerge]）
 */
/**
 * 标准 Base64（不依赖 android.util，便于在 JVM 单元测试里跑同一份代码）。
 */
internal fun b64Encode(src: ByteArray): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    var i = 0
    while (i + 2 < src.size) {
        val n = ((src[i].toInt() and 0xFF) shl 16) or
                ((src[i + 1].toInt() and 0xFF) shl 8) or
                (src[i + 2].toInt() and 0xFF)
        sb.append(table[(n ushr 18) and 0x3F])
        sb.append(table[(n ushr 12) and 0x3F])
        sb.append(table[(n ushr 6) and 0x3F])
        sb.append(table[n and 0x3F])
        i += 3
    }
    val rest = src.size - i
    if (rest == 1) {
        val n = (src[i].toInt() and 0xFF) shl 16
        sb.append(table[(n ushr 18) and 0x3F])
        sb.append(table[(n ushr 12) and 0x3F])
        sb.append("==")
    } else if (rest == 2) {
        val n = ((src[i].toInt() and 0xFF) shl 16) or ((src[i + 1].toInt() and 0xFF) shl 8)
        sb.append(table[(n ushr 18) and 0x3F])
        sb.append(table[(n ushr 12) and 0x3F])
        sb.append(table[(n ushr 6) and 0x3F])
        sb.append("=")
    }
    return sb.toString()
}

object Sync {

    const val HOST = "broker.emqx.io"
    const val PORT = 8083
    const val PATH = "/mqtt"
    const val PREFIX = "timemanager/room/"
    const val CODE_LEN = 6
    private const val CLIENT_PREFIX = "tm-an-"
    private const val KEEPALIVE_S = 60
    private const val MAX_BODY = 192 * 1024
    private const val SCAN_MS = 700L
    private const val SETTLE_MS = 500L
    private const val PING_MS = 25000L
    private const val CONNECT_MS = 7000
    private const val BACKOFF_MIN = 2000L
    private const val BACKOFF_MAX = 30000L

    const val ST_OFF = 0
    const val ST_WAIT = 1
    const val ST_ONLINE = 2

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val FILE_TASKS = "tasks.dat"
    private const val FILE_SETTINGS = "settings.dat"
    private const val BASE_TASKS = "sync_base_tasks.dat"
    private const val BASE_SETTINGS = "sync_base_settings.dat"
    private const val CONF = "sync.conf"

    // ---------------- 可配置状态 ----------------

    @Volatile private var running = false
    @Volatile private var status = ST_OFF
    @Volatile private var enabled = true
    @Volatile private var online = false
    @Volatile private var code = ""
    @Volatile private var devId = ""
    @Volatile private var host = HOST
    @Volatile private var forceResync = false
    @Volatile private var appliedTasks = 0
    @Volatile private var appliedSettings = 0

    private var dir: String = ""
    private var thread: Thread? = null
    private var onLog: ((String) -> Unit)? = null
    private var onStatus: ((Int) -> Unit)? = null
    private var onFiles: ((Int, Int) -> Unit)? = null

    // ---------------- 线程内部状态（只在工作线程里访问） ----------------

    private var sock: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var wsRaw = ByteArray(0)
    private var mqttRaw = ByteArray(0)
    private var seq = 0
    private var lastPing = 0L
    private var lastScan = 0L

    private val lastPubTasks = StringBuilder()
    private val lastPubSettings = StringBuilder()
    private var localTasksHash = ""
    private var pendingTasksHash = ""
    private var pendingTasksAt = 0L
    private var pendingSettingsHash = ""
    private var pendingSettingsAt = 0L
    private var baseTasks = ""
    private var baseSettings = ""

    // ---------------- 日志 ----------------

    private fun log(msg: String) {
        onLog?.invoke(msg)
    }

    private fun setStatus(v: Int) {
        if (status == v) return
        status = v
        onStatus?.invoke(v)
    }

    fun getStatus(): Int = status
    fun isEnabled(): Boolean = enabled
    fun isOnline(): Boolean = online
    fun getCode(): String = code
    fun getDevId(): String = devId
    fun appliedCounts(): Pair<Int, Int> = appliedTasks to appliedSettings

    fun statusText(v: Int): String = when (v) {
        ST_OFF -> "未启动"
        ST_WAIT -> "连接中"
        else -> "已连接"
    }

    // ---------------- 启动 / 停止 ----------------

    fun start(
        dataDir: File,
        logFn: (String) -> Unit,
        statusFn: (Int) -> Unit,
        filesFn: (Int, Int) -> Unit
    ) {
        if (running) return
        dir = dataDir.absolutePath
        onLog = logFn
        onStatus = statusFn
        onFiles = filesFn
        baseTasks = readText(BASE_TASKS)
        baseSettings = readText(BASE_SETTINGS)
        loadConf()
        if (devId.isEmpty()) {
            devId = randomHex(4)
            saveConf()
        }
        running = true
        status = ST_WAIT
        val t = Thread({ work() }, "tm-sync")
        t.isDaemon = true
        thread = t
        t.start()
        log("同步核心已启动")
    }

    fun stop() {
        running = false
        try {
            sock?.close()
        } catch (_: Exception) {
        }
        sock = null
        online = false
        setStatus(ST_OFF)
        thread?.join(1200)
        thread = null
    }

    fun setEnabled(v: Boolean) {
        enabled = v
        saveConf()
        if (!v) {
            online = false
            setStatus(ST_WAIT)
        }
        log(if (v) "已恢复同步" else "已暂停同步")
    }

    fun forceResync() {
        forceResync = true
        log("请求立即同步")
    }

    fun setCode(newCode: String): Boolean {
        val c = newCode.trim().uppercase(Locale.US)
        if (c.length != CODE_LEN) return false
        for (ch in c) if (ALPHABET.indexOf(ch) < 0) return false
        code = c
        enabled = true
        saveConf()
        log("配对码已设为 $c")
        return true
    }

    fun newCode(): String {
        val sb = StringBuilder()
        val rnd = SecureRandom()
        for (i in 0 until CODE_LEN) sb.append(ALPHABET[rnd.nextInt(ALPHABET.length)])
        code = sb.toString()
        enabled = true
        saveConf()
        log("已生成新的配对码 $code")
        return code
    }

    // ---------------- 配置与文件 ----------------

    private fun fileOf(name: String): File = File(dir, name)

    private fun readText(name: String): String = try {
        val f = fileOf(name)
        if (f.exists()) f.readText(Charsets.UTF_8) else ""
    } catch (e: Exception) {
        ""
    }

    private fun writeBytes(name: String, data: ByteArray) {
        try {
            fileOf(name).writeBytes(data)
        } catch (e: Exception) {
            log("写入文件失败：${e.message}")
        }
    }

    private fun loadConf() {
        val text = readText(CONF)
        if (text.isEmpty()) return
        for (line in text.split('\n')) {
            val s = line.trim()
            when {
                s.startsWith("dev=") -> devId = s.substring(4).trim()
                s.startsWith("code=") -> code = s.substring(5).trim().uppercase(Locale.US)
                s.startsWith("enabled=") -> enabled = s.substring(8).trim() != "0"
                s.startsWith("host=") -> host = s.substring(5).trim().ifEmpty { HOST }
            }
        }
    }

    private fun saveConf() {
        val sb = StringBuilder()
        sb.append("dev=").append(devId).append('\n')
        sb.append("code=").append(code).append('\n')
        sb.append("enabled=").append(if (enabled) 1 else 0).append('\n')
        if (host != HOST) sb.append("host=").append(host).append('\n')
        writeBytes(CONF, sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        val sb = StringBuilder()
        for (x in b) sb.append(String.format(Locale.US, "%02x", x.toInt() and 0xFF))
        return sb.toString()
    }

    // ---------------- 信封 ----------------

    private fun envelope(kind: Int, body: ByteArray?): ByteArray {
        seq++
        val mt = when (kind) {
            0 -> fileOf(FILE_TASKS).lastModified()
            1 -> fileOf(FILE_SETTINGS).lastModified()
            else -> System.currentTimeMillis()
        }
        val len = body?.size ?: 0
        val head = "TM2|$devId|$seq|$mt|$kind|$len\n"
        val hb = head.toByteArray(Charsets.UTF_8)
        if (body == null || len == 0) return hb
        val out = ByteArray(hb.size + len)
        System.arraycopy(hb, 0, out, 0, hb.size)
        System.arraycopy(body, 0, out, hb.size, len)
        return out
    }

    private class Env(val dev: String, val seq: Int, val mtime: Long, val kind: Int, val body: ByteArray)

    private fun parseEnvelope(data: ByteArray): Env? {
        var nl = -1
        for (i in 0 until minOf(data.size, 128)) if (data[i] == '\n'.code.toByte()) {
            nl = i
            break
        }
        if (nl < 0) return null
        val head = String(data, 0, nl, Charsets.UTF_8)
        val f = head.split('|')
        if (f.size < 6 || f[0] != "TM2") return null
        val len = f[5].toIntOrNull() ?: return null
        if (len < 0 || len > MAX_BODY) return null
        val start = nl + 1
        if (data.size < start + len) return null
        val body = ByteArray(len)
        System.arraycopy(data, start, body, 0, len)
        return Env(f[1], f[2].toIntOrNull() ?: 0, f[3].toLongOrNull() ?: 0L, f[4].toIntOrNull() ?: -1, body)
    }

    // ---------------- 发布 ----------------

    private fun publish(kind: Int, body: ByteArray) {
        val suffix = when (kind) {
            0 -> "tasks"
            1 -> "settings"
            else -> "hello"
        }
        val topic = PREFIX + code + "/" + suffix
        val env = envelope(kind, body)
        val pkt = buildPublish(topic, env)
        sendWs(0x2, pkt)
    }

    private fun publishLocal(kind: Int) {
        val name = if (kind == 0) FILE_TASKS else FILE_SETTINGS
        val raw = readText(name)
        val norm = SyncMerge.normalize(raw)
        if (norm.isEmpty()) return
        val bytes = norm.toByteArray(Charsets.UTF_8)
        publish(kind, bytes)
        markPublished(kind, norm)
        log(if (kind == 0) "已推送本机日程" else "已推送本机设置")
    }

    private fun markPublished(kind: Int, norm: String) {
        val hex = SyncMerge.hex8(SyncMerge.hash(norm))
        val sb = if (kind == 0) lastPubTasks else lastPubSettings
        sb.setLength(0)
        sb.append(hex)
        if (kind == 0) localTasksHash = hex
    }

    // ---------------- 应用远端 ----------------

    private fun remoteWins(kind: Int, remoteMtime: Long, remoteDev: String): Boolean {
        val localMtime = when (kind) {
            0 -> fileOf(FILE_TASKS).lastModified()
            else -> fileOf(FILE_SETTINGS).lastModified()
        }
        if (remoteMtime > localMtime) return true
        if (remoteMtime < localMtime) return false
        return remoteDev > devId
    }

    private fun applyRemote(kind: Int, body: ByteArray, mtime: Long, dev: String): Boolean {
        val remoteNorm = SyncMerge.normalize(String(body, Charsets.UTF_8))
        if (kind == 0) {
            if (!SyncMerge.parseTasks(remoteNorm).valid) return false
        }
        val localNorm = SyncMerge.normalize(readText(if (kind == 0) FILE_TASKS else FILE_SETTINGS))
        val base = if (kind == 0) baseTasks else baseSettings
        val merged = if (kind == 0) {
            SyncMerge.mergeTasks(base, localNorm, remoteNorm, remoteWins(0, mtime, dev))
        } else {
            SyncMerge.mergeSettings(base, localNorm, remoteNorm, remoteWins(1, mtime, dev))
        }

        var changed = false
        if (merged != localNorm) {
            writeBytes(if (kind == 0) FILE_TASKS else FILE_SETTINGS, merged.toByteArray(Charsets.UTF_8))
            changed = true
            if (kind == 0) appliedTasks++ else appliedSettings++
            log(if (kind == 0) "已应用远端日程" else "已应用远端设置")
        }

        if (kind == 0) baseTasks = merged else baseSettings = merged
        writeBytes(if (kind == 0) BASE_TASKS else BASE_SETTINGS, merged.toByteArray(Charsets.UTF_8))
        markPublished(kind, merged)

        if (changed && merged != remoteNorm && online) publish(kind, merged.toByteArray(Charsets.UTF_8))
        return changed
    }

    private fun handleFile(kind: Int, body: ByteArray, mtime: Long, dev: String) {
        if (body.isEmpty()) return
        if (body.size > MAX_BODY) {
            log("远端数据过大，已丢弃")
            return
        }
        val hex = SyncMerge.hex8(SyncMerge.hash(String(body, Charsets.UTF_8)))
        val last = if (kind == 0) lastPubTasks.toString() else lastPubSettings.toString()
        if (hex == last) return
        val changed = applyRemote(kind, body, mtime, dev)
        if (changed) {
            if (kind == 0) onFiles?.invoke(1, 0) else onFiles?.invoke(0, 1)
        }
    }

    private fun handlePublish(topic: String, payload: ByteArray) {
        if (!topic.contains(code)) return
        val env = parseEnvelope(payload) ?: return
        if (env.dev == devId) return
        when (env.kind) {
            2 -> {
                log("对端已连接")
                val peer = String(env.body, Charsets.UTF_8)
                if (localTasksHash.isNotEmpty() && !peer.contains(localTasksHash) && online) {
                    publishLocal(0)
                    publishLocal(1)
                }
            }
            3 -> {
                log("对端请求全量同步")
                if (online) {
                    publishLocal(0)
                    publishLocal(1)
                }
            }
            0, 1 -> handleFile(env.kind, env.body, env.mtime, env.dev)
        }
    }

    // ---------------- MQTT / WebSocket ----------------

    private fun buildConnect(clientId: String): ByteArray {
        val body = ArrayList<Byte>()
        val name = "MQTT".toByteArray(Charsets.US_ASCII)
        body.add(0)
        body.add(name.size.toByte())
        for (b in name) body.add(b)
        body.add(4)              // 协议级别
        body.add(0x02)           // clean session
        body.add(((KEEPALIVE_S shr 8) and 0xFF).toByte())
        body.add((KEEPALIVE_S and 0xFF).toByte())
        val cid = clientId.toByteArray(Charsets.UTF_8)
        body.add(((cid.size shr 8) and 0xFF).toByte())
        body.add((cid.size and 0xFF).toByte())
        for (b in cid) body.add(b)
        return mqttPacket(0x10, body)
    }

    private fun buildSubscribe(pid: Int, topics: List<String>): ByteArray {
        val body = ArrayList<Byte>()
        body.add(((pid shr 8) and 0xFF).toByte())
        body.add((pid and 0xFF).toByte())
        for (t in topics) {
            val b = t.toByteArray(Charsets.UTF_8)
            body.add(((b.size shr 8) and 0xFF).toByte())
            body.add((b.size and 0xFF).toByte())
            for (x in b) body.add(x)
            body.add(0) // QoS0
        }
        return mqttPacket(0x82, body)
    }

    private fun buildPublish(topic: String, payload: ByteArray): ByteArray {
        val body = ArrayList<Byte>()
        val t = topic.toByteArray(Charsets.UTF_8)
        body.add(((t.size shr 8) and 0xFF).toByte())
        body.add((t.size and 0xFF).toByte())
        for (b in t) body.add(b)
        for (b in payload) body.add(b)
        return mqttPacket(0x30, body)
    }

    private fun mqttPacket(type: Int, body: ArrayList<Byte>): ByteArray {
        val out = ArrayList<Byte>(body.size + 5)
        out.add(type.toByte())
        var len = body.size
        do {
            var d = len % 128
            len /= 128
            if (len > 0) d = d or 0x80
            out.add(d.toByte())
        } while (len > 0)
        out.addAll(body)
        return out.toByteArray()
    }

    private fun sendWs(opcode: Int, payload: ByteArray) {
        val so = output ?: return
        val hdr = ArrayList<Byte>()
        hdr.add((0x80 or opcode).toByte())
        val n = payload.size
        when {
            n < 126 -> hdr.add((0x80 or n).toByte())
            n < 65536 -> {
                hdr.add((0x80 or 126).toByte())
                hdr.add(((n shr 8) and 0xFF).toByte())
                hdr.add((n and 0xFF).toByte())
            }
            else -> {
                hdr.add((0x80 or 127).toByte())
                for (i in 7 downTo 0) hdr.add(((n.toLong() shr (8 * i)) and 0xFF).toByte())
            }
        }
        val mask = ByteArray(4)
        SecureRandom().nextBytes(mask)
        for (m in mask) hdr.add(m)
        val frame = ByteArray(hdr.size + n)
        for (i in hdr.indices) frame[i] = hdr[i]
        for (i in 0 until n) frame[hdr.size + i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        so.write(frame)
        so.flush()
    }

    private fun connectAndHandshake() {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, PORT), CONNECT_MS)
        // 握手与 CONNACK 阶段放宽超时，进入泵循环后再收紧到 200ms
        s.soTimeout = 1500
        input = s.getInputStream()
        output = s.getOutputStream()

        val keyBytes = ByteArray(16)
        SecureRandom().nextBytes(keyBytes)
        val key = b64Encode(keyBytes)
        val req = "GET $PATH HTTP/1.1\r\n" +
            "Host: $host:$PORT\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Protocol: mqtt\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n"
        output!!.write(req.toByteArray(Charsets.US_ASCII))
        output!!.flush()

        val resp = StringBuilder()
        val one = ByteArray(1)
        while (!resp.endsWith("\r\n\r\n")) {
            val n = input!!.read(one)
            if (n <= 0) throw IOException("握手失败")
            resp.append(one[0].toInt().toChar())
            if (resp.length > 4096) throw IOException("握手响应过长")
        }
        if (!resp.contains(" 101 ")) throw IOException("服务器拒绝 WebSocket 握手")

        sock = s
        wsRaw = ByteArray(0)
        mqttRaw = ByteArray(0)
        sendWs(0x2, buildConnect(CLIENT_PREFIX + devId + "-" + code))

        // 等 CONNACK
        val deadline = System.currentTimeMillis() + CONNECT_MS
        while (System.currentTimeMillis() < deadline) {
            pumpOnce()
            if (subscribeSent) break
        }
        if (!subscribeSent) throw IOException("未收到 MQTT 连接确认")

        sendWs(0x2, buildSubscribe(1, listOf(PREFIX + code + "/tasks", PREFIX + code + "/settings", PREFIX + code + "/hello")))
        subscribeSent = false
        lastPing = System.currentTimeMillis()
        s.soTimeout = 200
        online = true
        setStatus(ST_ONLINE)
        log("已进入房间，开始同步")
        publishLocal(0)
        publishLocal(1)
        publish(2, "$devId|$localTasksHash".toByteArray(Charsets.UTF_8))
        if (forceResync) {
            forceResync = false
            publish(3, ByteArray(0))
        }
    }

    private var subscribeSent = false

    private fun disconnect(reason: String?) {
        online = false
        try {
            sock?.close()
        } catch (_: Exception) {
        }
        sock = null
        input = null
        output = null
        wsRaw = ByteArray(0)
        mqttRaw = ByteArray(0)
        setStatus(ST_WAIT)
        if (reason != null) log(reason)
    }

    private fun pumpOnce() {
        val ins = input ?: throw IOException("未连接")
        val buf = ByteArray(8192)
        val n = try {
            ins.read(buf)
        } catch (e: SocketTimeoutException) {
            return
        }
        if (n <= 0) throw IOException("连接被服务器关闭")
        wsRaw = wsRaw + buf.copyOf(n)
        parseWs()
    }

    private fun parseWs() {
        var pos = 0
        var guard = 0
        while (wsRaw.size - pos >= 2 && guard++ < 64) {
            val b0 = wsRaw[pos].toInt() and 0xFF
            val b1 = wsRaw[pos + 1].toInt() and 0xFF
            val opcode = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            var hdr = 2
            if (len == 126L) {
                if (wsRaw.size - pos < 4) break
                len = (((wsRaw[pos + 2].toInt() and 0xFF) shl 8) or (wsRaw[pos + 3].toInt() and 0xFF)).toLong()
                hdr = 4
            } else if (len == 127L) {
                if (wsRaw.size - pos < 10) break
                len = 0
                for (i in 2 until 10) len = (len shl 8) or (wsRaw[pos + i].toInt() and 0xFF).toLong()
                hdr = 10
            }
            if (wsRaw.size - pos < hdr + len) break
            val payload = ByteArray(len.toInt())
            System.arraycopy(wsRaw, pos + hdr, payload, 0, payload.size)
            pos += hdr + len.toInt()

            when (opcode) {
                0x8 -> {
                    disconnect("服务器关闭了连接")
                    throw IOException("closed")
                }
                0x9 -> sendWs(0xA, payload)
                0xA -> {}
                else -> {
                    mqttRaw = mqttRaw + payload
                    parseMqtt()
                }
            }
        }
        if (pos > 0) wsRaw = wsRaw.copyOfRange(pos, wsRaw.size)
    }

    private fun parseMqtt() {
        var pos = 0
        var guard = 0
        while (mqttRaw.size - pos >= 2 && guard++ < 64) {
            val hdr0 = mqttRaw[pos].toInt() and 0xFF
            var rem = 0
            var shift = 0
            var n = 0
            var ok = false
            while (n < 4) {
                if (pos + 1 + n >= mqttRaw.size) break
                val d = mqttRaw[pos + 1 + n].toInt() and 0xFF
                rem = rem or ((d and 0x7F) shl shift)
                shift += 7
                n++
                if (d and 0x80 == 0) {
                    ok = true
                    break
                }
            }
            if (!ok) break
            val total = 1 + n + rem
            if (mqttRaw.size - pos < total) break
            val body = ByteArray(rem)
            System.arraycopy(mqttRaw, pos + 1 + n, body, 0, rem)
            pos += total
            dispatchMqtt(hdr0, body)
        }
        if (pos > 0) mqttRaw = mqttRaw.copyOfRange(pos, mqttRaw.size)
    }

    private fun dispatchMqtt(hdr0: Int, body: ByteArray) {
        val type = (hdr0 shr 4) and 0x0F
        if (type == 2) {
            if (body.size >= 2 && body[1].toInt() != 0) throw IOException("MQTT 连接被拒绝")
            subscribeSent = true
            return
        }
        if (type == 3 && body.size > 2) {
            val tl = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
            val qos = (hdr0 shr 1) and 0x03
            var off = 2 + tl
            if (off > body.size) return
            if (qos > 0) off += 2
            if (off > body.size) return
            val topic = String(body, 2, tl, Charsets.UTF_8)
            val payload = ByteArray(body.size - off)
            System.arraycopy(body, off, payload, 0, payload.size)
            handlePublish(topic, payload)
        }
    }

    private fun scanLocal() {
        val now = System.currentTimeMillis()
        if (now - lastScan < SCAN_MS) return
        lastScan = now

        if (online) {
            scanOne(0)
            scanOne(1)
        }
    }

    private fun scanOne(kind: Int) {
        val name = if (kind == 0) FILE_TASKS else FILE_SETTINGS
        val norm = SyncMerge.normalize(readText(name))
        val hex = SyncMerge.hex8(SyncMerge.hash(norm))
        val last = if (kind == 0) lastPubTasks.toString() else lastPubSettings.toString()
        val pending = if (kind == 0) pendingTasksHash else pendingSettingsHash
        val at = if (kind == 0) pendingTasksAt else pendingSettingsAt

        if (hex == last) return
        if (hex != pending) {
            if (kind == 0) {
                pendingTasksHash = hex
                pendingTasksAt = System.currentTimeMillis()
            } else {
                pendingSettingsHash = hex
                pendingSettingsAt = System.currentTimeMillis()
            }
            return
        }
        if (System.currentTimeMillis() - at < SETTLE_MS) return
        if (norm.isEmpty()) return
        publish(kind, norm.toByteArray(Charsets.UTF_8))
        markPublished(kind, norm)
        log(if (kind == 0) "本机日程有改动，已推送" else "本机设置有改动，已推送")
    }

    // ---------------- 主循环 ----------------

    private fun work() {
        var backoff = BACKOFF_MIN
        while (running) {
            if (!enabled || code.isEmpty()) {
                if (sock != null) disconnect(null)
                setStatus(ST_WAIT)
                try {
                    Thread.sleep(200)
                } catch (e: InterruptedException) {
                }
                continue
            }
            try {
                if (sock == null) {
                    setStatus(ST_WAIT)
                    connectAndHandshake()
                    backoff = BACKOFF_MIN
                }
                pumpOnce()
                val now = System.currentTimeMillis()
                if (now - lastPing >= PING_MS) {
                    sendWs(0x2, byteArrayOf(0xC0.toByte(), 0x00))
                    lastPing = now
                }
                if (forceResync) {
                    forceResync = false
                    publishLocal(0)
                    publishLocal(1)
                    publish(3, ByteArray(0))
                }
                scanLocal()
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                if (sock != null) disconnect("连接断开：$msg") else {
                    setStatus(ST_WAIT)
                    log("连接同步服务器失败：$msg")
                }
                if (!running) break
                try {
                    Thread.sleep(backoff)
                } catch (ie: InterruptedException) {
                }
                backoff = if (backoff * 2 > BACKOFF_MAX) BACKOFF_MAX else backoff * 2
            }
        }
        disconnect(null)
    }
}
