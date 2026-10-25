package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoData
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 直播流 URL 拼接/选择策略的纯单测（不访问网络）
 */
class LiveStreamSelectorTest {
    private val json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }

    /**
     * 按实际 v2 响应裁剪的样本：avc 的 current_qn 比 hevc 低，
     * 用于验证「编码优先级优先于画质」的选择策略。
     */
    private val playInfoJson = """
        {
          "room_id": 7734200,
          "live_status": 1,
          "playurl_info": {
            "conf_json": "",
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
                          "accept_qn": [250, 400, 10000],
                          "base_url": "/live-bvc/361384/live_7734200_bs_1348183_2500.flv?",
                          "url_info": [
                            {
                              "host": "https://d1--cn-gotcha04.bilivideo.com",
                              "extra": "expires=1793016145&qn=250&order=1",
                              "stream_ttl": 0
                            },
                            {
                              "host": "https://d1--cn-gotcha08.bilivideo.com",
                              "extra": "expires=1793016145&qn=250&order=2",
                              "stream_ttl": 0
                            }
                          ],
                          "hdr_qn": [],
                          "dolby_type": 0,
                          "attr_name": "",
                          "hdr_type": 0
                        },
                        {
                          "codec_name": "hevc",
                          "current_qn": 10000,
                          "accept_qn": [10000, 400, 250],
                          "base_url": "/live-bvc/361384/live_7734200_bs_1348183_10000.flv?",
                          "url_info": [
                            {
                              "host": "https://d1--cn-gotcha04.bilivideo.com",
                              "extra": "expires=1793016145&qn=10000&order=1",
                              "stream_ttl": 0
                            }
                          ],
                          "hdr_qn": [],
                          "dolby_type": 0,
                          "attr_name": "",
                          "hdr_type": 0
                        }
                      ]
                    }
                  ]
                },
                {
                  "protocol_name": "http_hls",
                  "format": [
                    {
                      "format_name": "fmp4",
                      "codec": [
                        {
                          "codec_name": "avc",
                          "current_qn": 250,
                          "base_url": "/live-bvc/967180/live_7734200_bs_1348183_2500/index.m3u8?",
                          "url_info": [
                            {
                              "host": "https://d1--cn-gotcha209.bilivideo.com",
                              "extra": "expires=1793016145&qn=250&order=1",
                              "stream_ttl": 0
                            }
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
    fun `build url appends extra when base url already ends with question mark`() {
        assertEquals(
            "https://d1--cn-gotcha04.bilivideo.com/live-bvc/361384/live.flv?expires=1&qn=250&order=1",
            LiveStreamSelector.buildUrl(
                host = "https://d1--cn-gotcha04.bilivideo.com",
                baseUrl = "/live-bvc/361384/live.flv?",
                extra = "expires=1&qn=250&order=1"
            )
        )
    }

    @Test
    fun `build url add question mark when base url has no query`() {
        assertEquals(
            "https://d1--cn-gotcha04.bilivideo.com/live-bvc/live.flv?expires=1",
            LiveStreamSelector.buildUrl(
                host = "https://d1--cn-gotcha04.bilivideo.com",
                baseUrl = "/live-bvc/live.flv",
                extra = "expires=1"
            )
        )
    }

    @Test
    fun `build url add ampersand when base url already has query`() {
        assertEquals(
            "https://d1--cn-gotcha04.bilivideo.com/live-bvc/live.flv?token=abc&expires=1",
            LiveStreamSelector.buildUrl(
                host = "https://d1--cn-gotcha04.bilivideo.com/",
                baseUrl = "/live-bvc/live.flv?token=abc",
                extra = "&expires=1"
            )
        )
    }

    @Test
    fun `build url keeps url unchanged when extra is blank`() {
        assertEquals(
            "https://d1--cn-gotcha04.bilivideo.com/live-bvc/live.flv",
            LiveStreamSelector.buildUrl(
                host = "https://d1--cn-gotcha04.bilivideo.com",
                baseUrl = "/live-bvc/live.flv",
                extra = ""
            )
        )
    }

    @Test
    fun `build url adds https scheme when host has no protocol`() {
        assertEquals(
            "https://d1--cn-gotcha04.bilivideo.com/live-bvc/live.flv?expires=1",
            LiveStreamSelector.buildUrl(
                host = "d1--cn-gotcha04.bilivideo.com",
                baseUrl = "live-bvc/live.flv",
                extra = "expires=1"
            )
        )
    }

    @Test
    fun `select flv stream prefers avc even if hevc has higher qn`() {
        val data = json.decodeFromString<RoomPlayInfoData>(playInfoJson)
        val stream = LiveStreamSelector.selectFlvStream(data)!!
        assertEquals("http_stream", stream.protocolName)
        assertEquals("flv", stream.formatName)
        assertEquals("avc", stream.codecName)
        assertEquals(250, stream.qn)
        assertEquals("超清", stream.qualityDescription)
        assertEquals(
            "https://d1--cn-gotcha04.bilivideo.com/live-bvc/361384/live_7734200_bs_1348183_2500.flv?expires=1793016145&qn=250&order=1",
            stream.url
        )
    }

    @Test
    fun `select flv stream respects custom codec priority`() {
        val data = json.decodeFromString<RoomPlayInfoData>(playInfoJson)
        val stream = LiveStreamSelector.selectFlvStream(
            data = data,
            codecPriority = listOf(LiveStreamSelector.CODEC_HEVC, LiveStreamSelector.CODEC_AVC)
        )!!
        assertEquals("hevc", stream.codecName)
        assertEquals(10000, stream.qn)
        assertEquals("原画", stream.qualityDescription)
    }

    @Test
    fun `select flv stream uses url index and falls back to first`() {
        val data = json.decodeFromString<RoomPlayInfoData>(playInfoJson)
        val second = LiveStreamSelector.selectFlvStream(data = data, urlIndex = 1)!!
        assertEquals("https://d1--cn-gotcha08.bilivideo.com", second.url.substringBefore("/live-bvc/"))
        val fallback = LiveStreamSelector.selectFlvStream(data = data, urlIndex = 99)!!
        assertEquals("https://d1--cn-gotcha04.bilivideo.com", fallback.url.substringBefore("/live-bvc/"))
    }

    @Test
    fun `select flv stream returns null when only hls is available`() {
        val onlyHls = json.decodeFromString<RoomPlayInfoData>(
            """
            {
              "room_id": 1,
              "live_status": 1,
              "playurl_info": {
                "playurl": {
                  "stream": [
                    {
                      "protocol_name": "http_hls",
                      "format": [
                        {
                          "format_name": "fmp4",
                          "codec": [
                            {
                              "codec_name": "avc",
                              "current_qn": 250,
                              "base_url": "/live/index.m3u8?",
                              "url_info": [{"host": "https://d1.bilivideo.com", "extra": "expires=1"}]
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
        )
        assertNull(LiveStreamSelector.selectFlvStream(onlyHls))
    }

    @Test
    fun `select flv stream returns null for room without playurl info`() {
        assertNull(
            LiveStreamSelector.selectFlvStream(
                json.decodeFromString<RoomPlayInfoData>("""{"room_id": 1, "live_status": 0}""")
            )
        )
    }
}
