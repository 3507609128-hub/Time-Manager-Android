package com.fengshanglinye.timemanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*============================= 常量 =============================*/
const val MAX_TASKS = 200
const val MAX_REMIND = 12          // 每条日程最多 12 条提醒规则
const val REPEAT_MAX_WEEKS = 20    // 连续日程最多 20 周
const val MAX_TITLE = 64
const val MAX_NOTE = 256

/** 提示音选项编号（与 Win32 版一致） */
const val SOUND_MUTE = 0
const val SOUND_DEFAULT = 1
const val SOUND_ASTERISK = 2
const val SOUND_EXCLAM = 3
const val SOUND_HAND = 4
const val SOUND_BEEP = 5
const val SOUND_WAV = 6

/** 详情提示停留时长可选值（秒） */
val NOTIFY_DURATIONS = intArrayOf(8, 10, 12, 15, 20, 30)

/*============================= 数据结构 =============================*/

data class Reminder(
    val minutes: Int,     // 提前多少分钟
    val fired: Boolean    // 是否已弹过
)

data class Task(
    val id: Int,
    val category: Int,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val title: String,
    val note: String,
    val rules: List<Reminder>
) {
    val startDate: LocalDate get() = start.toLocalDate()

    /** 用于展示的起止时间，"09:30 – 10:30" */
    fun timeRange(): String = "${fmtTime(start.toLocalTime())} – ${fmtTime(end.toLocalTime())}"

    /** 是否发生在某一天（支持跨天任务） */
    fun onDate(d: LocalDate): Boolean {
        val d0 = d.atStartOfDay()
        val d1 = d0.plusDays(1)
        return start < d1 && end > d0
    }

    /** 在某一天内被覆盖的时间段（分钟，0..1440）；无交叠返回 null */
    fun daySpan(d: LocalDate): Pair<Int, Int>? {
        val d0 = d.atStartOfDay()
        val d1 = d0.plusDays(1)
        if (end <= d0 || start >= d1) return null
        val s = if (start < d0) d0 else start
        val e = if (end > d1) d1 else end
        val sMin = s.hour * 60 + s.minute
        val eMin = if (e == d1) 1440 else e.hour * 60 + e.minute
        return Pair(sMin, if (eMin <= sMin) 1440 else eMin)
    }
}

data class Settings(
    val autostart: Boolean = false,      // 开机自启
    val minStart: Boolean = false,       // 启动时最小化（仅通知栏提醒）
    val sound: Int = SOUND_ASTERISK,     // 提示音选项
    val toastSec: Int = 12,              // 通知停留秒数
    val wavPath: String = "",            // 自定义提示音文件路径
    val lang: Int = -1,                  // 界面语言：-1 跟随系统
    val theme: Int = ThemeId.DEFAULT.index
)

/*============================= 全局状态（对应 main.cpp 的全局变量）=============================*/

/**
 * 应用级可变状态。Win32 版把任务、设置、当前语言等放在全局变量里，
 * 这里用 Compose State 保持同样的“单一数据源”思路，任何界面都读同一份数据。
 */
object AppState {
    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set
    var settings by mutableStateOf(Settings())
        private set

    /** 当前界面语言（LANG_ZH / LANG_JA / LANG_EN） */
    var lang by mutableStateOf(0)
        private set

    /** 月视图日历当前显示的月份 */
    var calYear by mutableStateOf(java.time.LocalDate.now().year)
    var calMonth by mutableStateOf(java.time.LocalDate.now().monthValue)

    /** 日历中选中的筛选日期（null 表示未筛选） */
    var filterDate by mutableStateOf<LocalDate?>(null)

    /** 周视图锚定日期（所在周） */
    var weekAnchor by mutableStateOf(java.time.LocalDate.now())

    /** 周视图单击选中的日程 id（0 表示无） */
    var weekSelId by mutableStateOf(0)

    /** 月视图活动流的选择集（日程 id） */
    var selectedIds by mutableStateOf<Set<Int>>(emptySet())
    var selAnchor by mutableStateOf(-1)

    /** 下次提醒的触发时间，供倒计时显示刷新 */
    var nowTick by mutableStateOf(System.currentTimeMillis())

    val nextId: Int
        get() = (tasks.maxOfOrNull { it.id } ?: 0) + 1

    @JvmName("applyTasks")
    fun setTasks(list: List<Task>) {
        tasks = list
    }

    @JvmName("applySettings")
    fun setSettings(s: Settings, newLang: Int) {
        settings = s
        lang = newLang
    }

    @JvmName("applyLang")
    fun setLang(l: Int) {
        lang = l
    }

    /*------------------------- 查询辅助 -------------------------*/

    fun tasksOnDate(d: LocalDate): List<Task> = tasks.filter { it.startDate == d }

    fun countOnDate(d: LocalDate): Int = tasks.count { it.startDate == d }

    /** 某天涉及的所有分类（去重，按日程顺序） */
    fun dayCategories(d: LocalDate, max: Int = 3): List<Int> {
        val out = ArrayList<Int>(max)
        for (t in tasks) {
            if (t.startDate != d) continue
            val cat = Categories.normalize(t.category)
            if (out.contains(cat)) continue
            out.add(cat)
            if (out.size >= max) break
        }
        return out
    }

    fun findTask(id: Int): Task? = tasks.firstOrNull { it.id == id }
}

/*============================= 日期/时间格式化 =============================*/

fun fmtTime(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

fun fmtDate(d: LocalDate): String = "%04d/%02d/%02d".format(d.year, d.monthValue, d.dayOfMonth)

fun fmtDateShort(d: LocalDate): String = "%d/%d".format(d.monthValue, d.dayOfMonth)

/** 提前量按当前语言格式化：5 分钟 / 1 小时 / 1 天 2 小时 ... */
fun fmtLead(minutes: Int): String {
    if (minutes <= 0) return S(Str.NOW)
    val d = minutes / 1440
    val h = (minutes % 1440) / 60
    val m = minutes % 60
    val parts = ArrayList<String>()
    if (d > 0) parts.add(S(Str.LEAD_DAY).format(d))
    if (h > 0) parts.add(S(Str.LEAD_HOUR).format(h))
    if (m > 0) parts.add(S(Str.LEAD_MIN).format(m))
    return parts.joinToString(" ")
}

/** 月视图分组标题：10/26  一 */
fun fmtAgendaDate(d: LocalDate): String =
    "%d/%d  %s".format(d.monthValue, d.dayOfMonth, weekShort(d.dayOfWeek.value % 7))

/** 月份标题：2026年10月 / October 2026 */
fun fmtMonthTitle(year: Int, month: Int): String {
    if (AppState.lang == LANG_EN) {
        val names = MONTH_NAMES_EN
        return "${names[month - 1]} $year"
    }
    return "${year}年${month}月"
}

val MONTH_NAMES_EN = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/** 取给定日期所在周的周一（中国习惯以周一为一周开始） */
fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

/** 日期加减天数 */
fun plusDays(d: LocalDate, n: Long): LocalDate = d.plusDays(n)

fun sameDate(a: LocalDate?, b: LocalDate?): Boolean = a == b
