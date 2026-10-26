package cn.vectory.biliview.tv.screens.settings.content

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cn.vectory.biliview.repository.UserRepository
import cn.vectory.biliview.shared.BuildConfig
import cn.vectory.biliview.shared.R
import cn.vectory.biliview.tv.component.settings.SettingListItem
import cn.vectory.biliview.tv.component.settings.SettingSwitchListItem
import cn.vectory.biliview.tv.activities.settings.LogsActivity
import cn.vectory.biliview.tv.screens.settings.SettingsMenuNavItem
import cn.vectory.biliview.util.Prefs
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

@Composable
fun OtherSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userRepository = getKoin().get<UserRepository>()

    var showFps by remember { mutableStateOf(Prefs.showFps) }
    var enableFfmpegAudioRenderer by remember { mutableStateOf(Prefs.enableFfmpegAudioRenderer) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = userRepository.exportAccount()
            if (json == null) {
                Toast.makeText(
                    context, R.string.account_export_not_login, Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray())
                } ?: error("Can not open output stream")
            }.onSuccess {
                Toast.makeText(
                    context, R.string.account_export_success, Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.account_export_failed, it.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                } ?: error("Can not open input stream")
                userRepository.importAccount(json)
            }.onSuccess {
                Toast.makeText(
                    context, R.string.account_import_success, Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.account_import_failed, it.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

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
            item {
                SettingListItem(
                    title = stringResource(R.string.settings_account_export_title),
                    supportText = stringResource(R.string.settings_account_export_text),
                    onClick = { exportLauncher.launch("biliview_account.json") }
                )
            }
            item {
                SettingListItem(
                    title = stringResource(R.string.settings_account_import_title),
                    supportText = stringResource(R.string.settings_account_import_text),
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
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