package com.fengshanglinye.timemanager

import androidx.compose.ui.graphics.Color

/**
 * 界面风格（与 Win32 版一一对应）
 *  0 经典白 / 1 清新薄荷 / 2 奶油晨光 / 3 樱粉初雪，默认清新薄荷
 */
enum class ThemeId(val index: Int) {
    CLASSIC(0), MINT(1), CREAM(2), SAKURA(3);

    companion object {
        const val COUNT = 4
        val DEFAULT = MINT
        fun of(i: Int): ThemeId = entries.firstOrNull { it.index == i } ?: DEFAULT
    }
}

/**
 * 一套完整配色。字段顺序与 main.cpp 里的 struct Theme 完全一致，
 * 这样两端的颜色值可以逐个对照。
 */
data class Theme(
    val page: Color,          // 页面底色
    val card: Color,          // 卡片 / 列表 / 日历底色
    val border: Color,        // 卡片边框 / 网格线
    val text: Color,          // 主文字
    val muted: Color,         // 次要文字
    val accent: Color,        // 主强调色
    val accentDark: Color,    // 强调色深色
    val accentSoft: Color,    // 选中浅底
    val hover: Color,         // 悬停
    val today: Color,         // 今天虚线框
    val sun: Color,           // 周日
    val sat: Color,           // 周六
    val weekendBg: Color,     // 周末列淡底
    val nightBg: Color,       // 周视图夜间时段
    val axisBg: Color,        // 周视图时间轴 / 表头
    val altRow: Color,        // 列表交替行
    val todayBg: Color,       // 周视图今天列
    val line: Color,          // 周视图网格线
    val now: Color,           // 当前时间线
    val empty: Color,         // 空状态文字
    val toastBg: Color,       // Toast 外框底
    val toastBorder: Color,   // Toast 边框
    val toastCard: Color,     // Toast 卡片
    val toastTitle: Color,    // Toast 应用名
    val toastHead: Color,     // Toast 标题
    val toastBody: Color,     // Toast 正文
    val tipBg: Color,         // 悬停气泡底
    val tipText: Color,       // 悬停气泡主文字
    val tipMuted: Color,      // 悬停气泡次要文字
    val dialogBg: Color       // 对话框底色
)

private fun c(r: Int, g: Int, b: Int): Color = Color(r, g, b)

object Themes {

    /** 0 经典白 */
    val classic = Theme(
        page = c(238, 242, 247), card = c(255, 255, 255), border = c(222, 228, 238),
        text = c(38, 38, 38), muted = c(128, 128, 128), accent = c(0, 120, 212),
        accentDark = c(0, 76, 140), accentSoft = c(224, 238, 253), hover = c(242, 246, 251),
        today = c(224, 78, 26), sun = c(196, 64, 64), sat = c(64, 104, 176),
        weekendBg = c(245, 246, 250), nightBg = c(243, 246, 250), axisBg = c(247, 249, 252),
        altRow = c(247, 249, 252), todayBg = c(238, 246, 255), line = c(228, 232, 238),
        now = c(224, 78, 26), empty = c(160, 164, 170),
        toastBg = c(248, 249, 251), toastBorder = c(225, 226, 230), toastCard = c(255, 255, 255),
        toastTitle = c(140, 140, 140), toastHead = c(28, 30, 34), toastBody = c(96, 100, 108),
        tipBg = c(255, 255, 255), tipText = c(30, 30, 30), tipMuted = c(86, 86, 86),
        dialogBg = c(240, 240, 240)
    )

    /** 1 清新薄荷：柔和薄荷页 + 青绿强调 + 珊瑚今日 */
    val mint = Theme(
        page = c(241, 250, 246), card = c(255, 255, 255), border = c(216, 233, 225),
        text = c(31, 56, 49), muted = c(110, 141, 131), accent = c(42, 157, 130),
        accentDark = c(27, 116, 97), accentSoft = c(221, 243, 235), hover = c(233, 248, 242),
        today = c(240, 138, 93), sun = c(217, 120, 104), sat = c(79, 143, 166),
        weekendBg = c(239, 249, 245), nightBg = c(242, 250, 247), axisBg = c(247, 252, 250),
        altRow = c(247, 252, 250), todayBg = c(230, 247, 241), line = c(220, 235, 228),
        now = c(240, 138, 93), empty = c(154, 176, 168),
        toastBg = c(245, 252, 249), toastBorder = c(217, 235, 227), toastCard = c(255, 255, 255),
        toastTitle = c(126, 156, 146), toastHead = c(22, 53, 44), toastBody = c(94, 126, 115),
        tipBg = c(255, 255, 255), tipText = c(30, 58, 50), tipMuted = c(95, 127, 116),
        dialogBg = c(242, 251, 247)
    )

    /** 2 奶油晨光：暖奶油页 + 琥珀强调 */
    val cream = Theme(
        page = c(253, 248, 239), card = c(255, 255, 255), border = c(238, 223, 200),
        text = c(63, 51, 38), muted = c(141, 123, 100), accent = c(217, 144, 63),
        accentDark = c(163, 97, 30), accentSoft = c(251, 235, 215), hover = c(253, 243, 229),
        today = c(217, 103, 74), sun = c(200, 107, 82), sat = c(111, 142, 110),
        weekendBg = c(251, 244, 233), nightBg = c(253, 248, 240), axisBg = c(254, 250, 243),
        altRow = c(254, 250, 243), todayBg = c(252, 239, 224), line = c(240, 228, 210),
        now = c(217, 103, 74), empty = c(179, 161, 139),
        toastBg = c(255, 251, 244), toastBorder = c(239, 225, 204), toastCard = c(255, 255, 255),
        toastTitle = c(160, 138, 112), toastHead = c(59, 47, 35), toastBody = c(123, 105, 85),
        tipBg = c(255, 255, 255), tipText = c(62, 50, 37), tipMuted = c(130, 111, 89),
        dialogBg = c(254, 249, 241)
    )

    /** 3 樱粉初雪：淡粉页 + 玫红强调 */
    val sakura = Theme(
        page = c(255, 246, 249), card = c(255, 255, 255), border = c(242, 220, 229),
        text = c(67, 49, 58), muted = c(148, 122, 134), accent = c(217, 108, 146),
        accentDark = c(168, 70, 107), accentSoft = c(251, 226, 236), hover = c(255, 240, 245),
        today = c(229, 138, 78), sun = c(217, 108, 122), sat = c(138, 123, 184),
        weekendBg = c(255, 243, 247), nightBg = c(255, 247, 250), axisBg = c(255, 250, 252),
        altRow = c(255, 250, 252), todayBg = c(252, 235, 242), line = c(244, 225, 232),
        now = c(217, 108, 146), empty = c(180, 154, 165),
        toastBg = c(255, 248, 251), toastBorder = c(244, 223, 231), toastCard = c(255, 255, 255),
        toastTitle = c(165, 139, 150), toastHead = c(63, 46, 54), toastBody = c(133, 109, 120),
        tipBg = c(255, 255, 255), tipText = c(67, 49, 58), tipMuted = c(140, 115, 126),
        dialogBg = c(255, 248, 251)
    )

    val all = listOf(classic, mint, cream, sakura)

    fun of(id: ThemeId): Theme = all[id.index]
}

/**
 * 活动分类（下标 1..CAT_COUNT 与 Win32 版一致）
 *  g_catFill 为方块饱和色（配白字），g_catSoft 为月历浅色底（配深字）
 */
object Categories {
    const val COURSE = 1
    const val CHORES = 2
    const val WORK = 3
    const val SPORT = 4
    const val FUN = 5
    const val OTHER = 6
    const val COUNT = 6
    const val DEFAULT = COURSE

    /** 下标 0 占位，保持与 Win32 版下标从 1 开始的约定一致 */
    private val fill = arrayOf(
        Color.Transparent,
        c(0x5B, 0x9B, 0xD5),  // 1 课程 蓝
        c(0x70, 0xAD, 0x47),  // 2 家务 绿
        c(0x9B, 0x7F, 0xD4),  // 3 工作 紫
        c(0xE8, 0x71, 0x8A),  // 4 运动 红
        c(0xF5, 0xA6, 0x23),  // 5 娱乐 橙
        c(0x9A, 0xA0, 0xA6)   // 6 其他 灰
    )

    private val soft = arrayOf(
        Color.Transparent,
        c(0xDA, 0xE9, 0xF8),  // 1 课程 浅蓝
        c(0xE2, 0xF0, 0xD9),  // 2 家务 浅绿
        c(0xEC, 0xE6, 0xF7),  // 3 工作 浅紫
        c(0xFB, 0xE3, 0xE8),  // 4 运动 浅红
        c(0xFD, 0xEF, 0xD3),  // 5 娱乐 浅橙
        c(0xEC, 0xEE, 0xEF)   // 6 其他 浅灰
    )

    fun normalize(cat: Int): Int = if (cat < 1 || cat > COUNT) OTHER else cat

    fun fill(cat: Int): Color = fill[normalize(cat)]
    fun soft(cat: Int): Color = soft[normalize(cat)]
    fun list(): IntArray = intArrayOf(COURSE, CHORES, WORK, SPORT, FUN, OTHER)
}

/** 默认提醒规则（分钟）：1 天、1 小时、30 分、5 分 */
val DEFAULT_RULES = intArrayOf(1440, 60, 30, 5)
