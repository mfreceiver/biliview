package dev.aaa1115910.biliapi.http

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 回归：loggedGet 内联展开曾出现两个编译层问题（2026-10-26 实机故障定位）：
 * 1. defaultRequest host 合并未生效 → 连接 localhost:80；
 * 2. crossinline `{ build() }` 包装被 K2 内联丢弃 → 查询参数全部缺失 → 业务 code=-400。
 * 修复后应能直连并返回 code=0。依赖外网 bilibili 接口（仓库既有测试同样联网）。
 */
class BiliLiveHttpApiRegressionTest {
    @Test
    fun liveEndpointsReachHostAndCarryParams() = runBlocking {
        val rec = BiliLiveHttpApi.getLiveRecommendList(platform = "web")
        assertEquals(0, rec.code)
        val idx = BiliLiveHttpApi.getLiveIndexList(parentAreaId = 2, page = 1)
        assertEquals(0, idx.code)
    }
}
