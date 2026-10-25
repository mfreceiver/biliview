package dev.aaa1115910.biliapi.websocket

import dev.aaa1115910.biliapi.http.entity.RiskControlException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用假 socket 驱动的离线单测：不建真实 WebSocket，覆盖连接、认证、心跳、重连与关闭语义。
 */
internal class LiveDataWebSocketTest {

    @Test
    fun `连接后收到弹幕并发送认证与心跳`() = runBlocking {
        val factory = FakeSocketFactory()
        val provider = FakeRoomInfoProvider()
        val ws = createSocket(factory, provider, mid = 42L, heartbeatMs = 50)
        val recorder = EventRecorder(ws, this).start()
        try {
            val job = ws.connect(ROOM_ID, this)
            recorder.awaitTyped<LiveEvent.Connection.Connected>()
            val socket = awaitSocket(factory)

            // 认证包带真实 uid，且使用 brotli(v3) 协商
            val auth = socket.sent.first().bodyJson()
            assertEquals("42", auth["uid"]!!.jsonPrimitive.content)
            assertEquals("$ROOM_ID", auth["roomid"]!!.jsonPrimitive.content)
            assertEquals("3", auth["protover"]!!.jsonPrimitive.content)
            assertEquals("token-0", auth["key"]!!.jsonPrimitive.content)

            socket.push(danmakuFrame("你好"))
            val danmaku = recorder.awaitTyped<LiveEvent.DanmakuEvent>()
            assertEquals("你好", danmaku.text)
            assertEquals(100L, danmaku.uid)
            assertEquals(0xFF0000, danmaku.color)

            assertTrue(
                withTimeoutOrNull(2_000) {
                    while (socket.sent.none { it.contentEquals(LivePacketCodec.HEARTBEAT_PACKET) }) delay(10)
                    true
                } == true,
                "应在心跳间隔后发送心跳包",
            )
            assertTrue(ws.isConnected)
            job.cancel()
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `未登录时 uid 为 0`() = runBlocking {
        val factory = FakeSocketFactory()
        val ws = createSocket(factory, FakeRoomInfoProvider(), mid = null)
        val recorder = EventRecorder(ws, this).start()
        try {
            ws.connect(ROOM_ID, this)
            recorder.awaitTyped<LiveEvent.Connection.Connected>()
            val auth = awaitSocket(factory).sent.first().bodyJson()
            assertEquals("0", auth["uid"]!!.jsonPrimitive.content)
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `掉线后退避重连并重新认证`() = runBlocking {
        val factory = FakeSocketFactory()
        val provider = FakeRoomInfoProvider()
        val ws = createSocket(factory, provider)
        val recorder = EventRecorder(ws, this).start()
        try {
            ws.connect(ROOM_ID, this)
            recorder.awaitTyped<LiveEvent.Connection.Connected>()
            val firstSocket = awaitSocket(factory, index = 0)
            firstSocket.serverClose()

            val reconnecting = recorder.awaitTyped<LiveEvent.Connection.Reconnecting>()
            assertEquals(1, reconnecting.attempt)
            assertEquals(20L, reconnecting.delayMs)

            recorder.awaitEventCount(2, LiveEvent.Connection.Connected::class.java)
            val secondSocket = awaitSocket(factory, index = 1)
            assertEquals("token-0", secondSocket.sent.first().bodyJson()["key"]!!.jsonPrimitive.content)
            assertEquals(2, factory.attempts.size)
            assertEquals(2, provider.calls)
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `连接失败时按推荐顺序轮换主机`() = runBlocking {
        val factory = FakeSocketFactory(failHosts = setOf("h1.example.com"))
        val ws = createSocket(factory, FakeRoomInfoProvider())
        val recorder = EventRecorder(ws, this).start()
        try {
            ws.connect(ROOM_ID, this)
            val connected = recorder.awaitTyped<LiveEvent.Connection.Connected>()

            assertEquals("h2.example.com", connected.host)
            assertEquals(listOf("h1.example.com", "h2.example.com"), factory.attempts)
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `认证失败触发重连并重新获取 token`() = runBlocking {
        val factory = FakeSocketFactory()
        val provider = FakeRoomInfoProvider()
        val ws = createSocket(factory, provider)
        val recorder = EventRecorder(ws, this).start()
        try {
            ws.connect(ROOM_ID, this)
            recorder.awaitTyped<LiveEvent.Connection.Connected>()
            awaitSocket(factory, index = 0).push(authReplyFrame(code = -101))

            val reconnecting = recorder.awaitTyped<LiveEvent.Connection.Reconnecting>()
            assertTrue(reconnecting.cause?.contains("LiveAuthException") == true, "实际原因: ${reconnecting.cause}")

            recorder.awaitEventCount(2, LiveEvent.Connection.Connected::class.java)
            assertEquals(2, provider.calls)
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `重连次数耗尽后放弃并回调 onDisconnect`() = runBlocking {
        val factory = FakeSocketFactory(failHosts = setOf("h1.example.com", "h2.example.com"))
        val ws = createSocket(
            factory,
            FakeRoomInfoProvider(),
            policy = ReconnectPolicy(initialDelayMs = 10, maxDelayMs = 50, maxAttempts = 0),
        )
        val recorder = EventRecorder(ws, this).start()
        val disconnects = mutableListOf<LiveDisconnectInfo>()
        try {
            val job = ws.connect(ROOM_ID, this) { disconnects += it }
            recorder.awaitTyped<LiveEvent.Connection.Failed>()

            assertTrue(job.isCompleted)
            assertEquals(1, disconnects.size)
            val info = disconnects.single()
            assertEquals(false, info.willReconnect)
            assertEquals(0, info.attempt)
            assertTrue(info.reason?.contains("LiveConnectionException") == true, "实际原因: ${info.reason}")
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `命中风控时熔断不再重连`() = runBlocking {
        val factory = FakeSocketFactory()
        val provider = RiskControlRoomInfoProvider()
        val ws = createSocket(factory, provider)
        val recorder = EventRecorder(ws, this).start()
        val disconnects = mutableListOf<LiveDisconnectInfo>()
        try {
            val job = ws.connect(ROOM_ID, this) { disconnects += it }
            val failed = recorder.awaitTyped<LiveEvent.Connection.Failed>()

            assertTrue(failed.riskControl, "风控失败应带 riskControl 标记，实际: $failed")
            assertTrue(job.isCompleted)
            assertEquals(1, provider.calls, "风控后不应再拉取弹幕信息")
            assertEquals(emptyList(), factory.attempts, "风控后不应再连弹幕服务器")
            assertEquals(false, disconnects.single().willReconnect)
            assertEquals(
                emptyList(),
                recorder.snapshot().filterIsInstance<LiveEvent.Connection.Reconnecting>(),
                "风控不应产生重连事件",
            )
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `close 会关闭 socket 并停止重连`() = runBlocking {
        val factory = FakeSocketFactory()
        val ws = createSocket(factory, FakeRoomInfoProvider())
        val recorder = EventRecorder(ws, this).start()
        try {
            val job = ws.connect(ROOM_ID, this)
            recorder.awaitTyped<LiveEvent.Connection.Connected>()
            val socket = awaitSocket(factory)

            ws.close()

            assertTrue(job.isCompleted)
            assertTrue(socket.closed)
            assertEquals(false, ws.isConnected)
            recorder.awaitTyped<LiveEvent.Connection.Closed>()
            delay(200)
            assertEquals(1, factory.attempts.size, "close 后不应再发起重连")
        } finally {
            recorder.stop()
        }
    }

    @Test
    fun `取消返回的 Job 等同于断开连接`() = runBlocking {
        val factory = FakeSocketFactory()
        val ws = createSocket(factory, FakeRoomInfoProvider())
        val recorder = EventRecorder(ws, this).start()
        try {
            val job = ws.connect(ROOM_ID, this)
            recorder.awaitTyped<LiveEvent.Connection.Connected>()
            val socket = awaitSocket(factory)

            job.cancel()
            job.join()

            assertTrue(socket.closed)
            assertEquals(false, ws.isConnected)
            delay(200)
            assertEquals(1, factory.attempts.size)
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    @Test
    fun `重复 connect 会替换旧连接`() = runBlocking {
        val factory = FakeSocketFactory()
        val ws = createSocket(factory, FakeRoomInfoProvider())
        val recorder = EventRecorder(ws, this).start()
        try {
            val firstJob = ws.connect(ROOM_ID, this)
            recorder.awaitTyped<LiveEvent.Connection.Connected>()
            val firstSocket = awaitSocket(factory, index = 0)

            ws.connect(ROOM_ID, this)
            recorder.awaitEventCount(2, LiveEvent.Connection.Connected::class.java)

            firstJob.join()
            assertTrue(firstJob.isCompleted)
            assertTrue(firstSocket.closed)
            assertEquals(2, factory.sockets.size)
        } finally {
            ws.close()
            recorder.stop()
        }
    }

    // region 测试脚手架

    private suspend inline fun <reified T : LiveEvent> EventRecorder.awaitTyped(timeoutMs: Long = 5_000): T =
        awaitEvent(timeoutMs, T::class.java.simpleName) { it is T } as T

    private suspend fun awaitSocket(factory: FakeSocketFactory, index: Int = 0): FakeSocket =
        withTimeoutOrNull(2_000) {
            while (factory.sockets.size <= index) delay(10)
            factory.sockets[index]
        } ?: throw AssertionError("socket #$index 未建立")

    private fun createSocket(
        factory: LiveSocketFactory,
        provider: LiveRoomInfoProvider,
        mid: Long? = 42L,
        policy: ReconnectPolicy = ReconnectPolicy(initialDelayMs = 20, maxDelayMs = 100),
        heartbeatMs: Long = 30_000,
    ) = LiveDataWebSocket(
        roomInfoProvider = provider,
        socketFactory = factory,
        midProvider = { mid },
        compression = LiveCompression.BROTLI,
        reconnectPolicy = policy,
        heartbeatIntervalMs = heartbeatMs,
    )

    private class EventRecorder(
        private val ws: LiveDataWebSocket,
        private val scope: CoroutineScope,
    ) {
        private val lock = Any()
        private val items = mutableListOf<LiveEvent>()
        private var job: Job? = null

        fun start(): EventRecorder {
            job = scope.launch {
                ws.events.collect { event -> synchronized(lock) { items += event } }
            }
            return this
        }

        fun stop() {
            job?.cancel()
        }

        fun snapshot(): List<LiveEvent> = synchronized(lock) { items.toList() }

        suspend fun awaitEvent(
            timeoutMs: Long = 5_000,
            description: String = "事件",
            predicate: (LiveEvent) -> Boolean,
        ): LiveEvent {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                synchronized(lock) { items.firstOrNull(predicate) }?.let { return it }
                delay(10)
            }
            throw AssertionError(
                "等待 $description 超时，已收到: ${snapshot().map { it::class.simpleName }}"
            )
        }

        suspend fun awaitEventCount(count: Int, clazz: Class<out LiveEvent>, timeoutMs: Long = 5_000) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (synchronized(lock) { items.count { clazz.isInstance(it) } } >= count) return
                delay(10)
            }
            throw AssertionError(
                "等待 $count 个 ${clazz.simpleName} 超时，已收到: ${snapshot().map { it::class.simpleName }}"
            )
        }
    }

    private class FakeRoomInfoProvider : LiveRoomInfoProvider {
        var calls = 0
            private set

        override suspend fun getLiveRoomInfo(roomId: Int): LiveRoomInfo {
            calls++
            return LiveRoomInfo(
                roomId = ROOM_ID,
                token = "token-0",
                hosts = listOf(
                    LiveHost("h1.example.com", 443),
                    LiveHost("h2.example.com", 443),
                ),
            )
        }
    }

    /** 模拟 getDanmuInfo 返回 -352 */
    private class RiskControlRoomInfoProvider : LiveRoomInfoProvider {
        var calls = 0
            private set

        override suspend fun getLiveRoomInfo(roomId: Int): LiveRoomInfo {
            calls++
            throw RiskControlException("请求过于频繁")
        }
    }

    private class FakeSocketFactory(private val failHosts: Set<String> = emptySet()) : LiveSocketFactory {
        private val lock = Any()
        private val attemptList = mutableListOf<String>()
        private val socketList = mutableListOf<FakeSocket>()

        val attempts: List<String> get() = synchronized(lock) { attemptList.toList() }
        val sockets: List<FakeSocket> get() = synchronized(lock) { socketList.toList() }

        override suspend fun connect(host: String, port: Int, path: String): LiveSocket {
            synchronized(lock) { attemptList += host }
            if (host in failHosts) throw LiveConnectionException("拒绝连接 $host")
            return FakeSocket(host).also { socket -> synchronized(lock) { socketList += socket } }
        }
    }

    private class FakeSocket(val host: String) : LiveSocket {
        private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        private val lock = Any()
        private val sentList = mutableListOf<ByteArray>()

        @Volatile
        var closed = false
            private set

        val sent: List<ByteArray> get() = synchronized(lock) { sentList.toList() }

        override suspend fun send(data: ByteArray) {
            synchronized(lock) { sentList += data }
        }

        override suspend fun receive(): ByteArray? = try {
            incoming.receive()
        } catch (e: ClosedReceiveChannelException) {
            null
        }

        fun push(frame: ByteArray) {
            incoming.trySend(frame)
        }

        fun serverClose() {
            incoming.close()
        }

        override suspend fun close() {
            closed = true
            incoming.close()
        }
    }

    // endregion

    // region 封包构造

    private fun ByteArray.bodyJson(): JsonObject =
        Json.parseToJsonElement(copyOfRange(16, size).decodeToString()) as JsonObject

    private fun packet(body: ByteArray, version: Int, operation: Int): ByteArray =
        ByteBuffer.allocate(16 + body.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(16 + body.size)
            putShort(16)
            putShort(version.toShort())
            putInt(operation)
            putInt(1)
            put(body)
        }.array()

    private fun danmakuFrame(text: String, uid: Long = 100L): ByteArray = packet(
        """{"cmd":"DANMU_MSG","info":[[0,1,25,16711680,1700000000123],"$text",[$uid,"测试用户"]]}""".toByteArray(),
        version = 0,
        operation = LivePacketCodec.OPERATION_MESSAGE,
    )

    private fun authReplyFrame(code: Int): ByteArray = packet(
        """{"code":$code}""".toByteArray(),
        version = 1,
        operation = LivePacketCodec.OPERATION_AUTH_REPLY,
    )

    // endregion

    companion object {
        private const val ROOM_ID = 5555
    }
}
