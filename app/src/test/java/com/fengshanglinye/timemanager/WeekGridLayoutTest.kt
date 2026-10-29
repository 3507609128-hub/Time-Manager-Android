package com.fengshanglinye.timemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 周视图布局测试（宿主机 JVM + Robolectric，不需要真机或模拟器）。
 *
 * 针对真机上报过的两个问题回归：
 *  1. 只显示 0:00–7:00 —— 24 小时的内容被视口高度压扁
 *  2. 拖不动 —— 内容高度等于视口高度，可滚动范围是 0
 * 另外校验 7 天列与日程块的几何关系，防止"7 点之后排版乱"。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class WeekGridLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val theme = Themes.all[ThemeId.DEFAULT.index]

    @Before
    fun seed() {
        val day = AppState.weekAnchor
        AppState.setTasks(
            listOf(
                Task(
                    id = 1, category = 1,
                    start = day.atTime(9, 30), end = day.atTime(10, 30),
                    title = "高等数学", note = "复习第三章", rules = emptyList()
                ),
                Task(
                    id = 2, category = 3,
                    start = day.atTime(15, 0), end = day.atTime(16, 0),
                    title = "设计评审", note = "V2 界面走查", rules = emptyList()
                ),
                Task(
                    id = 3, category = 4,
                    start = day.atTime(21, 0), end = day.atTime(22, 30),
                    title = "健身训练", note = "带上运动鞋", rules = emptyList()
                )
            )
        )
        AppState.filterDate = null
        AppState.weekSelId = 0
    }

    private fun show() {
        compose.setContent { Box(Modifier.fillMaxSize()) { WeekGrid(t = theme) } }
        compose.waitForIdle()
    }

    /** 内容必须是完整的 24 小时（1440dp），不能被视口压扁成 7 小时 */
    @Test
    fun contentIsTwentyFourHoursTall() {
        show()
        compose.onNodeWithTag(TAG_WEEK_CONTENT).assertHeightIsEqualTo(1440.dp)
    }

    /** 7 个日期列都要是完整高度，且从左到右依次排开、互不重叠 */
    @Test
    fun sevenColumnsSpanFullDay() {
        show()
        val viewportW = compose.onNodeWithTag(TAG_WEEK_SCROLL)
            .fetchSemanticsNode().size.width.toFloat()
        var firstW = -1f
        var prevRight = Float.NEGATIVE_INFINITY
        for (i in 0 until 7) {
            val n = compose.onNodeWithTag(TAG_WEEK_COL + i).fetchSemanticsNode()
            assertEquals("第 $i 列高度应为 1440dp", 2880f, n.size.height.toFloat(), 1f)
            if (i == 0) firstW = n.size.width.toFloat()
            assertEquals("第 $i 列宽度应与其它列一致", firstW, n.size.width.toFloat(), 1f)
            assertTrue("第 $i 列不应与左邻列重叠", n.positionInRoot.x >= prevRight - 1f)
            prevRight = n.positionInRoot.x + n.size.width
        }
        // 时间轴宽度 = 可滚动区域宽度 - 7 列总宽，合理范围 44–54dp（88–108px）
        val axisW = viewportW - firstW * 7
        assertTrue("时间轴宽度应合理，实际 $axisW", axisW in 80f..120f)
        // 最后一列的右边缘应与可滚动区域右边缘对齐
        val vp = compose.onNodeWithTag(TAG_WEEK_SCROLL).fetchSemanticsNode()
        val vpRight = vp.positionInRoot.x + vp.size.width
        assertEquals("7 列应铺满右侧", vpRight, prevRight, 3f)
    }

    /** 可滚动范围必须明显大于 0，否则手指拖不动 */
    @Test
    fun gridIsScrollable() {
        show()
        val content = compose.onNodeWithTag(TAG_WEEK_CONTENT)
            .fetchSemanticsNode().size.height.toFloat()
        val viewport = compose.onNodeWithTag(TAG_WEEK_SCROLL)
            .fetchSemanticsNode().size.height.toFloat()
        assertTrue(
            "可滚动范围 $content-$viewport 必须 > 视口的 30%",
            content - viewport > viewport * 0.3f
        )
    }

    /** 默认停在 7:00（内容上移约 7 小时），并且能继续往下拖 */
    @Test
    fun startsAtSevenAndScrollsFurther() {
        show()
        val before = compose.onNodeWithTag(TAG_WEEK_CONTENT)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("初始应滚到约 7 小时（-840px）处，实际 $before", before <= -700f)

        compose.onNodeWithTag(TAG_WEEK_SCROLL).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val after = compose.onNodeWithTag(TAG_WEEK_CONTENT)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("上滑后内容应继续上移：$before -> $after", after < before - 50f)
    }

    /** 21:00 的日程块必须落在 21 点那条线上（原问题是 7 点后错位） */
    @Test
    fun lateEveningBlockSitsOnItsHourLine() {
        show()
        val block = compose.onNodeWithTag(TAG_WEEK_BLOCK + 3).fetchSemanticsNode()
        // 内容已上滚 840px；21:00 处 y = 21*120 - 840 = 1680（含 38dp 表头 76px）
        val expected = 21 * 120f - 840f + 76f
        assertEquals("21:00 的日程块应位于 $expected 附近", expected, block.positionInRoot.y, 3f)
        assertEquals("块高应为 1.5 小时 = 180px", 180f, block.size.height.toFloat(), 3f)
    }

    /** 晚上 22 点之后仍然有网格（不是被截断） */
    @Test
    fun gridReachesMidnight() {
        show()
        val col = compose.onNodeWithTag(TAG_WEEK_COL + 0).fetchSemanticsNode()
        // 列高 2880px = 24 小时，顶端相对屏幕在 -764 附近
        assertEquals(2880f, col.size.height.toFloat(), 1f)
    }
}

private fun LocalDate.atTime(h: Int, m: Int): LocalDateTime =
    atStartOfDay().withHour(h).withMinute(m)
