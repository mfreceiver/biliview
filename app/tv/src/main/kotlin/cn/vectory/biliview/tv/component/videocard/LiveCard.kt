package cn.vectory.biliview.tv.component.videocard

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import cn.vectory.biliview.entity.carddata.LiveCardData
import cn.vectory.biliview.shared.R
import cn.vectory.biliview.tv.component.UpIcon
import cn.vectory.biliview.ui.theme.BVTheme
import cn.vectory.biliview.util.ImageSize
import cn.vectory.biliview.util.resizedImageUrl

/**
 * 直播卡片，风格对齐 [SmallVideoCard]：封面 + 底部人气/分区信息 + 标题 + 主播名
 */
@Composable
fun LiveCard(
    modifier: Modifier = Modifier,
    data: LiveCardData,
    onClick: () -> Unit = {},
    onFocus: () -> Unit = {}
) {
    var hasFocus by remember { mutableStateOf(false) }

    LiveCardContent(
        modifier = modifier
            .onFocusChanged {
                hasFocus = it.isFocused
                if (hasFocus) onFocus()
            },
        data = data,
        hasFocus = hasFocus,
        onClick = onClick
    )
}

@Composable
fun LiveCardContent(
    modifier: Modifier = Modifier,
    data: LiveCardData,
    hasFocus: Boolean,
    onClick: () -> Unit = {}
) {
    val infoOffsetY by animateDpAsState(
        targetValue = if (hasFocus) 8.dp else 0.dp,
        animationSpec = spring(),
        label = "live info offset y"
    )

    Column(
        modifier = modifier
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                pressedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = CardDefaults.shape(shape = MaterialTheme.shapes.large),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 3.dp, color = MaterialTheme.colorScheme.border),
                    shape = MaterialTheme.shapes.large
                )
            )
        ) {
            LiveCover(
                cover = data.cover,
                onlineString = data.onlineString,
                areaName = data.areaName,
                liveStatus = data.liveStatus
            )
        }

        LiveCardInfo(
            modifier = Modifier
                .offset(y = infoOffsetY),
            title = data.title,
            uname = data.uname
        )
    }
}

@Composable
private fun LiveCover(
    modifier: Modifier = Modifier,
    cover: String,
    onlineString: String,
    areaName: String,
    liveStatus: Boolean
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(MaterialTheme.shapes.large)
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large),
            model = cover.resizedImageUrl(ImageSize.SmallVideoCardCover),
            contentDescription = null,
            contentScale = ContentScale.FillBounds
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onlineString.isNotBlank()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_count),
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = onlineString,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = if (liveStatus) areaName else "未开播",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiveCardInfo(
    modifier: Modifier = Modifier,
    title: String,
    uname: String
) {
    Column(
        modifier = modifier.padding(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UpIcon()
            Text(
                text = uname,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun previewLiveCardData(roomId: Long = 0L) = LiveCardData(
    roomId = roomId,
    title = "震惊！主播又在深夜整活了，这次居然是这样的内容",
    cover = "",
    uname = "bishi",
    online = 23333,
    areaName = "虚拟主播"
)

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LiveCardWithoutFocusPreview() {
    BVTheme {
        Surface(
            modifier = Modifier.width(300.dp)
        ) {
            LiveCardContent(
                modifier = Modifier.padding(20.dp),
                data = previewLiveCardData(),
                hasFocus = false
            )
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LiveCardWithFocusPreview() {
    BVTheme {
        Surface(
            modifier = Modifier.width(300.dp)
        ) {
            LiveCardContent(
                modifier = Modifier.padding(20.dp),
                data = previewLiveCardData(),
                hasFocus = true
            )
        }
    }
}
