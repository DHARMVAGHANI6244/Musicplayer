package com.example.musicplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MusicViewModel(
    private val appContext: Context,
    private val repository: MusicRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _playbackState = MutableStateFlow("Idle")
    val playbackState: StateFlow<String> = _playbackState

    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicService.LocalBinder ?: return
            musicService = binder.getService()
            isBound = true

            if (_songs.value.isNotEmpty()) {
                musicService?.setSongs(_songs.value)
            }

            viewModelScope.launch {
                musicService?.currentSong?.collect { song ->
                    _currentSong.value = song
                    _duration.value = song?.duration ?: 0L
                }
            }

            viewModelScope.launch {
                musicService?.isPlaying?.collect { playing ->
                    _isPlaying.value = playing
                }
            }

            viewModelScope.launch {
                musicService?.currentPosition?.collect { pos ->
                    _currentPosition.value = pos
                }
            }

            viewModelScope.launch {
                musicService?.playbackState?.collect { state ->
                    _playbackState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    fun bindService() {
        val intent = Intent(appContext, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (isBound) {
            appContext.unbindService(connection)
            isBound = false
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            val songsList = repository.loadSongs()
            _songs.value = songsList
            if (_currentSong.value == null && songsList.isNotEmpty()) {
                _currentSong.value = songsList.first()
                _duration.value = songsList.first().duration
            }
            musicService?.setSongs(songsList)
        }
    }

    fun playSong(song: Song) {
        musicService?.playSong(song)
    }

    fun playPause() {
        musicService?.togglePlayPause()
    }

    fun next() {
        musicService?.playNext()
    }

    fun previous() {
        musicService?.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        musicService?.seekTo(positionMs)
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}

class MusicViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            val repository = MusicRepository(appContext.applicationContext)
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(appContext.applicationContext, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
