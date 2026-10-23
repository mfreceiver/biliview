package cn.vectory.biliview.tv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.tv.screens.TagScreen
import cn.vectory.biliview.ui.theme.BVTheme

class TagActivity : ComponentActivity() {
    companion object {
        fun actionStart(context: Context, tagId: Int, tagName: String) {
            context.startActivity(
                Intent(context, TagActivity::class.java).apply {
                    putExtra("tagId", tagId)
                    putExtra("tagName", tagName)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                TagScreen()
            }
        }
    }
}