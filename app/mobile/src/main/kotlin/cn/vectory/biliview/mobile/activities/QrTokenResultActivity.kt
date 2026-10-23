package cn.vectory.biliview.mobile.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.mobile.screen.QrTokenResultScreen
import io.github.oshai.kotlinlogging.KotlinLogging

class QrTokenResultActivity : ComponentActivity() {
    companion object {
        private val logger = KotlinLogging.logger { }

        fun launch(context: Context, uri: Uri) {
            logger.info { "launch QrTokenResultActivity: uri=$uri" }
            context.startActivity(
                Intent(context, QrTokenResultActivity::class.java).apply {
                    putExtra("uri", uri)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QrTokenResultScreen()
        }
    }
}