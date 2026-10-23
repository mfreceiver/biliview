package cn.vectory.biliview.mobile.screen.settings.details

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.aaa1115910.biliapi.entity.ApiType
import cn.vectory.biliview.mobile.component.preferences.items.radioPreference
import cn.vectory.biliview.mobile.component.preferences.preferenceGroups
import cn.vectory.biliview.mobile.theme.BVMobileTheme
import cn.vectory.biliview.util.PrefKeys

@Composable
fun AdvanceContent(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        preferenceGroups(
            null to {
                radioPreference(
                    title = "接口偏好",
                    prefReq = PrefKeys.prefApiTypeRequest,
                    values = ApiType.entries.associate { it.ordinal to it.name }
                        .toSortedMap { a, b -> a.compareTo(b) }
                )
            }
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AdvanceContentPreview() {
    BVMobileTheme {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            AdvanceContent(
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}