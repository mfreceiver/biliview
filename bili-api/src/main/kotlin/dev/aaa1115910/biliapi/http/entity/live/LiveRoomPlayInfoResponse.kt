package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 直播间信息（`/xlive/web-room/v2/index/getRoomPlayInfo` 的 data）
 *
 * v2 接口不返回 `need_p2p` / `is_sp` / `special_type` 等字段（v1 才有），因此全部字段都给了默认值，
 * 避免 [kotlinx.serialization.MissingFieldException]。
 */
@Serializable
data class RoomPlayInfoData(
    @SerialName("room_id")
    val roomId: Int = 0,
    @SerialName("short_id")
    val shortId: Int = 0,
    val uid: Long = 0,
    @SerialName("need_p2p")
    val needP2P: Int = 0,
    @SerialName("is_hidden")
    val isHidden: Boolean = false,
    @SerialName("is_locked")
    val isLocked: Boolean = false,
    @SerialName("is_portrait")
    val isPortrait: Boolean = false,
    @SerialName("live_status")
    val liveStatus: Int = 0,
    @SerialName("hidden_till")
    val hiddenTill: Int = 0,
    @SerialName("lock_till")
    val lockTill: Int = 0,
    val encrypted: Boolean = false,
    @SerialName("pwd_verified")
    val pwdVerified: Boolean = false,
    @SerialName("live_time")
    val liveTime: Int = 0,
    @SerialName("room_shield")
    val roomShield: Int = 0,
    @SerialName("is_sp")
    val isSp: Int = 0,
    @SerialName("special_type")
    val specialType: Int = 0,
    @SerialName("play_url")
    val playUrl: String? = null,
    @SerialName("all_special_types")
    val allSpecialTypes: List<Int> = emptyList(),
    /**
     * 直播流地址信息，仅 v2 接口返回；v1 恒为 null
     */
    @SerialName("playurl_info")
    val playUrlInfo: PlayUrlInfo? = null
)
/*
示例（v2，截断）：
"data": {
  "room_id": 7734200,
  "short_id": 6,
  "uid": 50329118,
  "is_hidden": false,
  "is_locked": false,
  "is_portrait": false,
  "live_status": 1,
  "hidden_till": 0,
  "lock_till": 0,
  "encrypted": false,
  "pwd_verified": true,
  "live_time": 1792050615,
  "room_shield": 1,
  "all_special_types": [],
  "playurl_info": {
    "conf_json": "",
    "playurl": {
      "cid": 7734200,
      "g_qn_desc": [{"qn": 10000, "desc": "原画", "hdr_desc": "", "hdr_type": 0}],
      "stream": [{
        "protocol_name": "http_stream",
        "format": [{
          "format_name": "flv",
          "codec": [{
            "codec_name": "avc",
            "current_qn": 250,
            "accept_qn": [250, 400, 10000],
            "base_url": "/live-bvc/361384/live_7734200_bs_1348183_2500.flv?",
            "url_info": [{
              "host": "https://d1--cn-gotcha04.bilivideo.com",
              "extra": "expires=...&qn=250&...&order=1",
              "stream_ttl": 0
            }],
            "hdr_qn": [],
            "dolby_type": 0,
            "attr_name": "",
            "hdr_type": 0
          }]
        }]
      }]
    }
  }
}
 */
