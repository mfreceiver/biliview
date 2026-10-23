package cn.vectory.biliview.tv.screens.settings.content

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cn.vectory.biliview.BuildConfig
import cn.vectory.biliview.R
import cn.vectory.biliview.tv.component.settings.SettingListItem
import cn.vectory.biliview.tv.component.settings.SettingSwitchListItem
import cn.vectory.biliview.tv.activities.settings.LogsActivity
import cn.vectory.biliview.tv.screens.settings.SettingsMenuNavItem
import cn.vectory.biliview.util.Prefs

@Composable
fun OtherSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showFps by remember { mutableStateOf(Prefs.showFps) }
    var enableFfmpegAudioRenderer by remember { mutableStateOf(Prefs.enableFfmpegAudioRenderer) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = SettingsMenuNavItem.Other.getDisplayName(context),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingSwitchListItem(
                    title = stringResource(R.string.settings_other_fps_title),
                    supportText = stringResource(R.string.settings_other_fps_text),
                    checked = showFps,
                    onCheckedChange = {
                        showFps = it
                        Prefs.showFps = it
                    }
                )
            }
            item {
                SettingListItem(
                    title = stringResource(R.string.settings_create_logs_title),
                    supportText = stringResource(R.string.settings_create_logs_text),
                    onClick = {
                        context.startActivity(Intent(context, LogsActivity::class.java))
                    }
                )
            }
            if (BuildConfig.DEBUG) {
                item {
                    SettingListItem(
                        title = stringResource(R.string.settings_crash_test_title),
                        supportText = stringResource(R.string.settings_crash_test_text),
                        onClick = {
                            throw Exception("Boom!")
                        }
                    )
                }
            }
            item {
                SettingSwitchListItem(
                    title = stringResource(R.string.settings_other_ffmpeg_audio_renderer_title),
                    supportText = stringResource(R.string.settings_other_ffmpeg_audio_renderer_text),
                    checked = enableFfmpegAudioRenderer,
                    onCheckedChange = {
                        enableFfmpegAudioRenderer = it
                        Prefs.enableFfmpegAudioRenderer = it
                    }
                )
            }
        }
    }
}