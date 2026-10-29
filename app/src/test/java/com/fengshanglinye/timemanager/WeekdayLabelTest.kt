package com.fengshanglinye.timemanager

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 星期名取值回归：曾经 `fmtAgendaDate` 用 `S(Str.WEEK_SHORT).split('|')[...]` 取星期名，
 * 而 `S(Str.WEEK_SHORT)` 返回的只是“当前语言的单个星期名”，于是周一到周六都会
 * 抛 IndexOutOfBoundsException（表现为“打开就闪退”，周日不闪）。
 */
class WeekdayLabelTest {

    @Test
    fun agendaDateWorksForEveryWeekday() {
        AppState.setLang(LANG_ZH)
        // 2026-10-25 是周日
        val sunday = LocalDate.of(2026, 10, 25)
        val expected = listOf("日", "一", "二", "三", "四", "五", "六")
        for (i in 0..6) {
            val d = sunday.plusDays(i.toLong())
            val s = fmtAgendaDate(d)
            assertTrue("$s 应包含 ${d.monthValue}/${d.dayOfMonth}", s.contains("${d.monthValue}/${d.dayOfMonth}"))
            assertTrue("$s 应包含星期 ${expected[i]}", s.endsWith(expected[i]))
        }
    }

    @Test
    fun weekdayLabelsFollowLanguage() {
        AppState.setLang(LANG_ZH)
        assertEquals("一", weekShort(1))
        assertEquals("周一", weekFull(1))
        AppState.setLang(LANG_EN)
        assertEquals("Mon", weekShort(1))
        assertEquals("Monday", weekFull(1))
        AppState.setLang(LANG_JA)
        assertEquals("月", weekShort(1))
        assertEquals("月曜日", weekFull(1))
        AppState.setLang(LANG_ZH)
    }
}
