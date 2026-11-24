package com.nuvio.app.features.localvideos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.library.localVideosContent

@Composable
internal fun VideosScreen(
    modifier: Modifier = Modifier,
    onLocalVideoPlay: ((LocalVideoItem) -> Unit)?,
) {
    val uiState by LocalVideosRepository.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { LocalVideosRepository.ensureLoaded() }
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "videos-title") {
                Text(
                    text = "Videos",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                )
            }
            localVideosContent(
                uiState = uiState,
                onVideoClick = { video -> onLocalVideoPlay?.invoke(video) },
            )
        }
    }
}
