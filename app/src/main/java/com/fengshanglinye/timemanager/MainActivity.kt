package com.fengshanglinye.timemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDate

/** 从通知点进来时要打开哪条日程（0 表示不打开）；用 State 以便界面能感知变化 */
var pendingOpenTask by mutableStateOf(0)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tasks = Store.loadTasks(this)
        val (settings, lang) = Store.loadSettings(this)
        AppState.setTasks(tasks)
        AppState.setSettings(settings, lang)

        Reminders.ensureChannels(this)
        Reminders.rescheduleAll(this, tasks)
        if (settings.autostart) ReminderService.start(this)

        handleIntent(intent)
        requestNotifications()

        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val id = intent.getIntExtra("task_id", 0)
        if (id > 0) pendingOpenTask = id
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}

/*============================= 根界面 =============================*/

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val theme = Themes.of(ThemeId.of(AppState.settings.theme))

    var isMonth by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val toasts = remember { mutableStateListOf<ToastItem>() }

    /*--------- 音频选择器（“浏览…”按钮用） ---------*/
    var audioPickHost by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // 长期保留读权限，重启后仍能播放自定义提示音
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            audioPickHost?.invoke(uri.toString())
        }
        audioPickHost = null
    }

    /*--------- 点通知进来时直接打开对应日程（对应原版点 Toast 打开主窗口） ---------*/
    LaunchedEffect(pendingOpenTask) {
        if (pendingOpenTask > 0) {
            val target = AppState.findTask(pendingOpenTask)
            pendingOpenTask = 0
            if (target != null) {
                editing = target
                showAdd = true
            }
        }
    }

    /*--------- 提醒轮询：等价于 Win32 版的 SetTimer 检查 ---------*/
    LaunchedEffect(Unit) {
        while (true) {
            val (updated, fired) = Reminders.checkDue(AppState.tasks)
            // 用结构相等判断，避免每 5 秒无谓落盘与重排闹钟
            if (updated != AppState.tasks) {
                AppState.setTasks(updated)
                Store.saveTasks(ctx, updated)
                Reminders.rescheduleAll(ctx, updated)
            }
            for ((task, lead) in fired) {
                toasts.add(ToastItem(task, lead))
                Reminders.notifyReminder(ctx, task, lead, AppState.settings.theme)
            }
            val keep = AppState.settings.toastSec.toLong() * 1000L
            if (toasts.isNotEmpty()) {
                val now = System.currentTimeMillis()
                toasts.removeAll { now - it.atMillis > keep }
            }
            kotlinx.coroutines.delay(5_000)
        }
    }

    /*--------- 从通知点进来时直接打开对应日程 ---------*/
    LaunchedEffect(pendingOpenTask) {
        val id = pendingOpenTask
        if (id > 0) {
            AppState.findTask(id)?.let {
                editing = it
                showAdd = true
            }
            pendingOpenTask = 0
        }
    }

    /** 数据变更后统一落盘 + 重建闹钟 + 清空选择 */
    fun commit(list: List<Task>) {
        val sorted = list.sortedBy { it.start }.take(MAX_TASKS)
        AppState.setTasks(sorted)
        Store.saveTasks(ctx, sorted)
        Reminders.rescheduleAll(ctx, sorted)
        AppState.selectedIds = emptySet()
        AppState.weekSelId = 0
    }

    fun saveSettings(s: Settings, lang: Int) {
        AppState.setSettings(s, lang)
        Store.saveSettings(ctx, s)
        Reminders.rescheduleAll(ctx, AppState.tasks)
        if (s.autostart) ReminderService.start(ctx) else ReminderService.stop(ctx)
    }

    Box(Modifier.fillMaxSize().background(theme.page).systemBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            Column(Modifier.fillMaxSize()) {
                Toolbar(
                    t = theme,
                    isMonth = isMonth,
                    onSwitch = { isMonth = it },
                    weekRange = weekRangeText(),
                    onPrevWeek = {
                        AppState.weekAnchor = AppState.weekAnchor.minusWeeks(1); AppState.weekSelId = 0
                    },
                    onNextWeek = {
                        AppState.weekAnchor = AppState.weekAnchor.plusWeeks(1); AppState.weekSelId = 0
                    },
                    onThisWeek = {
                        AppState.weekAnchor = LocalDate.now(); AppState.weekSelId = 0
                    },
                    onAdd = {
                        editing = null
                        showAdd = true
                    },
                    onDelete = {
                        if (AppState.selectedIds.isEmpty()) message = S(Str.NO_SELECTION)
                        else confirmDelete = true
                    },
                    onSettings = { showSettings = true }
                )

                val cal: @Composable (Modifier) -> Unit = { m ->
                    CalendarCard(
                        t = theme,
                        modifier = m,
                        year = AppState.calYear,
                        month = AppState.calMonth,
                        selected = AppState.filterDate,
                        onPrevMonth = { shiftMonth(-1) },
                        onNextMonth = { shiftMonth(1) },
                        onPickDate = { pickDate(it) },
                        onToggleAutostart = { on ->
                            saveSettings(AppState.settings.copy(autostart = on), AppState.lang)
                        }
                    )
                }
                val agenda: @Composable (Modifier) -> Unit = { m ->
                    AgendaList(
                        t = theme,
                        modifier = m,
                        filter = AppState.filterDate,
                        onEdit = { editing = it; showAdd = true },
                        onLongPress = { toggleTaskSelection(it) }
                    )
                }

                if (wide) {
                    // 平板 / 横屏：与原版一样双栏
                    Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
                        cal(Modifier.width(340.dp).fillMaxHeight())
                        Spacer(Modifier.width(12.dp))
                        if (isMonth) {
                            agenda(Modifier.weight(1f).fillMaxHeight())
                        } else {
                            WeekGrid(t = theme, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                } else {
                    // 手机竖屏：两种视图都是「日历在上、内容在下」
                    //  · 月视图 → 下面放日程表
                    //  · 周视图 → 下面放时间表（用户要求与月视图一致）
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        // 格子按屏幕宽度算成接近正方形，整张卡片由内容决定高度，
                        // 这样竖屏下日历不会把整屏吃光，下面的内容才有空间显示。
                        val cellW = (maxWidth - 8.dp) / 7
                        val cellH = (cellW * 0.92f).coerceIn(32.dp, 56.dp)
                        CalendarCard(
                            t = theme,
                            modifier = Modifier.fillMaxWidth(),
                            year = AppState.calYear,
                            month = AppState.calMonth,
                            selected = AppState.filterDate,
                            cellHeight = cellH,
                            onPrevMonth = { shiftMonth(-1) },
                            onNextMonth = { shiftMonth(1) },
                            onPickDate = { pickDate(it) },
                            onToggleAutostart = { on ->
                                saveSettings(
                                    AppState.settings.copy(autostart = on), AppState.lang
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isMonth) {
                        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
                            agenda(Modifier.fillMaxSize())
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                            WeekGrid(t = theme, modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                MessageBar(theme, message) { message = null }
                Hairline(theme)
                StatusBar(theme, Store.tasksPath(ctx), AppState.filterDate)
            }
        }

        /*--------- 悬浮提醒：右下角，向上堆叠 ---------*/
        if (toasts.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(bottom = 46.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                ToastStack(theme, toasts, { toasts.clear() }) { task ->
                    editing = task
                    showAdd = true
                    toasts.clear()
                }
            }
        }

        /*--------- 对话框 ---------*/
        if (showAdd) {
            AddTaskDialog(
                t = theme,
                editing = editing,
                initialDate = AppState.filterDate,
                onCancel = { showAdd = false; editing = null },
                onSaved = { single ->
                    commit(AppState.tasks.map { if (it.id == single.id) single else it })
                    showAdd = false
                    editing = null
                },
                onSavedBatch = { list ->
                    commit(AppState.tasks + list)
                    showAdd = false
                    editing = null
                },
                onMessage = { message = it }
            )
        }

        if (showSettings) {
            SettingsDialog(
                t = theme,
                onCancel = { showSettings = false },
                onApplied = { s, lang ->
                    saveSettings(s, lang)
                    showSettings = false
                },
                onTestSound = { sound, path -> Reminders.playSound(ctx, sound, path) },
                onBrowseWav = { cb ->
                    audioPickHost = cb
                    runCatching { audioPicker.launch(arrayOf("audio/*")) }
                },
                onMessage = { message = it }
            )
        }

        if (confirmDelete) {
            val sel = AppState.selectedIds
            val single = if (sel.size == 1) AppState.findTask(sel.first())?.title else null
            ConfirmDialog(
                t = theme,
                title = S(Str.CONFIRM_DEL_TITLE),
                message = if (single != null) S(Str.CONFIRM_DELETE).format(single)
                else S(Str.CONFIRM_DELETE_MULTI).format(sel.size),
                onCancel = { confirmDelete = false },
                onConfirm = {
                    confirmDelete = false
                    commit(AppState.tasks.filter { it.id !in sel })
                }
            )
        }
    }
}

/*============================= 辅助函数 =============================*/

/** 长按切换某条日程的选中状态（对应原版活动流的多选集合） */
private fun toggleTaskSelection(task: Task) {
    val sel = AppState.selectedIds.toMutableSet()
    if (sel.contains(task.id)) sel.remove(task.id) else sel.add(task.id)
    AppState.selectedIds = sel
}

private fun shiftMonth(delta: Int) {
    var m = AppState.calMonth + delta
    var y = AppState.calYear
    if (m < 1) { m = 12; y -= 1 }
    if (m > 12) { m = 1; y += 1 }
    AppState.calMonth = m
    AppState.calYear = y
}

private fun pickDate(d: LocalDate) {
    AppState.filterDate = if (AppState.filterDate == d) null else d
}

private fun weekRangeText(): String {
    val monday = mondayOf(AppState.weekAnchor)
    val sunday = monday.plusDays(6)
    return "%d/%d – %d/%d".format(
        monday.monthValue, monday.dayOfMonth, sunday.monthValue, sunday.dayOfMonth
    )
}
