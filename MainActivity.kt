@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val permissionGranted = mutableStateOf(false)

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted.value = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionState()
        setContent {
            MusicPlayerTheme {
                MusicApp(
                    hasPermission = permissionGranted.value,
                    onRequestPermission = { requestAudioPermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun refreshPermissionState() {
        permissionGranted.value = ContextCompat.checkSelfPermission(
            this,
            audioPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        audioPermissionLauncher.launch(audioPermission())
    }
}

@Composable
fun MusicApp(hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    val viewModel: MusicViewModel = viewModel(factory = MusicViewModelFactory(context))

    DisposableEffect(Unit) {
        viewModel.bindService()
        onDispose { viewModel.unbindService() }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadSongs()
        }
    }

    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Music Player") }) },
        bottomBar = {
            if (currentSong != null) {
                MiniPlayer(
                    song = currentSong,
                    isPlaying = isPlaying,
                    duration = duration,
                    position = currentPosition,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeek = viewModel::seekTo
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                !hasPermission -> PermissionRequest(onRequestPermission)
                songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> SongList(
                    songs = songs,
                    currentSong = currentSong,
                    onSongClick = viewModel::playSong
                )
            }
        }
    }
}

@Composable
fun PermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Music Player needs access to your audio files to find and play your music.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant permission")
        }
    }
}

@Composable
fun SongList(songs: List<Song>, currentSong: Song?, onSongClick: (Song) -> Unit) {
    LazyColumn {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isPlaying = song.id == currentSong?.id,
                onClick = { onSongClick(song) }
            )
        }
    }
}

@Composable
fun SongRow(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text(formatDuration(song.duration), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    duration: Long,
    position: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Surface(shadowElevation = 8.dp, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song?.title.orEmpty(), fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(song?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }
            Slider(
                value = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
