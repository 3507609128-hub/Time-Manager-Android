package com.fengshanglinye.timemanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 右侧活动流（对应 main.cpp 的 DrawAgenda + AgGetItems）。
 *
 * 与 Win32 版一致：
 *  • 没有表头、没有网格线，只按日期分组
 *  • 分组标题：今天 10/26 一       右侧“2 个日程”
 *  • 每条：分类色点 / 时间范围 / 标题 / 备注
 *  • 长文本自动省略，单击编辑，长按进入多选
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaList(
    t: Theme,
    modifier: Modifier = Modifier,
    filter: LocalDate?,
    onEdit: (Task) -> Unit,
    onLongPress: (Task) -> Unit
) {
    val today = LocalDate.now()
    val days: List<Pair<LocalDate, List<Task>>> = remember(filter, AppState.tasks) {
        val src = AppState.tasks
            .filter { filter == null || it.startDate == filter }
            .sortedBy { it.start }
        val map = LinkedHashMap<LocalDate, MutableList<Task>>()
        for (task in src) map.getOrPut(task.startDate) { ArrayList() }.add(task)
        map.entries.map { Pair(it.key, it.value.toList()) }
    }

    Card(t, modifier) {
        Column(Modifier.fillMaxSize()) {
            // 多选辅助条：对应原版 Ctrl+A 全选 / Esc 取消选择
            val selCount = AppState.selectedIds.size
            if (selCount > 0) {
                val visibleIds = days.flatMap { it.second }.map { it.id }
                Row(
                    Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt(S(Str.SEL_COUNT).format(selCount), t.accent, 12.sp)
                    Spacer(Modifier.weight(1f))
                    Txt(
                        S(Str.SEL_ALL), t.muted, 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { AppState.selectedIds = visibleIds.toSet() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Txt(
                        S(Str.SEL_CLEAR), t.muted, 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { AppState.selectedIds = emptySet() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Hairline(t)
            }

            if (days.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(28.dp), contentAlignment = Alignment.TopCenter) {
                    Txt(
                        if (filter == null) S(Str.AGENDA_EMPTY) else S(Str.AGENDA_EMPTY_DAY),
                        t.empty, 13.sp, align = TextAlign.Center, maxLines = 3
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    for ((date, list) in days) {
                        item(key = "h-$date") {
                            GroupHeader(t, date, list.size, today)
                        }
                        items(list, key = { it.id }) { task ->
                            AgendaRow(
                                t = t,
                                task = task,
                                selected = AppState.selectedIds.contains(task.id),
                                onClick = { onEdit(task) },
                                onLongClick = { onLongPress(task) }
                            )
                        }
                        item(key = "g-$date") { Spacer(Modifier.height(6.dp)) }
                    }
                }
            }
        }
    }
}

/** 分组标题行 */
@Composable
private fun GroupHeader(t: Theme, date: LocalDate, count: Int, today: LocalDate) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (date == today) {
            Txt(S(Str.AGENDA_TODAY), t.accent, 13.sp, FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
        }
        Txt(fmtAgendaDate(date), t.text, 13.sp, FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Txt(S(Str.DAY_EVENT_COUNT).format(count), t.muted, 12.sp)
    }
}

/** 一条日程 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgendaRow(
    t: Theme,
    task: Task,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .let { m -> if (selected) m.background(t.accentSoft) else m }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(Categories.fill(task.category), 3.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(112.dp)) {
            Txt(task.timeRange(), t.muted, 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Txt(task.title, t.text, 13.sp, modifier = Modifier.weight(1f, fill = false))
        if (task.note.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Txt(task.note, t.muted, 12.sp, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 顶部工具栏（对应 main.cpp 的 DrawToolbar）。
 *  左：月视图 / 周视图 分段切换（周视图时追加 ◀ 10/19 – 10/25 ▶ 本周）
 *  右：添加日程 / 删除选中 (N) / 设置
 */
@Composable
fun Toolbar(
    t: Theme,
    isMonth: Boolean,
    onSwitch: (Boolean) -> Unit,
    weekRange: String,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onSettings: () -> Unit
) {
    val selCount = AppState.selectedIds.size
    val delText = if (selCount == 0) S(Str.BTN_DELETE) else S(Str.BTN_DELETE_N).format(selCount)

    /** 周视图的翻页控件：◀  10/19 – 10/25  ▶  本周 */
    @Composable
    fun WeekNav() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onPrevWeek() },
                contentAlignment = Alignment.Center
            ) { Chevron(-1, t.text, 14.dp) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(116.dp), contentAlignment = Alignment.Center) {
                Txt(weekRange, t.text, 13.sp, FontWeight.Medium)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNextWeek() },
                contentAlignment = Alignment.Center
            ) { Chevron(1, t.text, 14.dp) }
            Spacer(Modifier.width(6.dp))
            OutlineButton(S(Str.WEEK_TODAY), t) { onThisWeek() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 手机竖屏放不下一整行时，改成上下两行（切换器 + 翻页 / 三个操作按钮）
        val narrow = maxWidth < 560.dp
        if (narrow) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ViewToggle(
                        isMonth = isMonth,
                        t = t,
                        monthText = S(Str.VIEW_MONTH),
                        weekText = S(Str.VIEW_WEEK),
                        onMonth = { onSwitch(true) },
                        onWeek = { onSwitch(false) }
                    )
                    if (!isMonth) {
                        Spacer(Modifier.width(8.dp))
                        WeekNav()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlineButton(S(Str.BTN_ADD), t, onClick = onAdd)
                    Spacer(Modifier.width(6.dp))
                    OutlineButton(delText, t) { onDelete() }
                    Spacer(Modifier.width(6.dp))
                    OutlineButton(S(Str.BTN_SET), t, onClick = onSettings)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewToggle(
                    isMonth = isMonth,
                    t = t,
                    monthText = S(Str.VIEW_MONTH),
                    weekText = S(Str.VIEW_WEEK),
                    onMonth = { onSwitch(true) },
                    onWeek = { onSwitch(false) }
                )
                if (!isMonth) {
                    Spacer(Modifier.width(10.dp))
                    WeekNav()
                }
                Spacer(Modifier.weight(1f))
                OutlineButton(S(Str.BTN_ADD), t, onClick = onAdd)
                Spacer(Modifier.width(8.dp))
                OutlineButton(delText, t) { onDelete() }
                Spacer(Modifier.width(8.dp))
                OutlineButton(S(Str.BTN_SET), t, onClick = onSettings)
            }
        }
    }
}

/** 底部状态栏：共 N 个日程  数据文件：/data/.../tasks.dat */
@Composable
fun StatusBar(t: Theme, dataPath: String, filter: LocalDate?) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(t.card)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        val text = if (filter == null) {
            S(Str.STATUS_ALL).format(AppState.tasks.size, dataPath)
        } else {
            S(Str.STATUS_FILTER).format(
                fmtDateShort(filter), weekShort(filter.dayOfWeek.value % 7),
                AppState.tasks.count { it.startDate == filter }
            )
        }
        Txt(text, t.muted, 11.sp)
    }
}

/** 应用内悬浮提醒卡片（对应 Win32 版右下角滑入的 Toast）。
 *  Android 上也保留这个视图，主界面在前台时与通知栏同时出现，观感与原版一致。
 */
@Composable
fun ToastStack(t: Theme, toasts: List<ToastItem>, onDismiss: () -> Unit, onClick: (Task) -> Unit) {
    Column(
        Modifier.padding(12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (item in toasts) {
            val task = item.task
            val click = {
                onClick(task)
                onDismiss()
            }
            Box(Modifier.width(300.dp)) {
                ToastCard(t, task, item.lead, click)
            }
        }
    }
}

/** 单张 Toast 卡片：左侧分类色条 + 标题 + 时间 + 备注 */
@Composable
private fun ToastCard(t: Theme, task: Task, lead: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(t.toastCard)
                .clickable { onClick() }
        ) {
            // 分类色顶条，便于一眼区分（竖条会撑不满高度，改成顶部细条）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Categories.fill(task.category))
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Txt(S(Str.TOAST_APP), t.toastTitle, 11.sp)
                Spacer(Modifier.height(4.dp))
                Txt(
                    S(Str.TOAST_HEAD).format(short(task.title, 24), fmtLead(lead)),
                    t.toastHead, 13.sp, FontWeight.Bold, maxLines = 2
                )
                Spacer(Modifier.height(5.dp))
                Txt(
                    S(Str.TOAST_TIME).format(
                        fmtDate(task.startDate),
                        weekFull(task.startDate.dayOfWeek.value % 7),
                        fmtTime(task.start.toLocalTime()),
                        fmtTime(task.end.toLocalTime())
                    ),
                    t.toastBody, 12.sp, maxLines = 2
                )
                if (task.note.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Txt(S(Str.TOAST_NOTE).format(short(task.note, 40)), t.toastBody, 12.sp, maxLines = 2)
                }
            }
        }
        // 外框描边画在最上层，避免被内部背景覆盖
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, t.toastBorder, RoundedCornerShape(8.dp))
        )
    }
}

data class ToastItem(val task: Task, val lead: Int, val at: LocalDateTime = LocalDateTime.now()) {
    /** 加入时刻（毫秒），用于按“通知停留”秒数自动消失 */
    val atMillis: Long = System.currentTimeMillis()
}

/** 与原版 queue toast 相同的截断规则：超出 N 个字符补省略号 */
fun short(s: String, n: Int): String = if (s.length > n) s.take(n) + "…" else s

/** 小宽度开关：用于设置里“开机自动启动”等行 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    t: Theme,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).clickable { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(checked, t, 17.dp)
        Spacer(Modifier.width(8.dp))
        Txt(label, t.text, 13.sp)
    }
}

/** 对话框统一的底部按钮排布：取消 + 确定 */
@Composable
fun DialogButtons(
    t: Theme,
    onCancel: () -> Unit,
    onOk: () -> Unit,
    okEnabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlineButton(S(Str.CANCEL), t) { onCancel() }
        Spacer(Modifier.width(10.dp))
        FilledButton(S(Str.OK), t, enabled = okEnabled) { onOk() }
    }
}

/** 对话框标题栏（仅手机上显示，窗口标题与 Win32 版一致） */
@Composable
fun DialogHeader(title: String, t: Theme, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp)) {
            LeafMark(t.accent)
        }
        Spacer(Modifier.width(8.dp))
        Txt(title, t.text, 14.sp, FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(32.dp).clickable { onClose() }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(12.dp)) {
                val s = size.width
                drawLine(t.muted, Offset(0f, 0f), Offset(s, s), 1.6.dp.toPx())
                drawLine(t.muted, Offset(s, 0f), Offset(0f, s), 1.6.dp.toPx())
            }
        }
    }
    Hairline(t)
}

/** 极简叶子标记（与原版图标上的枫叶呼应） */
@Composable
fun LeafMark(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.02f)
            lineTo(w * 0.62f, h * 0.34f)
            lineTo(w * 0.9f, h * 0.24f)
            lineTo(w * 0.78f, h * 0.52f)
            lineTo(w * 0.98f, h * 0.56f)
            lineTo(w * 0.58f, h * 0.88f)
            lineTo(w * 0.54f, h * 0.98f)
            lineTo(w * 0.46f, h * 0.98f)
            lineTo(w * 0.42f, h * 0.88f)
            lineTo(w * 0.02f, h * 0.56f)
            lineTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.1f, h * 0.24f)
            lineTo(w * 0.38f, h * 0.34f)
            close()
        }
        drawPath(p, color)
    }
}

/** 尺寸辅助：把 dp 夹在合理区间，保证手机竖屏也放得下 */
fun clampDp(v: Float, min: Dp, max: Dp): Dp = v.coerceIn(min.value, max.value).dp
