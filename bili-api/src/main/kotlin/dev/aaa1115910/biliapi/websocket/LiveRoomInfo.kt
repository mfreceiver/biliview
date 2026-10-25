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
 */
object BiliLiveRoomInfoProvider : LiveRoomInfoProvider {
    override suspend fun getLiveRoomInfo(roomId: Int): LiveRoomInfo {
        val danmuInfo = BiliLiveHttpApi.getLiveDanmuInfo(roomId).data
            ?: throw LiveConnectionException("获取直播间 $roomId 的弹幕服务器信息失败")
        val realRoomId = BiliLiveHttpApi.getLiveRoomPlayInfo(roomId).data?.roomId ?: roomId
        return LiveRoomInfo(
            roomId = realRoomId,
            token = danmuInfo.token,
            hosts = danmuInfo.hostList.map { LiveHost(host = it.host, wssPort = it.wssPort) },
        )
    }
}

/** 弹幕连接相关异常 */
class LiveConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
