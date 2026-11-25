package com.mxplus.app

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Keeps audio/video playing in the background with a media notification. */
object ActivePlayer {
    var player: androidx.media3.exoplayer.ExoPlayer? = null
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val p = ActivePlayer.player ?: return
        session = MediaSession.Builder(this, p).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
