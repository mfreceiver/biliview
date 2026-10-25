package dev.aaa1115910.biliapi.websocket

import dev.aaa1115910.biliapi.http.util.zlibDecompress
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.brotli.dec.BrotliInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 直播弹幕 WebSocket 封包编解码。
 *
 * 封包格式（大端）：
 * ```
 * | 4B 封包总长度 | 2B 头部长度(16) | 2B 协议版本 | 4B 操作码 | 4B 序号 | 正文 |
 * ```
 * 协议版本：0/1 正文不压缩，2 正文 zlib 压缩，3 正文 brotli 压缩（解压后是若干「版本 0」封包）。
 * 操作码：2 心跳、3 心跳回复、5 普通消息、7 认证、8 认证回复。
 */
internal object LivePacketCodec {
    private val logger = KotlinLogging.logger { }

    const val HEADER_SIZE = 16

    const val OPERATION_HEARTBEAT = 2
    const val OPERATION_HEARTBEAT_REPLY = 3
    const val OPERATION_MESSAGE = 5
    const val OPERATION_AUTH = 7
    const val OPERATION_AUTH_REPLY = 8

    private const val PROTOCOL_VERSION_PLAIN = 0
    private const val PROTOCOL_VERSION_HEARTBEAT = 1
    private const val PROTOCOL_VERSION_ZLIB = 2
    private const val PROTOCOL_VERSION_BROTLI = 3

    /** 心跳包正文，与官方 web 端一致 */
    private const val HEARTBEAT_BODY = "[object Object]"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // 认证包要把 platform / type 这类默认值也写进 JSON，与官方客户端保持一致
        encodeDefaults = true
    }

    /** 心跳包（31 字节，与旧实现逐字节一致） */
    val HEARTBEAT_PACKET: ByteArray = buildPacket(
        version = PROTOCOL_VERSION_HEARTBEAT,
        operation = OPERATION_HEARTBEAT,
        body = HEARTBEAT_BODY.toByteArray(),
    )

    @Serializable
    private data class AuthPayload(
        val uid: Long,
        val roomid: Int,
        val protover: Int,
        val platform: String = "web",
        val type: Int = 2,
        val key: String,
    )

    /**
     * 认证包
     *
     * @param uid 当前登录用户 uid，未登录传 0
     * @param roomId 真实房间号
     * @param token [LiveRoomInfo.token]
     * @param protoVer 协商的压缩协议版本，见 [LiveCompression]
     */
    fun authPacket(uid: Long, roomId: Int, token: String, protoVer: Int): ByteArray = buildPacket(
        version = PROTOCOL_VERSION_HEARTBEAT,
        operation = OPERATION_AUTH,
        body = json.encodeToString(
            AuthPayload(uid = uid, roomid = roomId, protover = protoVer, key = token)
        ).toByteArray(),
    )

    /**
     * 解析 socket 收到的一帧数据。
     *
     * 一帧里可能串了多个封包，压缩包解压后同样是一串封包，这里递归展开。
     * 只会抛 [LiveAuthException]（认证失败），其余异常都在内部消化。
     */
    fun decodeFrame(data: ByteArray): List<LiveEvent> {
        val events = mutableListOf<LiveEvent>()
        decodePackets(data, events)
        return events
    }

    private fun buildPacket(version: Int, operation: Int, body: ByteArray, sequence: Int = 1): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + body.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(HEADER_SIZE + body.size)
        buffer.putShort(HEADER_SIZE.toShort())
        buffer.putShort(version.toShort())
        buffer.putInt(operation)
        buffer.putInt(sequence)
        buffer.put(body)
        return buffer.array()
    }

    private class PacketHeader(
        val totalLength: Int,
        val headerLength: Int,
        val version: Int,
        val operation: Int,
    )

    /**
     * 读取并校验一个封包头，长度非法 / 数据不足时返回 null（视为损坏包）
     */
    private fun readHeader(buffer: ByteArray, offset: Int): PacketHeader? {
        if (buffer.size - offset < HEADER_SIZE) return null
        val byteBuffer = ByteBuffer.wrap(buffer, offset, HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        val totalLength = byteBuffer.int
        val headerLength = byteBuffer.short.toInt() and 0xFFFF
        val version = byteBuffer.short.toInt() and 0xFFFF
        val operation = byteBuffer.int
        if (headerLength < HEADER_SIZE) return null
        if (totalLength < headerLength || totalLength > buffer.size - offset) return null
        return PacketHeader(totalLength, headerLength, version, operation)
    }

    private fun decodePackets(buffer: ByteArray, events: MutableList<LiveEvent>) {
        var offset = 0
        while (buffer.size - offset >= HEADER_SIZE) {
            val header = readHeader(buffer, offset) ?: run {
                logger.debug { "收到不完整或损坏的封包，丢弃剩余 ${buffer.size - offset} 字节" }
                return
            }
            val payload = buffer.copyOfRange(offset + header.headerLength, offset + header.totalLength)
            decodePacket(header, payload, events)
            offset += header.totalLength
        }
    }

    private fun decodePacket(header: PacketHeader, payload: ByteArray, events: MutableList<LiveEvent>) {
        when (header.operation) {
            OPERATION_MESSAGE -> decodeMessage(header.version, payload, events)
            OPERATION_HEARTBEAT_REPLY -> decodeHeartbeatReply(payload, events)
            OPERATION_AUTH_REPLY -> decodeAuthReply(payload)
            OPERATION_HEARTBEAT -> Unit
            else -> logger.debug { "忽略未知操作码: ${header.operation}" }
        }
    }

    private fun decodeMessage(version: Int, payload: ByteArray, events: MutableList<LiveEvent>) {
        when (version) {
            PROTOCOL_VERSION_PLAIN, PROTOCOL_VERSION_HEARTBEAT -> decodeJsonPayload(payload, events)
            PROTOCOL_VERSION_ZLIB -> decompress(payload, "zlib") { it.zlibDecompress() }
                ?.let { decodeDecompressedBody(it, events) }

            PROTOCOL_VERSION_BROTLI -> decompress(payload, "brotli") { it.brotliDecompress() }
                ?.let { decodeDecompressedBody(it, events) }

            else -> logger.debug { "忽略未知压缩协议版本: $version" }
        }
    }

    /**
     * 压缩包解压后正常是一串「版本 0」封包；个别服务器会直接下发裸 JSON，这里做一次兜底
     */
    private fun decodeDecompressedBody(buffer: ByteArray, events: MutableList<LiveEvent>) {
        if (readHeader(buffer, 0) != null) {
            decodePackets(buffer, events)
        } else {
            decodeJsonPayload(buffer, events)
        }
    }

    private inline fun decompress(
        payload: ByteArray,
        name: String,
        decompressor: (ByteArray) -> ByteArray,
    ): ByteArray? = try {
        decompressor(payload)
    } catch (e: Exception) {
        logger.warn(e) { "$name 解压失败，丢弃 ${payload.size} 字节" }
        null
    }

    private fun decodeHeartbeatReply(payload: ByteArray, events: MutableList<LiveEvent>) {
        if (payload.size < 4) return
        val popularity = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        events += LiveEvent.HeartbeatReply(popularity)
    }

    private fun decodeAuthReply(payload: ByteArray) {
        val code = runCatching {
            json.parseToJsonElement(payload.decodeToString()).jsonObject("code")?.toIntOrNull()
        }.getOrNull()
        if (code != null && code != 0) throw LiveAuthException(code)
    }

    private fun decodeJsonPayload(payload: ByteArray, events: MutableList<LiveEvent>) {
        val text = payload.decodeToString().trim()
        if (text.isEmpty()) return
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse {
            logger.debug { "无法解析的弹幕包: ${text.take(120)}" }
            return
        }
        when (element) {
            is JsonObject -> parseCommand(element, events)
            is JsonArray -> element.filterIsInstance<JsonObject>().forEach { parseCommand(it, events) }
            else -> Unit
        }
    }

    private fun parseCommand(data: JsonObject, events: MutableList<LiveEvent>) {
        val cmd = data.string("cmd") ?: return
        val body = data["data"] as? JsonObject
        when {
            // DANMU_MSG 带后缀的变体（如 DANMU_MSG:4:0:2:2:2:0）一并处理
            cmd.startsWith("DANMU_MSG") -> parseDanmaku(data)?.let { events += it }
            cmd == "PREPARING" -> events += LiveEvent.LivePreparing
            cmd == "LIVE" -> events += LiveEvent.LiveStart
            cmd == "ROOM_CHANGE" -> events += LiveEvent.RoomChange(
                title = body?.string("title"),
                areaName = body?.string("area_name"),
            )

            cmd == "GUARD_BUY" -> events += LiveEvent.GuardBuy(
                uid = body?.long("uid") ?: 0L,
                uname = body?.string("username"),
                guardLevel = body?.int("guard_level") ?: 0,
                guardName = body?.string("gift_name"),
                num = body?.int("num") ?: 1,
                price = body?.long("price"),
            )

            cmd == "SUPER_CHAT_MESSAGE" -> events += LiveEvent.SuperChatMessage(
                id = body?.long("id") ?: 0L,
                uid = body?.long("uid") ?: 0L,
                uname = (body?.get("user_info") as? JsonObject)?.string("uname"),
                message = body?.string("message"),
                price = body?.int("price") ?: 0,
                startTime = body?.long("start_time"),
                endTime = body?.long("end_time"),
            )

            cmd == "REVENUE" -> events += LiveEvent.Revenue(
                cmd = cmd,
                uid = body?.long("uid"),
                uname = body?.string("uname") ?: body?.string("username"),
                amount = body?.long("price") ?: body?.long("amount"),
                message = body?.string("message"),
            )

            // 未识别的 cmd 走 IGNORED 分支：只记 debug 日志，不计为错误
            else -> logger.debug { "忽略直播间事件: $cmd" }
        }
    }

    private fun parseDanmaku(data: JsonObject): LiveEvent.DanmakuEvent? = runCatching {
        val info = data["info"] as? JsonArray ?: return@runCatching null
        val text = (info.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
        val meta = info.getOrNull(0) as? JsonArray
        val user = info.getOrNull(2) as? JsonArray
        LiveEvent.DanmakuEvent(
            text = text,
            color = (meta?.getOrNull(3) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0xFFFFFF,
            uname = (user?.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: "",
            uid = (user?.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
            ts = (meta?.getOrNull(4) as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
        )
    }.getOrElse {
        logger.debug { "弹幕解析失败: ${it.message}" }
        null
    }

    /** 认证失败（token 过期 / 房间受限），由重连循环重新拉取 token */
    class LiveAuthException(val code: Int) : Exception("弹幕认证失败，code=$code")

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

    private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

    private fun JsonElement.jsonObject(key: String): JsonPrimitive? = (this as? JsonObject)?.get(key) as? JsonPrimitive

    private fun JsonPrimitive.toIntOrNull(): Int? = contentOrNull?.toIntOrNull()

    private fun ByteArray.brotliDecompress(): ByteArray =
        BrotliInputStream(this.inputStream()).use { it.readBytes() }
}
