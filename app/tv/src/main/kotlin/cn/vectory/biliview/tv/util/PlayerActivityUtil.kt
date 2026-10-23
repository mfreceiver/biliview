package cn.vectory.biliview.tv.util

import android.content.Context
import cn.vectory.biliview.entity.proxy.ProxyArea
import cn.vectory.biliview.tv.activities.video.RemoteControllerPanelDemoActivity
import cn.vectory.biliview.tv.activities.video.VideoPlayerV3Activity
import cn.vectory.biliview.util.Prefs

fun launchPlayerActivity(
    context: Context,
    avid: Long,
    cid: Long,
    title: String,
    partTitle: String,
    played: Int,
    fromSeason: Boolean,
    subType: Int? = null,
    epid: Int? = null,
    seasonId: Int? = null,
    isVerticalVideo: Boolean = false,
    proxyArea: ProxyArea = ProxyArea.MainLand,
    playerIconIdle: String = "",
    playerIconMoving: String = ""
) {
    if (Prefs.showedRemoteControllerPanelDemo) {
        VideoPlayerV3Activity.actionStart(
            context, avid, cid, title, partTitle, played, fromSeason, subType, epid, seasonId,
            isVerticalVideo, proxyArea, playerIconIdle, playerIconMoving
        )
    } else {
        RemoteControllerPanelDemoActivity.actionStart(
            context, avid, cid, title, partTitle, played, fromSeason, subType, epid, seasonId,
            isVerticalVideo, proxyArea, playerIconIdle, playerIconMoving
        )
    }
}