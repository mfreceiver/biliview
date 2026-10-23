package cn.vectory.biliview.player.factory

import android.content.Context
import cn.vectory.biliview.player.AbstractVideoPlayer
import cn.vectory.biliview.player.VideoPlayerOptions

abstract class PlayerFactory<T : AbstractVideoPlayer> {
    abstract fun create(context: Context, options: VideoPlayerOptions): T
}