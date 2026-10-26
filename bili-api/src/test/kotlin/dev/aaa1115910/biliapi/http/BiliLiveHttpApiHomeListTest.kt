package dev.aaa1115910.biliapi.http

import dev.aaa1115910.biliapi.http.entity.live.LiveIndexListData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 网页首页同款推荐接口（index/getList 无 parent_area_id）：
 * 离线部分覆盖 Cookie 拼接纯函数与实体解析；联网部分断言 code=0 且 recommend_room_list 非空
 * （仓库既有直播回归测试同样联网）。
 */
class BiliLiveHttpApiHomeListTest {

    // ---- 离线：Cookie 拼接 ----

    @Test
    fun cookieBothPresent() {
        assertEquals(
            "SESSDATA=abc; buvid3=xyz;",
            BiliLiveHttpApi.buildHomeListCookie("abc", "xyz")
        )
    }

    @Test
    fun cookieSessdataOnly() {
        assertEquals("SESSDATA=abc;", BiliLiveHttpApi.buildHomeListCookie("abc", null))
    }

    @Test
    fun cookieBuvid3Only() {
        assertEquals("buvid3=xyz;", BiliLiveHttpApi.buildHomeListCookie(null, "xyz"))
    }

    @Test
    fun cookieBlankOrMissingIsNull() {
        assertNull(BiliLiveHttpApi.buildHomeListCookie(null, null))
        assertNull(BiliLiveHttpApi.buildHomeListCookie("", "  "))
    }

    // ---- 离线：首页首屏实体解析 ----

    @Test
    fun parseHomeListData() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val data = json.decodeFromString<LiveIndexListData>(
            """
            {
              "recommend_room_list": [
                {
                  "roomid": 123, "title": "t", "uname": "u", "uid": 1,
                  "cover": "c", "face": "f", "online": 9, "link": "/123",
                  "area_v2_id": 4, "area_v2_name": "分区", "area_v2_parent_id": 2,
                  "area_v2_parent_name": "网游", "watched_show": {"switch": true, "num": 1, "text_large": "1人看过"},
                  "rec_status": 1, "show_callback": true
                }
              ],
              "room_list": [
                {
                  "module_info": {"id": 1, "title": "推荐直播"},
                  "list": [{"roomid": 456, "title": "m"}]
                }
              ],
              "banner": [],
              "cover_source": "",
              "area_entrance_v2": {"list": []}
            }
            """.trimIndent()
        )
        assertEquals(1, data.recommendRoomList.size)
        assertEquals(123L, data.recommendRoomList.first().roomId)
        assertEquals(1, data.recommendRoomList.first().watchedShow?.num)
        assertEquals(1, data.roomList.size)
        assertEquals(456L, data.roomList.first().list.first().roomId)
    }

    // ---- 联网回归 ----

    @Test
    fun homeListReturnsRecommendRooms() = runBlocking {
        val resp = BiliLiveHttpApi.getLiveHomeList(platform = "web")
        assertEquals(0, resp.code)
        assert(resp.data?.recommendRoomList?.isNotEmpty() == true) {
            "recommend_room_list 为空：$resp"
        }
    }
}
