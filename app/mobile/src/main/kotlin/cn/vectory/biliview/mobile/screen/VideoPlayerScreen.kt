package cn.vectory.biliview.mobile.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.origeek.imageViewer.previewer.ImagePreviewer
import com.origeek.imageViewer.previewer.ImagePreviewerState
import com.origeek.imageViewer.previewer.VerticalDragType
import com.origeek.imageViewer.previewer.rememberPreviewerState
import dev.aaa1115910.biliapi.entity.Picture
import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.entity.reply.CommentSort
import cn.vectory.biliview.shared.R
import cn.vectory.biliview.mobile.activities.VideoPlayerActivity
import cn.vectory.biliview.mobile.component.player.VideoPlayerPages
import cn.vectory.biliview.mobile.component.reply.CommentItem
import cn.vectory.biliview.mobile.component.reply.ReplySheetScaffold
import cn.vectory.biliview.mobile.component.videocard.RelatedVideoItem
import cn.vectory.biliview.mobile.theme.BVMobileTheme
import cn.vectory.biliview.player.entity.LocalVideoPlayerConfigData
import cn.vectory.biliview.player.entity.LocalVideoPlayerDanmakuMasksData
import cn.vectory.biliview.player.entity.LocalVideoPlayerHistoryData
import cn.vectory.biliview.player.entity.LocalVideoPlayerLoadStateData
import cn.vectory.biliview.player.entity.LocalVideoPlayerLogsData
import cn.vectory.biliview.player.entity.LocalVideoPlayerPaymentData
import cn.vectory.biliview.player.entity.LocalVideoPlayerSeekThumbData
import cn.vectory.biliview.player.entity.LocalVideoPlayerVideoInfoData
import cn.vectory.biliview.player.entity.LocalVideoPlayerVideoShotData
import cn.vectory.biliview.player.entity.VideoListPart
import cn.vectory.biliview.player.entity.VideoListPgcEpisode
import cn.vectory.biliview.player.entity.VideoListUgcEpisode
import cn.vectory.biliview.player.entity.VideoPlayerConfigData
import cn.vectory.biliview.player.entity.VideoPlayerDanmakuMasksData
import cn.vectory.biliview.player.entity.VideoPlayerHistoryData
import cn.vectory.biliview.player.entity.VideoPlayerLoadStateData
import cn.vectory.biliview.player.entity.VideoPlayerLogsData
import cn.vectory.biliview.player.entity.VideoPlayerPaymentData
import cn.vectory.biliview.player.entity.VideoPlayerSeekThumbData
import cn.vectory.biliview.player.entity.VideoPlayerVideoInfoData
import cn.vectory.biliview.player.entity.VideoPlayerVideoShotData
import cn.vectory.biliview.player.mobile.BvPlayer
import cn.vectory.biliview.util.Prefs
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.formatPubTimeString
import cn.vectory.biliview.util.ifElse
import cn.vectory.biliview.util.swapList
import cn.vectory.biliview.viewmodel.CommentViewModel
import cn.vectory.biliview.viewmodel.SeasonViewModel
import cn.vectory.biliview.viewmodel.VideoPlayerV3ViewModel
import cn.vectory.biliview.viewmodel.video.VideoDetailViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    playerViewModel: VideoPlayerV3ViewModel = koinViewModel(),
    commentVideModel: CommentViewModel = koinViewModel(),
    seasonVideModel: SeasonViewModel = koinViewModel(),
    videoDetailViewModel: VideoDetailViewModel = koinViewModel(),
    windowSizeClass: WindowSizeClass
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemUiController = rememberSystemUiController()
    val logger = KotlinLogging.logger("VideoPlayerScreen")

    var isVideoFullscreen by rememberSaveable { mutableStateOf(false) }
    val forcePortrait =
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact || windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

    val pictures = remember { mutableStateListOf<Picture>() }
    val previewerState = rememberPreviewerState(
        verticalDragType = VerticalDragType.UpAndDown,
        pageCount = { pictures.size },
        getKey = { pictures[it].key }
    )
    val replySheetState = rememberBottomSheetScaffoldState()

    val setPreviewerPictures: (List<Picture>, () -> Unit) -> Unit =
        { newPictures, afterSetPictures ->
            pictures.clear()
            pictures.addAll(newPictures)
            afterSetPictures()
        }

    SideEffect {
        systemUiController.isStatusBarVisible = !isVideoFullscreen
        systemUiController.isNavigationBarVisible = !isVideoFullscreen
        if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded) {
            systemUiController.statusBarDarkContentEnabled = true
            systemUiController.setStatusBarColor(Color.Black)
        }
        if (forcePortrait) {
            if (isVideoFullscreen) {
                (context as Activity).requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                //在模拟器设为手机尺寸时，横屏时会莫名其妙抛出异常，貌似与折叠屏特性有关，因此手机上强制竖屏
                //java.lang.IllegalArgumentException: Bounding rectangle must start at the top or left window edge for folding features
                @SuppressLint("SourceLockedOrientationActivity")
                (context as Activity).requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        } else {
            (context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(isVideoFullscreen) {
        if (isVideoFullscreen) {
            (context as Activity).window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        } else {
            (context as Activity).window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    BackHandler(previewerState.canClose || previewerState.animating) {
        if (previewerState.canClose) scope.launch {
            previewerState.closeTransform()
        }
    }

    BackHandler(isVideoFullscreen) {
        isVideoFullscreen = false
    }

    Scaffold(
        containerColor = if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .ifElse(
                    !isVideoFullscreen,
                    Modifier.padding(top = innerPadding.calculateTopPadding())
                )
            //.padding(top = innerPadding.calculateTopPadding())
        ) {
            val leftPartWidth by animateFloatAsState(
                targetValue = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded && !isVideoFullscreen) 0.6f else 1f,
                label = "VideoPlayerLeftPartWidth"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth(leftPartWidth)
            ) {
                if (playerViewModel.videoPlayer != null) {
                    CompositionLocalProvider(
                        LocalVideoPlayerSeekThumbData provides VideoPlayerSeekThumbData(
                            idleIcon = playerViewModel.playerIconIdle,
                            movingIcon = playerViewModel.playerIconMoving
                        ),
                        LocalVideoPlayerVideoInfoData provides VideoPlayerVideoInfoData(
                            width = playerViewModel.currentVideoWidth,
                            height = playerViewModel.currentVideoHeight,
                            codec = playerViewModel.currentVideoCodec.name,
                            title = playerViewModel.title,
                            partTitle = playerViewModel.partTitle,
                        ),
                        LocalVideoPlayerLogsData provides VideoPlayerLogsData(
                            logs = playerViewModel.logs
                        ),
                        LocalVideoPlayerHistoryData provides VideoPlayerHistoryData(
                            lastPlayed = playerViewModel.lastPlayed,
                        ),
                        LocalVideoPlayerPaymentData provides VideoPlayerPaymentData(
                            needPay = playerViewModel.needPay,
                            epid = playerViewModel.epid,
                        ),
                        LocalVideoPlayerLoadStateData provides VideoPlayerLoadStateData(
                            loadState = playerViewModel.loadState,
                            errorMessage = playerViewModel.errorMessage,
                        ),
                        LocalVideoPlayerConfigData provides VideoPlayerConfigData(
                            availableResolutions = playerViewModel.availableQuality,
                            availableVideoCodec = playerViewModel.availableVideoCodec,
                            availableAudio = playerViewModel.availableAudio,
                            availableSubtitleTracks = playerViewModel.availableSubtitle,
                            availableVideoList = playerViewModel.availableVideoList,
                            currentVideoCid = playerViewModel.currentCid,
                            currentResolution = playerViewModel.currentQuality,
                            currentVideoCodec = playerViewModel.currentVideoCodec,
                            currentVideoAspectRatio = playerViewModel.currentVideoAspectRatio,
                            currentVideoSpeed = playerViewModel.currentPlaySpeed,
                            currentAudio = playerViewModel.currentAudio,
                            currentDanmakuEnabled = playerViewModel.currentDanmakuEnabled,
                            currentDanmakuEnabledList = playerViewModel.currentDanmakuTypes,
                            currentDanmakuScale = playerViewModel.currentDanmakuScale,
                            currentDanmakuOpacity = playerViewModel.currentDanmakuOpacity,
                            currentDanmakuArea = playerViewModel.currentDanmakuArea,
                            currentDanmakuMask = playerViewModel.currentDanmakuMask,
                            currentSubtitleId = playerViewModel.currentSubtitleId,
                            currentSubtitleData = playerViewModel.currentSubtitleData,
                            currentSubtitleFontSize = playerViewModel.currentSubtitleFontSize,
                            currentSubtitleBackgroundOpacity = playerViewModel.currentSubtitleBackgroundOpacity,
                            currentSubtitleBottomPadding = playerViewModel.currentSubtitleBottomPadding,
                            currentPlayMode = playerViewModel.currentPlayMode,
                            incognitoMode = Prefs.incognitoMode,
                        ),
                        LocalVideoPlayerDanmakuMasksData provides VideoPlayerDanmakuMasksData(
                            danmakuMasks = playerViewModel.danmakuMasks,
                        ),
                        LocalVideoPlayerVideoShotData provides VideoPlayerVideoShotData(
                            videoShot = playerViewModel.videoShot,
                        ),
                    ) {
                        BvPlayer(
                            modifier = if (isVideoFullscreen) Modifier
                                .fillMaxSize()
                                .zIndex(1f)
                            else Modifier
                                .ifElse(
                                    { windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded },
                                    Modifier
                                        .padding(12.dp, 0.dp, 12.dp, 12.dp)
                                        .clip(MaterialTheme.shapes.large)
                                )
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            isFullScreen = isVideoFullscreen,
                            videoPlayer = playerViewModel.videoPlayer!!,
                            danmakuPlayer = playerViewModel.danmakuPlayer,
                            onClearBackToHistoryData = { playerViewModel.lastPlayed = 0 },
                            onEnterFullScreen = {
                                isVideoFullscreen = true
                            },
                            onExitFullScreen = {
                                isVideoFullscreen = false
                            },
                            onBack = { (context as Activity).finish() },
                            onChangeResolution = { code, afterChange ->
                                scope.launch(Dispatchers.IO) {
                                    playerViewModel.currentQuality = code
                                    playerViewModel.playQuality(code)
                                    afterChange()
                                }
                            },
                            onChangeVideoCodec = { codec, afterChange ->
                                scope.launch(Dispatchers.IO) {
                                    playerViewModel.currentVideoCodec = codec
                                    playerViewModel.playQuality(codec = codec)
                                    afterChange()
                                }
                            },
                            onChangeAudio = { audio, afterChange ->
                                scope.launch(Dispatchers.IO) {
                                    playerViewModel.currentAudio = audio
                                    playerViewModel.playQuality(audio = audio)
                                    afterChange()
                                }
                            },
                            onChangeSpeed = { speed ->
                                playerViewModel.currentPlaySpeed = speed
                                Prefs.defaultPlaySpeed = speed
                            },
                            onToggleDanmaku = { enabled ->
                                playerViewModel.currentDanmakuEnabled = enabled
                                Prefs.defaultDanmakuEnabled = enabled
                            },
                            onEnabledDanmakuTypesChange = { types ->
                                playerViewModel.currentDanmakuTypes.swapList(types)
                            },
                            onDanmakuOpacityChange = { opacity ->
                                playerViewModel.currentDanmakuOpacity = opacity
                                Prefs.defaultDanmakuOpacity = opacity
                            },
                            onDanmakuScaleChange = { scale ->
                                playerViewModel.currentDanmakuScale = scale
                                Prefs.defaultDanmakuScale = scale
                            },
                            onDanmakuAreaChange = { area ->
                                playerViewModel.currentDanmakuArea = area
                                Prefs.defaultDanmakuArea = area
                            },
                            onPlayModeChange = { playMode ->
                                playerViewModel.currentPlayMode = playMode
                                Prefs.defaultPlayMode = playMode
                            },
                            onLoadNextVideo = playerViewModel::playNextVideo,
                            onLoadNewVideo = { videoListItem ->
                                logger.fInfo { "on load new video: $videoListItem" }
                                var aid = 0L
                                var cid = 0L
                                var epid: Int? = null
                                var seasonId: Int? = null

                                when (videoListItem) {
                                    is VideoListPart -> {
                                        aid = videoListItem.aid
                                        cid = videoListItem.cid
                                        epid = videoListItem.epid
                                        seasonId = videoListItem.seasonId
                                    }

                                    is VideoListUgcEpisode -> {
                                        aid = videoListItem.aid
                                        cid = videoListItem.cid
                                        epid = videoListItem.epid
                                        seasonId = videoListItem.seasonId
                                    }

                                    is VideoListPgcEpisode -> {
                                        aid = videoListItem.aid
                                        cid = videoListItem.cid
                                        epid = videoListItem.epid
                                        seasonId = videoListItem.seasonId
                                    }
                                }
                                playerViewModel.loadPlayUrl(
                                    avid = aid,
                                    cid = cid,
                                    epid = epid,
                                    seasonId = seasonId,
                                    continuePlayNext = true
                                )
                            }
                        )
                    }
                }
                val titles = listOf("简介", "评论")
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    initialPageOffsetFraction = 0f,
                    pageCount = { 2 }
                )
                if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded) {
                    // 小屏幕下的视频详情推荐/评论
                    ReplySheetScaffold(
                        aid = commentVideModel.commentId,
                        rpid = commentVideModel.rpid,
                        repliesCount = commentVideModel.rpCount,
                        sheetState = replySheetState,
                        previewerState = previewerState,
                        onShowPreviewer = setPreviewerPictures
                    ) {
                        Column {
                            TabRow(
                                selectedTabIndex = pagerState.currentPage
                            ) {
                                titles.forEachIndexed { index, title ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = { scope.launch { pagerState.scrollToPage(index) } },
                                        text = {
                                            Text(
                                                text = title,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                            HorizontalPager(
                                state = pagerState
                            ) { page ->
                                when (page) {
                                    0 -> {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            item {
                                                VideoPlayerInfo(
                                                    modifier = Modifier.padding(12.dp),
                                                    upAvatar = videoDetailViewModel.videoDetail?.author?.face
                                                        ?: "",
                                                    upName = videoDetailViewModel.videoDetail?.author?.name
                                                        ?: "",
                                                    upFansCount = 0,
                                                    title = videoDetailViewModel.videoDetail?.title
                                                        ?: "",
                                                    description = videoDetailViewModel.videoDetail?.description
                                                        ?: "",
                                                    playCount = videoDetailViewModel.videoDetail?.stat?.view
                                                        ?: 0,
                                                    danmakuCount = videoDetailViewModel.videoDetail?.stat?.danmaku
                                                        ?: 0,
                                                    date = videoDetailViewModel.videoDetail?.publishDate
                                                        ?.formatPubTimeString(context) ?: "",
                                                    avid = videoDetailViewModel.videoDetail?.aid
                                                        ?: 0
                                                )
                                            }
                                            item {
                                                VideoPlayerPages(
                                                    currentCid = playerViewModel.currentCid,
                                                    pages = videoDetailViewModel.videoDetail?.pages
                                                        ?: emptyList(),
                                                    ugcSeason = videoDetailViewModel.videoDetail?.ugcSeason,
                                                    pgcSections = seasonVideModel.seasonData?.sections
                                                        ?: emptyList(),
                                                    onClickPage = { videoPage ->
                                                        playerViewModel.loadPlayUrl(
                                                            avid = videoDetailViewModel.videoDetail!!.aid,
                                                            cid = videoPage.cid,
                                                            continuePlayNext = true
                                                        )
                                                    },
                                                    onClickEpisode = { sectionIndex, episode ->
                                                        videoDetailViewModel.updateUgcSeasonSectionVideoList(
                                                            sectionIndex
                                                        )
                                                        playerViewModel.loadPlayUrl(
                                                            avid = episode.aid,
                                                            cid = episode.cid,
                                                            epid = episode.epid,
                                                            continuePlayNext = true
                                                        )
                                                    }
                                                )
                                            }
                                            items(
                                                items = videoDetailViewModel.videoDetail?.relatedVideos
                                                    ?: emptyList()
                                            ) { relatedVideo ->
                                                RelatedVideoItem(
                                                    relatedVideo = relatedVideo,
                                                    onClick = {
                                                        VideoPlayerActivity.actionStart(
                                                            context = context,
                                                            aid = relatedVideo.aid,
                                                            fromSeason = relatedVideo.jumpToSeason
                                                        )
                                                    }
                                                )
                                            }
                                            item {
                                                Spacer(modifier = Modifier.navigationBarsPadding())
                                            }
                                        }
                                    }

                                    1 -> {
                                        VideoComments(
                                            previewerState = previewerState,
                                            comments = commentVideModel.comments,
                                            commentSort = commentVideModel.commentSort,
                                            refreshingComments = commentVideModel.refreshingComments,
                                            onLoadMoreComments = {
                                                scope.launch(Dispatchers.IO) { commentVideModel.loadMoreComment() }
                                            },
                                            onRefreshComments = {
                                                scope.launch(Dispatchers.IO) { commentVideModel.refreshComments() }
                                            },
                                            onSwitchCommentSort = {
                                                scope.launch(Dispatchers.IO) {
                                                    commentVideModel.switchCommentSort(it)
                                                }
                                            },
                                            onShowPreviewer = setPreviewerPictures,
                                            onShowReplies = { rpId, repliesCount ->
                                                commentVideModel.rpid = rpId
                                                commentVideModel.rpCount = repliesCount
                                                scope.launch { replySheetState.bottomSheetState.expand() }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 大屏幕下视频下方的视频详情和推荐视频
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        item {
                            VideoPlayerInfo(
                                modifier = Modifier.padding(12.dp),
                                upAvatar = videoDetailViewModel.videoDetail?.author?.face
                                    ?: "",
                                upName = videoDetailViewModel.videoDetail?.author?.name ?: "",
                                upFansCount = 0,
                                title = videoDetailViewModel.videoDetail?.title ?: "",
                                description = videoDetailViewModel.videoDetail?.description
                                    ?: "",
                                playCount = videoDetailViewModel.videoDetail?.stat?.view ?: 0,
                                danmakuCount = videoDetailViewModel.videoDetail?.stat?.danmaku
                                    ?: 0,
                                date = videoDetailViewModel.videoDetail?.publishDate
                                    ?.formatPubTimeString(context) ?: "",
                                avid = videoDetailViewModel.videoDetail?.aid ?: 0,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        }
                        item {
                            VideoPlayerPages(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .clip(MaterialTheme.shapes.large),
                                currentCid = playerViewModel.currentCid,
                                pages = videoDetailViewModel.videoDetail?.pages ?: emptyList(),
                                ugcSeason = videoDetailViewModel.videoDetail?.ugcSeason,
                                pgcSections = seasonVideModel.seasonData?.sections ?: emptyList(),
                                onClickPage = { videoPage ->
                                    playerViewModel.loadPlayUrl(
                                        avid = videoDetailViewModel.videoDetail!!.aid,
                                        cid = videoPage.cid,
                                        continuePlayNext = true
                                    )
                                },
                                onClickEpisode = { sectionIndex, episode ->
                                    videoDetailViewModel.updateUgcSeasonSectionVideoList(
                                        sectionIndex
                                    )
                                    playerViewModel.loadPlayUrl(
                                        avid = episode.aid,
                                        cid = episode.cid,
                                        epid = episode.epid,
                                        continuePlayNext = true
                                    )
                                }
                            )
                        }
                        itemsIndexed(
                            items = videoDetailViewModel.videoDetail?.relatedVideos
                                ?: emptyList()
                        ) { index, relatedVideo ->
                            RelatedVideoItem(
                                modifier = Modifier
                                    .ifElse(
                                        { index == 0 },
                                        Modifier.clip(
                                            MaterialTheme.shapes.large.copy(
                                                bottomStart = CornerSize(0.dp),
                                                bottomEnd = CornerSize(0.dp)
                                            )
                                        )
                                    )
                                    .ifElse(
                                        {
                                            index == (videoDetailViewModel.videoDetail?.relatedVideos?.size
                                                ?: 0) - 1
                                        },
                                        Modifier.clip(
                                            MaterialTheme.shapes.large.copy(
                                                topStart = CornerSize(0.dp),
                                                topEnd = CornerSize(0.dp)
                                            )
                                        )
                                    ),
                                relatedVideo = relatedVideo,
                                onClick = {
                                    VideoPlayerActivity.actionStart(
                                        context = context,
                                        aid = relatedVideo.aid,
                                        fromSeason = relatedVideo.jumpToSeason
                                    )
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.navigationBarsPadding())
                        }
                    }
                }
            }
            if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                // 大屏幕下的右侧评论
                Box(
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    ReplySheetScaffold(
                        modifier = Modifier,
                        aid = commentVideModel.commentId,
                        rpid = commentVideModel.rpid,
                        repliesCount = commentVideModel.rpCount,
                        sheetState = replySheetState,
                        previewerState = previewerState,
                        onShowPreviewer = setPreviewerPictures
                    ) {
                        VideoComments(
                            modifier = Modifier.fillMaxWidth(),
                            previewerState = previewerState,
                            comments = commentVideModel.comments,
                            commentSort = commentVideModel.commentSort,
                            refreshingComments = commentVideModel.refreshingComments,
                            onLoadMoreComments = {
                                scope.launch(Dispatchers.IO) { commentVideModel.loadMoreComment() }
                            },
                            onRefreshComments = {
                                scope.launch(Dispatchers.IO) { commentVideModel.refreshComments() }
                            },
                            onSwitchCommentSort = {
                                scope.launch(Dispatchers.IO) {
                                    commentVideModel.switchCommentSort(it)
                                }
                            },
                            onShowPreviewer = setPreviewerPictures,
                            onShowReplies = { rpId, repliesCount ->
                                commentVideModel.rpid = rpId
                                commentVideModel.rpCount = repliesCount
                                scope.launch { replySheetState.bottomSheetState.expand() }
                            }
                        )
                    }
                }
            }
        }
    }

    ImagePreviewer(
        modifier = Modifier
            .fillMaxSize(),
        state = previewerState,
        imageLoader = { index ->
            val imageRequest = ImageRequest.Builder(LocalContext.current)
                .data(pictures[index].url)
                .size(coil.size.Size.ORIGINAL)
                .build()
            // 获取图片的初始大小
            rememberAsyncImagePainter(imageRequest)
            //rememberAsyncImagePainter(pictures[index].url)
        }
    )
}

@Composable
fun VideoPlayerInfo(
    modifier: Modifier = Modifier,
    upAvatar: String,
    upName: String,
    upFansCount: Int,
    title: String,
    description: String,
    playCount: Int,
    danmakuCount: Int,
    date: String,
    avid: Long,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    val summaryTextStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .padding(0.dp, 8.dp, 8.dp, 8.dp)
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Gray),
                        model = upAvatar,
                        contentDescription = null
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = upName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "$upFansCount",
                        style = summaryTextStyle,
                        fontSize = 10.sp
                    )
                }
            }

            Button(onClick = { /*TODO*/ }) {
                Text(text = "Follow")
            }
        }
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
        ProvideTextStyle(summaryTextStyle) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        modifier = Modifier,
                        painter = painterResource(id = R.drawable.ic_play_count),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(text = "$playCount")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        modifier = Modifier,
                        painter = painterResource(id = R.drawable.ic_danmaku_count),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(text = "$danmakuCount")
                }
                Text(text = date)
                Text(text = "av$avid")
            }
            Text(text = description)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun VideoComments(
    modifier: Modifier = Modifier,
    previewerState: ImagePreviewerState,
    comments: List<Comment>,
    commentSort: CommentSort,
    refreshingComments: Boolean,
    onLoadMoreComments: () -> Unit,
    onRefreshComments: () -> Unit,
    onSwitchCommentSort: (CommentSort) -> Unit,
    onShowPreviewer: (newPictures: List<Picture>, afterSetPictures: () -> Unit) -> Unit,
    onShowReplies: (rpId: Long, repliesCount: Int) -> Unit
) {
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullRefreshState(refreshingComments, { onRefreshComments() })

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true

            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 10
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMoreComments()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(state = pullRefreshState)
    ) {
        LazyColumn(
            state = listState
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (commentSort) {
                            CommentSort.Hot -> "热门评论"
                            CommentSort.Time -> "最新评论"
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = {
                        onSwitchCommentSort(
                            when (commentSort) {
                                CommentSort.Hot -> CommentSort.Time
                                CommentSort.Time -> CommentSort.Hot
                                else -> CommentSort.Hot
                            }
                        )
                    }) {
                        Text(
                            text = when (commentSort) {
                                CommentSort.Hot -> "按热度"
                                CommentSort.Time -> "按时间"
                                else -> ""
                            }
                        )
                    }
                }
            }

            itemsIndexed(items = comments) { index, comment ->
                Box {
                    CommentItem(
                        comment = comment,
                        previewerState = previewerState,
                        onShowPreviewer = onShowPreviewer,
                        onShowReply = { rpId ->
                            onShowReplies(rpId, comment.repliesCount)
                        }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
        PullRefreshIndicator(
            refreshingComments,
            pullRefreshState,
            Modifier.align(Alignment.TopCenter)
        )
    }
}

@Preview
@Composable
private fun VideoPlayerInfoPreview() {
    BVMobileTheme {
        Surface {
            VideoPlayerInfo(
                modifier = Modifier.padding(24.dp),
                upAvatar = "https://i0.hdslb.com/bfs/article/b6b843d84b84a3ba5526b09ebf538cd4b4c8c3f3.jpg@450w_450h_progressive.webp",
                upName = "bishi",
                upFansCount = 1400000000,
                title = "This is the video title... repeat, this is the video title.",
                description = "descriptions....descriptions....descriptions....descriptions....descriptions....descriptions....descriptions....descriptions....descriptions....",
                playCount = 2434,
                danmakuCount = 14,
                date = "2023-5-22 23:17",
                avid = 170001,
            )
        }
    }
}