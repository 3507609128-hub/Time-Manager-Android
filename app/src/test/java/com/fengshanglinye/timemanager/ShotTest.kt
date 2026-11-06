package com.fengshanglinye.timemanager

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 只为人工核对渲染效果：把竖屏周视图、双击详情卡片分别截图写到 `app/build/shots/`。
 * 不属于回归测试（不做断言），手动执行：
 *   gradlew --offline testDebugUnitTest --tests "com.fengshanglinye.timemanager.ShotTest"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class ShotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        val v = compose.activity.window.decorView
        val w = if (v.width > 0) v.width else 822
        val h = if (v.height > 0) v.height else 1782
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = File("build/shots").also { it.mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("shot -> ${f.absolutePath} ${w}x$h")
    }

    private fun seed() {
        AppState.setLang(LANG_ZH)
        val today = LocalDate.now()
        AppState.setTasks(
            listOf(
                Task(
                    id = 1, category = 1,
                    start = today.atTime(9, 30), end = today.atTime(11, 0),
                    title = "高等数学", note = "复习第三章：多元函数微分",
                    rules = listOf(Reminder(15, false), Reminder(1440, true))
                ),
                Task(
                    id = 2, category = 3,
                    start = today.atTime(14, 0), end = today.atTime(15, 30),
                    title = "设计评审", note = "V2 界面走查",
                    rules = listOf(Reminder(10, false))
                ),
                Task(
                    id = 3, category = 2,
                    start = today.atTime(19, 0), end = today.atTime(20, 0),
                    title = "英语听力", note = "",
                    rules = emptyList()
                )
            )
        )
        AppState.filterDate = null
        AppState.weekSelId = 0
        compose.waitForIdle()
    }

    @Test
    fun weekScreenPortrait() {
        seed()
        compose.onNodeWithText(S(Str.VIEW_WEEK)).performClick()
        shot("01_week_top")
    }

    @Test
    fun weekScreenScrolled() {
        seed()
        compose.onNodeWithText(S(Str.VIEW_WEEK)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TAG_WEEK_SCROLL, useUnmergedTree = true)
            .performTouchInput { swipeUp(startY = bottom - 10f, endY = 120f) }
        shot("02_week_scrolled")
    }

    @Test
    fun weekBlockDoubleClickDetail() {
        seed()
        compose.onNodeWithText(S(Str.VIEW_WEEK)).performClick()
        compose.waitForIdle()
        val block = compose.onNodeWithTag("${TAG_WEEK_BLOCK}1", useUnmergedTree = true)
        block.performScrollTo()
        compose.waitForIdle()
        block.performTouchInput { doubleClick() }
        shot("03_detail_dialog")
    }
}

/** 横屏/平板：双栏周视图（不受本轮竖屏改动影响，留一张图核对） */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
class ShotLandscapeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun weekLandscape() {
        AppState.setLang(LANG_ZH)
        val today = LocalDate.now()
        AppState.setTasks(
            listOf(
                Task(
                    id = 1, category = 1,
                    start = today.atTime(9, 30), end = today.atTime(11, 0),
                    title = "高等数学", note = "", rules = listOf(Reminder(15, false))
                )
            )
        )
        AppState.filterDate = null
        compose.waitForIdle()
        compose.onNodeWithText(S(Str.VIEW_WEEK)).performClick()
        compose.waitForIdle()
        val v = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = File("build/shots").also { it.mkdirs() }
        File(dir, "04_week_landscape.png").outputStream()
            .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
