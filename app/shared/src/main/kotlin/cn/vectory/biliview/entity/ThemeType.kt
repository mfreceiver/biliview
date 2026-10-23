package cn.vectory.biliview.entity

import android.content.Context
import cn.vectory.biliview.R
import cn.vectory.biliview.util.stringRes

enum class ThemeType(private val strRes: Int) {
    Auto(R.string.theme_type_auto),
    Dark(R.string.theme_type_dark),
    Light(R.string.theme_type_light);

    fun getDisplayName(context: Context): String = strRes.stringRes(context)
}