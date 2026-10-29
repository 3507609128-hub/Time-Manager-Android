package com.fengshanglinye.timemanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max

/**
 * 周视图右侧的时间表（对应原版 DrawWeek + WeekBuildBlocks）。
 *
 *  • 表头：一 10/19 … 日 10/25，今天那一列的标题用主色填充白字
 *  • 网格：0:00–24:00 每小时一条线，夜间（<7 点、≥22 点）淡色底，今天整列淡色高亮
 *  • 日程块：分类饱和色圆角矩形，白字粗体标题，块足够高时再显示时间与备注
 *  • 重叠日程自动横向分列（最多 4 列）
 *  • 当前时间：橙色细线 + 左端圆点
 *
 * 滚动用 verticalScroll 实现：内容是一个固定 24 小时高度的 Box，
 * 放在可滚动容器里才不会被视口高度压扁（否则只能看到 0:00–7:00 且无法滚动）。
 */
@Composable
fun WeekGrid(
    t: Theme,
    modifier: Modifier = Modifier,
    hourHeight: Dp = 60.dp
) {
    val anchor = AppState.weekAnchor
    val monday = mondayOf(anchor)
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    val density = LocalDensity.current

    val headerH = 38.dp
    val contentH = hourHeight * 24

    // 打开时停在 7:00，与原版“7:00–23:00 可滚动”一致。
    // 用初始值而不是 LaunchedEffect：首帧测量前滚动会因 maxValue 未就绪被钳回 0:00。
    val initialScroll = with(density) { (hourHeight * 7).roundToPx() }
    val scrollState = rememberScrollState(initial = initialScroll)

    Card(t, modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 手机竖屏列宽很窄（约 40dp），时间轴和字号都要收一点
            val axisW = if (maxWidth < 400.dp) 44.dp else 54.dp
            val colW = (maxWidth - axisW) / 7
            val narrow = colW < 50.dp
            val blocks = remember(anchor, AppState.tasks.toList()) { buildWeekBlocks(monday) }

            Column(Modifier.fillMaxSize()) {

                /*-------------------- 表头 --------------------*/
                Row(Modifier.fillMaxWidth().height(headerH)) {
                    Box(Modifier.width(axisW).fillMaxHeight())
                    for (i in 0 until 7) {
                        val d = monday.plusDays(i.toLong())
                        val isToday = d == today
                        Box(
                            Modifier
                                .width(colW)
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .let { m -> if (isToday) m.background(t.accent) else m }
                                    .padding(horizontal = if (narrow) 3.dp else 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dc = when {
                                    isToday -> Color.White
                                    i == 6 -> t.sun
                                    i == 5 -> t.sat
                                    else -> t.text
                                }
                                val fs = if (narrow) 10 else 11
                                if (narrow) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Txt(weekShort(i + 1), dc, fs.sp)
                                        Txt("${d.dayOfMonth}", dc, fs.sp, FontWeight.Medium)
                                    }
                                } else {
                                    Txt(weekShort(i + 1), dc, fs.sp)
                                    Spacer(Modifier.width(5.dp))
                                    Txt(
                                        "${d.monthValue}/${d.dayOfMonth}", dc, fs.sp, FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                /*-------------------- 主体（可滚动） --------------------*/
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .testTag(TAG_WEEK_SCROLL)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(contentH)
                                .testTag(TAG_WEEK_CONTENT)
                        ) {

                            Row(Modifier.fillMaxSize()) {

                                /* 时间轴 */
                                Column(Modifier.width(axisW).fillMaxHeight()) {
                                    for (hh in 0 until 24) {
                                        Box(Modifier.fillMaxWidth().height(hourHeight)) {
                                            Txt(
                                                "%02d:00".format(hh), t.muted,
                                                (if (narrow) 9 else 11).sp,
                                                align = TextAlign.End,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(y = (-5).dp)
                                                    .padding(end = 4.dp)
                                            )
                                        }
                                    }
                                }

                                /* 7 个日期列 */
                                for (i in 0 until 7) {
                                    val d = monday.plusDays(i.toLong())
                                    val isToday = d == today
                                    val isWeekend = i >= 5
                                    val colBlocks = blocks.filter { it.col == i }
                                    Box(
                                        Modifier
                                            .width(colW)
                                            .fillMaxHeight()
                                            .testTag(TAG_WEEK_COL + i)
                                            .background(
                                                if (isToday) t.todayBg
                                                else if (isWeekend) t.weekendBg
                                                else t.card
                                            )
                                    ) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            val pxH = hourHeight.toPx()
                                            // 夜间时段：0–7 点与 22–24 点
                                            drawRect(t.nightBg, Offset.Zero, Size(size.width, 7 * pxH))
                                            drawRect(
                                                t.nightBg, Offset(0f, 22 * pxH),
                                                Size(size.width, size.height - 22 * pxH)
                                            )
                                            // 小时线
                                            for (hh in 0..24) {
                                                val y = hh * pxH
                                                drawLine(
                                                    t.line, Offset(0f, y),
                                                    Offset(size.width, y), 1f
                                                )
                                            }
                                            // 竖线
                                            drawLine(
                                                t.line, Offset(0f, 0f),
                                                Offset(0f, size.height), 1f
                                            )
                                            drawLine(
                                                t.line, Offset(size.width - 0.5f, 0f),
                                                Offset(size.width - 0.5f, size.height), 1f
                                            )
                                        }

                                        /* 日程块 */
                                        for (b in colBlocks) {
                                            val lanes = b.lanes.coerceIn(1, 4)
                                            val laneW = colW / lanes
                                            val topDp =
                                                (b.startMin / 60f * hourHeight.value).dp
                                            val heightDp = max(
                                                22f,
                                                (b.endMin - b.startMin) / 60f * hourHeight.value
                                            ).dp
                                            val selected = AppState.weekSelId == b.task.id
                                            Box(
                                                Modifier
                                                    .testTag(TAG_WEEK_BLOCK + b.task.id)
                                                    .offset(x = laneW * b.lane, y = topDp)
                                                    .width(if (lanes == 1) colW - 3.dp else laneW - 2.dp)
                                                    .height(heightDp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Categories.fill(b.task.category))
                                                    .clickable {
                                                        if (selected) {
                                                            AppState.weekSelId = 0
                                                        } else {
                                                            AppState.weekSelId = b.task.id
                                                            AppState.selectedIds =
                                                                setOf(b.task.id)
                                                        }
                                                    }
                                            ) {
                                                if (selected) {
                                                    Canvas(Modifier.fillMaxSize()) {
                                                        drawRoundRect(
                                                            t.accent, Offset.Zero, size,
                                                            CornerRadius(4.dp.toPx()),
                                                            style = Stroke(2.dp.toPx())
                                                        )
                                                    }
                                                }
                                                Column(
                                                    Modifier.padding(
                                                        horizontal = if (narrow) 3.dp else 5.dp,
                                                        vertical = 2.dp
                                                    )
                                                ) {
                                                    Txt(
                                                        b.task.title, Color.White,
                                                        (if (narrow) 10 else 11).sp,
                                                        FontWeight.Bold,
                                                        maxLines = if (heightDp.value < 32f) 1 else 2
                                                    )
                                                    if (heightDp.value >= 44f) {
                                                        Txt(
                                                            fmtTime(b.task.start.toLocalTime()),
                                                            Color.White.copy(alpha = 0.92f),
                                                            (if (narrow) 9 else 10).sp
                                                        )
                                                    }
                                                    if (heightDp.value >= 66f && b.task.note.isNotEmpty()) {
                                                        Txt(
                                                            b.task.note,
                                                            Color.White.copy(alpha = 0.85f),
                                                            (if (narrow) 9 else 10).sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            /* 当前时间线（画在今天所在的一列） */
                            val colToday = today.dayOfWeek.value - 1
                            val inThisWeek = !today.isBefore(monday) && !today.isAfter(monday.plusDays(6))
                            if (inThisWeek) {
                                val nowMin = now.hour * 60 + now.minute
                                val yDp = nowMin / 60f * hourHeight.value
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .offset(y = yDp.dp)
                                        .height(2.dp)
                                ) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        with(density) {
                                            val x0 = (axisW + colW * colToday).toPx()
                                            drawLine(
                                                t.now, Offset(x0, 1f),
                                                Offset(size.width, 1f), 2.dp.toPx()
                                            )
                                            drawCircle(t.now, 5.dp.toPx(), Offset(x0, 1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 测试用 tag：可滚动容器与 24 小时内容 */
const val TAG_WEEK_SCROLL = "weekScroll"
const val TAG_WEEK_COL = "weekCol"
const val TAG_WEEK_BLOCK = "weekBlock"
const val TAG_WEEK_CONTENT = "weekContent"

data class WeekBlock(
    val task: Task,
    val col: Int,
    val startMin: Int,
    val endMin: Int,
    val lane: Int,
    val lanes: Int
)

/**
 * 为一个星期内的日程分配横向列：同一时段重叠的日程并排显示（最多 4 列）。
 * 对应 WeekBuildBlocks。
 */
fun buildWeekBlocks(monday: LocalDate): List<WeekBlock> {
    val out = ArrayList<WeekBlock>()
    val days = (0 until 7).map { monday.plusDays(it.toLong()) }

    // 每一天单独计算：先取出当天所有日程的时段，再按开始时间贪心分列
    for (col in 0 until 7) {
        val spans = ArrayList<Triple<Task, Int, Int>>()
        for (t in AppState.tasks) {
            val span = t.daySpan(days[col]) ?: continue
            if (span.second <= span.first) continue
            spans.add(Triple(t, span.first, span.second))
        }
        if (spans.isEmpty()) continue
        spans.sortBy { it.second }
        val laneEnd = IntArray(4) { -1 }
        for ((task, s, e) in spans) {
            var lane = -1
            for (l in 0 until 4) {
                if (s >= laneEnd[l]) { lane = l; break }
            }
            if (lane < 0) continue
            laneEnd[lane] = e
            // 与当前块重叠的数量决定这一簇用几列
            var overlap = 0
            for ((_, s2, e2) in spans) {
                if (s2 < e && e2 > s) overlap++
            }
            val lanes = overlap.coerceIn(1, 4)
            out.add(WeekBlock(task, col, s, e, lane.coerceAtMost(lanes - 1), lanes))
        }
    }
    return out
}
