package cn.vectory.biliview.player.impl.exo

import android.content.Context
import cn.vectory.biliview.player.VideoPlayerOptions
import cn.vectory.biliview.player.factory.PlayerFactory

class ExoPlayerFactory : PlayerFactory<ExoMediaPlayer>() {
    override fun create(context: Context, options: VideoPlayerOptions): ExoMediaPlayer {
        return ExoMediaPlayer(context, options)
    }
}