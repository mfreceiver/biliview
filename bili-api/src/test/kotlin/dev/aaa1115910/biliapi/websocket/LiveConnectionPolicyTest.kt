package dev.aaa1115910.biliapi.websocket

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 重连退避与主机排序的离线单测（纯函数，不连网）。
 */
internal class LiveConnectionPolicyTest {

    @Test
    fun `退避按指数增长并封顶 30 秒`() {
        val policy = ReconnectPolicy(initialDelayMs = 1_000, maxDelayMs = 30_000, multiplier = 2.0)

        val delays = List(7) { policy.nextDelayMs() }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays)
        assertEquals(7, policy.attempt)
    }

    @Test
    fun `reset 后从初始退避重新开始`() {
        val policy = ReconnectPolicy(initialDelayMs = 100, maxDelayMs = 1_000, multiplier = 2.0)
        repeat(3) { policy.nextDelayMs() }

        policy.reset()

        assertEquals(0, policy.attempt)
        assertEquals(100L, policy.nextDelayMs())
    }

    @Test
    fun `达到最大重连次数后返回 null`() {
        val policy = ReconnectPolicy(
            initialDelayMs = 100,
            maxDelayMs = 100,
            multiplier = 1.0,
            maxAttempts = 2,
        )

        assertEquals(100L, policy.nextDelayMs())
        assertEquals(100L, policy.nextDelayMs())
        assertNull(policy.nextDelayMs())
        assertEquals(2, policy.attempt)
    }

    @Test
    fun `maxAttempts 为 0 表示不重连`() {
        val policy = ReconnectPolicy(maxAttempts = 0)

        assertNull(policy.nextDelayMs())
    }

    @Test
    fun `默认重连次数有限且耗尽后返回 null`() {
        val policy = ReconnectPolicy()

        assertEquals(ReconnectPolicy.DEFAULT_MAX_ATTEMPTS, policy.maxAttempts)
        repeat(ReconnectPolicy.DEFAULT_MAX_ATTEMPTS) {
            assertNotNull(policy.nextDelayMs(), "第 ${it + 1} 次重连应仍在限额内")
        }
        assertNull(policy.nextDelayMs(), "超过默认上限后应放弃重连")
        assertEquals(ReconnectPolicy.DEFAULT_MAX_ATTEMPTS, policy.attempt)
    }

    @Test
    fun `非法构造参数被拒绝`() {
        val cases = listOf<() -> Unit>(
            { ReconnectPolicy(initialDelayMs = 0) },
            { ReconnectPolicy(initialDelayMs = 1_000, maxDelayMs = 500) },
            { ReconnectPolicy(multiplier = 0.5) },
            { ReconnectPolicy(maxAttempts = -1) },
        )

        cases.forEach { case ->
            var failed = false
            try {
                case()
            } catch (e: IllegalArgumentException) {
                failed = true
            }
            assertEquals(true, failed)
        }
    }

    @Test
    fun `没有 wss 端口的主机被过滤并去重`() {
        val hosts = listOf(
            LiveHost("a.example.com", 443),
            LiveHost("no-wss.example.com", 0),
            LiveHost("b.example.com", 443),
            LiveHost("a.example.com", 443),
        )

        assertEquals(
            listOf(LiveHost("a.example.com", 443), LiveHost("b.example.com", 443)),
            hosts.orderedHosts(),
        )
    }

    @Test
    fun `默认保持服务端推荐顺序而非 last 兜底主机`() {
        val hosts = listOf(
            LiveHost("first.example.com", 443),
            LiveHost("second.example.com", 443),
            LiveHost("last.example.com", 443),
        )

        assertEquals("first.example.com", hosts.orderedHosts().first().host)
        assertEquals("last.example.com", hosts.orderedHosts().last().host)
    }

    @Test
    fun `上次连上的主机优先`() {
        val preferred = LiveHost("second.example.com", 443)
        val hosts = listOf(
            LiveHost("first.example.com", 443),
            preferred,
            LiveHost("last.example.com", 443),
        )

        val ordered = hosts.orderedHosts(preferred = preferred)

        assertEquals(
            listOf("second.example.com", "first.example.com", "last.example.com"),
            ordered.map { it.host },
        )
    }

    @Test
    fun `不可用的 preferred 不会影响顺序`() {
        val hosts = listOf(LiveHost("first.example.com", 443), LiveHost("second.example.com", 443))

        assertEquals(
            listOf("first.example.com", "second.example.com"),
            hosts.orderedHosts(preferred = LiveHost("gone.example.com", 443)).map { it.host },
        )
    }

    @Test
    fun `hostAfter 轮换到下一台并在末尾回到开头`() {
        val hosts = listOf(
            LiveHost("first.example.com", 443),
            LiveHost("second.example.com", 443),
            LiveHost("third.example.com", 443),
        )

        assertEquals("second.example.com", hosts.hostAfter(hosts[0])?.host)
        assertEquals("third.example.com", hosts.hostAfter(hosts[1])?.host)
        assertEquals("first.example.com", hosts.hostAfter(hosts[2])?.host)
        assertEquals("first.example.com", hosts.hostAfter(null)?.host)
        assertEquals("first.example.com", hosts.hostAfter(LiveHost("unknown.example.com", 443))?.host)
    }

    @Test
    fun `空主机列表不会崩溃`() {
        val empty = emptyList<LiveHost>()

        assertEquals(emptyList(), empty.orderedHosts())
        assertNull(empty.hostAfter(null))
    }
}
