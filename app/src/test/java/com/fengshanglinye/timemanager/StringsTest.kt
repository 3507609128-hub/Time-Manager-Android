package com.fengshanglinye.timemanager

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多语言表自检：同一条文本在中文/日本語/English 三种语言下，
 * 必须拥有相同数量的格式占位符，否则运行到该语言时会抛
 * MissingFormatArgumentException / IllegalFormatConversionException。
 */
class StringsTest {

    private fun specs(s: String): List<String> =
        Regex("%[sd]").findAll(s).map { it.value }.toList()

    @Test
    fun placeholdersMatchAcrossLanguages() {
        val problems = ArrayList<String>()
        for (s in Str.values()) {
            val perLang = (0 until LANG_COUNT).map { specs(s.at(it)) }
            val zh = perLang[0]
            for (l in 1 until LANG_COUNT) {
                if (perLang[l] != zh) {
                    problems.add("${s.name} 语言 $l 占位符 ${perLang[l]} != 中文 $zh")
                }
            }
        }
        assertTrue("占位符不一致：\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun noStringLeaksLanguageSeparator() {
        val bad = ArrayList<String>()
        for (s in Str.values()) {
            if (s == Str.WEEK_SHORT || s == Str.WEEK_FULL) continue // 这两张表本身就是扁平多语言表
            for (l in 0 until LANG_COUNT) {
                val v = s.at(l)
                if (v.contains('|')) bad.add("${s.name} 语言 $l 含分隔符：$v")
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun everyLanguageHasNonEmptyText() {
        val bad = ArrayList<String>()
        for (s in Str.values()) {
            for (l in 0 until LANG_COUNT) {
                if (s.at(l).isBlank()) bad.add("${s.name} 语言 $l 为空")
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun formattingNeverThrows() {
        for (s in Str.values()) {
            for (l in 0 until LANG_COUNT) {
                val text = s.at(l)
                val args = specs(text).map { if (it == "%d") 1 as Any else "x" as Any }.toTypedArray()
                // 不校验结果，只要求不抛异常
                String.format(text, *args)
            }
        }
    }
}
