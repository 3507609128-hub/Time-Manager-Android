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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 基本信息区左侧标签宽度。桌面版是 76dp，手机上太宽会把日期框挤成「2026/…」，
 * 这里收到 50dp（标签都是 2–3 个汉字，右对齐后依然整齐）。
 */
private val LBL_W = 50.dp

internal const val TAG_ADD_START_DATE = "add_start_date"
internal const val TAG_ADD_START_TIME = "add_start_time"
internal const val TAG_ADD_END_DATE = "add_end_date"
internal const val TAG_ADD_END_TIME = "add_end_time"
internal const val TAG_ADD_CAT = "add_cat"
internal const val TAG_ADD_REPEAT = "add_repeat"
internal const val TAG_ADD_RULE_ROW = "add_rule_row"

/**
 * 添加 / 编辑日程对话框（对应 main.cpp 的 DialogAdd 及其子控件）。
 *
 * 布局与 Win32 版一致：
 *   基本信息（主色小标题）
 *     开始： [日期] [时间]
 *     结束： [日期] [时间]
 *     标题： [______________]
 *     分类： [课程 ▾]   重复： [单次日程 ▾]
 *     备注： [多行输入]
 *   ──────────────────────────
 *   提醒规则（主色小标题）
 *     [ 提前 1 天 / 提前 1 小时 / ... ]
 *     [15] [分钟 ▾] [添加规则] [删除选中]
 *     提示：提醒规则可自由添加或删除，最多 12 条
 *   ──────────────────────────
 *   [取消]  [确定]
 */
@Composable
fun AddTaskDialog(
    t: Theme,
    editing: Task?,
    initialDate: LocalDate?,
    onCancel: () -> Unit,
    onSaved: (Task) -> Unit,
    onSavedBatch: (List<Task>) -> Unit,
    onMessage: (String) -> Unit
) {
    val now = LocalDateTime.now()
    val baseStart: LocalDateTime = remember(editing, initialDate) {
        when {
            editing != null -> editing.start
            initialDate != null -> {
                // 默认放在下一个整点
                val hour = (now.hour + 1).coerceAtMost(23)
                if (initialDate == now.toLocalDate() && hour > now.hour) {
                    initialDate.atTime(hour, 0)
                } else {
                    initialDate.atTime(9, 0)
                }
            }
            else -> {
                val hour = (now.hour + 1).coerceAtMost(23)
                now.toLocalDate().atTime(hour, 0)
            }
        }
    }

    var start by remember { mutableStateOf(baseStart) }
    var end by remember {
        mutableStateOf(if (editing != null) editing.end else baseStart.plusHours(1))
    }
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var category by remember { mutableStateOf(editing?.category ?: Categories.DEFAULT) }
    var note by remember { mutableStateOf(editing?.note ?: "") }

    // 提醒规则：分钟数 + 是否已触发
    val rules = remember {
        mutableStateListOf<Int>().also { list ->
            val src = editing?.rules?.map { it.minutes } ?: DEFAULT_RULES.toList()
            list.addAll(src.sortedDescending())
        }
    }
    var ruleSel by remember { mutableStateOf(-1) }
    var ruleAmount by remember { mutableStateOf("15") }
    var ruleUnit by remember { mutableStateOf(0) }  // 0 分钟 1 小时 2 天

    // 重复周数（新建时可选 1..20，编辑时不显示重复）
    var repeatWeeks by remember { mutableStateOf(1) }
    var showRepeatSheet by remember { mutableStateOf(false) }
    var showCatSheet by remember { mutableStateOf(false) }
    var showUnitSheet by remember { mutableStateOf(false) }
    var showStartSheet by remember { mutableStateOf(false) }
    var showEndSheet by remember { mutableStateOf(false) }

    fun addRule() {
        val n = ruleAmount.toIntOrNull() ?: 0
        if (n <= 0) { onMessage(S(Str.ERR_RULE_POS)); return }
        val minutes = when (ruleUnit) {
            1 -> n * 60
            2 -> n * 1440
            else -> n
        }
        if (minutes > 365 * 24 * 60) { onMessage(S(Str.ERR_RULE_YEAR)); return }
        if (rules.size >= MAX_REMIND) { onMessage(S(Str.ERR_RULE_MAX)); return }
        if (rules.contains(minutes)) { onMessage(S(Str.ERR_RULE_DUP)); return }
        rules.add(minutes)
        val sorted = rules.sortedDescending()
        rules.clear()
        rules.addAll(sorted)
        ruleSel = -1
    }

    BaseDialog(t, { onCancel() }) {
        DialogHeader(
            if (editing == null) S(Str.ADD_TITLE) else S(Str.EDIT_TITLE), t
        ) { onCancel() }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            /*------------------------- 基本信息 -------------------------*/
            DialogCard(t) {
                SectionTitle(S(Str.ADD_SEC_BASE), t)
                Spacer(Modifier.height(12.dp))

                // 开始 / 结束：日期 + 时间（窄屏自动折行，保证日期完整显示）
                DateTimeRow(
                    label = S(Str.LBL_START), t = t,
                    dateText = fmtDate(start.toLocalDate()),
                    timeText = fmtTime(start.toLocalTime()),
                    dateTag = TAG_ADD_START_DATE, timeTag = TAG_ADD_START_TIME
                ) { showStartSheet = true }
                Spacer(Modifier.height(10.dp))
                DateTimeRow(
                    label = S(Str.LBL_END), t = t,
                    dateText = fmtDate(end.toLocalDate()),
                    timeText = fmtTime(end.toLocalTime()),
                    dateTag = TAG_ADD_END_DATE, timeTag = TAG_ADD_END_TIME
                ) { showEndSheet = true }

                Spacer(Modifier.height(10.dp))
                FormRow(S(Str.LBL_TITLE), t, labelWidth = LBL_W) {
                    TextFieldBox(
                        value = title, t = t, singleLine = true,
                        hint = "", maxLen = MAX_TITLE,
                        modifier = Modifier.fillMaxWidth()
                    ) { title = it }
                }

                Spacer(Modifier.height(10.dp))
                FormRow(S(Str.LBL_CAT), t, labelWidth = LBL_W) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(t.card)
                            .clickable { showCatSheet = true }
                            .testTag(TAG_ADD_CAT)
                            .padding(start = 8.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Dot(Categories.fill(category), 3.dp)
                        Spacer(Modifier.width(7.dp))
                        Txt(catName(category), t.text, 13.sp, modifier = Modifier.weight(1f))
                        Chevron(1, t.muted, 14.dp)
                    }
                }

                if (editing == null) {
                    Spacer(Modifier.height(10.dp))
                    FormRow(S(Str.LBL_REPEAT), t, labelWidth = LBL_W) {
                        Box(Modifier.testTag(TAG_ADD_REPEAT)) {
                            FakeDropdown(
                                if (repeatWeeks <= 1) S(Str.REPEAT_ONCE)
                                else S(Str.REPEAT_WEEKS).format(repeatWeeks),
                                t
                            ) { showRepeatSheet = true }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                FormRow(S(Str.LBL_NOTE), t, labelWidth = LBL_W) {
                    TextFieldBox(
                        value = note, t = t, singleLine = false,
                        hint = "", maxLen = MAX_NOTE,
                        height = 96.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { note = it }
                }
            }

            Spacer(Modifier.height(14.dp))
            Hairline(t)
            Spacer(Modifier.height(14.dp))

            /*------------------------- 提醒规则 -------------------------*/
            DialogCard(t) {
                SectionTitle(S(Str.ADD_SEC_RULES), t)
                Spacer(Modifier.height(10.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 126.dp, max = 126.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(t.card)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (rules.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(10.dp)) {
                            Txt(S(Str.RULE_ITEM).format(fmtLead(0)), t.empty, 13.sp)
                        }
                    }
                    for ((idx, minutes) in rules.withIndex()) {
                        val sel = ruleSel == idx
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .background(if (sel) t.accentSoft else Color.Transparent)
                                .clickable { ruleSel = if (sel) -1 else idx }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Dot(Categories.fill(category), 2.5.dp)
                            Spacer(Modifier.width(8.dp))
                            Txt(
                                S(Str.RULE_ITEM).format(fmtLead(minutes)),
                                if (sel) t.accentDark else t.text, 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().testTag(TAG_ADD_RULE_ROW)) {
                    val unitBox: @Composable () -> Unit = {
                        Box(Modifier.width(88.dp)) {
                            FakeDropdown(
                                when (ruleUnit) {
                                    1 -> S(Str.UNIT_HOUR)
                                    2 -> S(Str.UNIT_DAY)
                                    else -> S(Str.UNIT_MIN)
                                }, t
                            ) { showUnitSheet = true }
                        }
                    }
                    val amountBox: @Composable () -> Unit = {
                        Box(Modifier.width(56.dp)) {
                            TextFieldBox(
                                value = ruleAmount, t = t, singleLine = true,
                                hint = "", maxLen = 4,
                                modifier = Modifier.fillMaxWidth()
                            ) { v -> ruleAmount = v.filter { it.isDigit() } }
                        }
                    }
                    val addBtn: @Composable () -> Unit = {
                        OutlineButton(S(Str.RULE_ADD), t, onClick = { addRule() })
                    }
                    val delBtn: @Composable () -> Unit = {
                        OutlineButton(S(Str.RULE_DEL), t) {
                            if (ruleSel < 0) onMessage(S(Str.ERR_RULE_SEL))
                            else {
                                rules.removeAt(ruleSel)
                                ruleSel = -1
                            }
                        }
                    }
                    if (maxWidth >= 300.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            amountBox(); Spacer(Modifier.width(8.dp))
                            unitBox(); Spacer(Modifier.width(8.dp))
                            addBtn(); Spacer(Modifier.width(8.dp))
                            delBtn()
                        }
                    } else {
                        // 手机竖屏：分两行，避免按钮被挤出屏幕
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                amountBox(); Spacer(Modifier.width(8.dp))
                                unitBox(); Spacer(Modifier.width(8.dp))
                                addBtn()
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) { delBtn() }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HintText(S(Str.ADD_HINT), t)
            }
        }

        Hairline(t)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineButton(S(Str.CANCEL), t) { onCancel() }
            Spacer(Modifier.width(10.dp))
            FilledButton(S(Str.OK), t) {
                if (title.isBlank()) { onMessage(S(Str.ERR_TITLE_EMPTY)); return@FilledButton }
                if (end.isBefore(start)) { onMessage(S(Str.ERR_TIME_ORDER)); return@FilledButton }
                if (rules.isEmpty()) { onMessage(S(Str.ERR_RULE_POS)); return@FilledButton }

                val newRules = rules.map { Reminder(it, false) }.let { sortRules(it) }

                if (editing != null) {
                    val diff = java.time.Duration.between(LocalDateTime.now(), start).seconds
                    val updated = Task(
                        id = editing.id, category = category, start = start, end = end,
                        title = title.take(MAX_TITLE), note = note.take(MAX_NOTE),
                        rules = newRules
                    )
                    val updated2 = normalizeRules(updated, diff, true)
                    onSaved(updated2)
                } else {
                    val count = repeatWeeks
                    if (AppState.tasks.size + count > MAX_TASKS) {
                        onMessage(S(Str.ERR_TASK_MAX)); return@FilledButton
                    }
                    // 连续多周：每周生成一条独立日程（与原版一致）
                    var id = AppState.nextId
                    val list = ArrayList<Task>(count)
                    for (w in 0 until count) {
                        val ds = start.plusWeeks(w.toLong())
                        val de = end.plusWeeks(w.toLong())
                        val task = Task(
                            id = id++, category = category, start = ds, end = de,
                            title = title.take(MAX_TITLE), note = note.take(MAX_NOTE),
                            rules = newRules.map { it.copy(fired = false) }
                        )
                        list.add(
                            normalizeRules(
                                task,
                                java.time.Duration.between(LocalDateTime.now(), ds).seconds,
                                true
                            )
                        )
                    }
                    onSavedBatch(list)
                }
            }
        }
    }

    /*------------------------- 弹层 -------------------------*/
    if (showCatSheet) {
        SheetPicker(
            t = t,
            items = Categories.list().map { catName(it) to Categories.fill(it) },
            selected = Categories.list().indexOf(category),
            onDismiss = { showCatSheet = false },
            onPick = { i ->
                category = Categories.list()[i]
                showCatSheet = false
            }
        )
    }
    if (showUnitSheet) {
        SheetPicker(
            t = t,
            items = listOf(S(Str.UNIT_MIN) to null, S(Str.UNIT_HOUR) to null, S(Str.UNIT_DAY) to null),
            selected = ruleUnit,
            onDismiss = { showUnitSheet = false },
            onPick = { i -> ruleUnit = i; showUnitSheet = false }
        )
    }
    if (showRepeatSheet) {
        val items = (1..REPEAT_MAX_WEEKS).map { w ->
            (if (w == 1) S(Str.REPEAT_ONCE) else S(Str.REPEAT_WEEKS).format(w)) to null
        }
        SheetPicker(
            t = t, items = items, selected = repeatWeeks - 1,
            onDismiss = { showRepeatSheet = false },
            onPick = { i -> repeatWeeks = i + 1; showRepeatSheet = false }
        )
    }
    if (showStartSheet) {
        DateTimeSheet(
            t = t, initial = start,
            onDismiss = { showStartSheet = false },
            onPick = { picked ->
                val dur = java.time.Duration.between(start, end)
                start = picked
                end = picked.plus(dur)
                showStartSheet = false
            }
        )
    }
    if (showEndSheet) {
        DateTimeSheet(
            t = t, initial = end,
            onDismiss = { showEndSheet = false },
            onPick = { picked -> end = picked; showEndSheet = false }
        )
    }
}

/*============================= 通用小部件 =============================*/

/** 对话框里的白色卡片区块（原版 基本信息 / 提醒规则 两块） */
@Composable
fun DialogCard(t: Theme, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(t.card)
    ) { Column(Modifier.padding(12.dp)) { content() } }
}

/** 只读输入框外观（日期 / 时间） */
@Composable
fun FieldBox(
    t: Theme,
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(t.card)
            .clickable { onClick() }
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(text, t.text, fontSize, modifier = Modifier.weight(1f))
        Chevron(1, t.muted, 11.dp)
    }
}

/**
 * 「开始 / 结束」行：宽屏与桌面版一致排成 [日期] [时间]，
 * 内容区宽度不足 225dp 时自动折成上下两行，让日期永远能完整显示
 *（真机 320dp 宽、或系统字体放大时尤其重要）。
 */
@Composable
private fun DateTimeRow(
    label: String,
    t: Theme,
    dateText: String,
    timeText: String,
    dateTag: String,
    timeTag: String,
    onPick: () -> Unit
) {
    FormRow(label, t, labelWidth = LBL_W) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 225.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldBox(
                            t, dateText,
                            Modifier.fillMaxWidth().testTag(dateTag)
                        ) { onPick() }
                    }
                    Box(Modifier.width(74.dp)) {
                        FieldBox(
                            t, timeText,
                            Modifier.fillMaxWidth().testTag(timeTag)
                        ) { onPick() }
                    }
                }
            } else {
                Column {
                    FieldBox(t, dateText, Modifier.fillMaxWidth().testTag(dateTag)) { onPick() }
                    Spacer(Modifier.height(6.dp))
                    FieldBox(t, timeText, Modifier.fillMaxWidth().testTag(timeTag)) { onPick() }
                }
            }
        }
    }
}

/** 可编辑输入框外观：白底 + 聚焦时主色描边（原版 EDIT 控件的观感） */
@Composable
fun TextFieldBox(
    value: String,
    t: Theme,
    singleLine: Boolean,
    hint: String,
    maxLen: Int,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 38.dp,
    onValueChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(t.card)
            .border(1.dp, if (focused) t.accent else t.border, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 9.dp)) {
            if (value.isEmpty() && hint.isNotEmpty()) {
                Txt(hint, t.empty, 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLen) onValueChange(it) },
                singleLine = singleLine,
                textStyle = TextStyle(color = t.text, fontSize = 13.sp),
                cursorBrush = SolidColor(t.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

/** 底部弹出的选择列表（替代 Win32 的下拉框） */
@Composable
fun SheetPicker(
    t: Theme,
    items: List<Pair<String, Color?>>,
    selected: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    ModalScrim(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.card)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for ((i, item) in items.withIndex()) {
                    val isSel = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(if (isSel) t.accentSoft else Color.Transparent)
                            .clickable { onPick(i) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.second?.let {
                            Dot(it, 3.5.dp)
                            Spacer(Modifier.width(9.dp))
                        }
                        Txt(
                            item.first,
                            if (isSel) t.accentDark else t.text,
                            14.sp,
                            if (isSel) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (i < items.size - 1) Hairline(t, Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

/** 遮罩层，点击空白处关闭 */
@Composable
fun ModalScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { }
        ) { content() }
    }
}

/** 通用对话框外壳：铺满、底部对齐、支持输入法避让 */
@Composable
fun BaseDialog(t: Theme, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ModalScrim(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxSize(1f)
                .padding(horizontal = 10.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.dialogBg)
                .imePadding(),
            content = content
        )
    }
}

/** 日期 + 时间选择（用系统日历/时钟的替代：日期列表 + 时/分滚轮简化为下拉） */
@Composable
fun DateTimeSheet(
    t: Theme,
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onPick: (LocalDateTime) -> Unit
) {
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    val today = LocalDate.now()

    ModalScrim(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.card)
                .imePadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt(S(Str.PICK_DATE), t.text, 14.sp, FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Txt(fmtDate(date), t.accent, 13.sp, FontWeight.Medium)
            }
            Hairline(t)

            // 日期：前后两周的快捷条 + 今天的快捷按钮
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (offset in -2..4) {
                    val d = today.plusDays(offset.toLong())
                    val sel = d == date
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) t.accent else t.hover)
                            .clickable { date = d },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt(weekShort(d.dayOfWeek.value % 7), if (sel) Color.White else t.muted, 11.sp)
                            Txt("${d.monthValue}/${d.dayOfMonth}", if (sel) Color.White else t.text, 13.sp)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    OutlineButton("-1 天", t, Modifier.width(72.dp)) { date = date.minusDays(1) }
                }
                Box(Modifier.weight(1f)) {
                    OutlineButton("+1 天", t, Modifier.width(72.dp)) { date = date.plusDays(1) }
                }
                Box(Modifier.weight(1f)) {
                    OutlineButton(
                        if (AppState.lang == LANG_EN) "Today" else "今天", t, Modifier.width(80.dp)
                    ) { date = today }
                }
            }

            Spacer(Modifier.height(8.dp))
            Hairline(t)
            Row(
                Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt(S(Str.PICK_TIME), t.text, 14.sp, FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Txt("%02d:%02d".format(hour, minute), t.accent, 15.sp, FontWeight.Bold)
            }
            Hairline(t)

            // 时间：小时 + 分钟按压式调节
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlineButton("-", t, Modifier.width(40.dp)) { hour = (hour + 23) % 24 }
                        Box(
                            Modifier
                                .width(52.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(t.accentSoft),
                            contentAlignment = Alignment.Center
                        ) { Txt("%02d".format(hour), t.accentDark, 15.sp, FontWeight.Bold) }
                        OutlineButton("+", t, Modifier.width(40.dp)) { hour = (hour + 1) % 24 }
                    }
                    Spacer(Modifier.height(4.dp))
                    Txt(if (AppState.lang == LANG_EN) "Hour" else "时", t.muted, 11.sp)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlineButton("-", t, Modifier.width(40.dp)) {
                            minute = if (minute >= 5) minute - 5 else 55
                        }
                        Box(
                            Modifier
                                .width(52.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(t.accentSoft),
                            contentAlignment = Alignment.Center
                        ) { Txt("%02d".format(minute), t.accentDark, 15.sp, FontWeight.Bold) }
                        OutlineButton("+", t, Modifier.width(40.dp)) {
                            minute = if (minute <= 50) minute + 5 else 0
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Txt(if (AppState.lang == LANG_EN) "Minute" else "分", t.muted, 11.sp)
                }
            }

            Hairline(t)
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlineButton(S(Str.CANCEL), t) { onDismiss() }
                Spacer(Modifier.width(10.dp))
                FilledButton(S(Str.OK), t) {
                    onPick(date.atTime(LocalTime.of(hour, minute)))
                }
            }
        }
    }
}
