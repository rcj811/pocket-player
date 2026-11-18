package com.rcj.pocketplayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val Pink = Color(0xFFFF7EA6)
private val Violet = Color(0xFF8E73FF)
private val Muted = Color(0xFFB9B3CC)
private val Card = Color(0x1AFFFFFF)

private val colors = darkColorScheme(
    primary = Pink,
    onPrimary = Color(0xFF2A0716),
    secondary = Violet,
    surface = Color(0xFF17142A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF241F3D),
    onSurfaceVariant = Muted,
    background = Color(0xFF0B0A12),
    secondaryContainer = Color(0x33FF7EA6),
)

@Composable
fun AppRoot(a: MainActivity) {
    MaterialTheme(colorScheme = colors) {
        var tab by remember { mutableIntStateOf(0) }
        LaunchedEffect(tab) { if (tab == 0) a.stopPreview() }
        val snackbar = remember { SnackbarHostState() }
        val msg = a.player.message
        LaunchedEffect(msg) {
            if (msg != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
                if (a.player.message == msg) a.player.message = null
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2B1A4F), Color(0xFF14102A), Color(0xFF0B0A12))))
        ) {
            val art = a.player.meta.artworkData ?: a.player.meta.artworkUri
            if (art != null) {
                AsyncImage(
                    model = art, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(460.dp).blur(70.dp).alpha(0.35f),
                )
            }
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column(Modifier.background(Color(0xF00E0C18))) {
                        if (tab == 1 && a.player.queue.isNotEmpty()) MiniPlayer(a) { tab = 0 }
                        NavigationBar(containerColor = Color.Transparent) {
                            val itemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Pink, selectedTextColor = Pink,
                                indicatorColor = Color(0x33FF7EA6),
                                unselectedIconColor = Muted, unselectedTextColor = Muted,
                            )
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 }, colors = itemColors,
                                icon = { Icon(painterResource(R.drawable.ic_music), null) }, label = { Text("播放") },
                            )
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 }, colors = itemColors,
                                icon = { Icon(Icons.Default.Search, null) }, label = { Text("搜尋") },
                            )
                        }
                    }
                },
            ) { pad ->
                if (tab == 0) PlayerScreen(a, pad) else SearchScreen(a, pad)
            }
            Dialogs(a)
        }
    }
}

// ============================ 對話框 ============================

@Composable
private fun Dialogs(a: MainActivity) {
    a.crashLog?.let { log ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = a::dismissCrash,
            title = { Text("上次發生錯誤而關閉") },
            text = {
                Column {
                    Text("已自動清空播放清單，確保 App 能正常開啟。可以按「複製錯誤」貼給開發者。", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        log, fontSize = 10.sp, lineHeight = 13.sp,
                        modifier = Modifier.height(220.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { clipboard.setText(AnnotatedString(log)); a.dismissCrash() },
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                ) { Text("複製錯誤") }
            },
            dismissButton = { TextButton(onClick = a::dismissCrash) { Text("關閉", color = Muted) } },
        )
        return
    }
    a.folderOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = { a.folderOffer = null },
            icon = { Icon(painterResource(R.drawable.ic_folder), null, tint = Pink) },
            title = { Text("「${offer.folderName}」共 ${offer.files.size} 首") },
            text = {
                Column {
                    Text("音樂 ${offer.audioCount} 首・影片 ${offer.videoCount} 部", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    offer.files.take(6).forEach {
                        Text("・${it.name}", fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (offer.files.size > 6) Text("…還有 ${offer.files.size - 6} 首", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Text("要加入播放清單嗎？（不會打斷正在播放的歌）", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = { a.acceptFolder(onlyAudio = false) }, colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                    Text("全部加入", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (offer.videoCount > 0 && offer.audioCount > 0) {
                        TextButton(onClick = { a.acceptFolder(onlyAudio = true) }) { Text("只加音樂") }
                    }
                    TextButton(onClick = { a.folderOffer = null }) { Text("取消", color = Muted) }
                }
            },
        )
        return
    }
    if (a.showWelcome) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(painterResource(R.drawable.ic_run), null, tint = Pink) },
            title = { Text("歡迎使用隨身聽") },
            text = {
                Text("跑步時關螢幕、切到別的 App，音樂都要繼續播。\n\n請按「一鍵開啟」，接著在跳出的畫面按「允許」即可。")
            },
            confirmButton = {
                Button(onClick = a::grantAll, colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                    Text("一鍵開啟", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { a.showWelcome = false }) { Text("稍後", color = Muted) } },
        )
        return
    }
    val u = a.update ?: return
    AlertDialog(
        onDismissRequest = { a.update = null },
        icon = { Icon(painterResource(R.drawable.ic_download), null, tint = Pink) },
        title = { Text("有新版本 build ${u.build}") },
        text = { Text(u.notes.ifBlank { "包含最新的 YouTube 解析引擎與修正。" }, fontSize = 14.sp) },
        confirmButton = {
            Button(onClick = a::startUpdate, colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                Text("立即更新", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { a.update = null }) { Text("下次再說", color = Muted) } },
    )
}

// ============================ 播放頁 ============================

@Composable
fun PlayerScreen(a: MainActivity, pad: PaddingValues) {
    val s = a.player
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item { Header(a) }
        item { NowPlaying(a) }
        item { Progress(a) }
        item { Controls(a) }
        item { AddBar(a) }
        item {
            Row(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_queue), null, tint = Pink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("播放清單", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("${s.queue.size} 首", color = Muted, fontSize = 13.sp)
            }
        }
        if (s.queue.isEmpty()) {
            item {
                Text(
                    "還沒有歌曲\n貼上 YouTube 網址、到「搜尋」找歌，或選手機裡的音樂資料夾",
                    color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                )
            }
        }
        itemsIndexed(s.queue, key = { i, m -> "$i-${m.mediaId}" }) { i, m ->
            val offline = remember(m.mediaId, s.cacheTick, s.index) { a.isOffline(m) }
            TrackRow(
                title = m.mediaMetadata.title?.toString() ?: "未命名",
                subtitle = m.mediaMetadata.artist?.toString() ?: "",
                thumb = m.mediaMetadata.artworkUri,
                current = i == s.index,
                playing = i == s.index && s.isPlaying,
                offline = offline,
                onClick = { a.playAt(i) },
                trailing = {
                    IconButton(onClick = { a.remove(i) }) {
                        Icon(Icons.Default.Close, "移除", tint = Muted, modifier = Modifier.size(18.dp))
                    }
                },
            )
        }
    }
}

@Composable
private fun Header(a: MainActivity) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("隨身聽", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("YouTube・MP3・MP4　關螢幕也照常播放", color = Muted, fontSize = 12.sp)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "選單") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("加入單一檔案（MP3 / MP4）") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_music), null) },
                    onClick = { menu = false; a.pickFiles.launch(arrayOf("audio/*", "video/*")) },
                )
                DropdownMenuItem(
                    text = { Text("離線下載整個清單") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_download), null) },
                    onClick = { menu = false; a.downloadAll() },
                )
                DropdownMenuItem(
                    text = { Text("允許背景播放（關閉省電限制）") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_run), null) },
                    onClick = { menu = false; a.openBatterySettings() },
                )
                DropdownMenuItem(
                    text = { Text("清空播放清單") },
                    leadingIcon = { Icon(Icons.Default.Close, null) },
                    onClick = { menu = false; a.clearQueue() },
                )
            }
        }
    }
}

@Composable
private fun NowPlaying(a: MainActivity) {
    val s = a.player
    val scale by animateFloatAsState(if (s.isPlaying || s.buffering) 1f else 0.9f, label = "art")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (s.hasVideo) {
            val c = s.controller
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = c
                    }
                },
                update = { it.player = c },
                onRelease = { it.player = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth(0.66f)
                    .aspectRatio(1f)
                    .scale(scale)
                    .shadow(24.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Pink, Violet))),
                contentAlignment = Alignment.Center,
            ) {
                val art = s.meta.artworkData ?: s.meta.artworkUri
                if (art != null) {
                    AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(painterResource(R.drawable.ic_music), null, tint = Color.White, modifier = Modifier.size(96.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            s.meta.title?.toString() ?: if (s.queue.isEmpty()) "準備好開跑了嗎？" else "—",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            s.meta.artist?.toString() ?: "加入歌曲開始播放",
            color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Progress(a: MainActivity) {
    val s = a.player
    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(s.controller) {
        while (true) {
            val c = s.controller
            if (c != null && !dragging) pos = c.currentPosition
            delay(500)
        }
    }
    val dur = s.durationMs
    val fraction = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Column(Modifier.padding(top = 16.dp)) {
        Slider(
            value = if (dragging) dragValue else fraction,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (dur > 0) {
                    pos = (dragValue * dur).toLong()
                    a.seekTo(pos)
                }
                dragging = false
            },
            enabled = dur > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Pink, inactiveTrackColor = Color(0x33FFFFFF),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging) (dragValue * dur).toLong() else pos
            Text(Links.formatDuration(shown), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(Links.formatDuration(dur), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Controls(a: MainActivity) {
    val s = a.player
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = a::toggleShuffle) {
            Icon(painterResource(R.drawable.ic_shuffle), "隨機", tint = if (s.shuffle) Pink else Muted)
        }
        IconButton(onClick = a::prev, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_prev), "上一首", modifier = Modifier.size(36.dp))
        }
        Box(
            Modifier
                .size(78.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Pink, Violet)))
                .clickable { a.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            if (s.buffering) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            } else {
                Icon(
                    painterResource(if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    if (s.isPlaying) "暫停" else "播放", tint = Color.White, modifier = Modifier.size(42.dp),
                )
            }
        }
        IconButton(onClick = a::next, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_next), "下一首", modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = a::cycleRepeat) {
            Icon(
                painterResource(if (s.repeat == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                "重複", tint = if (s.repeat == Player.REPEAT_MODE_OFF) Muted else Pink,
            )
        }
    }
}

@Composable
private fun AddBar(a: MainActivity) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    Surface(color = Card, shape = RoundedCornerShape(22.dp), modifier = Modifier.padding(top = 24.dp)) {
        Column(Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("貼上 YouTube 網址（影片或播放清單）", fontSize = 14.sp) },
                trailingIcon = {
                    if (text.isEmpty()) {
                        IconButton(onClick = { text = clipboard.getText()?.text ?: "" }) {
                            Icon(painterResource(R.drawable.ic_paste), "貼上")
                        }
                    } else {
                        IconButton(onClick = { text = "" }) { Icon(Icons.Default.Close, "清除") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) { a.addFromText(text); text = ""; focus.clearFocus() }
                }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Pink, unfocusedBorderColor = Color(0x33FFFFFF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Pink, Violet)))
                        .clickable(enabled = !a.player.busy) {
                            val t = text.ifBlank { clipboard.getText()?.text ?: "" }
                            if (t.isNotBlank()) { a.addFromText(t); text = ""; focus.clearFocus() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (a.player.busy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("加入網址", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { a.pickFolder.launch(null) },
                    enabled = !a.scanning,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    if (a.scanning) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(painterResource(R.drawable.ic_folder), null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("本機資料夾")
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    thumb: Any?,
    current: Boolean,
    playing: Boolean,
    offline: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) Color(0x26FF7EA6) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(R.drawable.ic_music), null, tint = Muted)
            }
            if (playing) {
                Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_equalizer), null, tint = Pink, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) Pink else Color.White, fontSize = 15.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (offline) {
                    Icon(painterResource(R.drawable.ic_download), "可離線", tint = Color(0xFF7CE0A3), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
            }
        }
        trailing()
    }
}

@Composable
private fun MiniPlayer(a: MainActivity, onOpen: () -> Unit) {
    val s = a.player
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))) {
            val art = s.meta.artworkData ?: s.meta.artworkUri
            if (art != null) AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.meta.title?.toString() ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(s.meta.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Muted)
        }
        IconButton(onClick = a::togglePlay) {
            Icon(painterResource(if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play), null, tint = Pink)
        }
        IconButton(onClick = a::next) { Icon(painterResource(R.drawable.ic_next), null) }
    }
}

// ============================ 搜尋頁 ============================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(a: MainActivity, pad: PaddingValues) {
    val st = a.search
    if (st.openList != null) {
        ListDetail(a, pad)
        return
    }
    val focus = LocalFocusManager.current
    val listState: LazyListState = rememberLazyListState()
    val results = st.visible()

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, st.results.size) {
        if (nearEnd && st.searched && st.results.isNotEmpty()) a.searchMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item { Column {
            Text("搜尋 YouTube", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 12.dp))
            OutlinedTextField(
                value = st.query,
                onValueChange = { st.query = it },
                singleLine = true,
                placeholder = { Text("歌名、歌手、BPM…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (st.query.isNotEmpty()) IconButton(onClick = { st.query = "" }) { Icon(Icons.Default.Close, "清除") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); a.runSearch() }),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Pink, unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = Card, unfocusedContainerColor = Card,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        } }
        item { Column {
            ChipRow("類型") {
                YouTube.Kind.entries.forEach { k ->
                    Chip(k.label, st.kind == k) {
                        st.kind = k
                        if (st.query.isNotBlank()) a.runSearch()
                    }
                }
            }
            ChipRow("長度") {
                DurationFilter.entries.forEach { d -> Chip(d.label, st.duration == d) { st.duration = d } }
            }
            ChipRow("運動快選") {
                runningPresets.forEach { p -> Chip(p.label, false) { focus.clearFocus(); a.applyPreset(p) } }
            }
        } }

        when {
            st.loading -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pink)
                }
            }
            st.error != null -> item {
                Text(st.error ?: "", color = Pink, modifier = Modifier.padding(vertical = 24.dp))
            }
            st.searched && results.isEmpty() -> item {
                Text("沒有符合條件的結果，試試放寬長度篩選", color = Muted, modifier = Modifier.padding(vertical = 24.dp))
            }
            !st.searched -> item {
                Text(
                    "提示：點「運動快選」一鍵找跑步音樂。\n點歌曲＝試聽（亮起的那首），按 ＋ 才加入清單；點專輯 / 歌單可展開曲目。",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }

        if (!st.loading) {
            itemsIndexed(results, key = { i, r -> "${r.id}-$i" }) { _, r ->
                val listLabel = if (st.kind == YouTube.Kind.ALBUMS) "專輯" else "歌單"
                ResultRow(a, r, if (r.isPlaylist) listLabel else null)
            }
            if (st.loadingMore) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pink, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** 搜尋結果一列：歌曲點一下試聽（光棒），＋ 加入；專輯 / 歌單點一下展開。 */
@Composable
private fun ResultRow(a: MainActivity, r: YouTube.Result, listLabel: String?) {
    val st = a.search
    val previewing = !r.isPlaylist && st.previewId == r.id
    val sub = buildString {
        if (listLabel != null) {
            append(listLabel)
            if (r.subtitle.isNotBlank()) append(" · ").append(r.subtitle)
        } else {
            if (previewing) append(if (st.previewLoading) "載入試聽中… · " else "試聽中 · ")
            append(r.subtitle)
            if (r.durationSec > 0) append(" · ").append(Links.formatDuration(r.durationSec * 1000))
        }
    }
    TrackRow(
        title = r.title, subtitle = sub, thumb = r.thumb,
        current = previewing, playing = previewing && !st.previewLoading, offline = false,
        onClick = { if (r.isPlaylist) a.openList(r) else a.togglePreview(r) },
        trailing = {
            IconButton(onClick = { a.addResult(r) }) {
                Icon(Icons.Default.Add, "加入清單", tint = Pink)
            }
        },
    )
}

/** 專輯 / 歌單展開頁 */
@Composable
private fun ListDetail(a: MainActivity, pad: PaddingValues) {
    val st = a.search
    val list = st.openList ?: return
    BackHandler { a.stopPreview(); a.closeList() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { a.stopPreview(); a.closeList() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                    Text("返回搜尋結果", color = Muted, fontSize = 14.sp)
                }
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(Card)) {
                        if (list.thumb != null) {
                            AsyncImage(list.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(list.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(list.subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = a::addAllTracks,
                            enabled = st.tracks.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Pink),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("全部加入（${st.tracks.size} 首）")
                        }
                    }
                }
                Text("點歌曲試聽，按 ＋ 單首加入", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (st.tracksLoading) item {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pink)
            }
        }
        itemsIndexed(st.tracks, key = { i, r -> "${r.id}-$i" }) { _, r -> ResultRow(a, r, null) }
    }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Card,
            labelColor = Color.White,
            selectedContainerColor = Pink,
            selectedLabelColor = Color(0xFF2A0716),
        ),
        border = null,
    )
}
