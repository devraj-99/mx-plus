package com.mxplus.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.mxplus.app.photos.PhotosScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.kyant.backdrop.backdrops.layerBackdrop

object PlayerHost {
    var request by mutableStateOf<PlayRequest?>(null)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.init(this)
        handle(intent)
        setContent {
            MxTheme {
                val req = PlayerHost.request
                if (req != null) {
                    PlayerScreen(req) {
                        PlayerHost.request = null
                        if (req.external) finish()
                    }
                } else {
                    Shell()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val data: Uri = i?.data ?: return
        if (i.action != Intent.ACTION_VIEW) return
        var name: String? = null
        runCatching {
            contentResolver.query(data, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        val title = (name ?: data.lastPathSegment ?: "Video").substringBeforeLast('.')
        PlayerHost.request = PlayRequest(listOf(PlayItem(data.toString(), title)), 0, external = true)
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Videos("Videos", Icons.Rounded.VideoLibrary),
    Folders("Folders", Icons.Rounded.Folder),
    Photos("Photos", Icons.Rounded.PhotoLibrary),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
private fun Shell() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var access by remember { mutableStateOf(VideoLibrary.access(context)) }
    val haze = remember { HazeState() }
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = VideoLibrary.access(context)
        tick++
    }

    val owner = context as ComponentActivity
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                access = VideoLibrary.access(context)
                tick++
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(tick) { VideoLibrary.refresh(context) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val title = (uri.lastPathSegment ?: "Video").substringAfterLast('/').substringBeforeLast('.')
            PlayerHost.request = PlayRequest(listOf(PlayItem(uri.toString(), title)), 0)
        }
    }
    val onPickFile = { pickFile.launch(arrayOf("video/*")) }
    val onAllFiles = {
        runCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)))
        }.onFailure {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    val play: (List<VideoItem>, Int) -> Unit = { list, i ->
        PlayerHost.request = PlayRequest(list.map { PlayItem(it.uri.toString(), it.title) }, i)
    }
    val openSettings = {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze).then(Modifier.layerBackdrop(backdrop))) {
            when (Tab.entries[tab]) {
                Tab.Videos -> VideosScreen(access, { launcher.launch(VideoLibrary.permissionsToRequest()) }, openSettings, { tick++ }, onPickFile, onAllFiles, play)
                Tab.Folders -> FoldersScreen(access, { launcher.launch(VideoLibrary.permissionsToRequest()) }, { tick++ }, onPickFile, onAllFiles, play)
                Tab.Photos -> PhotosScreen(Modifier.fillMaxSize())
                Tab.Settings -> SettingsScreen()
            }
        }
        // Floating glass tab bar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(32.dp), haze, isDarkTheme(), backdrop)
                .padding(6.dp),
        ) {
            Tab.entries.forEachIndexed { i, t ->
                val selected = i == tab
                Column(
                    Modifier
                        .weight(1f)
                        .height(54.dp)
                        .then(
                            if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(26.dp)) else Modifier,
                        )
                        .pressable { tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
