package com.fengshanglinye.timemanager

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 启动回归：模拟“已经有数据 → 打开 App”这条路。
 * 用户反馈“打开一次之后就打不开了”，这里在宿主机上复现两次连续启动，
 * 任何 onCreate 阶段的异常都会让测试失败。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class LaunchTest {

    private fun seed(ctx: Context) {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val monday = now.toLocalDate().minusDays((now.dayOfWeek.value - 1).toLong())
        val weekdays = (0..6).map { i ->
            val d = monday.plusDays(i.toLong()).atTime(9, 30)
            Task(
                id = i + 1, category = i % 6 + 1,
                start = d, end = d.plusMinutes(60),
                title = "周${i + 1}的日程", note = "覆盖每个星期几的分组标题",
                rules = listOf(Reminder(60, false), Reminder(5, false))
            )
        }
        val extra = listOf(
            Task(
                id = 20, category = 1,
                start = now.plusHours(2), end = now.plusHours(3),
                title = "未来的日程", note = "应该排闹钟",
                rules = listOf(
                    Reminder(1440, false), Reminder(60, false),
                    Reminder(30, false), Reminder(5, false)
                )
            ),
            Task(
                id = 21, category = 3,
                start = now.minusDays(1), end = now.minusDays(1).plusHours(1),
                title = "昨天的日程", note = "",
                rules = listOf(Reminder(60, true), Reminder(5, true))
            ),
            Task(
                id = 22, category = 4,
                start = now.minusHours(1), end = now.plusHours(1),
                title = "跨当前时刻", note = "正在进行的日程",
                rules = listOf(Reminder(5, true))
            )
        )
        Store.saveTasks(ctx, weekdays + extra)
        Store.saveSettings(ctx, Settings(autostart = true, sound = SOUND_ASTERISK, lang = 0))
    }

    @Test
    fun launchesTwiceWithExistingData() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        seed(ctx)

        // 第一次启动
        ActivityScenario.launch(MainActivity::class.java).use { it.recreate() }
        // 第二次启动（冷启动语义：新场景）
        ActivityScenario.launch(MainActivity::class.java).use { }
    }
}
