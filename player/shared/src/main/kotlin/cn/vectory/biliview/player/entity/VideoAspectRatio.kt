package cn.vectory.biliview.player.entity

import android.content.Context
import cn.vectory.biliview.player.shared.R

enum class VideoAspectRatio(private val strRes: Int) {
    Default(R.string.video_aspect_ratio_default),
    FourToThree(R.string.video_aspect_ratio_four_to_three),
    SixteenToNine(R.string.video_aspect_ratio_sixteen_to_nine);

    fun getDisplayName(context: Context) = context.getString(strRes)
}