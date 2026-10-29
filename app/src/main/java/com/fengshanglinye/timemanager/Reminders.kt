package com.fengshanglinye.timemanager

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 提醒引擎（对应 Win32 版的 CheckReminders + QueueToast + PlayReminderSound）。
 *
 * Android 上没有“窗口常驻 + 定时器弹 Toast”这一套，改成：
 *  1. 用 AlarmManager 为每条未触发的提醒规则排一个精确闹钟；
 *  2. 到点由 ReminderReceiver 拉起通知（通知栏相当于原来的右下角 Toast）；
 *  3. 主程序前台时同时显示应用内的悬浮提醒卡片（视觉上更贴近原版）。
 */
object Reminders {

    const val ACTION_FIRE = "com.fengshanglinye.timemanager.FIRE"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_LEAD = "lead"

    // v2：通知通道本身静音，提示音完全由 playSound() 按用户选择的音效播放。
    // 否则通道自带的声音会和用户选的音效叠在一起（选“静音”时也还会响）。
    private const val CHANNEL_REMINDER = "reminder_v2"

    /*------------------------- 通道 -------------------------*/

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_REMINDER) == null) {
            val ch = NotificationChannel(
                CHANNEL_REMINDER, ctx.getString(R.string.channel_reminder),
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.description = ctx.getString(R.string.channel_reminder_desc)
            ch.enableVibration(true)
            ch.setShowBadge(true)
            ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(ReminderService.CHANNEL_SERVICE) == null) {
            val ch = NotificationChannel(
                ReminderService.CHANNEL_SERVICE, ctx.getString(R.string.channel_service),
                NotificationManager.IMPORTANCE_MIN
            )
            ch.description = ctx.getString(R.string.channel_service_desc)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    /*------------------------- 闹钟 -------------------------*/

    private fun pendingFor(ctx: Context, taskId: Int, lead: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_LEAD, lead)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val code = taskId * 100 + (lead % 100)
        return PendingIntent.getBroadcast(ctx, code, i, flags)
    }

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 为一条提醒规则设定闹钟；已过去的时间点直接跳过 */
    fun schedule(ctx: Context, t: Task, lead: Int) {
        val fireAt = t.start.minusMinutes(lead.toLong())
        if (fireAt.isBefore(LocalDateTime.now())) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingFor(ctx, t.id, lead)
        val ms = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    fun cancelAll(ctx: Context, tasks: List<Task>) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (t in tasks) {
            for (r in t.rules) {
                am.cancel(pendingFor(ctx, t.id, r.minutes))
            }
        }
    }

    /** 重建所有未触发规则的闹钟（启动、开机、数据变更后调用） */
    fun rescheduleAll(ctx: Context, tasks: List<Task>) {
        ensureChannels(ctx)
        cancelAll(ctx, tasks)
        val now = LocalDateTime.now()
        for (t in tasks) {
            if (t.start.isBefore(now)) continue
            for (r in t.rules) {
                if (!r.fired) schedule(ctx, t, r.minutes)
            }
        }
    }

    /*------------------------- 触发检查 -------------------------*/

    /**
     * 与 Win32 版 CheckReminders 同逻辑：进入“提前 N 分钟”窗口就触发。
     * 返回被触发规则的 (任务, 提前分钟数) 列表，并把 fired 置真。
     */
    fun checkDue(tasks: List<Task>): Pair<List<Task>, List<Pair<Task, Int>>> {
        val now = LocalDateTime.now()
        val fired = ArrayList<Pair<Task, Int>>()
        val updated = tasks.map { t ->
            val diff = Duration.between(now, t.start).seconds
            if (diff < 0) {
                if (t.rules.any { !it.fired }) {
                    t.copy(rules = t.rules.map { it.copy(fired = true) })
                } else t
            } else {
                val due = t.rules.filter { !it.fired && diff <= it.minutes.toLong() * 60 }
                if (due.isEmpty()) t
                else {
                    for (r in due) fired.add(Pair(t, r.minutes))
                    t.copy(rules = t.rules.map {
                        if (!it.fired && diff <= it.minutes.toLong() * 60) it.copy(fired = true) else it
                    })
                }
            }
        }
        return Pair(updated, fired)
    }

    /*------------------------- 通知 -------------------------*/

    fun buildReminderNotification(
        ctx: Context, t: Task, lead: Int, themeNameIdx: Int
    ): Notification {
        ensureChannels(ctx)
        val theme = Themes.of(ThemeId.of(themeNameIdx))

        val shortTitle = if (t.title.length > 24) t.title.take(24) + "…" else t.title
        val head = S(Str.TOAST_HEAD).format(shortTitle, fmtLead(lead))
        val body1 = S(Str.TOAST_TIME).format(
            fmtDate(t.startDate), weekFull(t.startDate.dayOfWeek.value % 7),
            fmtTime(t.start.toLocalTime()), fmtTime(t.end.toLocalTime())
        )
        val body2 = if (t.note.isNotEmpty()) S(Str.TOAST_NOTE).format(t.note) else ""

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) CHANNEL_REMINDER else null

        val b = NotificationCompat.Builder(ctx, ch ?: CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(android.graphics.Color.rgb(
                (theme.accent.red * 255).toInt(),
                (theme.accent.green * 255).toInt(),
                (theme.accent.blue * 255).toInt()
            ))
            .setContentTitle(head)
            .setContentText(body1)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (body2.isEmpty()) body1 else "$body1\n$body2"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx, t.id))
            .setOnlyAlertOnce(false)
            .setVibrate(longArrayOf(0, 180, 90, 180))

        if (lead <= 5) {
            // 马上开始：优先提醒。只开震动，声音统一由 playSound() 负责，避免和通道声音叠音
            b.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }
        return b.build()
    }

    fun openAppIntent(ctx: Context, taskId: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("task_id", taskId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(ctx, taskId + 900000, i, flags)
    }

    fun notifyReminder(ctx: Context, t: Task, lead: Int, themeIdx: Int) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = t.id * 100 + (lead % 100)
        try {
            nm.notify(id, buildReminderNotification(ctx, t, lead, themeIdx))
        } catch (e: SecurityException) {
            // 没有通知权限时静默失败（与原版“不弹提示”一致）
        }
        val st = Store.loadSettings(ctx).first
        playSound(ctx, st.sound, st.wavPath)
    }

    /*------------------------- 提示音 -------------------------*/
    // 对应 Win32 的 PlayReminderSound()：静音 / 系统默认 / 五种系统音 / 自定义文件。
    // 手机上要注意两件事：
    //  1) 试听一律走“媒体音量”，否则手机把通知音量调到 0 时按试听完全没声音（真机反馈）；
    //  2) 系统提示音 URI 在部分 ROM 上取不到，必须一层层回落，最差也要响一声 beep。

    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var tone: ToneGenerator? = null

    /** 提醒到点时播放（跟随通知音量，和系统通知保持一致） */
    fun playSound(ctx: Context, sound: Int, wavPath: String) {
        play(ctx, sound, wavPath, preview = false)
    }

    /** 设置里的「试听」：走媒体音量，保证按下去一定能听到 */
    fun previewSound(ctx: Context, sound: Int, wavPath: String) {
        play(ctx, sound, wavPath, preview = true)
    }

    private fun play(ctx: Context, sound: Int, wavPath: String, preview: Boolean) {
        if (sound == SOUND_MUTE) return
        stopSound()
        val attrs = AudioAttributes.Builder()
            .setUsage(if (preview) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(
                if (preview) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SONIFICATION
            )
            .build()
        try {
            if (sound == SOUND_BEEP) {
                beep(preview)
                return
            }
            if (sound == SOUND_WAV) {
                val uri = fileUri(wavPath)
                if (uri != null && playUri(ctx, uri, attrs)) return
                beep(preview)          // 文件被删/权限失效时至少响一声
                return
            }
            // 系统音：感叹号 / 手形对应闹钟音，其余对应通知音
            val type = when (sound) {
                SOUND_EXCLAM, SOUND_HAND -> RingtoneManager.TYPE_ALARM
                else -> RingtoneManager.TYPE_NOTIFICATION
            }
            var uri = RingtoneManager.getDefaultUri(type)
            if (uri == null) uri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            if (uri != null) {
                // 试听优先用 MediaPlayer 走媒体音量（部分 ROM 上 Ringtone 会被当成通知音而静音），
                // 提醒到点则优先用 Ringtone，跟随系统通知设置。
                if (preview && playUri(ctx, uri, attrs)) return
                val rt = RingtoneManager.getRingtone(ctx, uri)
                if (rt != null) {
                    rt.audioAttributes = attrs
                    ringtone = rt
                    rt.play()
                    return
                }
                if (playUri(ctx, uri, attrs)) return
            }
            beep(preview)
        } catch (e: Exception) {
            runCatching { beep(preview) }   // 播放失败不影响提醒本身，也不再完全静音
        }
    }

    private fun fileUri(path: String): Uri? {
        if (path.isEmpty()) return null
        return runCatching {
            if (path.startsWith("/")) Uri.fromFile(java.io.File(path)) else Uri.parse(path)
        }.getOrNull()
    }

    private fun playUri(ctx: Context, uri: Uri, attrs: AudioAttributes): Boolean {
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(ctx, uri)
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { p, _, _ -> runCatching { p.release() }; true }
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 兜底蜂鸣：系统音取不到时用它，保证「有反馈」 */
    private fun beep(preview: Boolean) {
        runCatching {
            val stream = if (preview) AudioManager.STREAM_MUSIC else AudioManager.STREAM_NOTIFICATION
            val g = ToneGenerator(stream, 90)
            tone = g
            g.startTone(ToneGenerator.TONE_PROP_BEEP, 220)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { g.release() }
                if (tone === g) tone = null
            }, 520)
        }
    }

    private fun stopSound() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching {
            tone?.stopTone()
            tone?.release()
        }
        tone = null
    }
}

/** 闹钟到点接收器 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION_FIRE) return
        val taskId = intent.getIntExtra(Reminders.EXTRA_TASK_ID, 0)
        val lead = intent.getIntExtra(Reminders.EXTRA_LEAD, 0)
        // 读最新数据，避免使用过期快照
        val tasks = Store.loadTasks(context)
        val t = tasks.firstOrNull { it.id == taskId } ?: return
        val settings = Store.loadSettings(context).first

        // 与 Win32 版 CheckReminders 一致：先把这条规则标为已提醒并落盘，
        // 否则下次 rescheduleAll 会重新排同一个闹钟，导致重复通知。
        if (t.rules.none { it.minutes == lead && !it.fired }) return
        val updated = tasks.map { task ->
            if (task.id != taskId) task
            else task.copy(
                rules = task.rules.map {
                    if (it.minutes == lead) it.copy(fired = true) else it
                }
            )
        }
        Store.saveTasks(context, updated)
        Reminders.rescheduleAll(context, updated)

        Reminders.notifyReminder(context, t, lead, settings.theme)
    }
}
