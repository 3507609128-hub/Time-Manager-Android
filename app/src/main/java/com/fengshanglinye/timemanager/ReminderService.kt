package com.fengshanglinye.timemanager

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 常驻提醒服务（对应 Win32 版的“托盘常驻 + 定时器检查提醒”）。
 *
 * Android 上后台定时器会被系统回收，这里用前台服务保持进程存活，
 * 每 30 秒调用一次 Reminders.checkDue()，让“提前 N 分钟”的窗口判定与
 * 原版一样精确到秒；同时由 AlarmManager 兜底，进程被杀也能准时提醒。
 */
class ReminderService : Service() {

    companion object {
        const val CHANNEL_SERVICE = "background"
        const val NOTIF_ID = 0x7A11
        const val CHECK_MS = 30_000L

        fun start(ctx: Context) {
            val i = Intent(ctx, ReminderService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(ctx, i)
                } else {
                    ctx.startService(i)
                }
            } catch (e: Exception) {
                // 系统限制启动前台服务时静默跳过（AlarmManager 仍会工作）
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, ReminderService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            checkOnce()
            handler.postDelayed(this, CHECK_MS)
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannels(this)
        val n = buildNotification(AppState.tasks.size)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIF_ID, n)
            } catch (e2: Exception) {
            }
        }
        handler.post(tick)
    }

    private fun buildNotification(count: Int): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(S(Str.SERVICE_TITLE))
            .setContentText(S(Str.SERVICE_TEXT).format(count))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(Reminders.openAppIntent(this, 0))
        return b.build()
    }

    private fun checkOnce() {
        if (AppState.tasks.isEmpty()) return
        val (updated, fired) = Reminders.checkDue(AppState.tasks)
        if (updated != AppState.tasks) {
            AppState.setTasks(updated)
            Store.saveTasks(this, updated)
            Reminders.rescheduleAll(this, updated)
        }
        for ((t, lead) in fired) {
            Reminders.notifyReminder(this, t, lead, AppState.settings.theme)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkOnce()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}

/** 开机自动启动 */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != "android.intent.action.QUICKBOOT_POWERON" &&
            a != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val tasks = Store.loadTasks(context)
        val (settings, lang) = Store.loadSettings(context)
        AppState.setTasks(tasks)
        AppState.setSettings(settings, lang)
        if (!settings.autostart) return
        Reminders.rescheduleAll(context, tasks)
        ReminderService.start(context)
    }
}
