package com.fengshanglinye.timemanager

/*============================= 多语言 =============================*/

const val LANG_ZH = 0
const val LANG_JA = 1
const val LANG_EN = 2
const val LANG_COUNT = 3

/**
 * 界面文本（与 main.cpp 的 STR_TABLE 一一对应，三语条目按行对齐）。
 * 用 "|" 分隔三种语言：中文|日本語|English，避免大段三元组表格难以维护。
 */
enum class Str(private val line: String) {
    APP_TITLE("时间管理器|タイムマネージャー|Time-Manager"),
    BTN_ADD("添加日程|予定を追加|Add"),
    BTN_DELETE("删除选中|選択を削除|Delete"),
    BTN_DELETE_N("删除选中 (%d)|選択を削除 (%d)|Delete (%d)"),
    BTN_TODAY("今日|今日|Today"),
    BTN_ALL("全部|すべて|All"),
    BTN_SET("设置|設定|Settings"),
    BTN_EDIT("编辑|編集|Edit"),

    STAT_TODAY("今日（%s）有 %d 个日程|今日（%s）の予定：%d 件|Today (%s): %d events"),
    STATUS_ALL("共 %d 个日程    数据文件：%s|%d 件の予定    データ：%s|%d events    Data: %s"),
    STATUS_FILTER("%s（%s）共 %d 个日程    再次点击日期显示全部|%s（%s）%d 件    日付をもう一度押すと全件表示|%s (%s): %d events    Tap the date again for all"),
    NO_SELECTION("请先选中要删除的日程（可长按多选）。|削除する予定をリストから選択してください（長押しで複数選択可）。|Select one or more events first (long-press for multiple)."),
    SEL_COUNT("已选 %d 个日程|%d 件選択中|%d selected"),
    SEL_ALL("全选|すべて選択|Select all"),
    SEL_CLEAR("清空|選択解除|Clear"),
    CONFIRM_DELETE("确定删除日程「%s」吗？此操作不可恢复。|予定「%s」を削除しますか？この操作は元に戻せません。|Delete \"%s\"? This cannot be undone."),
    CONFIRM_DELETE_MULTI("确定删除选中的 %d 个日程吗？此操作不可恢复。|選択した %d 件の予定を削除しますか？この操作は元に戻せません。|Delete the %d selected events? This cannot be undone."),
    CONFIRM_DEL_TITLE("确认删除|削除の確認|Confirm deletion"),
    NOTICE("提示|ヒント|Notice"),

    ADD_TITLE("添加日程|予定を追加|Add event"),
    EDIT_TITLE("编辑日程|予定を編集|Edit event"),
    ADD_SEC_BASE("基本信息|基本情報|Event details"),
    ADD_SEC_RULES("提醒规则|通知ルール|Reminders"),
    LBL_START("开始：|開始：|Start:"),
    LBL_END("结束：|終了：|End:"),
    LBL_TITLE("标题：|タイトル：|Title:"),
    LBL_NOTE("备注：|メモ：|Note:"),
    LBL_RULES("提醒规则：|通知ルール：|Reminders:"),
    LBL_REPEAT("重复：|繰返し：|Repeat:"),
    REPEAT_ONCE("单次日程|単発の予定|Single event"),
    REPEAT_WEEKS("每周重复，连续 %d 周|毎週繰り返し（%d 週間）|Weekly for %d weeks"),
    ERR_TASK_REPEAT("连续日程将超出数量上限，请减少周数。|繰り返し予定が上限を超えます。週数を減らしてください。|The weekly events would exceed the event limit. Reduce the number of weeks."),
    RULE_ADD("添加规则|ルールを追加|Add rule"),
    RULE_DEL("删除选中|選択を削除|Remove"),
    ADD_HINT("提醒规则可自由添加或删除，最多 12 条；到点会弹出通知。|通知ルールは自由に追加・削除できます（最大 12 件）。時刻になると通知が表示されます。|Add or remove reminder rules (up to 12). Notifications appear when the time comes."),
    UNIT_MIN("分钟|分|min"),
    UNIT_HOUR("小时|時間|hour"),
    UNIT_DAY("天|日|day"),
    RULE_ITEM("提前 %s|%s 前|%s before"),
    ERR_RULE_MAX("每条日程最多 12 条提醒规则。|通知ルールは 1 件の予定につき最大 12 件です。|Up to 12 reminder rules per event."),
    ERR_RULE_POS("请输入大于 0 的数字。|0 より大きい数値を入力してください。|Please enter a number greater than 0."),
    ERR_RULE_YEAR("提前时间不能超过 1 年。|通知の指定は 1 年以内にしてください。|The lead time cannot exceed one year."),
    ERR_RULE_DUP("该提醒规则已存在。|同じ通知ルールが既にあります。|This reminder rule already exists."),
    ERR_RULE_SEL("请先选中要删除的提醒规则。|削除する通知ルールを選択してください。|Select a reminder rule to remove first."),
    ERR_TASK_MAX("日程数量已达上限。|予定の上限に達しました。|The event limit has been reached."),
    ERR_TIME_INVALID("请选择有效的日期和时间。|有効な日時を選択してください。|Please choose a valid date and time."),
    ERR_TIME_ORDER("结束时间不能早于开始时间。|終了時刻は開始時刻より後にしてください。|The end time cannot be earlier than the start time."),
    ERR_TITLE_EMPTY("请填写任务标题。|タイトルを入力してください。|Please enter a title."),
    PICK_DATE("选择日期|日付を選択|Choose a date"),
    PICK_TIME("选择时间|時刻を選択|Choose a time"),
    OK("确定|OK|OK"),
    CANCEL("取消|キャンセル|Cancel"),
    SAVE("保存|保存|Save"),

    SET_TITLE("设置|設定|Settings"),
    SET_SEC_APPEAR("外观与启动|外観と起動|Appearance & Startup"),
    SET_SEC_ALERT("提醒方式|通知|Notifications"),
    SET_LANG("语言：|言語：|Language:"),
    SET_THEME("界面风格：|テーマ：|Theme:"),
    THEME_CLASSIC("经典白|クラシック|Classic"),
    THEME_MINT("清新薄荷|フレッシュミント|Fresh Mint"),
    THEME_CREAM("奶油晨光|クリームモーニング|Cream Morning"),
    THEME_SAKURA("樱粉初雪|サクラ|Sakura"),
    LANG_ZH_NAME("中文|中文|中文"),
    LANG_JA_NAME("日本語|日本語|日本語"),
    LANG_EN_NAME("English|English|English"),
    SET_AUTOSTART("开机时自动启动|ログイン時に自動起動|Start automatically at boot"),
    SET_MINSTART("手动启动时也最小化（仅通知栏提醒）|手動起動時も最小化|Also start minimized on manual launch"),
    SET_DURATION("通知停留：|通知の表示：|Show for:"),
    SET_SECONDS("%d 秒|%d 秒|%d sec"),
    SET_SOUND("提示音：|通知音：|Sound:"),
    SND_MUTE("静音（不播放）|消音（再生しない）|Mute"),
    SND_DEFAULT("系统默认|システム既定|System default"),
    SND_ASTERISK("信息提示（叮咚）|情報（ピン）|Information (ding)"),
    SND_EXCLAM("警告提示|警告|Warning"),
    SND_HAND("关键提示|重大|Critical"),
    SND_BEEP("简单蜂鸣|ビープ|Simple beep"),
    SND_WAV("自定义音频文件|カスタム音声ファイル|Custom audio file"),
    SET_TEST("试听|試聴|Test"),
    SET_WAV("自定义音频：|カスタム音声：|Custom audio:"),
    SET_BROWSE("浏览…|参照…|Browse…"),
    SET_HINT("选择“自定义音频文件”时，请指定一个音频文件作为提醒声音。|「カスタム音声ファイル」を選んだ場合は、通知音として音声ファイルを指定してください。|When \"Custom audio file\" is selected, choose an audio file as the reminder sound."),
    SET_TEST_MUTE("已选择静音，不会播放声音。|消音が選択されているため、音は再生されません。|Mute is selected, so no sound will play."),
    SET_TEST_NO_WAV("请先选择一个音频文件。|先に音声ファイルを選択してください。|Please choose an audio file first."),
    SET_EXACT("精确提醒|正確な通知|Exact reminders"),
    SET_EXACT_HINT("允许准时提醒（需要系统“闹钟与提醒”权限）。|時間どおりの通知を許可します（「アラームとリマインダー」権限）。|Allow on-time reminders (requires the system \"Alarms & reminders\" permission)."),
    SET_NOTIFY("通知权限|通知権限|Notification permission"),
    SET_NOTIFY_HINT("打开通知权限，到点才能在通知栏收到提醒。|通知を許可すると、通知バーにリマインダーが表示されます。|Enable notifications to receive reminders."),
    SET_GRANT("去授权|許可する|Grant"),
    SET_GRANTED("已授权|許可済み|Granted"),
    CREDITS_AUTHOR("制作人员：枫殇林叶|制作：枫殇林叶|Created by: 枫殇林叶"),
    CREDITS_EMAIL("联系邮箱：%s|連絡先：%s|Contact: %s"),
    TODAY_LABEL("今天|今日|Today"),

    VIEW_MONTH("月视图|月表示|Month"),
    VIEW_WEEK("周视图|週表示|Week"),
    WEEK_TODAY("本周|今週|This week"),
    WEEK_RANGE("%d/%d – %d/%d"),
    WEEK_NO_EVENT("本周暂无日程|今週の予定はありません|No events this week"),
    DAY_EVENT_COUNT("%d 个日程|%d 件の予定|%d events"),
    AGENDA_EMPTY("还没有日程，点右上角「添加日程」新增一条|予定はまだありません。右上の「予定を追加」から作成できます|No events yet — use \"Add\" at the top right"),
    AGENDA_EMPTY_DAY("这一天还没有安排，再点一次日期可取消筛选|この日の予定はありません。日付をもう一度押すと絞り込みを解除します|Nothing planned for this day. Tap the date again to clear the filter"),
    AGENDA_TODAY("今天|今日|Today"),
    TIP_CAT("分类：%s|カテゴリー：%s|Category: %s"),
    TIP_TIME("%s %s – %s|%s %s – %s|%s %s – %s"),
    TIP_REMIND("提醒：%d 次|通知：%d 回|Reminders: %d"),
    TIP_NOTE("备注：%s|メモ：%s|Note: %s"),
    TIP_EMPTY("暂无日程|予定なし|No events"),
    TIP_ACTION("点击编辑 / 长按多选|タップで編集 / 長押しで複数選択|Tap to edit / long-press to multi-select"),

    NOW("现在|今|now"),
    LEAD_DAY("%d 天|%d日|%dd"),
    LEAD_HOUR("%d 小时|%d時間|%dh"),
    LEAD_MIN("%d 分钟|%d分|%dm"),
    LBL_CAT("分类：|カテゴリー：|Category:"),
    CAT_COURSE("课程|授業|Course"),
    CAT_CHORES("家务|家事|Chores"),
    CAT_WORK("工作|仕事|Work"),
    CAT_SPORT("运动|運動|Sport"),
    CAT_FUN("娱乐|趣味|Leisure"),
    CAT_OTHER("其他|その他|Other"),

    TOAST_APP("时间管理器|タイムマネージャー|Time-Manager"),
    TOAST_HEAD("任务「%s」将在 %s后开始|「%s」は %s後に開始します|\"%s\" starts in %s"),
    TOAST_TIME("%s（%s）%s 开始，%s 结束|%s（%s）%s 開始、%s 終了|%s (%s)  %s – %s"),
    TOAST_NOTE("备注：%s|メモ：%s|Note: %s"),
    SERVICE_TITLE("时间管理器正在后台提醒|タイムマネージャーがバックグラウンドで通知中|Time-Manager is reminding in the background"),
    SERVICE_TEXT("共 %d 个日程，未完成的提醒会按时通知|予定 %d 件。未完了の通知は時間どおりに届きます|%d events — pending reminders will fire on time"),
    BOOT_RESTORED("日程提醒已恢复|リマインダーを復元しました|Reminders restored"),

    // 7 天 × 3 语言（中文 | 日本語 | English），每种语言占 7 个条目，顺序必须与 WEEK_FULL 一致
    WEEK_SHORT("日|一|二|三|四|五|六|日|月|火|水|木|金|土|Sun|Mon|Tue|Wed|Thu|Fri|Sat"),
    WEEK_FULL("周日|周一|周二|周三|周四|周五|周六|日曜日|月曜日|火曜日|水曜日|木曜日|金曜日|土曜日|Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday"),
    MO_B("1月|2月|3月|4月|5月|6月|7月|8月|9月|10月|11月|12月");

    fun at(lang: Int): String {
        val parts = line.split('|')
        val l = lang.coerceIn(0, LANG_COUNT - 1)
        if (this == WEEK_SHORT || this == WEEK_FULL) {
            // 7 天 × 3 语言的扁平表：每种语言占 7 个条目，直接取整条会得到单个星期名，
            // 所以这里只返回该语言的第一天；要取具体星期请用 weekShort()/weekFull()。
            val base = l * 7
            return parts.getOrElse(base) { parts.first() }
        }
        // 兜底：字符串里缺少某种语言时退回第一种，绝不越界（历史上这里崩过）
        return parts.getOrElse(l) { parts.first() }
    }

    /** 星期表按 (语言, 序号) 取值 */
    fun day(lang: Int, i: Int): String {
        val parts = line.split('|')
        val base = lang.coerceIn(0, LANG_COUNT - 1) * 7
        return parts.getOrElse(base + i.coerceIn(0, 6)) { parts.first() }
    }
}

/** 取当前语言的文本 */
fun S(s: Str): String = s.at(AppState.lang)
/** 取指定语言的文本 */
fun S(s: Str, lang: Int): String = s.at(lang)

/** 取当前语言的星期短名（0=周日 … 6=周六，与 Win32 版 wDayOfWeek 对齐） */
fun weekShort(i: Int, lang: Int = AppState.lang): String = Str.WEEK_SHORT.day(lang, i)

/** 取当前语言的星期全名 */
fun weekFull(i: Int, lang: Int = AppState.lang): String = Str.WEEK_FULL.day(lang, i)

/** 分类名称（分类编号 1..CAT_COUNT） */
fun catName(cat: Int): String {
    return when (Categories.normalize(cat)) {
        Categories.COURSE -> S(Str.CAT_COURSE)
        Categories.CHORES -> S(Str.CAT_CHORES)
        Categories.WORK -> S(Str.CAT_WORK)
        Categories.SPORT -> S(Str.CAT_SPORT)
        Categories.FUN -> S(Str.CAT_FUN)
        else -> S(Str.CAT_OTHER)
    }
}

/** 界面风格名称 */
fun themeName(id: Int): String = when (id) {
    ThemeId.CLASSIC.index -> S(Str.THEME_CLASSIC)
    ThemeId.MINT.index -> S(Str.THEME_MINT)
    ThemeId.CREAM.index -> S(Str.THEME_CREAM)
    ThemeId.SAKURA.index -> S(Str.THEME_SAKURA)
    else -> S(Str.THEME_MINT)
}

/** 提示音选项名称 */
fun soundName(sound: Int): String = when (sound) {
    SOUND_MUTE -> S(Str.SND_MUTE)
    SOUND_DEFAULT -> S(Str.SND_DEFAULT)
    SOUND_ASTERISK -> S(Str.SND_ASTERISK)
    SOUND_EXCLAM -> S(Str.SND_EXCLAM)
    SOUND_HAND -> S(Str.SND_HAND)
    SOUND_BEEP -> S(Str.SND_BEEP)
    SOUND_WAV -> S(Str.SND_WAV)
    else -> S(Str.SND_ASTERISK)
}
