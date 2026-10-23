package cn.vectory.biliview.player.entity

import android.content.Context
import cn.vectory.biliview.player.shared.R

enum class VideoPlayerOthersMenuItem(private val strRes: Int) {
    PlayMode(R.string.video_player_menu_others_play_mode);

    fun getDisplayName(context: Context) = context.getString(strRes)
}