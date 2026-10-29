package com.fengshanglinye.timemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 添加日程对话框的窄屏布局测试（宿主机 Robolectric 实测像素，不需要真机）。
 *
 * 回归自真机反馈（320dp 宽的手机）：
 *  1. 日期框被挤成「2026/…」—— 左侧标签 76dp + 时间框 96dp 把日期宽度吃光了
 *  2. 「分类」右侧的控件被挤成 0 宽，和「重复」叠在一起
 *  3. 提醒规则那一行的按钮被挤出对话框边缘
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class AddTaskDialogLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val theme = Themes.all[ThemeId.DEFAULT.index]

    /** 真机宽度（dp）——320dp 是最窄的一档，720p 手机开了「大显示」后就是这个值 */
    private var phoneW = 320f

    @Before
    fun seed() {
        AppState.setLang(LANG_ZH)
        AppState.setTasks(emptyList())
    }

    private fun show() {
        val w = phoneW
        compose.setContent {
            Box(Modifier.size(w.dp, 640.dp)) {
                AddTaskDialog(
                    t = theme,
                    editing = null,
                    initialDate = LocalDate.of(2026, 10, 26),
                    onCancel = {},
                    onSaved = {},
                    onSavedBatch = {},
                    onMessage = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private fun widthOf(tag: String): Float =
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().size.width.toFloat()

    private fun rightOf(tag: String): Float {
        val n = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return n.positionInRoot.x + n.size.width
    }

    /** 日期必须能完整显示「2026/10/26」：xhdpi 下 100dp = 200px */
    @Test
    fun dateFieldIsWideEnoughAt320dp() {
        show()
        val w = widthOf(TAG_ADD_START_DATE)
        println("start date field width = ${w / 2f}dp")
        assertTrue("日期框只有 ${w / 2f}dp，装不下「2026/10/26」", w >= 200f)
        val t = widthOf(TAG_ADD_START_TIME)
        assertTrue("时间框过窄：${t / 2f}dp", t >= 120f)
    }

    /** 分类下拉必须有完整宽度，不能再被「重复」挤成 0 */
    @Test
    fun categoryChipIsNotSqueezed() {
        show()
        val cat = widthOf(TAG_ADD_CAT)
        println("category chip width = ${cat / 2f}dp")
        assertTrue("分类控件只有 ${cat / 2f}dp", cat >= 150f)
        val rep = widthOf(TAG_ADD_REPEAT)
        assertTrue("重复下拉宽度异常：${rep / 2f}dp", rep >= 150f)
    }

    /** 所有控件都不能越过对话框右边缘（原问题是按钮被切掉） */
    @Test
    fun nothingOverflowsDialog() {
        show()
        val limit = phoneW * 2f - 4f
        for (tag in listOf(
            TAG_ADD_START_DATE, TAG_ADD_START_TIME,
            TAG_ADD_END_DATE, TAG_ADD_END_TIME,
            TAG_ADD_CAT, TAG_ADD_REPEAT, TAG_ADD_RULE_ROW
        )) {
            val right = rightOf(tag)
            assertTrue("$tag 右边缘 $right 越过了 $limit", right <= limit)
        }
    }

    /** 320dp 太窄时日期与时间应折成两行，保证日期完整 */
    @Test
    fun narrowestPhoneStillReadable() {
        phoneW = 300f
        show()
        val date = widthOf(TAG_ADD_START_DATE)
        val time = widthOf(TAG_ADD_START_TIME)
        println("300dp: date=${date / 2f}dp time=${time / 2f}dp")
        assertTrue("300dp 下日期框只有 ${date / 2f}dp", date >= 200f)
    }

    /** 常见 411dp 手机：仍旧一行 [日期][时间]，日期框也要够宽 */
    @Test
    fun widePhoneKeepsSingleLine() {
        phoneW = 411f
        show()
        val date = widthOf(TAG_ADD_START_DATE)
        val time = widthOf(TAG_ADD_START_TIME)
        println("411dp: date=${date / 2f}dp time=${time / 2f}dp")
        assertTrue("411dp 下日期框只有 ${date / 2f}dp", date >= 220f)
        assertTrue("411dp 下时间框应更窄：${time / 2f}dp", time < date)
    }
}
