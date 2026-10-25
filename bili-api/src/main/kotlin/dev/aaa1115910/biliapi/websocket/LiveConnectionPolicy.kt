package dev.aaa1115910.biliapi.websocket

import kotlin.math.pow

/**
 * 断线重连退避策略（指数退避）。
 *
 * 连续掉线时依次等待 `initialDelayMs * multiplier^n`，上限 [maxDelayMs]（默认 30s）；
 * 连接稳定超过阈值后由 [LiveDataWebSocket] 调用 [reset] 清零。
 */
class ReconnectPolicy(
    private val initialDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 30_000,
    private val multiplier: Double = 2.0,
    /** 最大连续重连次数，[Int.MAX_VALUE] 表示一直重连 */
    val maxAttempts: Int = Int.MAX_VALUE,
) {
    init {
        require(initialDelayMs > 0) { "initialDelayMs 必须大于 0" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs 不能小于 initialDelayMs" }
        require(multiplier >= 1.0) { "multiplier 不能小于 1" }
        require(maxAttempts >= 0) { "maxAttempts 不能为负" }
    }

    /** 已连续重连次数 */
    var attempt: Int = 0
        private set

    /**
     * 取下一次重连的退避时长，返回 null 表示已达到 [maxAttempts]，不再重连
     */
    fun nextDelayMs(): Long? {
        if (attempt >= maxAttempts) return null
        val delay = initialDelayMs * multiplier.pow(attempt.toDouble())
        attempt++
        return delay.toLong().coerceIn(initialDelayMs, maxDelayMs)
    }

    fun reset() {
        attempt = 0
    }
}

/**
 * 候选主机排序：过滤掉没有 wss 端口的主机并去重，「上一次连上的主机」（[preferred]）提到最前。
 *
 * 原始列表按服务器的推荐顺序排列，最后一个通常是兜底 CDN，因此默认从第一个（非 last）开始；
 * 连接失败时由调用方按此顺序轮换。
 */
internal fun List<LiveHost>.orderedHosts(preferred: LiveHost? = null): List<LiveHost> {
    val usable = filter { it.wssPort > 0 }.distinct()
    if (usable.isEmpty()) return emptyList()
    val preferredHost = preferred?.takeIf { it in usable } ?: return usable
    return listOf(preferredHost) + usable.filter { it != preferredHost }
}

/**
 * 轮换到 [host] 之后的那台主机，[host] 不在列表里时从头开始
 */
internal fun List<LiveHost>.hostAfter(host: LiveHost?): LiveHost? {
    val ordered = orderedHosts()
    if (ordered.isEmpty()) return null
    val index = ordered.indexOf(host)
    return ordered[(index + 1).mod(ordered.size)]
}
