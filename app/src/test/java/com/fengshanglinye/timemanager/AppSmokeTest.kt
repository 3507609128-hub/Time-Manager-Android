package com.fengshanglinye.timemanager

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 整机冒烟测试：在手机尺寸下把整个 AppRoot（工具栏 + 月历 + 活动流 + 弹窗层）组合起来，
 * 确认不会崩溃、根节点尺寸正确。宿主机 Robolectric 运行，无需真机。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appRootComposesOnPhone() {
        compose.setContent { AppRoot() }
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode()
        // 411×891dp @2x = 822×1782px
        assertTrue("根节点宽度应为 822px，实际 ${root.size.width}", root.size.width >= 800)
        assertTrue("根节点高度应为 1782px，实际 ${root.size.height}", root.size.height >= 1700)
    }
}
