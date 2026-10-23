package cn.vectory.biliview.tv.screens.main.ugc

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aaa1115910.biliapi.entity.ugc.UgcTypeV2
import cn.vectory.biliview.viewmodel.ugc.UgcTechViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun TechContent(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    ugcViewModel: UgcTechViewModel = koinViewModel()
) {
    UgcRegionScaffold(
        modifier = modifier,
        lazyListState = lazyListState,
        ugcViewModel = ugcViewModel,
        childRegionButtons = { TechChildRegionButtons() }
    )
}

@Composable
fun TechChildRegionButtons(modifier: Modifier = Modifier) {
    UgcChildRegionButtons(
        modifier = modifier.fillMaxWidth(),
        childUgcTypes = UgcTypeV2.techList
    )
}