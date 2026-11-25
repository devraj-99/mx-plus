package com.mxplus.app

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Easing
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlin.math.abs

data class PlayItem(val uri: String, val title: String)
data class PlayRequest(val items: List<PlayItem>, val index: Int, val external: Boolean = false)

private enum class DragMode { SEEK, BRIGHTNESS, VOLUME }

private val Speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

private fun speedLabel(s: Float): String = (if (s % 1f == 0f) "%.0f".format(s) else "%.2f".format(s).trimEnd('0')) + "x"

val SubColors = listOf(Color.White, Color(0xFFFFEB3B), Color(0xFF69F0AE), Color(0xFF40C4FF))

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(request: PlayRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val view = LocalView.current
    val dark = true
    val haze = remember { HazeState() }
    val playerBackdrop = rememberLayerBackdrop()

    var playlist by remember { mutableStateOf(request.items) }
    var currentIndex by remember { mutableIntStateOf(request.index) }
    var titleText by remember { mutableStateOf(request.items[request.index].title) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(Prefs.defaultSpeed) }
    var resize by remember { mutableIntStateOf(0) } // 0 fit, 1 fill, 2 zoom
    var zoom by remember { mutableFloatStateOf(1f) }
    var showControls by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Long?>(null) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(0) } // 0 none, 1 speed, 2 audio, 3 subs, 4 playlist, 5 sleep
    var error by remember { mutableStateOf<String?>(null) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var sleepAt by remember { mutableStateOf<Long?>(null) }
    var videoAspect by remember { mutableFloatStateOf(0f) }
    var boosted by remember { mutableStateOf(false) }
    var aPoint by remember { mutableLongStateOf(-1L) }
    var bPoint by remember { mutableLongStateOf(-1L) }
    var videoFrame by remember { mutableStateOf<AspectRatioFrameLayout?>(null) }
    val audioFx = remember { AudioFx() }
    val attachedTv = remember { arrayOfNulls<TextureView>(1) }
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }

    val player = remember {
        ExoPlayer.Builder(context, androidx.media3.exoplayer.DefaultRenderersFactory(context).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).setHandleAudioBecomingNoisy(true).build().apply {
            val mediaItems = request.items.map {
                MediaItem.Builder().setUri(it.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build()).build()
            }
            val start = request.items[request.index]
            val resumeAt = if (Prefs.resume) Prefs.resumePosition(start.uri) else 0L
            setMediaItems(mediaItems, request.index, resumeAt)
            setPlaybackSpeed(Prefs.defaultSpeed)
            repeatMode = when (Prefs.loopMode) {
                1 -> Player.REPEAT_MODE_ALL
                2 -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player) {
        ActivePlayer.player = player
        audioFx.attach(player)
        if (Prefs.bgPlay) {
            runCatching {
                val i = android.content.Intent(context.applicationContext, PlaybackService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, i)
            }
        }
    }

    fun flash(text: String) {
        hud = text
        hudTick++
    }

    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mime = when {
                uri.toString().endsWith(".ass", true) || uri.toString().endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
                uri.toString().endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val sub = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            val idx = player.currentMediaItemIndex
            val at = player.currentPosition
            val items = playlist.mapIndexed { i, it ->
                val b = MediaItem.Builder().setUri(it.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build())
                if (i == idx) b.setSubtitleConfigurations(listOf(sub))
                b.build()
            }
            player.setMediaItems(items, idx, at)
            player.prepare()
            player.play()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            flash("Subtitle file loaded")
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
                titleText = mediaItem?.mediaMetadata?.title?.toString() ?: titleText
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    if (!(Prefs.autoNext && player.hasNextMediaItem())) {
                        Prefs.saveResume(playlist[player.currentMediaItemIndex].uri, 0, 0)
                        onClose()
                    }
                }
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    videoAspect = size.width * size.pixelWidthHeightRatio / size.height
                    activity.requestedOrientation = if (size.height > size.width) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "Can't play this video (" + (e.errorCodeName) + ")"
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                val item = playlist.getOrNull(player.currentMediaItemIndex)
                if (item != null) Prefs.saveResume(item.uri, player.currentPosition, player.duration.coerceAtLeast(0))
            }
            audioFx.release()
            if (ActivePlayer.player === player) ActivePlayer.player = null
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Prefs.keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    LaunchedEffect(player) {
        var saveTick = 0
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            if (!boosted) speed = player.playbackParameters.speed
            val b = bPoint
            if (aPoint >= 0 && b > aPoint && pos > b) {
                player.seekTo(aPoint)
                pos = aPoint
            }
            val deadline = sleepAt
            if (deadline != null && System.currentTimeMillis() >= deadline) {
                sleepAt = null
                player.pause()
                flash("Sleep timer: paused")
            }
            saveTick++
            if (saveTick >= 20) {
                saveTick = 0
                playlist.getOrNull(player.currentMediaItemIndex)?.let { Prefs.saveResume(it.uri, player.currentPosition, dur) }
            }
            delay(250)
        }
    }

    LaunchedEffect(showControls, playing, interaction) {
        if (showControls && playing && !locked && Prefs.autoHideSec > 0) {
            delay(Prefs.autoHideSec * 1000L)
            showControls = false
        }
    }
    LaunchedEffect(hudTick) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }

    BackHandler {
        if (dialog != 0) dialog = 0 else onClose()
    }

    val lockedState by rememberUpdatedState(locked)
    val controlsState by rememberUpdatedState(showControls)

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        player.seekTo(target)
        pos = target
    }

    fun pipParams(): PictureInPictureParams {
        val ar = if (videoAspect in 0.42f..2.39f) Rational((videoAspect * 1000).toInt(), 1000) else Rational(16, 9)
        val b = PictureInPictureParams.Builder().setAspectRatio(ar)
        if (android.os.Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(playing)
        return b.build()
    }

    fun enterPip() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            runCatching { activity.enterPictureInPictureMode(pipParams()) }
                .onFailure { flash("PiP blocked: enable it in Settings > Apps > MX Plus > Picture in picture") }
        }
    }

    LaunchedEffect(playing, videoAspect) {
        if (android.os.Build.VERSION.SDK_INT >= 26) runCatching { activity.setPictureInPictureParams(pipParams()) }
    }

    val glassHaze = if (Prefs.glass && Prefs.playerGlass) haze else null
    val playerLens = if (Prefs.glass && Prefs.playerGlass) playerBackdrop else null

    LaunchedEffect(Prefs.bright, Prefs.contrast, Prefs.saturate, Prefs.hue) {
        videoFrame?.let { VisualEnhancer.apply(it) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // NETMAX method: capture the TextureView video into BOTH a Kyant LayerBackdrop
        // (for the refraction lens) and a Haze source (for edge blur). No Compose
        // graphicsLayer on this chain - zoom is applied to the TextureView itself.
        AndroidView(
            modifier = Modifier.fillMaxSize().layerBackdrop(playerBackdrop).hazeSource(haze),
            factory = { ctx ->
                AspectRatioFrameLayout(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(TextureView(ctx), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            update = { frame ->
                videoFrame = frame
                frame.setAspectRatio(videoAspect)
                frame.resizeMode = when (resize) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                val tv = frame.getChildAt(0) as? TextureView
                if (tv != null) {
                    if (tv.scaleX != zoom) { tv.scaleX = zoom; tv.scaleY = zoom }
                    if (attachedTv[0] !== tv) {
                        player.setVideoTextureView(tv)
                        attachedTv[0] = tv
                    }
                }
            },
        )

        // Gesture layer: tap, double-tap seek, long-press 2x, drags, pinch zoom
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val z = event.calculateZoom()
                                if (z != 1f) {
                                    zoom = (zoom * z).coerceIn(1f, 4f)
                                    if (zoom > 1.02f && resize != 2) resize = 2
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val released = runCatching { tryAwaitRelease() }.getOrDefault(true)
                            if (released && boosted) {
                                boosted = false
                                player.setPlaybackSpeed(speedBeforeBoost)
                                flash(speedLabel(speedBeforeBoost))
                            }
                        },
                        onTap = { showControls = !controlsState; interaction++ },
                        onLongPress = {
                            if (!lockedState && player.isPlaying) {
                                speedBeforeBoost = player.playbackParameters.speed
                                boosted = true
                                player.setPlaybackSpeed(2f)
                                flash("2x")
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!lockedState) {
                                val ms = Prefs.seekSeconds * 1000L
                                if (offset.x < size.width / 2f) {
                                    seekBy(-ms); flash("-${Prefs.seekSeconds}s")
                                } else {
                                    seekBy(ms); flash("+${Prefs.seekSeconds}s")
                                }
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var mode: DragMode? = null
                    var startX = 0f
                    var totalX = 0f
                    var totalY = 0f
                    var basePos = 0L
                    var baseBright = 0.5f
                    var baseVol = 0f
                    var seekTarget = 0L
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    detectDragGestures(
                        onDragStart = { offset ->
                            mode = null
                            startX = offset.x
                            totalX = 0f
                            totalY = 0f
                            basePos = player.currentPosition
                            val b = activity.window.attributes.screenBrightness
                            baseBright = if (b < 0f) 0.5f else b
                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            baseVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                        },
                        onDragEnd = {
                            if (mode == DragMode.SEEK) {
                                player.seekTo(seekTarget)
                                scrub = null
                            }
                            mode = null
                        },
                        onDragCancel = { scrub = null; mode = null },
                        onDrag = { change, drag ->
                            if (lockedState || !Prefs.gestures) return@detectDragGestures
                            change.consume()
                            totalX += drag.x
                            totalY += drag.y
                            if (mode == null && (abs(totalX) > 24f || abs(totalY) > 24f)) {
                                mode = if (abs(totalX) > abs(totalY)) DragMode.SEEK else if (startX < size.width / 2f) DragMode.BRIGHTNESS else DragMode.VOLUME
                            }
                            when (mode) {
                                DragMode.SEEK -> {
                                    if (dur <= 0) { flash("Can't seek in this file"); return@detectDragGestures }
                                    val d = dur
                                    seekTarget = (basePos + (totalX / size.width * 120_000f).toLong()).coerceIn(0L, d)
                                    scrub = seekTarget
                                    flash((if (seekTarget >= basePos) "+" else "-") + formatTime(abs(seekTarget - basePos)) + "  " + formatTime(seekTarget))
                                }
                                DragMode.BRIGHTNESS -> {
                                    val v = (baseBright - totalY / size.height * 1.2f).coerceIn(0.02f, 1f)
                                    val lp = activity.window.attributes
                                    lp.screenBrightness = v
                                    activity.window.attributes = lp
                                    flash("Brightness " + (v * 100).toInt() + "%")
                                }
                                DragMode.VOLUME -> {
                                    val v = (baseVol - totalY / size.height * 1.2f).coerceIn(0f, 1f)
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt(), 0)
                                    flash("Volume " + (v * 100).toInt() + "%")
                                }
                                null -> {}
                            }
                        },
                    )
                },
        )

        // NETMAX-style edge blur: progressive blur of the live video at the top and
        // bottom edges, Instagram-style fade, no black gradient band.
        if (glassHaze != null && showControls) {
            Box(
                Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter)
                    .playerEdgeBlurFade(top = true)
                    .hazeEffect(haze) {
                        blurRadius = (Prefs.glassBlur * 0.625f).coerceIn(0f, 32f).dp
                        noiseFactor = 0.012f
                        progressive = HazeProgressive.verticalGradient(
                            easing = PlayerEdgeBlurEasing,
                            startIntensity = 1f,
                            endIntensity = 0f,
                        )
                    },
            )
            Box(
                Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
                    .playerEdgeBlurFade(top = false)
                    .hazeEffect(haze) {
                        blurRadius = (Prefs.glassBlur * 0.625f).coerceIn(0f, 32f).dp
                        noiseFactor = 0.012f
                        progressive = HazeProgressive.verticalGradient(
                            easing = PlayerEdgeBlurEasing,
                            startIntensity = 0f,
                            endIntensity = 1f,
                        )
                    },
            )
        }

        if (buffering && error == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(44.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).glass(RoundedCornerShape(16.dp), glassHaze, dark, backdrop = playerLens, overVideo = true).padding(20.dp))
        }

        // Subtitles overlay
        if (cues.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (showControls && !locked) 150.dp else 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                cues.forEach { cue ->
                    cue.text?.let { t ->
                        Text(
                            t.toString(),
                            color = SubColors[Prefs.subColor.coerceIn(0, SubColors.size - 1)],
                            fontSize = (17f * Prefs.subScale).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        hud?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp).glass(RoundedCornerShape(20.dp), glassHaze, dark, backdrop = playerLens, overVideo = true).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        fun frameStep(forward: Boolean) {
            player.pause()
            val step = 40L
            val t = (player.currentPosition + if (forward) step else -step).coerceIn(0L, dur.coerceAtLeast(0L))
            player.seekTo(t)
            pos = t
            flash(if (forward) "+1 frame" else "-1 frame")
        }

        fun screenshot() {
            val tv = attachedTv[0] ?: run { flash("No video frame"); return }
            val bmp = runCatching { tv.bitmap }.getOrNull() ?: run { flash("Screenshot failed"); return }
            val name = "MXPlus-" + System.currentTimeMillis() + ".jpg"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MX Plus")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            val ok = runCatching {
                uri?.let { context.contentResolver.openOutputStream(it)?.use { o -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, o) } } ?: false
            }.getOrDefault(false)
            flash(if (ok == true) "Screenshot saved to Photos" else "Screenshot failed")
        }

        val displayPos = scrub ?: pos
        val safe = Modifier.windowInsetsPadding(WindowInsets.displayCutout)

        if (locked) {
            if (showControls) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(78.dp).glass(CircleShape, glassHaze, dark, backdrop = playerLens, overVideo = true).pressable { locked = false; showControls = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Lock, "Unlock", tint = Color.White, modifier = Modifier.size(34.dp)) }
                    Spacer(Modifier.height(10.dp))
                    Text("Tap to unlock", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (showControls) {
            Box(Modifier.fillMaxSize().then(safe).padding(horizontal = 20.dp)) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        titleText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Row(
                        Modifier.glass(RoundedCornerShape(26.dp), glassHaze, dark, backdrop = playerLens, overVideo = true).padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BareIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture") { enterPip() }
                        if (playlist.size > 1) {
                            BareIcon(Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlist") { dialog = 4; interaction++ }
                        }
                        BareIcon(Icons.Rounded.LockOpen, "Lock controls") { locked = true; showControls = true }
                        BareIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { onClose() }
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIcon(backIcon(Prefs.seekSeconds), "Back", 64.dp, 32.dp, glassHaze, playerLens) { seekBy(-Prefs.seekSeconds * 1000L); interaction++ }
                    GlassIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", 88.dp, 48.dp, glassHaze, playerLens) {
                        if (player.isPlaying) player.pause() else player.play()
                        interaction++
                    }
                    GlassIcon(fwdIcon(Prefs.seekSeconds), "Forward", 64.dp, 32.dp, glassHaze, playerLens) { seekBy(Prefs.seekSeconds * 1000L); interaction++ }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 14.dp)) {
                    Slider(
                        value = displayPos.coerceIn(0L, dur.coerceAtLeast(1L)).toFloat(),
                        onValueChange = { scrub = it.toLong(); interaction++ },
                        onValueChangeFinished = {
                            scrub?.let { player.seekTo(it) }
                            scrub = null
                        },
                        valueRange = 0f..dur.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                        ),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TimePill(formatTime(displayPos), glassHaze, playerLens)
                        TimePill(formatTime(dur), glassHaze, playerLens)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.Center) {
                        Row(
                            Modifier.glass(RoundedCornerShape(26.dp), glassHaze, dark, backdrop = playerLens, overVideo = true).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (playlist.size > 1) {
                                PillButton("Prev", Icons.Rounded.SkipPrevious) { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); interaction++ }
                            }
                            PillButton(listOf("Fit", "Fill", "Zoom")[resize], Icons.Rounded.AspectRatio) { resize = (resize + 1) % 3; interaction++ }
                            PillButton(speedLabel(speed), Icons.Rounded.Speed) { dialog = 1; interaction++ }
                            PillButton("Subs", Icons.Rounded.Subtitles) { dialog = 3; interaction++ }
                            PillButton("Audio", Icons.Rounded.Audiotrack) { dialog = 2; interaction++ }
                            PillButton("Sleep", Icons.Rounded.Bedtime) { dialog = 5; interaction++ }
                            PillButton("", Icons.Rounded.RepeatOneOn, tint = if (aPoint >= 0) MaterialTheme.colorScheme.primary else Color.White) {
                                when {
                                    aPoint < 0L -> { aPoint = player.currentPosition; flash("A set") }
                                    bPoint < 0L -> { bPoint = player.currentPosition; flash("B set, looping A-B") }
                                    else -> { aPoint = -1; bPoint = -1; flash("A-B cleared") }
                                }
                                interaction++
                            }
                            PillButton("Shot", Icons.Rounded.CameraAlt) { screenshot(); interaction++ }
                            PillButton("FX", Icons.Rounded.Equalizer) { dialog = 6; interaction++ }
                            PillButton("", when (Prefs.loopMode) { 2 -> Icons.Rounded.RepeatOne; else -> Icons.Rounded.Repeat }, tint = if (Prefs.loopMode == 0) Color.White else MaterialTheme.colorScheme.primary) {
                                Prefs.loopMode = (Prefs.loopMode + 1) % 3
                                Prefs.save()
                                player.repeatMode = when (Prefs.loopMode) {
                                    1 -> Player.REPEAT_MODE_ALL
                                    2 -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                flash(when (Prefs.loopMode) { 1 -> "Repeat all"; 2 -> "Repeat one"; else -> "Repeat off" })
                                interaction++
                            }
                            if (playlist.size > 1) {
                                PillButton("Next", Icons.Rounded.SkipNext) { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); interaction++ }
                            }
                        }
                    }
                }
            }
        }

        when (dialog) {
            1 -> PickerDialog("Speed", Speeds.map { speedLabel(it) }, Speeds.indexOf(speed).let { if (it < 0) -1 else it }, { i ->
                player.setPlaybackSpeed(Speeds[i]); dialog = 0
            }) { dialog = 0 }
            2 -> {
                val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                val rows = mutableListOf<Triple<androidx.media3.common.Tracks.Group, Int, String>>()
                groups.forEach { g ->
                    for (t in 0 until g.length) rows += Triple(g, t, trackLabel(g.getTrackFormat(t), rows.size + 1))
                }
                val selected = rows.indexOfFirst { it.first.isTrackSelected(it.second) }
                if (rows.isEmpty()) PickerDialog("Audio", listOf("No audio tracks"), -1, { dialog = 0 }) { dialog = 0 }
                else PickerDialog("Audio", rows.map { it.third }, selected, { i ->
                    val (g, t, _) = rows[i]
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                    dialog = 0
                }) { dialog = 0 }
            }
            3 -> {
                val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                val rows = mutableListOf<Triple<androidx.media3.common.Tracks.Group, Int, String>>()
                groups.forEach { g ->
                    for (t in 0 until g.length) rows += Triple(g, t, trackLabel(g.getTrackFormat(t), rows.size + 1))
                }
                val off = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || rows.none { it.first.isTrackSelected(it.second) }
                val labels = listOf("Off") + rows.map { it.third } + listOf("Load subtitle file")
                val selected = if (off) 0 else rows.indexOfFirst { it.first.isTrackSelected(it.second) } + 1
                PickerDialog("Subtitles", labels, selected, { i ->
                    when {
                        i == 0 -> {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            dialog = 0
                        }
                        i == labels.size - 1 -> {
                            dialog = 0
                            subPicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream"))
                        }
                        else -> {
                            val (g, t, _) = rows[i - 1]
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                            dialog = 0
                        }
                    }
                }) { dialog = 0 }
            }
            4 -> PickerDialog("Playlist", playlist.map { it.title }, currentIndex, { i ->
                player.seekToDefaultPosition(i); dialog = 0
            }) { dialog = 0 }
            6 -> Dialog(onDismissRequest = { dialog = 0 }) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1F)).padding(20.dp)) {
                    Text("Effects", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Equalizer", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = Prefs.eqEnabled, onCheckedChange = { Prefs.eqEnabled = it; Prefs.save(); audioFx.applyEq() })
                    }
                    if (Prefs.eqEnabled) {
                        val presets = audioFx.presets()
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            presets.forEachIndexed { i, name ->
                                Box(
                                    Modifier.padding(end = 8.dp).clip(RoundedCornerShape(16.dp))
                                        .background(if (Prefs.eqPreset == i) MaterialTheme.colorScheme.primary else Color(0xFF2C2C30))
                                        .clickable { Prefs.eqPreset = i; Prefs.save(); audioFx.applyEq() }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) { Text(name, color = Color.White, fontSize = 13.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FxSlider("Volume boost", Prefs.volumeBoost / 100f, 1f..2f) { Prefs.volumeBoost = (it * 100).toInt(); Prefs.save(); audioFx.applyBoost() }
                    FxSlider("Brightness", (Prefs.bright + 50) / 100f, 0f..1f) { Prefs.bright = (it * 100 - 50).toInt(); Prefs.save() }
                    FxSlider("Contrast", Prefs.contrast / 200f, 0.25f..1f) { Prefs.contrast = (it * 200).toInt(); Prefs.save() }
                    FxSlider("Saturation", Prefs.saturate / 200f, 0f..1f) { Prefs.saturate = (it * 200).toInt(); Prefs.save() }
                    FxSlider("Hue", (Prefs.hue + 180) / 360f, 0f..1f) { Prefs.hue = (it * 360 - 180).toInt(); Prefs.save() }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallBtn("- frame") { frameStep(false) }
                        SmallBtn("+ frame") { frameStep(true) }
                        SmallBtn("Reset FX") {
                            Prefs.bright = 0; Prefs.contrast = 100; Prefs.saturate = 100; Prefs.hue = 0; Prefs.volumeBoost = 100
                            Prefs.save(); audioFx.applyBoost()
                        }
                    }
                }
            }
            5 -> {
                val opts = listOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes")
                val mins = listOf(0, 15, 30, 45, 60)
                PickerDialog("Sleep timer", opts, if (sleepAt == null) 0 else -1, { i ->
                    sleepAt = if (mins[i] == 0) null else System.currentTimeMillis() + mins[i] * 60_000L
                    if (mins[i] > 0) flash("Sleep in ${mins[i]} min")
                    dialog = 0
                }) { dialog = 0 }
            }
        }
    }
}

private fun trackLabel(f: androidx.media3.common.Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let { java.util.Locale(it).displayLanguage }
    val parts = listOfNotNull(f.label, lang, if (f.channelCount > 0 && f.sampleMimeType?.startsWith("audio") == true) "${f.channelCount}ch" else null)
    return if (parts.isEmpty()) "Track $n" else parts.joinToString("  ·  ")
}

private fun backIcon(s: Int): ImageVector = when (s) { 5 -> Icons.Rounded.Replay5; 30 -> Icons.Rounded.Replay30; else -> Icons.Rounded.Replay10 }
private fun fwdIcon(s: Int): ImageVector = when (s) { 5 -> Icons.Rounded.Forward5; 30 -> Icons.Rounded.Forward30; else -> Icons.Rounded.Forward10 }

@Composable
private fun GlassIcon(icon: ImageVector, desc: String, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, haze: HazeState?, lens: com.kyant.backdrop.backdrops.LayerBackdrop? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(size).glass(CircleShape, haze, true, backdrop = lens, overVideo = true).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
private fun BareIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun TimePill(text: String, haze: HazeState?, lens: com.kyant.backdrop.backdrops.LayerBackdrop? = null) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.glass(RoundedCornerShape(12.dp), haze, true, backdrop = lens, overVideo = true).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun PillButton(label: String, icon: ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
        if (label.isNotEmpty()) Text(label, color = tint, fontSize = 13.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun FxSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.width(100.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C30)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun PickerDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1F)).padding(vertical = 14.dp),
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.height((options.size.coerceAtMost(7) * 52).dp)) {
                itemsIndexed(options) { i, label ->
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clickable { onPick(i) }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            color = if (i == selected) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}


/*
   Edge-blur fade adapted from NETMAX (MIT License): fade the whole rendered blur
   effect, not just its radius, so the strip meets clear video with no shoulder.
 */
internal val PlayerEdgeBlurEasing = Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    t * t * (3f - 2f * t)
}

internal fun playerEdgeBlurStrength(distanceFromEdge: Float): Float {
    val t = (distanceFromEdge / 0.92f).coerceIn(0f, 1f)
    return 1f - t * t * (3f - 2f * t)
}

internal fun Modifier.playerEdgeBlurFade(top: Boolean): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val stops = Array(33) { index ->
                val position = index / 32f
                val distance = if (top) position else 1f - position
                position to Color.White.copy(alpha = playerEdgeBlurStrength(distance))
            }
            val fade = Brush.verticalGradient(colorStops = stops)
            onDrawWithContent {
                drawContent()
                drawRect(brush = fade, blendMode = BlendMode.DstIn)
            }
        }
