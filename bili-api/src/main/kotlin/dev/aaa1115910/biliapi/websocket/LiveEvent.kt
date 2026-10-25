package dev.aaa1115910.biliapi.websocket

/**
 * 直播间 WebSocket 事件。
 *
 * [LiveDataWebSocket] 按弹幕封包的 `cmd` 字段分发为强类型事件：弹幕走 [DanmakuEvent]，
 * 礼物 / 醒目留言 / 上舰等只发事件（默认不打印、不影响弹幕管线），未识别的 `cmd` 直接忽略。
 */
sealed interface LiveEvent {
    /**
     * 弹幕（`DANMU_MSG`）
     *
     * @param text 弹幕文本
     * @param color 弹幕颜色（RGB 的十进制表示，白色为 0xFFFFFF）
     * @param uname 发送者昵称
     * @param uid 发送者 uid
     * @param ts 发送时间戳（毫秒）
     */
    data class DanmakuEvent(
        val text: String,
        val color: Int,
        val uname: String,
        val uid: Long,
        val ts: Long,
    ) : LiveEvent

    /** 开播（`LIVE`） */
    data object LiveStart : LiveEvent

    /** 下播 / 直播准备中（`PREPARING`） */
    data object LivePreparing : LiveEvent

    /** 直播间信息变更（`ROOM_CHANGE`） */
    data class RoomChange(
        val title: String?,
        val areaName: String?,
    ) : LiveEvent

    /** 上舰（`GUARD_BUY`） */
    data class GuardBuy(
        val uid: Long,
        val uname: String?,
        /** 舰长等级：1 总督 2 提督 3 舰长 */
        val guardLevel: Int,
        val guardName: String?,
        val num: Int,
        val price: Long?,
    ) : LiveEvent

    /** 醒目留言（`SUPER_CHAT_MESSAGE`） */
    data class SuperChatMessage(
        val id: Long,
        val uid: Long,
        val uname: String?,
        val message: String?,
        val price: Int,
        val startTime: Long?,
        val endTime: Long?,
    ) : LiveEvent

    /**
     * 营收类提醒（`REVENUE` 等）
     *
     * 该类事件的字段随 cmd 不同而不同，取不到的一律为 null，UI 侧按需展示。
     */
    data class Revenue(
        val cmd: String,
        val uid: Long?,
        val uname: String?,
        val amount: Long?,
        val message: String?,
    ) : LiveEvent

    /** 心跳回复（人气值 / 观看人数） */
    data class HeartbeatReply(val popularity: Long) : LiveEvent

    /** 连接状态事件 */
    sealed interface Connection : LiveEvent {
        /** 正在连接某台弹幕服务器（[attempt] 为当前第几次重连，首次连接为 1） */
        data class Connecting(val host: String, val port: Int, val attempt: Int) : Connection

        /** 已建立连接并发出认证包 */
        data class Connected(val host: String, val port: Int) : Connection

        /** 连接断开，[delayMs] 毫秒后进行第 [attempt] 次重连 */
        data class Reconnecting(val attempt: Int, val delayMs: Long, val cause: String?) : Connection

        /** 重连次数耗尽 / 无法恢复，不再重连 */
        data class Failed(val cause: String?) : Connection

        /** 主动关闭（调用 [LiveDataWebSocket.close] 或取消连接 Job） */
        data object Closed : Connection
    }
}

/**
 * 掉线通知，供 `onDisconnect` 回调使用。
 *
 * @param reason 掉线原因（异常信息），主动关闭时为 null
 * @param willReconnect 是否会自动重连
 * @param attempt 已连续重连的次数
 * @param retryDelayMs 下次重连的退避时长，不再重连时为 null
 */
data class LiveDisconnectInfo(
    val reason: String?,
    val willReconnect: Boolean,
    val attempt: Int,
    val retryDelayMs: Long? = null,
)
