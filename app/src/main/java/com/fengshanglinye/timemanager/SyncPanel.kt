package com.fengshanglinye.timemanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 双端同步的界面部分。
 *
 * 数据来源是 [Sync]（同步引擎）与 [SyncUi]（界面状态）；本文件只负责显示与转发操作，
 * 合并算法在 SyncMerge.kt，协议/网络在 Sync.kt。
 */
object SyncUi {
    var status by mutableStateOf(0)          // 0=未连接 1=连接中 2=已连接
    var enabled by mutableStateOf(true)
    var code by mutableStateOf("")
    var devId by mutableStateOf("")
    var tasksApplied by mutableStateOf(0)
    var settingsApplied by mutableStateOf(0)
    val log = mutableStateListOf<String>()

    fun addLog(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        log.add("$t  $msg")
        while (log.size > 200) log.removeAt(0)
    }
}

fun syncStatusText(status: Int, enabled: Boolean): String =
    when {
        !enabled -> S(Str.SYNC_ST_OFF)
        status == Sync.ST_ONLINE -> S(Str.SYNC_ST_ON)
        else -> S(Str.SYNC_ST_WAIT)
    }

private fun syncStatusColor(t: Theme, status: Int, enabled: Boolean): Color =
    when {
        !enabled -> t.muted
        status == Sync.ST_ONLINE -> Color(0xFF16A34A)
        else -> Color(0xFFD97706)
    }

/** 设置对话框里的「双端同步」分组（配对码 + 状态 + 打开面板）。 */
@Composable
fun SyncSection(t: Theme, onOpen: () -> Unit) {
    SectionTitle(S(Str.SYNC_SEC_TITLE), t)
    Spacer(Modifier.height(12.dp))

    FormRow(S(Str.SYNC_CODE_LABEL), t) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(
                if (SyncUi.code.isEmpty()) "------" else SyncUi.code,
                t.accent, 20.sp, FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            StatusDot(t, SyncUi.status, SyncUi.enabled)
            Spacer(Modifier.weight(1f))
            OutlineButton(S(Str.SYNC_OPEN), t) { onOpen() }
        }
    }

    Spacer(Modifier.height(8.dp))
    HintText(S(Str.SYNC_SEC_HINT), t)
}

@Composable
private fun StatusDot(t: Theme, status: Int, enabled: Boolean) {
    val c = syncStatusColor(t, status, enabled)
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Spacer(Modifier.width(5.dp))
        Txt(syncStatusText(status, enabled), c, 11.sp)
    }
}

/**
 * 同步面板：配对码 + 二维码 + 立即同步/暂停 + 日志。
 * 与电脑端互联软件的窗口内容一一对应。
 */
@Composable
fun SyncPanel(t: Theme, onClose: () -> Unit) {
    var showInput by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var scanFile by remember { mutableStateOf<File?>(null) }

    // 扫码：拍一张照片后解码；相机不可用就退回相册
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) {
            SyncUi.addLog(S(Str.SYNC_SCAN_CANCEL))
            return@rememberLauncherForActivityResult
        }
        applyScannedText(QrScan.decodeFile(scanFile))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            SyncUi.addLog(S(Str.SYNC_SCAN_CANCEL))
            return@rememberLauncherForActivityResult
        }
        val bmp = try {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Throwable) {
            null
        }
        applyScannedText(QrScan.decodeBitmap(bmp))
    }
    fun startScan() {
        val f = File(ctx.cacheDir, "qr_scan.jpg")
        try {
            if (f.exists()) f.delete()
        } catch (e: Throwable) {
            // 删不掉也无所谓，相机应用会覆盖
        }
        scanFile = f
        val uri = try {
            FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        } catch (e: Throwable) {
            null
        }
        if (uri == null) {
            gallery.launch("image/*")
            return
        }
        try {
            camera.launch(uri)
        } catch (e: Throwable) {
            SyncUi.addLog(S(Str.SYNC_SCAN_NO_CAMERA))
            gallery.launch("image/*")
        }
    }

    BaseDialog(t, { onClose() }) {
        DialogHeader(S(Str.SYNC_TITLE), t) { onClose() }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // 状态 + 说明
            Card(t) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(S(Str.SYNC_TITLE), t.text, 14.sp, FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Txt(S(Str.SYNC_DEVICE_ID).format(SyncUi.devId), t.muted, 11.sp)
                    }
                    StatusDot(t, SyncUi.status, SyncUi.enabled)
                }
            }
            Spacer(Modifier.height(10.dp))

            // 配对码 + 二维码
            Card(t) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt(S(Str.SYNC_CODE_LABEL), t.text, 13.sp, FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Txt(
                                if (SyncUi.code.isEmpty()) "------" else SyncUi.code,
                                t.accent, 34.sp, FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Txt(S(Str.SYNC_HINT_SCAN), t.muted, 11.sp, maxLines = 3)
                        }
                        Spacer(Modifier.width(10.dp))
                        SyncQr(SyncUi.code, 108.dp, t)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledButton(S(Str.SYNC_NEW), t, Modifier.weight(1f)) {
                            SyncUi.code = Sync.newCode()
                            Sync.setEnabled(true)
                            SyncUi.enabled = true
                            SyncUi.addLog(S(Str.SYNC_NEWCODE).format(SyncUi.code))
                        }
                        OutlineButton(S(Str.SYNC_ENTER), t, Modifier.weight(1f)) { showInput = true }
                        OutlineButton(S(Str.SYNC_COPY), t) {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText("配对码", SyncUi.code)
                            )
                            SyncUi.addLog(S(Str.SYNC_COPIED).format(SyncUi.code))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // 扫一扫：用相机拍电脑端屏幕上的二维码，识别出配对码后自动填好
                    OutlineButton(S(Str.SYNC_SCAN), t, Modifier.fillMaxWidth()) { startScan() }
                }
            }
            Spacer(Modifier.height(10.dp))

            // 日志
            Card(t) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(S(Str.SYNC_LOG), t.text, 13.sp, FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Txt(
                            S(Str.SYNC_APPLIED).format(SyncUi.tasksApplied, SyncUi.settingsApplied),
                            t.muted, 10.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(S(Str.SYNC_NOW), t, Modifier.weight(1f)) {
                            Sync.setEnabled(true)
                            Sync.forceResync()
                            SyncUi.enabled = true
                            SyncUi.addLog(S(Str.SYNC_NOW_DONE))
                        }
                        OutlineButton(
                            if (SyncUi.enabled) S(Str.SYNC_PAUSE) else S(Str.SYNC_RESUME),
                            t, Modifier.weight(1f)
                        ) {
                            val v = !SyncUi.enabled
                            Sync.setEnabled(v)
                            SyncUi.enabled = v
                            SyncUi.addLog(if (v) S(Str.SYNC_RESUMED) else S(Str.SYNC_PAUSED))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(t.page)
                            .border(1.dp, t.border, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            val lines = SyncUi.log
                            if (lines.isEmpty()) {
                                Txt("—", t.muted, 11.sp)
                            } else {
                                for (i in lines.indices.reversed()) {
                                    Txt(lines[i], t.muted, 11.sp, maxLines = 2)
                                    Spacer(Modifier.height(3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Hairline(t)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineButton(S(Str.SYNC_CLOSE), t) { onClose() }
        }
    }

    if (showInput) {
        SyncInputDialog(t, onDismiss = { showInput = false }) { text ->
            showInput = false
            if (Sync.setCode(text)) {
                SyncUi.code = Sync.getCode()
                SyncUi.enabled = true
                SyncUi.addLog(S(Str.SYNC_SETCODE).format(SyncUi.code))
            } else {
                SyncUi.addLog(S(Str.SYNC_BADCODE))
            }
        }
    }
}

@Composable
private fun SyncQr(code: String, size: androidx.compose.ui.unit.Dp, t: Theme) {
    val matrix = remember(code) { if (code.length == 6) Qr.build("TM2:$code") else null }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, t.border, RoundedCornerShape(6.dp))
            .padding(7.dp)
    ) {
        if (matrix == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Txt("QR", t.muted, 11.sp)
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val cell = this.size.width / 21f
                for (y in 0 until 21) {
                    for (x in 0 until 21) {
                        if (matrix[y][x]) {
                            drawRect(
                                Color(0xFF111111),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.6f, cell + 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncInputDialog(t: Theme, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    BaseDialog(t, onDismiss) {
        DialogHeader(S(Str.SYNC_INPUT_TITLE), t) { onDismiss() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Txt(S(Str.SYNC_INPUT_HINT), t.muted, 12.sp, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(t.card)
                    .border(1.dp, t.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.uppercase(Locale.US).filter { it.isLetterOrDigit() }.take(6)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.accent
                    ),
                    cursorBrush = SolidColor(t.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Hairline(t)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineButton(S(Str.CANCEL), t) { onDismiss() }
            Spacer(Modifier.width(10.dp))
            FilledButton(S(Str.OK), t) { onOk(text) }
        }
    }
}

/** 面板打开时把日志里新来的条目滚动到底（视觉上等价于电脑端的逆序日志）。 */
@Composable
fun SyncLogAutoScroll(state: androidx.compose.foundation.ScrollState) {
    val n = SyncUi.log.size
    LaunchedEffect(n) {
        if (n > 0) state.scrollTo(state.maxValue)
    }
}

/** 扫码结果统一处理：解析出配对码就设进去。 */
private fun applyScannedText(text: String?) {
    if (text == null) {
        SyncUi.addLog(S(Str.SYNC_SCAN_FAIL))
        return
    }
    val code = QrScan.extractCode(text)
    if (code == null) {
        SyncUi.addLog(S(Str.SYNC_SCAN_NOCODE))
        return
    }
    if (Sync.setCode(code)) {
        SyncUi.code = Sync.getCode()
        SyncUi.enabled = true
        Sync.setEnabled(true)
        SyncUi.addLog(S(Str.SYNC_SCAN_OK).format(SyncUi.code))
    } else {
        SyncUi.addLog(S(Str.SYNC_BADCODE).format(code))
    }
}