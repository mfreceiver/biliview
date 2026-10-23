package cn.vectory.biliview.tv.activities.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.tv.screens.search.SearchResultScreen
import cn.vectory.biliview.ui.theme.BVTheme

class SearchResultActivity : ComponentActivity() {
    companion object {
        fun actionStart(context: Context, keyword: String, enableProxy: Boolean) {
            context.startActivity(
                Intent(context, SearchResultActivity::class.java).apply {
                    putExtra("keyword", keyword)
                    putExtra("enableProxy", enableProxy)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                SearchResultScreen()
            }
        }
    }
}