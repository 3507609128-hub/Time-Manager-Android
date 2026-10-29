package com.fengshanglinye.timemanager

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 数据持久化（对应 Win32 版的 tasks.dat / settings.dat）。
 *
 * 文件格式与 Win32 版逐字节兼容，同样是竖线分隔的 UTF-8 文本：
 *   首行：<数量> <下一个 id>
 *   任务行：T|id|分类|开始年|月|日|时|分|结束年|月|日|时|分|标题|备注
 *   规则行：R|<条数>|分钟|是否已触发|分钟|是否已触发|...
 *   设置行：自启|最小化|提示音|停留秒|WAV 路径|语言|界面风格
 *
 * Android 上文件放在应用私有目录 files/ 下，不需要任何存储权限。
 */
object Store {

    private const val TASKS_FILE = "tasks.dat"
    private const val SETTINGS_FILE = "settings.dat"

    fun dataDir(ctx: Context): File {
        val d = File(ctx.filesDir, "")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun tasksPath(ctx: Context): String = File(dataDir(ctx), TASKS_FILE).absolutePath
    fun settingsPath(ctx: Context): String = File(dataDir(ctx), SETTINGS_FILE).absolutePath

    /*============================= 任务存取 =============================*/

    /** 清除竖线 / 换行，保证一行一个字段不会被破坏 */
    private fun clean(s: String): String =
        s.replace('|', ' ').replace('\r', ' ').replace('\n', ' ')

    /** 序列化为 tasks.dat 文本（与 Win32 版逐字节兼容，便于互相拷贝数据文件） */
    fun serializeTasks(tasks: List<Task>): String {
        val sb = StringBuilder()
        val nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
        sb.append("${tasks.size} $nextId\n")
        for (t in tasks.sortedBy { it.start }) {
            sb.append("T|").append(t.id).append('|').append(t.category).append('|')
            sb.append(t.start.year).append('|').append(t.start.monthValue).append('|')
            sb.append(t.start.dayOfMonth).append('|').append(t.start.hour).append('|')
            sb.append(t.start.minute).append('|')
            sb.append(t.end.year).append('|').append(t.end.monthValue).append('|')
            sb.append(t.end.dayOfMonth).append('|').append(t.end.hour).append('|')
            sb.append(t.end.minute).append('|')
            sb.append(clean(t.title)).append('|').append(clean(t.note)).append('\n')
            sb.append("R|").append(t.rules.size)
            for (r in t.rules) {
                sb.append('|').append(r.minutes).append('|').append(if (r.fired) 1 else 0)
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun saveTasks(ctx: Context, tasks: List<Task>) {
        File(dataDir(ctx), TASKS_FILE).writeText(serializeTasks(tasks), Charsets.UTF_8)
    }

    fun loadTasks(ctx: Context): List<Task> {
        val f = File(dataDir(ctx), TASKS_FILE)
        if (!f.exists()) return emptyList()

        val tasks = parseTasks(f.readText(Charsets.UTF_8))
        val now = LocalDateTime.now()
        // 收敛已错过的提醒（程序启动加载时保留当前所处级别补响一次）
        val normalized = tasks.map { t ->
            normalizeRules(t, java.time.Duration.between(now, t.start).seconds, false)
        }
        val result = normalized.sortedBy { it.start }.take(MAX_TASKS)
        saveTasks(ctx, result)
        return result
    }

    /** 解析 tasks.dat 文本（同时兼容旧格式），只做语法解析，不改提醒状态 */
    fun parseTasks(text: String): List<Task> {
        val tasks = ArrayList<Task>()
        var pending: Task? = null
        val lines = text.lines()
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trimEnd('\r', '\n')
            if (line.isEmpty()) continue
            if (i == 0) continue   // 首行是数量与下一个 id
            if (line.startsWith("T|")) {
                val p = line.substring(2).split('|')
                if (p.size < 13) { pending = null; continue }
                val hasCat = p.size >= 14
                val off = if (hasCat) 1 else 0
                val cat = if (hasCat) p[1].toIntOrNull() ?: Categories.DEFAULT
                else Categories.DEFAULT
                val t = Task(
                    id = p[0].toIntOrNull() ?: 0,
                    category = Categories.normalize(cat),
                    start = mkDate(p[1 + off], p[2 + off], p[3 + off], p[4 + off], p[5 + off]),
                    end = mkDate(p[6 + off], p[7 + off], p[8 + off], p[9 + off], p[10 + off]),
                    title = p[11 + off],
                    note = p.getOrElse(12 + off) { "" },
                    rules = emptyList()
                )
                tasks.add(t)
                pending = t
            } else if (line.startsWith("R|") && pending != null) {
                val p = line.substring(2).split('|')
                val rc = (p.getOrNull(0)?.toIntOrNull() ?: 0).coerceIn(0, MAX_REMIND)
                val rules = ArrayList<Reminder>(rc)
                for (k in 1..rc) {
                    val min = p.getOrNull(2 * k - 1)?.toIntOrNull() ?: continue
                    val fired = (p.getOrNull(2 * k)?.toIntOrNull() ?: 0) != 0
                    rules.add(Reminder(min, fired))
                }
                val idx = tasks.size - 1
                tasks[idx] = tasks[idx].copy(rules = sortRules(rules))
                pending = null
            } else {
                // 旧格式兼容：固定 17 字段、4 条固定提醒
                val p = line.split('|')
                if (p.size < 17) { pending = null; continue }
                val rules = ArrayList<Reminder>()
                for (k in 1..4) {
                    rules.add(Reminder(DEFAULT_RULES[k - 1],
                        (p.getOrNull(13 + k)?.toIntOrNull() ?: 0) != 0))
                }
                tasks.add(
                    Task(
                        id = p[0].toIntOrNull() ?: 0,
                        category = Categories.DEFAULT,
                        start = mkDate(p[1], p[2], p[3], p[4], p[5]),
                        end = mkDate(p[6], p[7], p[8], p[9], p[10]),
                        title = p[11],
                        note = p[12],
                        rules = sortRules(rules)
                    )
                )
                pending = null
            }
        }

        return tasks
    }

    private fun mkDate(y: String, mo: String, d: String, h: String, mi: String): LocalDateTime {        return try {
            LocalDateTime.of(
                y.toInt(), mo.toInt().coerceIn(1, 12), d.toInt().coerceIn(1, 31),
                h.toInt().coerceIn(0, 23), mi.toInt().coerceIn(0, 59)
            )
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }

    /*============================= 设置存取 =============================*/

    fun loadSettings(ctx: Context): Pair<Settings, Int> {
        val systemLang = detectSystemLang(ctx)
        val f = File(dataDir(ctx), SETTINGS_FILE)
        if (!f.exists()) return Pair(Settings(lang = -1), systemLang)
        val line = f.readLines(Charsets.UTF_8).firstOrNull() ?: return Pair(Settings(lang = -1), systemLang)
        val p = line.split('|')
        if (p.size < 4) return Pair(Settings(lang = -1), systemLang)
        var sound = p[2].toIntOrNull() ?: SOUND_ASTERISK
        if (sound < 0 || sound > SOUND_WAV) sound = SOUND_ASTERISK
        var dur = p[3].toIntOrNull() ?: 12
        if (dur < 5 || dur > 60) dur = 12
        var lang = p.getOrNull(5)?.toIntOrNull() ?: -1
        if (lang < 0 || lang >= LANG_COUNT) lang = -1
        var theme = p.getOrNull(6)?.toIntOrNull() ?: ThemeId.DEFAULT.index
        if (theme < 0 || theme >= ThemeId.COUNT) theme = ThemeId.DEFAULT.index
        val s = Settings(
            autostart = (p[0].toIntOrNull() ?: 0) != 0,
            minStart = (p[1].toIntOrNull() ?: 0) != 0,
            sound = sound,
            toastSec = dur,
            wavPath = p.getOrNull(4) ?: "",
            lang = lang,
            theme = theme
        )
        val effective = if (lang < 0) systemLang else lang
        return Pair(s, effective)
    }

    fun saveSettings(ctx: Context, s: Settings) {
        val wav = clean(s.wavPath)
        val line = "${if (s.autostart) 1 else 0}|${if (s.minStart) 1 else 0}|${s.sound}|" +
                "${s.toastSec}|$wav|${s.lang}|${s.theme}\n"
        File(dataDir(ctx), SETTINGS_FILE).writeText(line, Charsets.UTF_8)
    }

    fun detectSystemLang(ctx: Context): Int {
        return when (ctx.resources.configuration.locales.get(0).language) {
            "ja" -> LANG_JA
            "en" -> LANG_EN
            else -> LANG_ZH
        }
    }
}

/*============================= 规则运算（与 main.cpp 同逻辑）=============================*/

/** 规则按提前分钟数降序（大的先触发） */
fun sortRules(list: List<Reminder>): List<Reminder> = list.sortedByDescending { it.minutes }

/**
 * 收敛已错过的提醒规则，返回收敛后的任务（无变化时原样返回同一对象）。
 *  includeNearest=false：程序启动加载时，保留当前所处级别补响一次；
 *  includeNearest=true ：新建/编辑任务时，已跨过的级别不再补响，但提前 5 分钟以内的规则仍然保留。
 */
fun normalizeRules(t: Task, diffSec: Long, includeNearest: Boolean): Task {
    if (t.rules.isEmpty()) return t
    var changed = false
    val rules = t.rules.toMutableList()
    if (diffSec < 0) {
        for (i in rules.indices) {
            if (!rules[i].fired) { rules[i] = rules[i].copy(fired = true); changed = true }
        }
        return if (changed) t.copy(rules = rules) else t
    }
    var nearest = -1
    for (i in rules.indices) {
        if (diffSec <= rules[i].minutes.toLong() * 60) nearest = i
    }
    if (nearest < 0) return t
    for (i in 0 until nearest) {
        if (!rules[i].fired) { rules[i] = rules[i].copy(fired = true); changed = true }
    }
    if (includeNearest && rules[nearest].minutes > 5 && !rules[nearest].fired) {
        rules[nearest] = rules[nearest].copy(fired = true)
        changed = true
    }
    return if (changed) t.copy(rules = rules) else t
}

/** 某天是否在给定年份月份内（用于日历格子） */
fun daysInMonth(year: Int, month: Int): Int =
    LocalDate.of(year, month, 1).lengthOfMonth()
