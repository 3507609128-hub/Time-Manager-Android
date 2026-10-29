package com.fengshanglinye.timemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置对话框（对应 main.cpp 的 DialogSettings）。
 *
 * 外观与启动
 *   语言：[中文 ▾]
 *   界面风格：[清新薄荷 ▾]
 *   ☑ 开机时自动启动
 *   ☐ 手动启动时也最小化（仅通知栏提醒）
 * ─────────────────────────
 * 提醒方式
 *   通知停留：[12 秒 ▾]
 *   提示音：[信息提示（叮咚） ▾] [试听]
 *   自定义音频：[__________] [浏览…]
 *   提示：选择“自定义音频文件”时，请指定一个音频文件作为提醒声音。
 * ─────────────────────────
 * [取消] [确定]
 * 制作人员：枫殇林叶
 * 联系邮箱：3507609128@qq.com
 */
@Composable
fun SettingsDialog(
    t: Theme,
    onCancel: () -> Unit,
    onApplied: (Settings, Int) -> Unit,
    onTestSound: (Int, String) -> Unit,
    onBrowseWav: ((String) -> Unit) -> Unit,
    onMessage: (String) -> Unit
) {
    val orig = AppState.settings
    var lang by remember { mutableStateOf(AppState.lang) }
    var autostart by remember { mutableStateOf(orig.autostart) }
    var minStart by remember { mutableStateOf(orig.minStart) }
    var sound by remember { mutableStateOf(orig.sound) }
    var toastSec by remember { mutableStateOf(orig.toastSec) }
    var wavPath by remember { mutableStateOf(orig.wavPath) }
    var theme by remember { mutableStateOf(ThemeId.of(orig.theme)) }

    var showLangSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showDurSheet by remember { mutableStateOf(false) }
    var showSoundSheet by remember { mutableStateOf(false) }

    BaseDialog(t, { onCancel() }) {
        DialogHeader(S(Str.SET_TITLE), t) { onCancel() }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            DialogCard(t) {
                SectionTitle(S(Str.SET_SEC_APPEAR), t)
                Spacer(Modifier.height(12.dp))

                FormRow(S(Str.SET_LANG), t) {
                    FakeDropdown(
                        when (lang) {
                            LANG_JA -> S(Str.LANG_JA_NAME)
                            LANG_EN -> S(Str.LANG_EN_NAME)
                            else -> S(Str.LANG_ZH_NAME)
                        }, t
                    ) { showLangSheet = true }
                }
                Spacer(Modifier.height(10.dp))

                FormRow(S(Str.SET_THEME), t) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            FakeDropdown(themeName(theme.index), t) { showThemeSheet = true }
                        }
                        Spacer(Modifier.width(10.dp))
                        // 主题色小预览
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in listOf(
                                Themes.of(theme).accent, Themes.of(theme).accentSoft,
                                Themes.of(theme).page
                            )) {
                                Box(
                                    Modifier
                                        .width(16.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(c)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                ToggleRow(S(Str.SET_AUTOSTART), autostart, t) { autostart = it }
                ToggleRow(S(Str.SET_MINSTART), minStart, t) { minStart = it }
            }

            Spacer(Modifier.height(14.dp))
            Hairline(t)
            Spacer(Modifier.height(14.dp))

            DialogCard(t) {
                SectionTitle(S(Str.SET_SEC_ALERT), t)
                Spacer(Modifier.height(12.dp))

                FormRow(S(Str.SET_DURATION), t) {
                    FakeDropdown(S(Str.SET_SECONDS).format(toastSec), t) { showDurSheet = true }
                }
                Spacer(Modifier.height(10.dp))

                FormRow(S(Str.SET_SOUND), t) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val testBtn: @Composable () -> Unit = {
                            OutlineButton(S(Str.SET_TEST), t) {
                                if (sound == SOUND_MUTE) onMessage(S(Str.SET_TEST_MUTE))
                                else if (sound == SOUND_WAV && wavPath.isEmpty()) onMessage(S(Str.SET_TEST_NO_WAV))
                                else onTestSound(sound, wavPath)
                            }
                        }
                        val dropdown: @Composable () -> Unit = {
                            FakeDropdown(soundName(sound), t) { showSoundSheet = true }
                        }
                        // 手机竖屏时「试听」放到下一行，避免提示音名称被截断
                        if (maxWidth >= 240.dp) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { dropdown() }
                                testBtn()
                            }
                        } else {
                            Column {
                                dropdown()
                                Spacer(Modifier.height(8.dp))
                                testBtn()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                FormRow(S(Str.SET_WAV), t) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(t.card)
                                    .padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Txt(wavPath, if (wavPath.isEmpty()) t.empty else t.text, 12.sp)
                            }
                        }
                        OutlineButton(S(Str.SET_BROWSE), t) {
                            onBrowseWav { picked -> wavPath = picked }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HintText(S(Str.SET_HINT), t)
            }

            Spacer(Modifier.height(16.dp))
            Hairline(t)
            Spacer(Modifier.height(12.dp))

            // 制作人员 / 联系邮箱（与原版一样放在最底部）
            Txt(S(Str.CREDITS_AUTHOR), t.muted, 12.sp)
            Spacer(Modifier.height(4.dp))
            Txt(
                S(Str.CREDITS_EMAIL).format("3507609128@qq.com"),
                t.accent, 12.sp
            )
        }

        Hairline(t)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineButton(S(Str.CANCEL), t) { onCancel() }
            Spacer(Modifier.width(10.dp))
            FilledButton(S(Str.OK), t) {
                // 语言保存为 -1 表示跟随系统；这里始终写入显式选择，与原版一致
                val s = orig.copy(
                    autostart = autostart,
                    minStart = minStart,
                    sound = sound,
                    toastSec = toastSec,
                    wavPath = wavPath,
                    lang = lang,
                    theme = theme.index
                )
                onApplied(s, lang)
            }
        }
    }

    if (showLangSheet) {
        SheetPicker(
            t = t,
            items = listOf(
                S(Str.LANG_ZH_NAME) to null,
                S(Str.LANG_JA_NAME) to null,
                S(Str.LANG_EN_NAME) to null
            ),
            selected = lang,
            onDismiss = { showLangSheet = false },
            onPick = { i -> lang = i; showLangSheet = false }
        )
    }
    if (showThemeSheet) {
        SheetPicker(
            t = t,
            items = (0 until ThemeId.COUNT).map {
                themeName(it) to Themes.of(ThemeId.of(it)).accent
            },
            selected = theme.index,
            onDismiss = { showThemeSheet = false },
            onPick = { i -> theme = ThemeId.of(i); showThemeSheet = false }
        )
    }
    if (showDurSheet) {
        SheetPicker(
            t = t,
            items = NOTIFY_DURATIONS.map { S(Str.SET_SECONDS).format(it) to null },
            selected = NOTIFY_DURATIONS.indexOf(toastSec).coerceAtLeast(0),
            onDismiss = { showDurSheet = false },
            onPick = { i -> toastSec = NOTIFY_DURATIONS[i]; showDurSheet = false }
        )
    }
    if (showSoundSheet) {
        SheetPicker(
            t = t,
            items = (0..SOUND_WAV).map { soundName(it) to null },
            selected = sound,
            onDismiss = { showSoundSheet = false },
            onPick = { i -> sound = i; showSoundSheet = false }
        )
    }
}

/**
 * 删除确认对话框（对应原版的 MessageBox 确认）。
 */
@Composable
fun ConfirmDialog(
    t: Theme,
    title: String,
    message: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalScrim(onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.card)
                .padding(18.dp)
        ) {
            Txt(title, t.text, 15.sp, FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Txt(message, t.muted, 13.sp, maxLines = 5)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlineButton(S(Str.CANCEL), t) { onCancel() }
                Spacer(Modifier.width(10.dp))
                FilledButton(S(Str.OK), t) { onConfirm() }
            }
        }
    }
}

/**
 * 轻量提示条（对应原版 MessageBox 的“提示”：上限、格式校验等），
 * 安卓上用底部横幅，不打断操作。
 */
@Composable
fun MessageBar(t: Theme, text: String?, onDismiss: () -> Unit) {
    if (text == null) return
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(t.toastCard)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(t.today)
            )
            Spacer(Modifier.width(10.dp))
            Txt(text, t.toastHead, 13.sp, modifier = Modifier.weight(1f), maxLines = 3)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.clickable { onDismiss() }) {
                Txt(S(Str.OK), t.accent, 13.sp, FontWeight.Medium)
            }
        }
    }
}
