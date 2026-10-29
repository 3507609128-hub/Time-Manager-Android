package com.fengshanglinye.timemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/*============================= 字体 ==============================*/

/**
 * 与 Win32 版一致：中文用雅黑系、日文用游ゴシック系、英文用 Segoe/默认。
 * Android 上通过 FontFamily.Default 走系统字体，中日文由系统自动回退到
 * 思源黑体 / Noto Sans CJK，观感接近"Microsoft YaHei UI"。
 */
val UiFont: FontFamily = FontFamily.Default

/*============================= 卡片 ==============================*/

/** 白底 + 1px 发丝线边框 + 8dp 圆角，对应原版的「卡片」外观 */
@Composable
fun Card(
    t: Theme,
    modifier: Modifier = Modifier,
    radius: Dp = 8.dp,
    background: Color = t.card,
    borderColor: Color = t.border,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(radius))
    ) { content() }
}

/*============================= 按钮 ==============================*/

/** 白底描边按钮（原版 添加日程 / 删除选中 / 设置 / 取消） */
@Composable
fun OutlineButton(
    text: String,
    t: Theme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val txtColor = if (enabled) t.text else t.muted.copy(alpha = 0.6f)
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(t.card)
            .border(1.dp, t.border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Txt(text, txtColor, 13.sp, FontWeight.Medium)
    }
}

/** 主色填充按钮（原版 确定 / 选中的视图切换） */
@Composable
fun FilledButton(
    text: String,
    t: Theme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (enabled) t.accent else t.accent.copy(alpha = 0.45f)
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Txt(text, Color.White, 13.sp, FontWeight.Medium)
    }
}

/**
 * 月视图 / 周视图 分段切换：两个连在一起的按钮，
 * 选中项填充主色白字，未选中项白底描边（与原版工具栏完全一致）。
 */
@Composable
fun ViewToggle(
    isMonth: Boolean,
    t: Theme,
    monthText: String,
    weekText: String,
    onMonth: () -> Unit,
    onWeek: () -> Unit
) {
    Row {
        Box(
            Modifier
                .height(38.dp)
                .width(84.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                .background(if (isMonth) t.accent else t.card)
                .border(
                    1.dp,
                    if (isMonth) t.accent else t.border,
                    RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                )
                .clickable { onMonth() },
            contentAlignment = Alignment.Center
        ) {
            Txt(
                monthText,
                color = if (isMonth) Color.White else t.text,
                size = 13.sp,
                weight = if (isMonth) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .height(38.dp)
                .width(84.dp)
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .background(if (!isMonth) t.accent else t.card)
                .border(
                    1.dp,
                    if (!isMonth) t.accent else t.border,
                    RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
                )
                .clickable { onWeek() },
            contentAlignment = Alignment.Center
        ) {
            Txt(
                weekText,
                color = if (!isMonth) Color.White else t.text,
                size = 13.sp,
                weight = if (!isMonth) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

/*============================= 图形 ==============================*/

/** 左右尖括号（对应 DrawChevron） */
@Composable
fun Chevron(dir: Int, color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val dx = w * 0.20f
        val dy = h * 0.28f
        val cx = w / 2f
        val cy = h / 2f
        val p = androidx.compose.ui.graphics.Path()
        if (dir < 0) {
            p.moveTo(cx + dx, cy - dy)
            p.lineTo(cx - dx, cy)
            p.lineTo(cx + dx, cy + dy)
        } else {
            p.moveTo(cx - dx, cy - dy)
            p.lineTo(cx + dx, cy)
            p.lineTo(cx - dx, cy + dy)
        }
        drawPath(
            p, color,
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** 分类圆点（对应 FillDot） */
@Composable
fun Dot(color: Color, r: Dp = 3.dp) {
    Box(Modifier.size(r * 2).clip(RoundedCornerShape(50)).background(color))
}

/** 复选框（原版用系统 checkbox，这里手绘成 4dp 圆角的方块 + 对勾） */
@Composable
fun CheckBox(checked: Boolean, t: Theme, size: Dp = 18.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(if (checked) t.accent else t.card)
            .border(1.dp, if (checked) t.accent else t.muted.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(Modifier.size(size)) {
                val w = this.size.width
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.24f, w * 0.52f)
                    lineTo(w * 0.42f, w * 0.70f)
                    lineTo(w * 0.77f, w * 0.31f)
                }
                drawPath(p, Color.White, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/*============================= 列表 / 区块 ==============================*/

/** 分组标题（主色小标题，对应对话框里的「基本信息」「提醒规则」） */
@Composable
fun SectionTitle(text: String, t: Theme) {
    Txt(text, t.accent, 13.sp, FontWeight.Bold)
}

/** 发丝分隔线 */
@Composable
fun Hairline(t: Theme, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(t.border.copy(alpha = 0.7f))
    )
}

/** 左侧标签 + 右侧控件的表单行（原版所有对话框都是这个排布） */
@Composable
fun FormRow(
    label: String,
    t: Theme,
    labelWidth: Dp = 76.dp,
    content: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(labelWidth), contentAlignment = Alignment.CenterEnd) {
            Txt(label, t.text, 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 下拉选择用的“输入框”外观 */
@Composable
fun FakeDropdown(text: String, t: Theme, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(t.card)
            .border(1.dp, t.border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(
            text,
            color = if (enabled) t.text else t.muted,
            size = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Chevron(1, t.muted, 14.dp)
    }
}

/** 提示文字 */
@Composable
fun HintText(text: String, t: Theme) {
    Txt(text, t.muted, 12.sp)
}

/** 小号标签（状态栏 / 次要信息） */
@Composable
fun MutedText(text: String, t: Theme, size: Int = 12) {
    Txt(text, t.muted, size.sp)
}

/** 列表里的小色条（分类标签） */
@Composable
fun CategoryTag(cat: Int, label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Categories.fill(cat))
            .padding(horizontal = 5.dp)
            .height(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Txt(label, Color.White, 11.sp)
    }
}

/**
 * 统一文本绘制（不用 Material3，保持与原版一致的“细描边、小字号、纯色”观感）。
 * maxLines 默认 1，超出用省略号截断，对应原版 DrawText 的 DT_END_ELLIPSIS。
 */
@Composable
fun Txt(
    text: String,
    color: Color,
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = align,
            fontFamily = UiFont,
            lineHeight = size * 1.35f
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}