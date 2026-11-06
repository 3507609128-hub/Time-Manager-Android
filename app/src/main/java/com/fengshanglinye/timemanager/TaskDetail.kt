package com.fengshanglinye.timemanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 周视图里双击某个日程色块后弹出的「日程详情」。
 *
 * 只读展示：标题 / 分类 / 日期 / 时间 / 备注 / 提醒规则；
 * 底部「关闭」与「编辑」（编辑会关掉本卡片并打开原有的编辑对话框）。
 */
@Composable
fun TaskDetailDialog(
    t: Theme,
    task: Task,
    onClose: () -> Unit,
    onEdit: () -> Unit
) {
    BaseDialog(t, onDismiss = onClose) {
        DialogHeader(S(Str.DETAIL_TITLE), t, onClose)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            /*-------------------- 标题 + 分类 --------------------*/
            DialogCard(t) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryTag(task.category, catName(task.category))
                    Spacer(Modifier.weight(1f))
                    Txt(S(Str.DETAIL_ID).format(task.id), t.muted, 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Txt(task.title.ifBlank { S(Str.NO_TITLE) }, t.text, 17.sp, FontWeight.Bold, maxLines = 3)
            }

            Spacer(Modifier.height(10.dp))

            /*-------------------- 时间 --------------------*/
            DialogCard(t) {
                SectionTitle(S(Str.DETAIL_WHEN), t)
                Spacer(Modifier.height(8.dp))
                FormRow(S(Str.DETAIL_DATE), t) {
                    Txt(fmtDate(task.start.toLocalDate()), t.text, 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                FormRow(S(Str.DETAIL_TIME), t) {
                    Txt(task.timeRange(), t.text, 13.sp)
                }
                if (task.start.toLocalDate() != task.end.toLocalDate()) {
                    Spacer(Modifier.height(6.dp))
                    HintText(S(Str.DETAIL_SPAN).format(fmtDate(task.end.toLocalDate())), t)
                }
            }

            /*-------------------- 备注 --------------------*/
            if (task.note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                DialogCard(t) {
                    SectionTitle(S(Str.DETAIL_NOTE), t)
                    Spacer(Modifier.height(6.dp))
                    Txt(task.note, t.text, 13.sp, maxLines = 8)
                }
            }

            /*-------------------- 提醒规则 --------------------*/
            Spacer(Modifier.height(10.dp))
            DialogCard(t) {
                SectionTitle(S(Str.DETAIL_REMIND), t)
                Spacer(Modifier.height(6.dp))
                if (task.rules.isEmpty()) {
                    HintText(S(Str.DETAIL_NO_REMIND), t)
                } else {
                    for (r in task.rules) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Dot(if (r.fired) t.accent else t.muted, 3.dp)
                            Spacer(Modifier.width(8.dp))
                            Txt(
                                S(Str.RULE_ITEM).format(fmtLead(r.minutes)),
                                t.text, 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (r.fired) Txt(S(Str.DETAIL_FIRED), t.muted, 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Hairline(t)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineButton(S(Str.DETAIL_CLOSE), t, onClick = onClose)
            Spacer(Modifier.width(8.dp))
            FilledButton(S(Str.DETAIL_EDIT), t, onClick = onEdit)
        }
    }
}
