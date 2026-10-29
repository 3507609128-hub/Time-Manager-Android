package com.fengshanglinye.timemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.min
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val TAG_CALENDAR_CARD = "calendar_card"
internal const val TAG_CAL_GRID = "cal_grid"

/**
 * 左侧月历卡片（月视图与周视图共用，对应 main.cpp 的 DrawCalendar + CalGetLayout）。
 *
 *  • 顶部：‹  2026年10月  ›
 *  • 星期条：日 一 二 三 四 五 六（周日红、周六蓝）
 *  • 日期格：有日程 → 首个分类的浅色底 + 加粗数字 + 最多 3 个饱和色圆点
 *            今天   → 橙色虚线圆角框
 *            选中   → 主色实线圆角框
 *            周末列 → 淡色底
 *  • 底部：今日（10/26）有 N 个日程 + 开机自动启动复选框
 */
@Composable
fun CalendarCard(
    t: Theme,
    modifier: Modifier = Modifier,
    year: Int,
    month: Int,
    selected: LocalDate?,
    /** 竖屏模式传入固定格子高度；为 null 时沿用双栏的“撑满可用高度”行为 */
    cellHeight: Dp? = null,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onToggleAutostart: (Boolean) -> Unit
) {
    val today = LocalDate.now()
    val first = LocalDate.of(year, month, 1)
    val days = daysInMonth(year, month)
    val firstW = first.dayOfWeek.value % 7           // 周日 = 0，与 Win32 一致
    val rows = (firstW + days + 6) / 7               // 4 ~ 6 行自动适配

    // 左右滑动翻月：对应原版的滚轮 / 方向键切换月份
    var dragAcc by remember { mutableStateOf(0f) }
    val swipe = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { dragAcc = 0f },
            onDragEnd = {
                if (dragAcc > 60f) onPrevMonth()
                else if (dragAcc < -60f) onNextMonth()
                dragAcc = 0f
            },
            onDragCancel = { dragAcc = 0f }
        ) { _, dx -> dragAcc += dx }
    }

    Card(t, modifier.testTag(TAG_CALENDAR_CARD)) {
        // cellHeight != null → 竖屏模式：卡片按内容高度排版（日历在上、日程表在下）
        // cellHeight == null → 双栏模式：日历撑满可用高度（与原版桌面一致）
        Column(if (cellHeight == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {

            /*-------------------- 月份标题行 --------------------*/
            Row(
                Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clickable { onPrevMonth() },
                    contentAlignment = Alignment.Center
                ) { Chevron(-1, t.muted, 18.dp) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Txt(fmtMonthTitle(year, month), t.text, 15.sp, FontWeight.Bold)
                }
                Box(
                    Modifier.size(34.dp).clickable { onNextMonth() },
                    contentAlignment = Alignment.Center
                ) { Chevron(1, t.muted, 18.dp) }
            }

            /*-------------------- 星期条 --------------------*/
            Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 4.dp)) {
                for (i in 0 until 7) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        val c = when (i) {
                            0 -> t.sun
                            6 -> t.sat
                            else -> t.muted
                        }
                        Txt(weekShort(i), c, 12.sp)
                    }
                }
            }

            /*-------------------- 日期网格 --------------------*/
            if (cellHeight == null) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp).then(swipe)
                ) {
                    val cellW = maxWidth / 7
                    val availH = maxHeight
                    // 与原版一致：优先撑满剩余高度，单格最高 64dp
                    val cellH = min(availH.value / rows, 64f).dp
                    // 网格整体在剩余空间里垂直居中
                    val offsetY = ((availH.value - cellH.value * rows) / 2f)
                        .coerceAtLeast(0f).dp
                    Box(Modifier.fillMaxWidth().offset(y = offsetY)) {
                        DateGrid(
                            t, year, month, firstW, days, rows,
                            today, selected, cellW, cellH, onPickDate
                        )
                    }
                }
            } else {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp).then(swipe)
                ) {
                    DateGrid(
                        t, year, month, firstW, days, rows,
                        today, selected, maxWidth / 7, cellHeight, onPickDate
                    )
                }
            }

            /*-------------------- 今日提示 + 开机自启 --------------------*/
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                val cnt = AppState.countOnDate(today)
                Txt(S(Str.STAT_TODAY).format(fmtDateShort(today), cnt), t.muted, 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onToggleAutostart(!AppState.settings.autostart)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckBox(AppState.settings.autostart, t, 17.dp)
                    Spacer(Modifier.width(8.dp))
                    Txt(S(Str.SET_AUTOSTART), t.text, 12.sp)
                }
            }
        }
    }
}

/** 单个日期格：底色 → 数字 → 圆点 → 边框（顺序与原版一致） */
@Composable
private fun DayCell(
    t: Theme,
    d: LocalDate,
    w: Dp,
    h: Dp,
    hasTask: Boolean,
    cats: List<Int>,
    isToday: Boolean,
    isSel: Boolean,
    isWeekend: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val pad = 2.dp
    val innerW = w - pad * 2
    val innerH = h - pad * 2

    val fill: Color? = when {
        hasTask -> Categories.soft(cats[0])
        isSel -> t.accentSoft
        isWeekend -> t.weekendBg
        else -> null
    }
    val numColor = when {
        hasTask -> t.text
        isSel -> t.accentDark
        isWeekend -> t.muted
        else -> t.text
    }
    val bold = hasTask || isToday || isSel

    // 横屏时整张卡片被压矮，单格可能只有 25～30dp：字号、上边距、圆点都要跟着收，
    // 否则数字底部会被格子裁掉（真机横屏反馈的“日期数字显示不全”）。
    val compact = innerH < 32.dp
    val numSize = when {
        compact -> 10.5.sp
        d.dayOfMonth >= 10 -> 12.sp
        else -> 13.sp
    }
    val numTop = if (compact) 1.dp else 6.dp
    val dotR = if (compact) 2.dp else 2.5.dp
    val dotBottom = if (compact) 2.dp else 5.dp

    Box(Modifier.size(w, h).clickable { onClick() }) {
        Box(
            Modifier
                .padding(pad)
                .size(innerW, innerH)
                .clip(shape)
                .let { m -> if (fill != null) m.background(fill) else m }
                // 选中：主色实线框
                .let { m -> if (isSel) m.border(2.dp, t.accent, shape) else m }
        ) {
            // 今天：橙色虚线框（Compose 无内置虚线边框，用 Canvas 画圆角虚线）
            if (isToday) {
                DashedRoundBorder(t.today, 8.dp, 2.dp)
            }

            Txt(
                "${d.dayOfMonth}",
                numColor,
                numSize,
                if (bold) FontWeight.Bold else FontWeight.Normal,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = numTop)
            )

            // 分类圆点：最多 3 个，居中排列在格子底部
            if (cats.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = dotBottom),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (c in cats.take(3)) {
                        Dot(Categories.fill(c), dotR)
                        Spacer(Modifier.width(3.dp))
                    }
                }
            }
        }
    }
}

/** 日期格子布局（两种模式共用：双栏撑满 / 竖屏固定格子高度） */
@Composable
private fun DateGrid(
    t: Theme,
    year: Int,
    month: Int,
    firstW: Int,
    days: Int,
    rows: Int,
    today: LocalDate,
    selected: LocalDate?,
    cellW: Dp,
    cellH: Dp,
    onPickDate: (LocalDate) -> Unit
) {
    Column(Modifier.fillMaxWidth().testTag(TAG_CAL_GRID)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().height(cellH)) {
                for (col in 0 until 7) {
                    val day = r * 7 + col - firstW + 1
                    Box(Modifier.width(cellW).height(cellH)) {
                        if (day in 1..days) {
                            val d = LocalDate.of(year, month, day)
                            DayCell(
                                t = t, d = d, w = cellW, h = cellH,
                                hasTask = AppState.countOnDate(d) > 0,
                                cats = AppState.dayCategories(d, 3),
                                isToday = d == today,
                                isSel = d == selected,
                                isWeekend = col == 0 || col == 6,
                                onClick = { onPickDate(d) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 圆角虚线框（对应 DrawDashedRoundRect） */
@Composable
fun DashedRoundBorder(
    color: Color,
    radius: Dp = 8.dp,
    width: Dp = 2.dp,
    dash: Dp = 3.dp
) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val r = radius.toPx()
        val sw = width.toPx()
        // 描边内缩半个线宽，避免虚线被裁掉
        val inset = sw / 2f
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                size.width - sw, size.height - sw
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = sw,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(dash.toPx(), dash.toPx()), 0f
                )
            )
        )
    }
}
