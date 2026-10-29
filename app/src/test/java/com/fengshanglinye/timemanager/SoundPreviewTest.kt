package com.fengshanglinye.timemanager

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 「设置 → 提示音 → 试听」的接线回归。
 *
 * 真机反馈「试听没反应」，原因有两层：
 *  1. 试听走的是通知音量，手机把通知音量调 0 时按了完全没声音 → 改为 previewSound 走媒体音量；
 *  2. 系统提示音 URI 在部分 ROM 上取不到、MediaPlayer 静默失败 → 加了 Ringtone / beep 两层兜底。
 * 这里保证第 1 层的接线不再断：点「试听」必须把当前选中的音效编号交给上层。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SoundPreviewTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun testButtonReportsSelectedSound() {
        var gotSound = -1
        var gotPath = "<none>"
        AppState.setSettings(
            Settings(
                autostart = true, minStart = false, sound = SOUND_DEFAULT,
                toastSec = 12, wavPath = "", lang = LANG_ZH, theme = 3
            ),
            LANG_ZH
        )
        compose.setContent {
            SettingsDialog(
                t = Themes.sakura,
                onCancel = {},
                onApplied = { _, _ -> },
                onTestSound = { s, p -> gotSound = s; gotPath = p },
                onBrowseWav = { },
                onMessage = { }
            )
        }
        compose.waitForIdle()

        val testLabel = S(Str.SET_TEST)
        val nodes = compose.onAllNodesWithText(testLabel)
        assertTrue("设置窗口里找不到「$testLabel」按钮", nodes.fetchSemanticsNodes().isNotEmpty())
        nodes[0].performClick()
        compose.waitForIdle()

        assertEquals("试听没有把音效传给上层", SOUND_DEFAULT, gotSound)
        assertEquals("试听应把当前自定义音频路径一起传出去", "", gotPath)
    }

    @Test
    fun mutesSoundReportsHintInsteadOfPlaying() {
        var played = false
        var msg = ""
        AppState.setSettings(
            Settings(
                autostart = true, minStart = false, sound = SOUND_MUTE,
                toastSec = 12, wavPath = "", lang = LANG_ZH, theme = 3
            ),
            LANG_ZH
        )
        compose.setContent {
            SettingsDialog(
                t = Themes.sakura,
                onCancel = {},
                onApplied = { _, _ -> },
                onTestSound = { _, _ -> played = true },
                onBrowseWav = { },
                onMessage = { msg = it }
            )
        }
        compose.waitForIdle()
        compose.onAllNodesWithText(S(Str.SET_TEST))[0].performClick()
        compose.waitForIdle()

        assertTrue("选「静音」时不应该去播音效", !played)
        assertEquals(S(Str.SET_TEST_MUTE), msg)
    }
}
