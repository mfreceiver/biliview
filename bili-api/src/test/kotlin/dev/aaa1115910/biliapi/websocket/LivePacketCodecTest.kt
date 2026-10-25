package dev.aaa1115910.biliapi.websocket

import dev.aaa1115910.biliapi.http.util.zlibCompress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 封包编解码离线单测：不连网络，全部用构造出来的二进制帧驱动。
 */
internal class LivePacketCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    // region 固定样本

    /** 用 python3 brotli quality=11 压缩的 DANMU_MSG 封包（外层 protover=3） */
    private val brotliDanmakuFrame = Base64.getDecoder().decode(
        "AAAAmAAQAAMAAAAFAAAAARuaAEA8FHCHyTguyrZ5YliDKcKlcB3+mNOKkAm3IA/MT/gWKScDmLmVvLo68vFg1ddAXqyrRY6aquRYW1/qOH0FSAVN6eR2sDBAiMK2gPObZfgtoNzGwKXLp41OMX5Dq28OOvjqK8S+ml0xmG0a2xlqCyuyAqcOCNQRL020hDs/r8dd3AZSrhs="
    )

    private val plainDanmakuJson =
        """{"cmd":"DANMU_MSG","info":[[0,1,25,16777215,1700000000123,1700000000,0,"0",0],"测试弹幕",[1234567,"测试用户",0,0]]}"""

    // endregion

    @Test
    fun `心跳包字段与旧实现逐字节一致`() {
        val packet = LivePacketCodec.HEARTBEAT_PACKET
        val header = parseHeader(packet)

        assertEquals(31, packet.size)
        assertEquals(31, header.totalLength)
        assertEquals(16, header.headerLength)
        assertEquals(1, header.version)
        assertEquals(LivePacketCodec.OPERATION_HEARTBEAT, header.operation)
        assertEquals("[object Object]", packet.copyOfRange(16, packet.size).decodeToString())
    }

    @Test
    fun `认证包携带真实 uid 与协商版本`() {
        val packet = LivePacketCodec.authPacket(uid = 1234567L, roomId = 5555, token = "token-abc", protoVer = 3)
        val auth = json.parseToJsonElement(packet.copyOfRange(16, packet.size).decodeToString()) as JsonObject

        assertEquals("1234567", auth["uid"]!!.jsonPrimitive.content)
        assertEquals("5555", auth["roomid"]!!.jsonPrimitive.content)
        assertEquals("3", auth["protover"]!!.jsonPrimitive.content)
        assertEquals("web", auth["platform"]!!.jsonPrimitive.content)
        assertEquals("2", auth["type"]!!.jsonPrimitive.content)
        assertEquals("token-abc", auth["key"]!!.jsonPrimitive.content)
    }

    @Test
    fun `解析明文弹幕`() {
        val events = LivePacketCodec.decodeFrame(
            buildPacket(plainDanmakuJson.toByteArray(), version = 0, operation = LivePacketCodec.OPERATION_MESSAGE)
        )

        val danmaku = events.filterIsInstance<LiveEvent.DanmakuEvent>().single()
        assertEquals("测试弹幕", danmaku.text)
        assertEquals(0xFFFFFF, danmaku.color)
        assertEquals(1234567L, danmaku.uid)
        assertEquals("测试用户", danmaku.uname)
        assertEquals(1_700_000_000_123L, danmaku.ts)
    }

    @Test
    fun `一帧内多个封包全部解析`() {
        val frame = buildPacket(plainDanmakuJson.toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE) +
            heartbeatReplyPacket(2333)
        val events = LivePacketCodec.decodeFrame(frame)

        assertEquals(2, events.size)
        assertEquals("测试弹幕", (events[0] as LiveEvent.DanmakuEvent).text)
        assertEquals(2333L, (events[1] as LiveEvent.HeartbeatReply).popularity)
    }

    @Test
    fun `解析 zlib 压缩帧`() {
        val inner = buildPacket(plainDanmakuJson.toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE) +
            buildPacket(plainDanmakuJson.toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE)
        val frame = buildPacket(inner.zlibCompress(), version = 2, operation = LivePacketCodec.OPERATION_MESSAGE)

        val events = LivePacketCodec.decodeFrame(frame).filterIsInstance<LiveEvent.DanmakuEvent>()

        assertEquals(2, events.size)
        assertEquals("测试弹幕", events.first().text)
    }

    @Test
    fun `解析 brotli 压缩帧`() {
        val events = LivePacketCodec.decodeFrame(brotliDanmakuFrame).filterIsInstance<LiveEvent.DanmakuEvent>()

        val danmaku = events.single()
        assertEquals("测试弹幕", danmaku.text)
        assertEquals(1234567L, danmaku.uid)
        assertEquals("测试用户", danmaku.uname)
        assertEquals(0xFFFFFF, danmaku.color)
        assertEquals(1_700_000_000_123L, danmaku.ts)
    }

    @Test
    fun `损坏的压缩帧只丢弃不抛异常`() {
        val frame = buildPacket(byteArrayOf(1, 2, 3, 4), version = 3, operation = LivePacketCodec.OPERATION_MESSAGE)

        assertTrue(LivePacketCodec.decodeFrame(frame).isEmpty())
    }

    @Test
    fun `认证失败抛出认证异常`() {
        val frame = buildPacket(
            """{"code":-101,"message":"token 校验失败"}""".toByteArray(),
            version = 1,
            operation = LivePacketCodec.OPERATION_AUTH_REPLY,
        )

        val exception = assertThrows<LivePacketCodec.LiveAuthException> { LivePacketCodec.decodeFrame(frame) }
        assertEquals(-101, exception.code)
    }

    @Test
    fun `认证成功不抛异常`() {
        val frame = buildPacket(
            """{"code":0}""".toByteArray(),
            version = 1,
            operation = LivePacketCodec.OPERATION_AUTH_REPLY,
        )

        assertTrue(LivePacketCodec.decodeFrame(frame).isEmpty())
    }

    @Test
    fun `未知 cmd 走忽略分支`() {
        val events = LivePacketCodec.decodeFrame(
            buildPacket(
                """{"cmd":"ONLINE_RANK_V2","data":{"list":[]}}""".toByteArray(),
                0,
                LivePacketCodec.OPERATION_MESSAGE,
            )
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `直播状态与房间信息事件`() {
        val frame =
            buildPacket("""{"cmd":"PREPARING"}""".toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE) +
                buildPacket("""{"cmd":"LIVE"}""".toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE) +
                buildPacket(
                    """{"cmd":"ROOM_CHANGE","data":{"title":"新标题","area_name":"虚拟主播"}}""".toByteArray(),
                    0,
                    LivePacketCodec.OPERATION_MESSAGE,
                )
        val events = LivePacketCodec.decodeFrame(frame)

        assertEquals(LiveEvent.LivePreparing, events[0])
        assertEquals(LiveEvent.LiveStart, events[1])
        val roomChange = events[2] as LiveEvent.RoomChange
        assertEquals("新标题", roomChange.title)
        assertEquals("虚拟主播", roomChange.areaName)
    }

    @Test
    fun `礼物醒目留言与营收事件`() {
        val frame = buildPacket(
            """{"cmd":"GUARD_BUY","data":{"uid":10086,"username":"舰长用户","guard_level":3,"gift_name":"舰长","num":1,"price":198000}}""".toByteArray(),
            0,
            LivePacketCodec.OPERATION_MESSAGE,
        ) + buildPacket(
            """{"cmd":"SUPER_CHAT_MESSAGE","data":{"id":999,"uid":10010,"user_info":{"uname":"SC 用户"},"message":"恭喜","price":30,"start_time":1700000000000,"end_time":1700000060000}}""".toByteArray(),
            0,
            LivePacketCodec.OPERATION_MESSAGE,
        ) + buildPacket(
            """{"cmd":"REVENUE","data":{"uid":10011,"uname":"营收用户","price":1000,"message":"投喂"}}""".toByteArray(),
            0,
            LivePacketCodec.OPERATION_MESSAGE,
        )
        val events = LivePacketCodec.decodeFrame(frame)

        val guard = events[0] as LiveEvent.GuardBuy
        assertEquals(3, guard.guardLevel)
        assertEquals("舰长", guard.guardName)
        assertEquals(198000L, guard.price)

        val superChat = events[1] as LiveEvent.SuperChatMessage
        assertEquals("SC 用户", superChat.uname)
        assertEquals("恭喜", superChat.message)
        assertEquals(30, superChat.price)

        val revenue = events[2] as LiveEvent.Revenue
        assertEquals("REVENUE", revenue.cmd)
        assertEquals(1000L, revenue.amount)
        assertEquals("营收用户", revenue.uname)
    }

    @Test
    fun `带后缀的 DANMU_MSG 变体同样解析`() {
        val events = LivePacketCodec.decodeFrame(
            buildPacket(
                """{"cmd":"DANMU_MSG:4:0:2:2:2:0","info":[[0,1,25,255,1700000000123],"变体弹幕",[42,"变体用户"]]}""".toByteArray(),
                0,
                LivePacketCodec.OPERATION_MESSAGE,
            )
        )

        val danmaku = events.single() as LiveEvent.DanmakuEvent
        assertEquals("变体弹幕", danmaku.text)
        assertEquals(255, danmaku.color)
        assertEquals(42L, danmaku.uid)
    }

    @Test
    fun `截断的封包被丢弃`() {
        val full = buildPacket(plainDanmakuJson.toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE)
        val truncated = full.copyOf(full.size - 10)

        assertTrue(LivePacketCodec.decodeFrame(truncated).isEmpty())
    }

    @Test
    fun `长度字段非法的封包被丢弃`() {
        val packet = buildPacket(plainDanmakuJson.toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE)
        ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN).putInt(0, packet.size + 100)

        assertTrue(LivePacketCodec.decodeFrame(packet).isEmpty())
    }

    @Test
    fun `空帧不产生事件`() {
        assertTrue(LivePacketCodec.decodeFrame(ByteArray(0)).isEmpty())
    }

    // region 测试内构造封包

    private class Header(
        val totalLength: Int,
        val headerLength: Int,
        val version: Int,
        val operation: Int,
    )

    private fun parseHeader(packet: ByteArray): Header {
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        return Header(
            totalLength = buffer.int,
            headerLength = buffer.short.toInt(),
            version = buffer.short.toInt(),
            operation = buffer.int,
        )
    }

    private fun buildPacket(body: ByteArray, version: Int, operation: Int, sequence: Int = 1): ByteArray =
        ByteBuffer.allocate(16 + body.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(16 + body.size)
            putShort(16)
            putShort(version.toShort())
            putInt(operation)
            putInt(sequence)
            put(body)
        }.array()

    private fun heartbeatReplyPacket(popularity: Int): ByteArray =
        buildPacket(
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(popularity).array(),
            version = 1,
            operation = LivePacketCodec.OPERATION_HEARTBEAT_REPLY,
        )

    // endregion

    @Test
    fun `非 JSON 正文被忽略且不抛异常`() {
        val events = LivePacketCodec.decodeFrame(
            buildPacket("not a json".toByteArray(), 0, LivePacketCodec.OPERATION_MESSAGE)
        )

        assertTrue(events.isEmpty())
    }
}
