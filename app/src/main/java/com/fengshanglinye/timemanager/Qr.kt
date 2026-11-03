package com.fengshanglinye.timemanager

/**
 * 二维码生成（Version 1-M，21×21，字节模式，最多 14 字节）。
 *
 * 与电脑端 `TmSync.h` 的 `TmQrBuild` 逐位一致：同一段文字在两端生成同样的矩阵，
 * 手机扫电脑屏幕、电脑扫手机屏幕都成立。
 */
object Qr {

    const val SIZE = 21
    private const val DATA_CW = 16
    private const val EC_CW = 10
    private const val RAW_CW = DATA_CW + EC_CW

    private val exp = IntArray(512)
    private val log = IntArray(256)
    private var ready = false

    private fun gfInit() {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
        ready = true
    }

    private fun mul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return exp[log[a] + log[b]]
    }

    private fun rs(data: IntArray, dataLen: Int, ecLen: Int): IntArray {
        val gen = IntArray(ecLen)
        val reg = IntArray(ecLen)
        val ec = IntArray(ecLen)
        gen[ecLen - 1] = 1
        for (i in 0 until ecLen) {
            val factor = exp[i]
            for (j in 0 until ecLen) {
                gen[j] = mul(gen[j], factor) xor (if (j + 1 < ecLen) gen[j + 1] else 0)
            }
        }
        for (i in 0 until dataLen) {
            val factor = data[i] xor reg[0]
            for (j in 0 until ecLen - 1) reg[j] = reg[j + 1]
            reg[ecLen - 1] = 0
            if (factor != 0) for (j in 0 until ecLen) reg[j] = reg[j] xor mul(gen[j], factor)
        }
        for (i in 0 until ecLen) ec[i] = reg[i]
        return ec
    }

    private fun encodeData(text: ByteArray): IntArray {
        val bits = IntArray(DATA_CW * 8 + 64)
        var nBits = 0
        val len = text.size
        bits[nBits++] = 0
        bits[nBits++] = 1
        bits[nBits++] = 0
        bits[nBits++] = 0
        for (i in 7 downTo 0) bits[nBits++] = (len shr i) and 1
        for (b in text) {
            val c = b.toInt() and 0xFF
            for (k in 7 downTo 0) bits[nBits++] = (c shr k) and 1
        }
        var i = 0
        while (i < 4 && nBits < DATA_CW * 8) {
            bits[nBits++] = 0
            i++
        }
        while (nBits % 8 != 0) bits[nBits++] = 0
        val words = IntArray(DATA_CW)
        for (w in 0 until DATA_CW) {
            var v = 0
            for (k in 0 until 8) v = (v shl 1) or bits[w * 8 + k]
            words[w] = v
        }
        var fill = 0
        var w = nBits / 8
        while (w < DATA_CW) {
            words[w] = if (fill % 2 == 0) 0xEC else 0x11
            fill++
            w++
        }
        return words
    }

    private fun maskBit(mask: Int, y: Int, x: Int): Boolean = when (mask) {
        0 -> (y + x) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (y + x) % 3 == 0
        4 -> ((y / 2) + (x / 3)) % 2 == 0
        5 -> ((y * x) % 2) + ((y * x) % 3) == 0
        6 -> (((y * x) % 2) + ((y * x) % 3)) % 2 == 0
        7 -> (((y + x) % 2) + ((y * x) % 3)) % 2 == 0
        else -> false
    }

    private fun formatBits(mask: Int): Int {
        var poly = mask shl 10
        for (i in 14 downTo 10) if ((poly shr i) and 1 != 0) poly = poly xor (0x537 shl (i - 10))
        return ((mask shl 10) or poly) xor 0x5412
    }

    private fun penalty(m: Array<IntArray>): Int {
        var score = 0
        val pat1 = intArrayOf(1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0)
        val pat2 = intArrayOf(0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1)

        for (y in 0 until SIZE) {
            var run = 1
            for (x in 1 until SIZE) {
                if (m[y][x] == m[y][x - 1]) {
                    run++
                    if (run == 5) score += 3 else if (run > 5) score += 1
                } else run = 1
            }
        }
        for (x in 0 until SIZE) {
            var run = 1
            for (y in 1 until SIZE) {
                if (m[y][x] == m[y - 1][x]) {
                    run++
                    if (run == 5) score += 3 else if (run > 5) score += 1
                } else run = 1
            }
        }
        for (y in 0 until SIZE - 1) for (x in 0 until SIZE - 1) {
            if (m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x] && m[y][x] == m[y + 1][x + 1]) score += 3
        }
        for (y in 0 until SIZE) {
            for (x in 0..SIZE - 11) {
                var ok1 = true
                var ok2 = true
                for (i in 0 until 11) {
                    if (m[y][x + i] != pat1[i]) ok1 = false
                    if (m[y][x + i] != pat2[i]) ok2 = false
                }
                if (ok1 || ok2) score += 40
            }
        }
        for (x in 0 until SIZE) {
            for (y in 0..SIZE - 11) {
                var ok1 = true
                var ok2 = true
                for (i in 0 until 11) {
                    if (m[y + i][x] != pat1[i]) ok1 = false
                    if (m[y + i][x] != pat2[i]) ok2 = false
                }
                if (ok1 || ok2) score += 40
            }
        }
        var dark = 0
        for (y in 0 until SIZE) for (x in 0 until SIZE) if (m[y][x] != 0) dark++
        var k = dark * 100 / (SIZE * SIZE) - 50
        if (k < 0) k = -k
        score += (k / 5) * 10
        return score
    }

    private fun finder(m: Array<IntArray>, top: Int, left: Int) {
        for (y in -1..7) {
            for (x in -1..7) {
                val r = top + y
                val c = left + x
                if (r < 0 || c < 0 || r >= SIZE || c >= SIZE) continue
                val v = when {
                    y < 0 || y > 6 || x < 0 || x > 6 -> 0
                    y == 0 || y == 6 || x == 0 || x == 6 -> 1
                    y == 1 || y == 5 || x == 1 || x == 5 -> 0
                    else -> 1
                }
                m[r][c] = v
            }
        }
    }

    private fun putFormat(m: Array<IntArray>, mask: Int) {
        val fmt = formatBits(mask)
        // 标准布局：format 串的最高位（bit14）放在 (行 8, 列 0)，依次向右；bit0 在 (行 0, 列 8)。
        for (k in 0 until 15) {
            val bit = (fmt shr (14 - k)) and 1
            when {
                k < 6 -> m[8][k] = bit
                k == 6 -> m[8][7] = bit
                k == 7 -> m[8][8] = bit
                k == 8 -> m[7][8] = bit
                else -> m[14 - k][8] = bit
            }
        }
        // 第二份 format（同样 MSB 在前）：bit14 → (行 8, 列 20) 向左到 bit7 → (行 8, 列 13)；
        // bit6..0 → 第 8 列的第 14..20 行。(行 13, 列 8) 是固定深色模块，不能写。
        for (k in 0 until 15) {
            val bit = (fmt shr (14 - k)) and 1
            when {
                k < 8 -> m[8][SIZE - 1 - k] = bit
                else -> m[SIZE - 15 + k][8] = bit
            }
        }
    }

    /** 生成 21×21 矩阵：`true` 表示深色。文字超过 14 字节时返回 null。 */
    fun build(text: String): Array<BooleanArray>? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > 14) return null
        if (!ready) gfInit()

        val base = Array(SIZE) { IntArray(SIZE) { -1 } }
        val dataMap = Array(SIZE) { BooleanArray(SIZE) }

        finder(base, 0, 0)
        finder(base, 0, SIZE - 7)
        finder(base, SIZE - 7, 0)

        for (i in 8 until SIZE - 8) {
            base[6][i] = if (i % 2 == 0) 1 else 0
            base[i][6] = if (i % 2 == 0) 1 else 0
        }
        // 固定深色模块在 (第 SIZE-8 行, 第 8 列)，putFormat 不会写这里
        base[SIZE - 8][8] = 1

        for (i in 0 until 9) {
            if (base[8][i] < 0) base[8][i] = -2
            if (base[i][8] < 0) base[i][8] = -2
        }
        for (i in 0 until 8) {
            if (base[8][SIZE - 1 - i] < 0) base[8][SIZE - 1 - i] = -2
            if (base[SIZE - 1 - i][8] < 0) base[SIZE - 1 - i][8] = -2
        }

        val words = encodeData(bytes)
        val ec = rs(words, DATA_CW, EC_CW)
        val all = IntArray(RAW_CW)
        for (i in 0 until DATA_CW) all[i] = words[i]
        for (i in 0 until EC_CW) all[DATA_CW + i] = ec[i]

        val bits = IntArray(RAW_CW * 8)
        var nBits = 0
        for (v in all) for (k in 7 downTo 0) bits[nBits++] = (v shr k) and 1

        var idx = 0
        var right = SIZE - 1
        var upward = true
        while (right > 0) {
            if (right == 6) right--
            for (i in 0 until SIZE) {
                val row = if (upward) SIZE - 1 - i else i
                for (c in 0 until 2) {
                    val col = right - c
                    if (base[row][col] != -1) continue
                    base[row][col] = if (idx < nBits) bits[idx] else 0
                    dataMap[row][col] = true
                    idx++
                }
            }
            upward = !upward
            right -= 2
        }

        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        for (mask in 0 until 8) {
            val mm = Array(SIZE) { IntArray(SIZE) }
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                var v = base[y][x]
                if (v < 0) v = 0
                if (dataMap[y][x] && maskBit(mask, y, x)) v = v xor 1
                mm[y][x] = v
            }
            putFormat(mm, mask)
            val score = penalty(mm)
            if (score < bestScore) {
                bestScore = score
                bestMask = mask
            }
        }

        val out = Array(SIZE) { BooleanArray(SIZE) }
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            var v = base[y][x]
            if (v < 0) v = 0
            if (dataMap[y][x] && maskBit(bestMask, y, x)) v = v xor 1
            out[y][x] = v != 0
        }
        val outInt = Array(SIZE) { y -> IntArray(SIZE) { x -> if (out[y][x]) 1 else 0 } }
        putFormat(outInt, bestMask)
        for (y in 0 until SIZE) for (x in 0 until SIZE) out[y][x] = outInt[y][x] != 0
        return out
    }

    /** 测试用：指定掩码对应的 15 位 format 值。 */
    internal fun formatForTest(mask: Int): Int = formatBits(mask)

    internal fun maskBitForTest(mask: Int, y: Int, x: Int): Boolean = maskBit(mask, y, x)

    /** 测试用：返回（矩阵、数据模块标记、选中的掩码），便于和标准实现逐位比对。 */
    internal class Debug(val matrix: Array<BooleanArray>, val data: Array<BooleanArray>, val mask: Int, val codewords: IntArray)

    internal fun buildDebug(text: String, forceMask: Int = -1): Debug? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > 14) return null
        if (!ready) gfInit()
        val base = Array(SIZE) { IntArray(SIZE) { -1 } }
        val dataMap = Array(SIZE) { BooleanArray(SIZE) }
        finder(base, 0, 0)
        finder(base, 0, SIZE - 7)
        finder(base, SIZE - 7, 0)
        for (i in 8 until SIZE - 8) {
            base[6][i] = if (i % 2 == 0) 1 else 0
            base[i][6] = if (i % 2 == 0) 1 else 0
        }
        base[SIZE - 8][8] = 1
        for (i in 0 until 9) {
            if (base[8][i] < 0) base[8][i] = -2
            if (base[i][8] < 0) base[i][8] = -2
        }
        for (i in 0 until 8) {
            if (base[8][SIZE - 1 - i] < 0) base[8][SIZE - 1 - i] = -2
            if (base[SIZE - 1 - i][8] < 0) base[SIZE - 1 - i][8] = -2
        }
        val words = encodeData(bytes)
        val ec = rs(words, DATA_CW, EC_CW)
        val all = IntArray(RAW_CW)
        for (i in 0 until DATA_CW) all[i] = words[i]
        for (i in 0 until EC_CW) all[DATA_CW + i] = ec[i]
        val bits = IntArray(RAW_CW * 8)
        var nBits = 0
        for (v in all) for (k in 7 downTo 0) bits[nBits++] = (v shr k) and 1
        var idx = 0
        var right = SIZE - 1
        var upward = true
        while (right > 0) {
            if (right == 6) right--
            for (i in 0 until SIZE) {
                val row = if (upward) SIZE - 1 - i else i
                for (c in 0 until 2) {
                    val col = right - c
                    if (base[row][col] != -1) continue
                    base[row][col] = if (idx < nBits) bits[idx] else 0
                    dataMap[row][col] = true
                    idx++
                }
            }
            upward = !upward
            right -= 2
        }
        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        for (mask in 0 until 8) {
            val mm = Array(SIZE) { IntArray(SIZE) }
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                var v = base[y][x]
                if (v < 0) v = 0
                if (dataMap[y][x] && maskBit(mask, y, x)) v = v xor 1
                mm[y][x] = v
            }
            putFormat(mm, mask)
            val score = penalty(mm)
            if (score < bestScore) {
                bestScore = score
                bestMask = mask
            }
        }
        if (forceMask in 0..7) bestMask = forceMask
        val matrix = Array(SIZE) { BooleanArray(SIZE) }
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            var v = base[y][x]
            if (v < 0) v = 0
            if (dataMap[y][x] && maskBit(bestMask, y, x)) v = v xor 1
            matrix[y][x] = v != 0
        }
        val asInt = Array(SIZE) { y -> IntArray(SIZE) { x -> if (matrix[y][x]) 1 else 0 } }
        putFormat(asInt, bestMask)
        for (y in 0 until SIZE) for (x in 0 until SIZE) matrix[y][x] = asInt[y][x] != 0
        return Debug(matrix, dataMap, bestMask, all)
    }

    /** 调试/测试用：把矩阵转成 `.`/`#` 文本。 */    fun toText(m: Array<BooleanArray>): String {
        val sb = StringBuilder()
        for (y in m.indices) {
            for (x in m[y].indices) sb.append(if (m[y][x]) '#' else '.')
            sb.append('\n')
        }
        return sb.toString()
    }
}
