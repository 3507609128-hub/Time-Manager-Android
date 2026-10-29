package com.fengshanglinye.timemanager

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private fun seedTasks() {
    AppState.setLang(LANG_ZH)
    val today = LocalDate.now()
    AppState.setTasks(
        listOf(
            Task(
                id = 1, category = 1,
                start = today.atTime(9, 30), end = today.atTime(10, 30),
                title = "高等数学", note = "复习第三章", rules = emptyList()
            ),
            Task(
                id = 2, category = 3,
                start = today.atTime(15, 0), end = today.atTime(16, 0),
                title = "设计评审", note = "V2 界面走查", rules = emptyList()
            )
        )
    )
    AppState.filterDate = null
    AppState.weekSelId = 0
}

/**
 * 横竖屏布局测试（宿主机 Robolectric 实测像素）。
 *
 * 对应真机问题：竖屏下日历卡片把整屏吃光、日程表被挤成 0 高度，看起来“只有横屏能用”。
 * 现在竖屏是「日历在上、日程表在下」，横屏/平板仍旧双栏，这里把两种模式都断言住。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class PortraitLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun seed() = seedTasks()

    private class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    private fun bounds(tag: String): Bounds {
        val n = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return Bounds(
            n.positionInRoot.x, n.positionInRoot.y,
            n.positionInRoot.x + n.size.width, n.positionInRoot.y + n.size.height
        )
    }

    /** 竖屏：日历在上、日程表在日历下方，且日程表要占满剩下的空间 */
    @Test
    fun calendarSitsAboveAgenda() {
        compose.setContent { AppRoot() }
        compose.waitForIdle()

        val cal = bounds(TAG_CALENDAR_CARD)
        val ag = bounds(TAG_AGENDA_LIST)
        println("portrait cal=${cal.height / 2f}dp ag=${ag.height / 2f}dp")

        assertTrue("日历应在日程表上方：cal.bottom=${cal.bottom} ag.top=${ag.top}", cal.bottom <= ag.top + 1f)
        assertTrue("日历宽度应铺满：${cal.width / 2f}dp", cal.width > 700f)
        assertTrue("日程表宽度应铺满：${ag.width / 2f}dp", ag.width > 700f)
        // 日历不能吃掉整屏（原来这里是 0 高度日程表 / 整屏日历）
        assertTrue("日历高度 ${cal.height / 2f}dp 不应超过屏幕 62%", cal.height < 891f * 2f * 0.62f)
        assertTrue("日历高度 ${cal.height / 2f}dp 至少要放得下 4 行", cal.height > 280f)
        // 日程表必须留出足够的可读区域
        assertTrue("日程表只剩 ${ag.height / 2f}dp", ag.height > 400f)
    }

    /** 竖屏周视图：与月视图一样「日历在上、时间表在下」 */
    @Test
    fun weekViewSharesPortrait() {
        compose.setContent { AppRoot() }
        compose.waitForIdle()
        // 切到周视图（工具栏上的「周视图」按钮）
        compose.onNodeWithText(S(Str.VIEW_WEEK)).performClick()
        compose.waitForIdle()

        val cal = bounds(TAG_CALENDAR_CARD)
        val scroll = bounds(TAG_WEEK_SCROLL)
        println("portrait week cal=${cal.height / 2f}dp grid=${scroll.height / 2f}dp")

        // 1) 周视图也要有日历卡片，且在时间表上方
        assertTrue(
            "竖屏周视图应显示日历（cal.bottom=${cal.bottom} grid.top=${scroll.top}）",
            cal.bottom <= scroll.top + 1f
        )
        assertTrue("日历宽度应铺满：${cal.width / 2f}dp", cal.width > 600f)
        // 2) 时间表占满剩余空间
        assertTrue("时间表宽度应铺满：${scroll.width / 2f}dp", scroll.width > 600f)
        assertTrue("时间表只剩 ${scroll.height / 2f}dp", scroll.height > 300f)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class LandscapeLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun seed() = seedTasks()

    /** 横屏：与桌面版一致，日历在左、日程表在右，并排显示 */
    @Test
    fun calendarLeftAgendaRight() {
        compose.setContent { AppRoot() }
        compose.waitForIdle()

        val cal = compose.onNodeWithTag(TAG_CALENDAR_CARD, useUnmergedTree = true)
            .fetchSemanticsNode()
        val ag = compose.onNodeWithTag(TAG_AGENDA_LIST, useUnmergedTree = true)
            .fetchSemanticsNode()
        val calRight = cal.positionInRoot.x + cal.size.width
        val agLeft = ag.positionInRoot.x

        println("landscape cal=${cal.size.width / 2f}dp ag=${ag.size.width / 2f}dp")
        assertTrue("横屏日历应在左：calRight=$calRight agLeft=$agLeft", calRight <= agLeft + 1f)
        assertTrue("横屏日历宽度应约 340dp：${cal.size.width / 2f}dp", cal.size.width.toFloat() in 660f..720f)
        assertTrue("横屏日程表应占满右侧：${ag.size.width / 2f}dp", ag.size.width > 900f)
        // 两栏都应撑满可用高度（411dp * 2 - 工具栏与状态栏）
        assertTrue("横屏日历高度 ${cal.size.height / 2f}dp", cal.size.height > 500f)
        assertTrue("横屏日程表高度 ${ag.size.height / 2f}dp", ag.size.height > 500f)

        // 横屏时卡片被压矮，日期格也要装得下数字（真机反馈“日期数字显示不全”）
        val grid = compose.onNodeWithTag(TAG_CAL_GRID, useUnmergedTree = true).fetchSemanticsNode()
        val rows = 5   // 2026-10 是 5 行
        val cellH = grid.size.height.toFloat() / rows
        println("landscape calGrid=${grid.size.height / 2f}dp cellH=${cellH / 2f}dp")
        assertTrue("横屏日期格只有 ${cellH / 2f}dp，数字会被裁", cellH >= 24f * 2f)
        assertTrue("日期网格高度 ${grid.size.height / 2f}dp 应有 5 行", grid.size.height > 260f)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w740dp-h360dp-land-xhdpi")
class SmallLandscapeLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun seed() = seedTasks()

    /**
     * 矮横屏（常见手机横过来只有 360dp 高）：日历被压到最扁，
     * 日期数字必须仍然放得下，不能被格子裁掉。
     */
    @Test
    fun dateNumbersFitOnShortLandscape() {
        compose.setContent { AppRoot() }
        compose.waitForIdle()
        val grid = compose.onNodeWithTag(TAG_CAL_GRID, useUnmergedTree = true).fetchSemanticsNode()
        val rows = 5
        val cellH = grid.size.height.toFloat() / rows
        println("shortLandscape grid=${grid.size.height / 2f}dp cellH=${cellH / 2f}dp")
        // 10.5sp 数字(≈14dp) + 1dp 上边距 + 上下 2dp 内边距 → 至少 22dp 才不会裁
        assertTrue("矮横屏日期格只有 ${cellH / 2f}dp，数字会被裁", cellH >= 22f * 2f)
        val cal = compose.onNodeWithTag(TAG_CALENDAR_CARD, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("矮横屏日历宽度应约 340dp：${cal.size.width / 2f}dp", cal.size.width.toFloat() in 660f..720f)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-port-xhdpi")
class SmallPhoneLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun seed() = seedTasks()

    /** 320dp 窄屏（用户真机就是这种）：日历上方、日程表仍要有可读空间 */
    @Test
    fun smallPhoneStillShowsAgenda() {
        compose.setContent { AppRoot() }
        compose.waitForIdle()
        val cal = compose.onNodeWithTag(TAG_CALENDAR_CARD, useUnmergedTree = true).fetchSemanticsNode()
        val ag = compose.onNodeWithTag(TAG_AGENDA_LIST, useUnmergedTree = true).fetchSemanticsNode()
        val calBottom = cal.positionInRoot.y + cal.size.height
        println("small cal=${cal.size.height / 2f}dp ag=${ag.size.height / 2f}dp w=${cal.size.width / 2f}dp")

        assertTrue("日历应在日程表上方", calBottom <= ag.positionInRoot.y + 1f)
        assertTrue("日历高度 ${cal.size.height / 2f}dp 不应超过屏幕 62%", cal.size.height < 640f * 2f * 0.62f)
        assertTrue("日程表只剩 ${ag.size.height / 2f}dp", ag.size.height > 220f)
    }
}

private fun LocalDate.atTime(h: Int, m: Int): LocalDateTime =
    atTime(LocalTime.of(h, m))
