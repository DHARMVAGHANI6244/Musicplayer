package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.media.app.NotificationCompat as MediaNotificationCompat

class MusicService : Service() {

    private var player: ExoPlayer? = null
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _playbackState = MutableStateFlow("Idle")
    val playbackState: StateFlow<String> = _playbackState

    private var songs = emptyList<Song>()
    private var currentIndex = -1

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                _playbackState.value = if (isPlaying) "Playing" else "Paused"
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    _duration.value = player?.duration?.coerceAtLeast(0L) ?: 0L
                    _currentPosition.value = player?.currentPosition ?: 0L
                }
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })

        createNotificationChannel()
    }

    override fun onDestroy() {
        stopPositionUpdates()
        serviceScope.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
        }
        return START_STICKY
    }

    fun setSongs(songList: List<Song>) {
        songs = songList
        if (currentIndex == -1) {
            currentIndex = songs.indexOfFirst { it.id == _currentSong.value?.id }
        }
    }

    fun playSong(song: Song) {
        val index = songs.indexOfFirst { it.id == song.id }
        if (index >= 0) currentIndex = index

        _currentSong.value = song
        _duration.value = song.duration

        val mediaItem = MediaItem.fromUri(song.uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

        updateNotification()
    }

    fun togglePlayPause() {
        val p = player ?: return
        when {
            p.isPlaying -> p.pause()
            p.playbackState == Player.STATE_IDLE -> songs.getOrNull(currentIndex)?.let { playSong(it) }
            else -> p.play()
        }
    }

    fun playPrevious() {
        if (songs.isEmpty()) return
        currentIndex = if (currentIndex <= 0) songs.size - 1 else currentIndex - 1
        playSong(songs[currentIndex])
    }

    fun playNext() {
        if (songs.isEmpty()) return
        currentIndex = if (currentIndex >= songs.size - 1) 0 else currentIndex + 1
        playSong(songs[currentIndex])
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                _currentPosition.value = player?.currentPosition ?: 0L
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification() {
        val current = _currentSong.value ?: return
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playing = player?.isPlaying == true

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(current.title)
            .setContentText(current.artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                servicePendingIntent(ACTION_PREVIOUS, 1)
            )
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                servicePendingIntent(ACTION_PLAY_PAUSE, 2)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                servicePendingIntent(ACTION_NEXT, 3)
            )
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.musicplayer.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicplayer.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.musicplayer.action.PREVIOUS"
        private const val CHANNEL_ID = "music_player_channel"
        private const val NOTIFICATION_ID = 101
    }
}
