package com.fengshanglinye.timemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 纯逻辑单元测试：不依赖 Android 运行时，直接验证与 Win32 版对齐的
 * 数据格式、提醒收敛规则、跨天时间段与周视图分列算法。
 */
class CoreLogicTest {

    private fun mkTask(
        id: Int = 1,
        cat: Int = Categories.COURSE,
        title: String = "高等数学",
        note: String = "复习第三章",
        rules: List<Reminder> = listOf(
            Reminder(1440, false), Reminder(60, false),
            Reminder(30, false), Reminder(5, false)
        )
    ) = Task(
        id = id,
        category = cat,
        start = LocalDateTime.of(2026, 10, 26, 9, 30),
        end = LocalDateTime.of(2026, 10, 26, 10, 30),
        title = title,
        note = note,
        rules = rules
    )

    /*------------------------- 数据文件格式 -------------------------*/

    @Test
    fun serializedFormatMatchesWin32Layout() {
        val text = Store.serializeTasks(listOf(mkTask()))
        val expected = "1 2\n" +
                "T|1|1|2026|10|26|9|30|2026|10|26|10|30|高等数学|复习第三章\n" +
                "R|4|1440|0|60|0|30|0|5|0\n"
        assertEquals(expected, text)
    }

    @Test
    fun roundTripKeepsEveryField() {
        val src = listOf(
            mkTask(id = 7, cat = Categories.FUN, title = "看电影", note = "和朋友一起",
                rules = listOf(Reminder(30, true), Reminder(5, false))),
            mkTask(id = 9, cat = Categories.SPORT, title = "健身训练", note = "带上运动鞋",
                rules = emptyList())
        )
        val parsed = Store.parseTasks(Store.serializeTasks(src))
        assertEquals(2, parsed.size)
        for (i in src.indices) {
            assertEquals(src[i].id, parsed[i].id)
            assertEquals(src[i].category, parsed[i].category)
            assertEquals(src[i].start, parsed[i].start)
            assertEquals(src[i].end, parsed[i].end)
            assertEquals(src[i].title, parsed[i].title)
            assertEquals(src[i].note, parsed[i].note)
            assertEquals(src[i].rules, parsed[i].rules)
        }
    }

    @Test
    fun rulesAreSortedByLeadTimeDescending() {
        val t = mkTask(rules = listOf(Reminder(5, false), Reminder(1440, false), Reminder(60, false)))
        val parsed = Store.parseTasks(Store.serializeTasks(listOf(t))).single()
        assertEquals(listOf(1440, 60, 5), parsed.rules.map { it.minutes })
    }

    @Test
    fun legacySeventeenFieldLineIsStillReadable() {
        // 旧格式：14 个数字/文本字段 + 4 个 fired 标记，无分类列
        val legacy = buildString {
            append("1 2\n")
            append("3|2026|10|27|18|0|2026|10|27|19|30|团队周会|同步进度")
            append("|0|1|0|0\n")
        }
        val parsed = Store.parseTasks(legacy)
        assertEquals(1, parsed.size)
        val t = parsed.single()
        assertEquals(3, t.id)
        assertEquals(Categories.DEFAULT, t.category)
        assertEquals(LocalDateTime.of(2026, 10, 27, 18, 0), t.start)
        assertEquals(LocalDateTime.of(2026, 10, 27, 19, 30), t.end)
        assertEquals("团队周会", t.title)
        assertEquals("同步进度", t.note)
        assertEquals(listOf(1440, 60, 30, 5), t.rules.map { it.minutes })
        // 与原版 main.cpp:1310 的 parts[13 + k] 完全一致（k 从 1 开始），
        // 即 4 个标记位从第 5 个字段起读；这里刻意保留原版行为以做到格式级兼容。
        assertEquals(listOf(true, false, false, false), t.rules.map { it.fired })
    }

    @Test
    fun unknownCategoryFallsBackToOther() {
        val t = mkTask(cat = 99)
        val parsed = Store.parseTasks(Store.serializeTasks(listOf(t))).single()
        assertEquals(Categories.OTHER, parsed.category)
    }

    @Test
    fun pipesInTextAreSanitized() {
        val t = mkTask(title = "a|b", note = "c|d")
        val parsed = Store.parseTasks(Store.serializeTasks(listOf(t))).single()
        assertEquals("a b", parsed.title)
        assertEquals("c d", parsed.note)
        assertEquals("a|b", t.title)   // 原对象不被就地修改
    }

    /*------------------------- 提醒收敛 -------------------------*/

    @Test
    fun startedEventMarksEveryRuleFired() {
        val t = normalizeRules(mkTask(), diffSec = -1, includeNearest = false)
        assertTrue(t.rules.all { it.fired })
    }

    @Test
    fun passedLevelsBecomeFiredButNearestStaysReady() {
        // 距开始 45 分钟：只有“提前 1 天”这一级已经过去；“提前 1 小时”正是当前所处级别，
        // 仍保持未触发（启动加载时保留补响一次的机会），30/5 分钟两级同样未触发。
        val rules = normalizeRules(mkTask(), 45 * 60, false).rules
        assertTrue(rules.first { it.minutes == 1440 }.fired)
        assertFalse(rules.first { it.minutes == 60 }.fired)
        assertFalse(rules.first { it.minutes == 30 }.fired)
        assertFalse(rules.first { it.minutes == 5 }.fired)
    }

    @Test
    fun includeNearestAlsoSilencesNearestRuleAboveFiveMinutes() {
        val rules = normalizeRules(mkTask(), 20 * 60, true).rules
        assertTrue(rules.first { it.minutes == 30 }.fired)
        assertFalse(rules.first { it.minutes == 5 }.fired)
    }

    @Test
    fun noRuleFiresWhenFarInFuture() {
        val t = mkTask()
        val out = normalizeRules(t, 10L * 24 * 3600, false)
        assertTrue(out === t)   // 未变化时返回同一对象，避免无谓重组
    }

    /*------------------------- 跨天时间片 -------------------------*/

    @Test
    fun daySpanClipsToRequestedDay() {
        val t = mkTask().copy(
            start = LocalDateTime.of(2026, 10, 26, 22, 0),
            end = LocalDateTime.of(2026, 10, 27, 2, 0)
        )
        assertEquals(Pair(22 * 60, 1440), t.daySpan(LocalDate.of(2026, 10, 26)))
        assertEquals(Pair(0, 120), t.daySpan(LocalDate.of(2026, 10, 27)))
        assertNull(t.daySpan(LocalDate.of(2026, 10, 28)))
        assertTrue(t.onDate(LocalDate.of(2026, 10, 27)))
        assertFalse(t.onDate(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun daySpanOfFullDayIsWholeDay() {
        val t = mkTask().copy(
            start = LocalDateTime.of(2026, 10, 26, 0, 0),
            end = LocalDateTime.of(2026, 10, 27, 0, 0)
        )
        assertEquals(Pair(0, 1440), t.daySpan(LocalDate.of(2026, 10, 26)))
        assertNull(t.daySpan(LocalDate.of(2026, 10, 27)))
    }

    /*------------------------- 周视图分列 -------------------------*/

    @Test
    fun overlappingEventsGetSeparateLanes() {
        val base = LocalDateTime.of(2026, 10, 26, 9, 0)
        AppState.setTasks(
            listOf(
                mkTask(id = 1).copy(
                    start = base, end = base.plusHours(2),
                    rules = emptyList()
                ),
                mkTask(id = 2, cat = Categories.WORK).copy(
                    start = base.plusMinutes(30), end = base.plusHours(1),
                    rules = emptyList()
                )
            )
        )
        val blocks = buildWeekBlocks(mondayOf(base.toLocalDate()))
        val monday = blocks.filter { it.col == 0 }
        assertEquals(2, monday.size)
        assertEquals(setOf(0, 1), monday.map { it.lane }.toSet())
        assertTrue(monday.all { it.lanes == 2 })
        AppState.setTasks(emptyList())
    }

    @Test
    fun weekBlocksAreEmptyWithoutTasks() {
        AppState.setTasks(emptyList())
        assertTrue(buildWeekBlocks(mondayOf(LocalDate.of(2026, 10, 26))).isEmpty())
    }

    /*------------------------- 其它辅助 -------------------------*/

    @Test
    fun mondayOfAnyWeekdayIsMonday() {
        for (d in 19..25) {
            val day = LocalDate.of(2026, 10, d)
            val mon = mondayOf(day)
            assertEquals(1, mon.dayOfWeek.value)
            assertTrue(!mon.isAfter(day) && day.isBefore(mon.plusDays(7)))
        }
    }

    @Test
    fun monthTitleFollowsLanguage() {
        AppState.setLang(LANG_ZH)
        assertEquals("2026年10月", fmtMonthTitle(2026, 10))
        AppState.setLang(LANG_EN)
        assertEquals("October 2026", fmtMonthTitle(2026, 10))
        AppState.setLang(LANG_ZH)
    }

    @Test
    fun leadTimeFormattingCoversDayHourMinute() {
        AppState.setLang(LANG_ZH)
        assertEquals("1 天 1 小时", fmtLead(1500))
        assertEquals("30 分钟", fmtLead(30))
        assertEquals("现在", fmtLead(0))
    }

    @Test
    fun sortRulesOrdersByDescendingLeadTime() {
        val sorted = sortRules(listOf(Reminder(5, false), Reminder(1440, false), Reminder(60, false)))
        assertEquals(listOf(1440, 60, 5), sorted.map { it.minutes })
    }

    @Test
    fun emptyTextParsesToNoTasks() {
        assertTrue(Store.parseTasks("0 1\n").isEmpty())
        assertTrue(Store.parseTasks("").isEmpty())
        assertNotNull(Store.serializeTasks(emptyList()))
        assertEquals("0 1\n", Store.serializeTasks(emptyList()))
    }
}
