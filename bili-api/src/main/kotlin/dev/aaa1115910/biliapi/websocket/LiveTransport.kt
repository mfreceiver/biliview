package dev.aaa1115910.biliapi.websocket

import dev.aaa1115910.biliapi.http.plugins.BiliUserAgent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.http.URLProtocol
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.ClosedReceiveChannelException

/**
 * 一条已建立的弹幕 socket。
 *
 * 抽出接口是为了让单测用假实现驱动封包收发，不连真网。
 */
interface LiveSocket {
    suspend fun send(data: ByteArray)

    /** 返回下一帧数据；连接已关闭（正常关闭 / 服务器断开）返回 null */
    suspend fun receive(): ByteArray?

    suspend fun close()
}

/** 弹幕 socket 工厂 */
interface LiveSocketFactory {
    suspend fun connect(host: String, port: Int, path: String): LiveSocket
}

/**
 * 基于 Ktor OkHttp 引擎的 wss 实现。
 *
 * [client] 进程级共享：HttpClient 设计上可复用，实例级关闭反而容易泄漏连接池。
 */
object KtorLiveSocketFactory : LiveSocketFactory {
    private val logger = KotlinLogging.logger { }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            BiliUserAgent()
            install(WebSockets)
        }
    }

    override suspend fun connect(host: String, port: Int, path: String): LiveSocket {
        logger.debug { "连接弹幕服务器 wss://$host:$port$path" }
        val session = client.webSocketSession {
            url {
                protocol = URLProtocol.WSS
                this.host = host
                this.port = port
                encodedPathSegments = path.trimStart('/').split('/').filter { it.isNotEmpty() }
            }
        }
        return KtorLiveSocket(session)
    }
}

private class KtorLiveSocket(private val session: DefaultClientWebSocketSession) : LiveSocket {
    override suspend fun send(data: ByteArray) {
        session.outgoing.send(Frame.Binary(fin = true, data = data))
    }

    override suspend fun receive(): ByteArray? {
        while (true) {
            val frame = try {
                session.incoming.receive()
            } catch (e: ClosedReceiveChannelException) {
                return null
            }
            when (frame) {
                is Frame.Binary -> return frame.data
                // 服务端不应下发文本帧，容错当作正文处理
                is Frame.Text -> return frame.data
                is Frame.Close -> return null
                else -> Unit
            }
        }
    }

    override suspend fun close() {
        runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "bye")) }
    }
}
