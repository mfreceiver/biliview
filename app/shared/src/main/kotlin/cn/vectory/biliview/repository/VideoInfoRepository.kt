package cn.vectory.biliview.repository

import cn.vectory.biliview.player.entity.VideoListItem
import org.koin.core.annotation.Single

@Single
class VideoInfoRepository {
    val videoList = mutableListOf<VideoListItem>()
}
