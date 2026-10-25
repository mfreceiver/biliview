package dev.aaa1115910.biliapi.websocket

import dev.aaa1115910.biliapi.repositories.AuthRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** 协商的压缩协议版本，数值即弹幕认证包里的 `protover` */
enum class LiveCompression(val protoVer: Int) {
    /** zlib（protover=2），兼容性最好 */
    ZLIB(2),

    /** brotli（protover=3），官方 web 端默认 */
    BROTLI(3),
}

/**
 * B 站直播弹幕 WebSocket。
 *
 * 用法：
 * ```
 * val ws = LiveDataWebSocket(midProvider = { authRepository.mid })
 * val job = ws.connect(roomId, lifecycleScope)
 * ws.events.collect { ... }
 * // 退出
 * ws.close()
 * ```
 *
 * 语义：
 * - [connect] 返回 [Job]，调用方可直接 `cancel()`；[close] 会取消内部 Job 并彻底关闭 socket。
 * - 连接断开（服务端关闭、网络错误、认证失败）后按 [ReconnectPolicy] 指数退避重连，
 *   每次重连都会重新拉取 token 与主机列表并重新认证。
 * - 事件经 [events] 单向下发；未识别的 cmd 只记 debug 日志，不会中断连接。
 */
class LiveDataWebSocket(
    private val roomInfoProvider: LiveRoomInfoProvider,
    private val socketFactory: LiveSocketFactory = KtorLiveSocketFactory,
    private val midProvider: () -> Long? = { null },
    private val compression: LiveCompression = LiveCompression.BROTLI,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS,
    private val stableConnectionMs: Long = DEFAULT_STABLE_CONNECTION_MS,
) {
    /** 便捷入口：uid 从 [AuthRepository] 取，未登录时为 0 */
    constructor(
        roomInfoProvider: LiveRoomInfoProvider,
        authRepository: AuthRepository,
        socketFactory: LiveSocketFactory = KtorLiveSocketFactory,
        compression: LiveCompression = LiveCompression.BROTLI,
        reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    ) : this(
        roomInfoProvider = roomInfoProvider,
        socketFactory = socketFactory,
        midProvider = { authRepository.mid },
        compression = compression,
        reconnectPolicy = reconnectPolicy,
    )

    private val logger = KotlinLogging.logger { }

    private val _events = MutableSharedFlow<LiveEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 直播间事件流（弹幕、连接状态等） */
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()

    private val jobRef = AtomicReference<Job?>(null)
    private val socketRef = AtomicReference<LiveSocket?>(null)

    /** 上一次成功连上的主机，重连时优先复用 */
    @Volatile
    private var lastConnectedHost: LiveHost? = null

    /** 当前连接已建立多久（毫秒），0 表示当前未连上 */
    @Volatile
    private var connectedSinceMillis: Long = 0L

    /** 上一次连接是否稳定存活过 [stableConnectionMs]（决定重连后是否清零退避） */
    @Volatile
    private var lastConnectionWasStable = false

    val isConnected: Boolean
        get() = connectedSinceMillis > 0L

    /**
     * 连接直播间并持续接收事件，返回的 [Job] 取消即断开。
     *
     * 重复调用会先取消上一次连接。内部已在重连循环里兜住所有异常，
     * 需要感知「彻底放弃重连」时使用 [onDisconnect]（`willReconnect == false`）。
     */
    fun connect(
        roomId: Int,
        scope: CoroutineScope,
        onDisconnect: ((LiveDisconnectInfo) -> Unit)? = null,
    ): Job {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            runCatching { runConnection(roomId, onDisconnect) }
                .onFailure { if (it is CancellationException) throw it else logger.error(it) { "弹幕连接异常退出" } }
        }
        job.invokeOnCompletion { cause ->
            if (jobRef.compareAndSet(job, null)) {
                connectedSinceMillis = 0L
                _events.tryEmit(LiveEvent.Connection.Closed)
            }
            if (cause != null && cause !is CancellationException) {
                logger.debug(cause) { "弹幕连接结束" }
            }
        }
        jobRef.getAndSet(job)?.cancel()
        job.start()
        return job
    }

    /**
     * 取消重连并彻底断开，返回前 socket 已关闭
     */
    suspend fun close() {
        val job = jobRef.getAndSet(null)
        if (job != null) {
            job.cancelAndJoin()
            _events.tryEmit(LiveEvent.Connection.Closed)
        }
        socketRef.getAndSet(null)?.let { socket ->
            runCatching { socket.close() }
                .onFailure { logger.debug(it) { "关闭弹幕 socket 失败" } }
        }
        connectedSinceMillis = 0L
    }

    private suspend fun runConnection(
        roomId: Int,
        onDisconnect: ((LiveDisconnectInfo) -> Unit)?,
    ) {
        reconnectPolicy.reset()
        while (currentCoroutineContext().isActive) {
            val cause: Throwable? = try {
                pump(roomId)
                logger.debug { "弹幕服务器关闭了连接" }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e
            }
            connectedSinceMillis = 0L

            // 稳定连接过一段时间才认为恢复，避免抖动时退避归零
            if (lastConnectionWasStable) {
                reconnectPolicy.reset()
            }
            lastConnectionWasStable = false

            val delayMs = reconnectPolicy.nextDelayMs()
            if (delayMs == null) {
                _events.tryEmit(LiveEvent.Connection.Failed(cause.describe()))
                onDisconnect?.invoke(
                    LiveDisconnectInfo(
                        reason = cause?.describe(),
                        willReconnect = false,
                        attempt = reconnectPolicy.attempt,
                        retryDelayMs = 0L,
                    )
                )
                return
            }

            val attempt = reconnectPolicy.attempt
            logger.debug(cause) { "弹幕连接断开，第 $attempt 次重连将在 ${delayMs}ms 后开始" }
            _events.tryEmit(LiveEvent.Connection.Reconnecting(attempt, delayMs, cause.describe()))
            onDisconnect?.invoke(
                LiveDisconnectInfo(
                    reason = cause?.describe(),
                    willReconnect = true,
                    attempt = attempt,
                    retryDelayMs = delayMs,
                )
            )
            delay(delayMs)
        }
    }

    /**
     * 拉取 token 与主机列表，按「非 last 优先 + 失败轮换」依次尝试，直到某台主机连上；
     * 连接建立后的异常不再换主机，直接抛给上层走退避重连
     */
    private suspend fun pump(roomId: Int) {
        val roomInfo = roomInfoProvider.getLiveRoomInfo(roomId)
        val hosts = roomInfo.hosts.orderedHosts(preferred = lastConnectedHost)
        if (hosts.isEmpty()) throw LiveConnectionException("房间 ${roomInfo.roomId} 没有可用的弹幕服务器")

        var lastError: Throwable? = null
        for (host in hosts) {
            if (!currentCoroutineContext().isActive) return
            val socket = try {
                openHost(roomInfo, host)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.debug(e) { "连接弹幕服务器 ${host.host}:${host.wssPort} 失败" }
                lastError = e
                continue
            }
            readEvents(socket)
            return
        }
        throw LiveConnectionException("房间 ${roomInfo.roomId} 的弹幕服务器均连接失败", lastError)
    }

    /** 连接单台主机并发送认证包，成功后返回 socket；失败时自行收尾并抛出 */
    private suspend fun openHost(roomInfo: LiveRoomInfo, host: LiveHost): LiveSocket {
        _events.tryEmit(LiveEvent.Connection.Connecting(host.host, host.wssPort, reconnectPolicy.attempt + 1))
        val socket = socketFactory.connect(host.host, host.wssPort, SUB_PATH)
        socketRef.set(socket)
        try {
            socket.send(
                LivePacketCodec.authPacket(
                    uid = midProvider() ?: 0L,
                    roomId = roomInfo.roomId,
                    token = roomInfo.token,
                    protoVer = compression.protoVer,
                )
            )
        } catch (e: Throwable) {
            socketRef.compareAndSet(socket, null)
            runCatching { socket.close() }
            throw e
        }
        lastConnectedHost = host
        connectedSinceMillis = System.currentTimeMillis()
        _events.tryEmit(LiveEvent.Connection.Connected(host.host, host.wssPort))
        return socket
    }

    /** 持续收取事件直到连接结束；退出时关闭 socket 并记录连接是否稳定 */
    private suspend fun readEvents(socket: LiveSocket) {
        try {
            coroutineScope {
                val heartbeat = launch { heartbeatLoop(socket) }
                try {
                    while (true) {
                        val frame = socket.receive() ?: break
                        for (event in LivePacketCodec.decodeFrame(frame)) {
                            _events.tryEmit(event)
                        }
                    }
                } finally {
                    heartbeat.cancel()
                }
            }
        } finally {
            socketRef.compareAndSet(socket, null)
            val connectedFor = if (connectedSinceMillis > 0L) {
                System.currentTimeMillis() - connectedSinceMillis
            } else {
                0L
            }
            lastConnectionWasStable = connectedFor >= stableConnectionMs
            connectedSinceMillis = 0L
            runCatching { socket.close() }
                .onFailure { logger.debug(it) { "关闭弹幕 socket 失败" } }
        }
    }

    private suspend fun heartbeatLoop(socket: LiveSocket) {
        while (currentCoroutineContext().isActive) {
            delay(heartbeatIntervalMs)
            if (!currentCoroutineContext().isActive) break
            try {
                socket.send(LivePacketCodec.HEARTBEAT_PACKET)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.debug(e) { "心跳发送失败，等待读取循环发现断线" }
                return
            }
        }
    }

    companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000L
        const val DEFAULT_STABLE_CONNECTION_MS = 30_000L
        private const val EVENT_BUFFER_CAPACITY = 256
        private const val SUB_PATH = "/sub"
    }
}

/** 把异常压成给 UI / 日志用的一行文本 */
private fun Throwable?.describe(): String? =
    this?.let { "${it::class.simpleName}: ${it.message ?: "无详细信息"}" }
