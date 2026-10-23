package cn.vectory.biliview.tv.screens.main.ugc

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aaa1115910.biliapi.entity.ugc.UgcTypeV2
import cn.vectory.biliview.viewmodel.ugc.UgcInformationViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun InformationContent(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    ugcViewModel: UgcInformationViewModel = koinViewModel()
) {
    UgcRegionScaffold(
        modifier = modifier,
        lazyListState = lazyListState,
        ugcViewModel = ugcViewModel,
        childRegionButtons = { InformationChildRegionButtons() }
    )
}

@Composable
fun InformationChildRegionButtons(modifier: Modifier = Modifier) {
    UgcChildRegionButtons(
        modifier = modifier.fillMaxWidth(),
        childUgcTypes = UgcTypeV2.informationList
    )
}