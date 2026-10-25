package dev.aaa1115910.biliapi.websocket

import dev.aaa1115910.biliapi.http.BiliLiveHttpApi

/** 一台弹幕服务器（只关心 wss 端口） */
data class LiveHost(
    val host: String,
    /** wss 端口，0 表示该主机不支持 wss */
    val wssPort: Int,
)

/**
 * 建立弹幕连接所需的信息
 *
 * @param roomId 真实房间号（短号转换后的房间号）
 * @param token 认证 token，每次重连都需要重新申请
 * @param hosts 弹幕服务器列表
 */
data class LiveRoomInfo(
    val roomId: Int,
    val token: String,
    val hosts: List<LiveHost>,
)

/** 提供直播间弹幕连接信息，抽成接口是为了让单测脱离网络 */
interface LiveRoomInfoProvider {
    suspend fun getLiveRoomInfo(roomId: Int): LiveRoomInfo
}

/**
 * 默认实现：走 [BiliLiveHttpApi]。
 *
 * 每次（重）连都会重新获取 token，避免 token 过期后陷入无限重连。
 *
 * 风控纪律：
 * - 响应统一走 `getResponseData()`，-352 会抛 `RiskControlException` 而不是被压成
 *   [LiveConnectionException]（后者会被上层当成网络抖动无限重连，每次都重打两个请求）。
 * - 只打 `getDanmuInfo` 一个请求：[getLiveRoomInfo] 的调用方（直播列表 / 播放页）传入的
 *   已经是官方接口返回的真实 roomId，不再额外调 v1 `getRoomPlayInfo` 反查房间号。
 *   若上游确实只拿到短号（shortId ≠ 0 且未换算），由上游先换算成真实房间号再传进来。
 */
object BiliLiveRoomInfoProvider : LiveRoomInfoProvider {
    override suspend fun getLiveRoomInfo(roomId: Int): LiveRoomInfo {
        val danmuInfo = BiliLiveHttpApi.getLiveDanmuInfo(roomId).getResponseData()
        return LiveRoomInfo(
            roomId = roomId,
            token = danmuInfo.token,
            hosts = danmuInfo.hostList.map { LiveHost(host = it.host, wssPort = it.wssPort) },
        )
    }
}

/** 弹幕连接相关异常 */
class LiveConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
