package com.fengshanglinye.timemanager

import java.util.Locale

/**
 * 三路合并核心。
 *
 * 这是电脑端 `TmSync.h`（C++）里合并逻辑的逐行移植：
 * 同样的文本规范化、同样的内容指纹、同样的排序与 id 重排，
 * 因此两端对同一组输入会得到**逐字节相同**的结果，保证收敛且不会来回改写。
 */
object SyncMerge {

    private val UTF8 = Charsets.UTF_8

    class Rec(val key: String, val text: String)

    class Doc {
        val items = ArrayList<Rec>()
        var valid = true

        fun find(key: String): Rec? {
            for (r in items) if (r.key == key) return r
            return null
        }

        fun push(key: String, text: String) {
            items.add(Rec(key, text))
        }

        fun pushTask(text: String) {
            push("c" + hex8(hash(rest(text))), text)
        }
    }

    // ---------------- 基础工具 ----------------

    /** 去 `\r`，去掉行尾空白的换行，末尾补**恰好一个** `\n`（空文本返回空串）。 */
    fun normalize(text: String): String {
        val b = StringBuilder(text.length + 1)
        for (c in text) if (c != '\r') b.append(c)
        var end = b.length
        while (end > 0) {
            val c = b[end - 1]
            if (c == '\n' || c == ' ' || c == '\t') end-- else break
        }
        if (end == 0) return ""
        return b.substring(0, end) + "\n"
    }

    /** FNV-1a（32 位），按 UTF-8 字节计算，与 C++ 版一致。 */
    fun hash(s: String): Int {
        var h = 2166136261u
        for (b in s.toByteArray(UTF8)) {
            h = h xor (b.toInt() and 0xFF).toUInt()
            h *= 16777619u
        }
        return h.toInt()
    }

    fun hex8(v: Int): String = String.format(Locale.US, "%08x", v.toLong() and 0xFFFFFFFFL)

    /** 记录指纹正文：跳过 `T|` 与 id 字段，从第二个 `|` 起。 */
    fun rest(text: String): String {
        if (!text.startsWith("T|")) return text
        val i = text.indexOf('|', 2)
        return if (i < 0) text else text.substring(i)
    }

    // ---------------- 解析 ----------------

    private fun declaredHeader(text: String): Int {
        val line = text.substringBefore('\n')
        val m = Regex("^\\s*(-?\\d+)\\s+(-?\\d+)").find(line)
        return if (m == null) -1 else 1
    }

    fun parseTasks(text: String): Doc {
        val d = Doc()
        val lines = text.split('\n')
        var cur: StringBuilder? = null
        var lineNo = 0
        for (line in lines) {
            if (lineNo == 0) {
                if (declaredHeader(text) < 0) d.valid = false
                lineNo++
                continue
            }
            lineNo++
            if (line.startsWith("T|")) {
                cur?.let { d.pushTask(it.toString()) }
                cur = StringBuilder(line)
            } else if (line.startsWith("R|")) {
                if (cur != null) {
                    cur.append('\n').append(line)
                } else {
                    d.push("r" + hex8(hash(line)), line)
                }
            } else if (line.isNotEmpty()) {
                if (cur != null) {
                    d.pushTask(cur.toString())
                    cur = null
                }
                d.push("z" + hex8(hash(line)), line)
            }
        }
        cur?.let { d.pushTask(it.toString()) }
        if (text.isEmpty()) d.valid = false
        return d
    }

    fun parseSettings(text: String): Doc {
        val d = Doc()
        if (text.isEmpty()) return d
        if (text.length > 4096) {
            d.valid = false
            return d
        }
        d.push("\$set", text)
        return d
    }

    // ---------------- 排序 ----------------

    /** 取 T 行第 3..7 个字段（起始 年/月/日/时/分）作为排序主键。 */
    private fun sortKey(text: String): IntArray {
        val out = IntArray(5)
        if (!text.startsWith("T|")) return out
        val line = text.substringBefore('\n')
        for (i in 0 until 5) out[i] = intField(line, 3 + i)
        return out
    }

    /** `atoi` 语义：跳过空白、可选符号、取连续数字。 */
    private fun intField(line: String, idx: Int): Int {
        var count = 0
        var i = 0
        val n = line.length
        while (i <= n) {
            val start = i
            while (i < n && line[i] != '|') i++
            if (count == idx) return atoi(line.substring(start, i))
            count++
            i++
        }
        return 0
    }

    private fun atoi(s: String): Int {
        var i = 0
        while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
        var sign = 1
        if (i < s.length && (s[i] == '+' || s[i] == '-')) {
            if (s[i] == '-') sign = -1
            i++
        }
        var v = 0L
        while (i < s.length && s[i] in '0'..'9') {
            v = v * 10 + (s[i] - '0')
            if (v > Int.MAX_VALUE) return if (sign > 0) Int.MAX_VALUE else Int.MIN_VALUE
            i++
        }
        return (v * sign).toInt()
    }

    private fun cmpBytes(a: String, b: String): Int {
        val x = a.toByteArray(UTF8)
        val y = b.toByteArray(UTF8)
        val n = if (x.size < y.size) x.size else y.size
        for (i in 0 until n) {
            val c = (x[i].toInt() and 0xFF) - (y[i].toInt() and 0xFF)
            if (c != 0) return c
        }
        return x.size - y.size
    }

    private fun recLess(a: Rec, b: Rec): Boolean {
        val ka = sortKey(a.text)
        val kb = sortKey(b.text)
        for (i in 0 until 5) if (ka[i] != kb[i]) return ka[i] < kb[i]
        return cmpBytes(rest(a.text), rest(b.text)) < 0
    }

    // ---------------- 合并 ----------------

    fun merge(base: Doc, local: Doc, remote: Doc, remoteWins: Boolean, isTasks: Boolean): String {
        val out = ArrayList<Rec>()

        for (l in local.items) {
            val b = base.find(l.key)
            val r = remote.find(l.key)
            var chosen = l.text
            if (r != null) {
                chosen = when {
                    l.text == r.text -> l.text
                    b != null && l.text == b.text -> r.text
                    b != null && r.text == b.text -> l.text
                    else -> if (remoteWins) r.text else l.text
                }
            } else if (b != null && l.text == b.text) {
                // 本地与基点相同、远端已经没有这条 —— 对端删除了它
                continue
            }
            out.add(Rec(l.key, chosen))
        }

        for (r in remote.items) {
            if (local.find(r.key) != null) continue
            val b = base.find(r.key)
            if (b != null && b.text == r.text) continue
            out.add(Rec(r.key, r.text))
        }

        val sorted = out.sortedWith { x, y -> if (recLess(x, y)) -1 else if (recLess(y, x)) 1 else 0 }

        val sb = StringBuilder()
        if (isTasks) {
            sb.append(sorted.size).append(' ').append(sorted.size + 1).append('\n')
            for (i in sorted.indices) {
                val txt = sorted[i].text
                if (txt.startsWith("T|")) {
                    val bar = txt.indexOf('|', 2)
                    if (bar >= 0) {
                        sb.append("T|").append(i + 1).append(txt.substring(bar))
                    } else {
                        sb.append(txt)
                    }
                } else {
                    sb.append(txt)
                }
                sb.append('\n')
            }
        } else {
            for (rec in sorted) sb.append(rec.text)
        }
        return sb.toString()
    }

    // ---------------- 对外便捷接口 ----------------

    fun mergeTasks(baseText: String, localText: String, remoteText: String, remoteWins: Boolean): String =
        merge(parseTasks(baseText), parseTasks(localText), parseTasks(remoteText), remoteWins, true)

    fun mergeSettings(baseText: String, localText: String, remoteText: String, remoteWins: Boolean): String =
        merge(parseSettings(baseText), parseSettings(localText), parseSettings(remoteText), remoteWins, false)

    /** 任务文档里「内容行」的条数（不含表头），用于界面统计。 */
    fun taskCount(text: String): Int = text.split('\n').count { it.startsWith("T|") }
}
