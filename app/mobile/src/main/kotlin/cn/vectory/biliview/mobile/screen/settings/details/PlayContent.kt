package cn.vectory.biliview.mobile.screen.settings.details

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.vectory.biliview.mobile.component.preferences.items.radioPreference
import cn.vectory.biliview.mobile.component.preferences.preferenceGroups
import cn.vectory.biliview.mobile.theme.BVMobileTheme
import cn.vectory.biliview.player.entity.Audio
import cn.vectory.biliview.player.entity.Resolution
import cn.vectory.biliview.player.entity.VideoCodec
import cn.vectory.biliview.util.PrefKeys

@Composable
fun PlayContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        preferenceGroups(
            "画面" to {
                radioPreference(
                    title = "默认画质",
                    prefReq = PrefKeys.prefDefaultQualityRequest,
                    values = Resolution.entries.associate { it.code to it.getDisplayName(context) }
                        .toSortedMap { a, b -> a.compareTo(b) }
                )
                radioPreference(
                    title = "默认视频编码",
                    prefReq = PrefKeys.prefDefaultVideoCodecRequest,
                    values = VideoCodec.entries.associate { it.codecId to it.getDisplayName(context) }
                        .toSortedMap { a, b -> a.compareTo(b) }
                )
            },
            "音频" to {
                radioPreference(
                    title = "默认音频",
                    prefReq = PrefKeys.prefDefaultAudioRequest,
                    values = Audio.entries.associate { it.code to it.getDisplayName(context) }
                        .toSortedMap { a, b -> a.compareTo(b) }
                )
            }
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlayContentPreview() {
    BVMobileTheme {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            PlayContent(
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
