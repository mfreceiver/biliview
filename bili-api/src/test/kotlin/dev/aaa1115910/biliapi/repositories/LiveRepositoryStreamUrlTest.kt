package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.LiveStreamSelector
import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoData
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * [LiveRepository.getLiveStreamUrl] 的「复用已有 RoomPlayInfoData」入口单测（不访问网络）
 *
 * 直播播放页进房时会先请求一次 v2 `getRoomPlayInfo` 拿 `live_status` / `g_qn_desc`，
 * 选流必须复用这份数据：旧实现再调一次 `getLiveStreamUrl(roomId, qn)`，内部又打一遍
 * 同参数请求，等于进房打两次，既浪费配额又更容易撞风控。
 */
class LiveRepositoryStreamUrlTest {
    private val json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }

    private val repository = LiveRepository(authRepository = AuthRepository())

    /** 与 v2 实际响应同构的裁剪样本：avc 的 current_qn 低于 hevc，用于验证编码优先级 */
    private val playInfoJson = """
        {
          "room_id": 7734200,
          "live_status": 1,
          "playurl_info": {
            "playurl": {
              "cid": 7734200,
              "g_qn_desc": [
                {"qn": 10000, "desc": "原画", "hdr_desc": "", "hdr_type": 0},
                {"qn": 250, "desc": "超清", "hdr_desc": "", "hdr_type": 0}
              ],
              "stream": [
                {
                  "protocol_name": "http_stream",
                  "format": [
                    {
                      "format_name": "flv",
                      "codec": [
                        {
                          "codec_name": "avc",
                          "current_qn": 250,
                          "base_url": "/live-bvc/live_7734200_2500.flv?",
                          "url_info": [
                            {"host": "https://d1--cn-gotcha04.bilivideo.com", "extra": "expires=1&qn=250"}
                          ]
                        },
                        {
                          "codec_name": "hevc",
                          "current_qn": 10000,
                          "base_url": "/live-bvc/live_7734200_10000.flv?",
                          "url_info": [
                            {"host": "https://d1--cn-gotcha04.bilivideo.com", "extra": "expires=1&qn=10000"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `selects stream from already fetched room info without another request`() {
        val roomInfo = json.decodeFromString<RoomPlayInfoData>(playInfoJson)
        val stream = repository.getLiveStreamUrl(roomInfo = roomInfo)
        assertEquals("avc", stream.codecName)
        assertEquals(250, stream.qn)
        assertEquals("超清", stream.qualityDescription)
        assertEquals(
            "https://d1--cn-gotcha04.bilivideo.com/live-bvc/live_7734200_2500.flv?expires=1&qn=250",
            stream.url
        )
    }

    @Test
    fun `honors custom codec priority on already fetched room info`() {
        val roomInfo = json.decodeFromString<RoomPlayInfoData>(playInfoJson)
        val stream = repository.getLiveStreamUrl(
            roomInfo = roomInfo,
            codecPriority = listOf(LiveStreamSelector.CODEC_HEVC, LiveStreamSelector.CODEC_AVC)
        )
        assertEquals("hevc", stream.codecName)
        assertEquals(10000, stream.qn)
        assertEquals("原画", stream.qualityDescription)
    }

    @Test
    fun `throws when already fetched room info has no flv stream`() {
        val notLive = json.decodeFromString<RoomPlayInfoData>(
            """{"room_id": 1, "live_status": 0}"""
        )
        assertThrows(IllegalStateException::class.java) {
            repository.getLiveStreamUrl(roomInfo = notLive)
        }
    }
}
