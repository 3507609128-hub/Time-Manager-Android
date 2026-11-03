package com.fengshanglinye.timemanager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File

/**
 * 二维码扫描：解码用 zxing core（纯 Java，无界面依赖），
 * 编码端是本项目的 [Qr]（Version 1-M），两端内容一致：`TM2:<6 位配对码>`。
 */
object QrScan {

    /** 配对码字符集，与 [Sync] 保持一致（不含 I、O、0、1）。 */
    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private const val MAX_SIDE = 1600

    /** 从 ARGB 像素解码，纯 JVM 可测；正色失败时自动反色再试一次。 */
    fun decodePixels(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        if (pixels.size < width * height) return null
        decodeOnce(pixels, width, height)?.let { return it }
        // 深底浅码（反色二维码）再试一次
        val flipped = IntArray(width * height)
        for (i in flipped.indices) flipped[i] = pixels[i] xor 0x00FFFFFF
        return decodeOnce(flipped, width, height)
    }

    private fun decodeOnce(pixels: IntArray, width: Int, height: Int): String? {
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
        val reader = MultiFormatReader()
        reader.setHints(hints)
        return try {
            val source = RGBLuminanceSource(width, height, pixels)
            reader.decode(BinaryBitmap(HybridBinarizer(source)))?.text
        } catch (t: Throwable) {
            null
        } finally {
            reader.reset()
        }
    }

    /** 从位图解码，自动缩放/反色重试一次。 */
    fun decodeBitmap(bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        var cur = bitmap
        val maxSide = maxOf(cur.width, cur.height)
        if (maxSide > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / maxSide.toFloat()
            val w = (cur.width * scale).toInt().coerceAtLeast(1)
            val h = (cur.height * scale).toInt().coerceAtLeast(1)
            cur = try {
                Bitmap.createScaledBitmap(cur, w, h, true)
            } catch (t: Throwable) {
                cur
            }
        }
        val w = cur.width
        val h = cur.height
        val pixels = IntArray(w * h)
        cur.getPixels(pixels, 0, w, 0, 0, w, h)
        return decodePixels(pixels, w, h)
    }

    /** 从照片文件解码。 */
    fun decodeFile(file: File?): String? {
        if (file == null || !file.exists() || file.length() == 0L) return null
        val opts = BitmapFactory.Options()
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888
        val bmp = try {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (t: Throwable) {
            null
        }
        return decodeBitmap(bmp)
    }

    /**
     * 从扫到的文本里提取 6 位配对码。
     * 支持 `TM2:ABC234`、`ABC234`、`https://x/?code=ABC234`、`tm2://ABC234` 等形态。
     */
    fun extractCode(text: String?): String? {
        if (text == null) return null
        val upper = text.trim().uppercase()
        if (upper.isEmpty()) return null

        if (upper.length == 6 && isValid(upper)) return upper

        val mark = upper.indexOf("TM2:")
        if (mark >= 0) {
            val tail = upper.substring(mark + 4)
            var cut = 0
            while (cut < tail.length && CHARSET.indexOf(tail[cut]) >= 0) cut++
            if (cut >= 6) {
                val cand = tail.substring(0, 6)
                if (isValid(cand)) return cand
            }
        }

        val withKey = Regex("(?i)(?:code|room|pair|c)=([A-Z0-9]{6})").find(upper)
        if (withKey != null && isValid(withKey.groupValues[1])) return withKey.groupValues[1]

        for (token in Regex("\\b[A-Z0-9]{6}\\b").findAll(upper)) {
            if (isValid(token.value)) return token.value
        }
        return null
    }

    fun isValid(code: String): Boolean {
        if (code.length != 6) return false
        for (c in code) if (CHARSET.indexOf(c) < 0) return false
        return true
    }
}
