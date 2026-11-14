package dev.chanflow.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val Ink = Color(0xff111111)
private val Paper = Color(0xffedede9)
private val Gray = Color(0xff898985)
private val Sage = Color(0xffa3b7a3)
private val Line = Color(0xff30302e)
private val Palette = darkColorScheme(primary = Sage, onPrimary = Ink, background = Ink,
    surface = Ink, surfaceVariant = Color(0xff1c1c1c), onSurface = Paper,
    onSurfaceVariant = Gray, outline = Line, secondary = Sage)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = Palette) { FlowUi() } }
    }
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }
}

@Composable
private fun FlowUi(vm: FlowViewModel = viewModel()) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var boards by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var post by remember { mutableStateOf<MediaPost?>(null) }
    var saveBatch by remember { mutableStateOf<List<MediaPost>?>(null) }
    var deletion by remember { mutableStateOf<Pair<List<MediaPost>, Boolean>?>(null) }
    val work = remember { WorkManager.getInstance(context) }
    val jobs by work.getWorkInfosByTagFlow("downloads").collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(vm.message) {
        val text = vm.message ?: return@LaunchedEffect
        snack.showSnackbar(text)
        if (vm.message == text) vm.dismissMessage()
    }
    fun save(items: List<MediaPost>) { (context as? MainActivity)?.askNotifications(); vm.save(items) }
    BackHandler(enabled = vm.feed != null) { vm.navigate(vm.tab) }
    Scaffold(containerColor = Ink, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snack) }, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("chanflow", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp,
                modifier = Modifier.clickable { vm.navigate("browse") })
            Text(" /", color = Sage, fontSize = 18.sp)
            Spacer(Modifier.width(21.dp))
            TextButton(onClick = { vm.navigate("browse") }, contentPadding = PaddingValues(7.dp)) {
                Text("Explore", fontSize = 12.sp, color = if (vm.tab == "browse") Paper else Gray)
            }
            TextButton(onClick = { vm.navigate("saved") }, contentPadding = PaddingValues(7.dp)) {
                Text("Library", fontSize = 12.sp, color = if (vm.tab == "saved") Paper else Gray)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { settings = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.MoreHoriz, "Settings", Modifier.size(21.dp), tint = Gray)
            }
        }
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).navigationBarsPadding()) {
            HorizontalDivider(color = Line)
            val media = vm.feed
            if (media != null) {
                Feed(vm, media, onBack = { vm.navigate(vm.tab) }, onSave = { save(listOf(it)) },
                    onSaveThread = { saveBatch = media }, onPost = { post = it },
                    onDelete = { deletion = listOf(it) to false })
            } else if (vm.tab == "saved") {
                Library(vm, jobs, onDelete = { items, historyOnly -> deletion = items to historyOnly })
            } else Browse(vm, onBoards = { boards = true })
        }
    }
    if (boards) BoardDialog(vm, onClose = { boards = false })
    if (settings) SettingsDialog(vm, onClose = { settings = false })
    post?.let { p -> AlertDialog(onDismissRequest = { post = null }, title = { Text(p.title, fontSize = 18.sp) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("/${p.board}/  #${p.post}", fontFamily = FontFamily.Monospace, color = Gray, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp)); Text(p.caption.ifBlank { "This post has no text." }, fontSize = 13.sp)
        } }, confirmButton = { TextButton(onClick = { save(listOf(p)); post = null }) { Text("Save to library") } },
        dismissButton = { TextButton(onClick = { post = null }) { Text("Close") } }) }
    saveBatch?.let { items -> AlertDialog(onDismissRequest = { saveBatch = null }, title = { Text("Save this thread?") },
        text = { Text("Save ${items.size} files (${size(items.sumOf { it.bytes })}) inside ChanFlow. Already saved files are skipped. Track or cancel the queue in Library.") },
        confirmButton = { TextButton(onClick = { save(items); saveBatch = null }) { Text("Save ${items.size} files") } },
        dismissButton = { TextButton(onClick = { saveBatch = null }) { Text("Cancel") } }) }
    deletion?.let { (items, historyOnly) -> AlertDialog(onDismissRequest = { deletion = null },
        title = { Text(if (historyOnly) "Remove from history?" else "Delete ${items.size} files?") },
        text = { Text(if (historyOnly) "Remove these playback records and resume positions. Downloaded files stay in your library."
            else "Remove ${size(items.sumOf { it.bytes })} from this device. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { vm.remove(items, historyOnly); deletion = null }) { Text(if (historyOnly) "Remove history" else "Delete files", color = Color(0xffe2ac92)) } },
        dismissButton = { TextButton(onClick = { deletion = null }) { Text("Cancel") } }) }
}

@Composable
private fun Browse(vm: FlowViewModel, onBoards: () -> Unit) {
    var filter by rememberSaveable(vm.board) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(vm.boards.firstOrNull { it.id == vm.board }?.title ?: "/${vm.board}/", fontSize = 23.sp, letterSpacing = (-.7).sp)
                Text("/${vm.board}/  ${vm.threads.size} active threads", color = Gray, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
            IconButton(onClick = { vm.chooseBoard(vm.board) }) { Icon(Icons.Outlined.Refresh, "Refresh threads", tint = Gray, modifier = Modifier.size(20.dp)) }
        }
        LazyRow(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(23.dp), verticalAlignment = Alignment.CenterVertically) {
            items(listOf(vm.board, "wsg", "w", "g", "a", "v", "co", "lit").distinct()) { id ->
                Text("/$id/", color = if (id == vm.board) Sage else Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.clickable { vm.chooseBoard(id) }.padding(vertical = 7.dp))
            }
            item { IconButton(onClick = onBoards, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Add, "Find a board", Modifier.size(18.dp), tint = Gray) } }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Latest threads", fontSize = 11.sp, color = Gray, modifier = Modifier.weight(1f))
            TextField(value = filter, onValueChange = { filter = it }, placeholder = { Text("Filter threads…", fontSize = 11.sp) },
                singleLine = true, modifier = Modifier.width(150.dp), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                colors = TextFieldDefaults.colors(unfocusedContainerColor = Ink, focusedContainerColor = Ink, unfocusedIndicatorColor = Color.Transparent))
        }
        if (vm.loading) Loading("Opening 4chan…")
        else if (vm.error != null) Empty(vm.error!!, "Try again") { vm.chooseBoard(vm.board) }
        else {
            val rows = vm.threads.filter { "${it.title} ${it.caption}".contains(filter, ignoreCase = true) }
            if (rows.isEmpty()) Empty("No matching threads. Try a different filter.")
            LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(158.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 20.dp, contentPadding = PaddingValues(bottom = 24.dp)) {
                items(rows, key = { it.id }) { row ->
                    Column(Modifier.clickable { vm.openThread(row) }) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(Color(0xff1b1b1a))) {
                            AsyncImage(model = row.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 230.dp).aspectRatio(1.25f))
                            Text("${row.images + 1}", fontSize = 9.sp, color = Paper,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xcc111111)).padding(4.dp))
                        }
                        Text(row.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Text("${row.replies} replies", fontSize = 10.sp, color = Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardDialog(vm: FlowViewModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Find a board", fontSize = 22.sp) }, text = {
        Column {
            TextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search boards…") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.allowAdult, onCheckedChange = vm::adult)
                Text("Show adult boards (18+)", fontSize = 12.sp)
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(vm.boards.filter { (it.safe || vm.allowAdult) && "${it.id} ${it.title}".contains(query, true) }, key = { it.id }) { board ->
                    Row(Modifier.fillMaxWidth().clickable { vm.chooseBoard(board.id); onClose() }.padding(vertical = 14.dp)) {
                        Text("/${board.id}/", fontFamily = FontFamily.Monospace, color = Sage, modifier = Modifier.width(60.dp))
                        Text(board.title + if (board.safe) "" else " (18+)", fontSize = 12.sp)
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun Library(vm: FlowViewModel, jobs: List<WorkInfo>, onDelete: (List<MediaPost>, Boolean) -> Unit) {
    var recent by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var grouping by rememberSaveable { mutableStateOf(false) }
    var threadFilter by rememberSaveable { mutableStateOf("") }
    var threadMenu by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val watched = vm.history.associateBy { it.post.key }
    val source = if (recent) vm.history.map { h -> vm.saved.firstOrNull { it.key == h.post.key } ?: h.post } else vm.saved
    var rows = source.filter { "${it.title} ${it.filename} ${it.board} ${it.thread}".contains(query, true) &&
        (threadFilter.isBlank() || "${it.board}_${it.thread}" == threadFilter) &&
        (recent || filter == "All" || (watched.containsKey(it.key) == (filter == "Watched"))) }
    if (grouping) rows = rows.sortedWith(compareBy<MediaPost> { it.board }.thenByDescending { it.thread })
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", fontSize = 25.sp, letterSpacing = (-.8).sp)
                Text("${vm.saved.size} files / ${size(vm.saved.sumOf { it.bytes })} on this device", fontSize = 10.sp, color = Gray, modifier = Modifier.padding(top = 6.dp))
            }
            if (vm.saved.isNotEmpty()) TextButton(onClick = { vm.openSaved(vm.saved, 0) }) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(17.dp)); Text("Play saved", fontSize = 11.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOf(false to "Downloads", true to "Recently watched").forEach { (value, text) ->
                Text(text, color = if (recent == value) Paper else Gray, fontSize = 12.sp,
                    modifier = Modifier.clickable { recent = value; selected = emptySet(); selecting = false }.padding(bottom = 15.dp))
            }
        }
        HorizontalDivider(color = Line)
        TextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search library…", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Ink, focusedContainerColor = Ink, unfocusedIndicatorColor = Color.Transparent))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { selecting = !selecting; selected = emptySet() }, contentPadding = PaddingValues(0.dp)) { Text(if (selecting) "Done" else "Select files", fontSize = 11.sp) }
            if (selecting) {
                TextButton(onClick = { selected = if (rows.all { it.key in selected }) emptySet() else rows.map { it.key }.toSet() }) { Text("Select visible", fontSize = 10.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = selected.isNotEmpty(), onClick = { onDelete(source.filter { it.key in selected }, recent); selected = emptySet(); selecting = false }) { Text("Delete (${selected.size})", fontSize = 11.sp, color = Color(0xffd6a58e)) }
            } else {
                TextButton(onClick = { grouping = !grouping }) { Text(if (grouping) "By thread" else "Newest", fontSize = 11.sp) }
                Spacer(Modifier.weight(1f))
                if (recent) TextButton(onClick = { onDelete(source, true) }, enabled = source.isNotEmpty()) { Text("Clear history", fontSize = 11.sp, color = Gray) }
            }
        }
        if (!recent && !selecting) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            listOf("All", "Unwatched", "Watched").forEach { label ->
                Text(label, color = if (filter == label) Sage else Gray, fontSize = 11.sp,
                    modifier = Modifier.clickable { filter = label }.padding(vertical = 6.dp))
            }
        }
        Box {
            TextButton(onClick = { threadMenu = true }, contentPadding = PaddingValues(0.dp)) {
                Text(if (threadFilter.isBlank()) "All threads" else source.firstOrNull { "${it.board}_${it.thread}" == threadFilter }?.title ?: "Selected thread",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Gray)
            }
            DropdownMenu(expanded = threadMenu, onDismissRequest = { threadMenu = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                DropdownMenuItem(text = { Text("All threads", fontSize = 12.sp) }, onClick = { threadFilter = ""; selected = emptySet(); threadMenu = false })
                source.distinctBy { "${it.board}_${it.thread}" }.forEach { p ->
                    DropdownMenuItem(text = { Text("/${p.board}/ ${p.title}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { threadFilter = "${p.board}_${p.thread}"; selected = emptySet(); threadMenu = false })
                }
            }
        }
        DownloadStatus(jobs, vm)
        if (rows.isEmpty()) Empty(if (recent) "Media you watch will appear here with its resume position."
            else if (vm.saved.isEmpty()) "Save a file or a whole thread. Play it here, even offline." else "No files match this filter.",
            if (!recent && vm.saved.isEmpty()) "Explore threads" else null) { vm.navigate("browse") }
        else LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(155.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 20.dp, contentPadding = PaddingValues(bottom = 20.dp)) {
            items(rows, key = { it.key }) { p ->
                Column(Modifier.clickable {
                    if (selecting) selected = if (p.key in selected) selected - p.key else selected + p.key
                    else vm.openSaved(rows, rows.indexOf(p))
                }) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(Color(0xff22221e))) {
                        AsyncImage(model = if (p.localPath.isNotBlank()) File(p.localPath) else p.thumbnail, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio((p.width.toFloat() / p.height.coerceAtLeast(1)).coerceIn(.7f, 1.8f)))
                        if (selecting) Checkbox(checked = p.key in selected, onCheckedChange = null, modifier = Modifier.align(Alignment.TopStart))
                        if (p.video) Icon(Icons.Outlined.PlayArrow, null, tint = Paper, modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp).size(18.dp))
                        watched[p.key]?.let { h -> if (h.duration > 0) LinearProgressIndicator(progress = { (h.position.toFloat() / h.duration).coerceIn(0f, 1f) },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp), color = Sage, trackColor = Line) }
                    }
                    Text(p.filename + p.extension, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    Text("/${p.board}/  ${if (p.localPath.isNotBlank()) "Offline" else "Stream"}${if (p.key in watched) "  / Watched" else ""}", fontSize = 9.sp, color = Gray)
                    if (grouping) Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 9.sp, color = Gray)
                }
            }
        }
    }
}

@Composable
private fun DownloadStatus(jobs: List<WorkInfo>, vm: FlowViewModel) {
    val pending = jobs.filter { !it.state.isFinished }
    val failed = jobs.filter { it.state == WorkInfo.State.FAILED }
    if (pending.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 17.dp).background(Color(0xff1d241a)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(15.dp), color = Sage, strokeWidth = 1.dp)
        val running = pending.firstOrNull { it.state == WorkInfo.State.RUNNING }
        val bytes = running?.progress?.getLong("bytes", 0) ?: 0
        val total = running?.progress?.getLong("total", 0) ?: 0
        Text("  ${pending.size} saving${if (total > 0) " / ${bytes * 100 / total}%" else ""}", fontSize = 11.sp, color = Sage, modifier = Modifier.weight(1f))
        TextButton(onClick = { WorkManager.getInstance(vm.app).cancelAllWorkByTag("downloads") }) { Text("Cancel", fontSize = 11.sp) }
    }
    if (failed.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${failed.size} saves failed. ${failed.last().outputData.getString("error").orEmpty()}", fontSize = 10.sp,
            color = Color(0xffd6a58e), maxLines = 2, modifier = Modifier.weight(1f))
        TextButton(onClick = { WorkManager.getInstance(vm.app).pruneWork() }) { Text("Dismiss", fontSize = 10.sp) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Feed(vm: FlowViewModel, posts: List<MediaPost>, onBack: () -> Unit, onSave: (MediaPost) -> Unit,
                 onSaveThread: () -> Unit, onPost: (MediaPost) -> Unit, onDelete: (MediaPost) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val sources by produceState<CacheDataSource.Factory?>(null) { value = withContext(Dispatchers.IO) { vm.app.mediaSources } }
    if (sources == null) { Loading("Preparing playback…"); return }
    val player = remember { ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(sources!!)).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    val pager = rememberPagerState(initialPage = vm.feedStart.coerceIn(posts.indices)) { posts.size }
    var muted by rememberSaveable { mutableStateOf(true) }
    var fill by rememberSaveable { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var nextReady by remember { mutableStateOf(false) }
    val current = posts[pager.settledPage]
    val active by rememberUpdatedState(current)
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { ready = state == Player.STATE_READY }
            override fun onPlayerError(error: PlaybackException) { playbackError = "This video could not play on this device. Try again or swipe to another file." }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                resumed = false
                if (active.video && player.currentPosition >= 2000) vm.watched(active, player.currentPosition, player.duration.coerceAtLeast(0))
                player.pause()
            } else if (event == Lifecycle.Event.ON_START) resumed = true
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            if (active.video && player.currentPosition >= 2000) vm.watched(active, player.currentPosition, player.duration.coerceAtLeast(0))
            owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release()
        }
    }
    LaunchedEffect(current.key) {
        ready = false; playbackError = null
        if (current.video) {
            val uri = if (current.localPath.isNotBlank()) android.net.Uri.fromFile(vm.app.vault.file(current)).toString() else current.url
            val position = vm.history.firstOrNull { it.post.key == current.key }?.let { h -> if (h.duration > h.position + 2000) h.position else 0 } ?: 0
            player.setMediaItem(MediaItem.fromUri(uri), position); player.prepare()
        } else { player.stop(); player.clearMediaItems() }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(current.key, resumed, pager.isScrollInProgress) {
        player.playWhenReady = current.video && resumed && !pager.isScrollInProgress
    }
    LaunchedEffect(current.key, vm.preload) {
        nextReady = false
        val next = posts.getOrNull(pager.settledPage + 1)
        if (vm.preload && next != null && next.localPath.isBlank()) {
            nextReady = if (next.video) LookAhead.prepare(vm.app, next)
            else vm.app.imageLoader.execute(ImageRequest.Builder(context).data(next.url).size(1440, 2400).build()) is coil.request.SuccessResult
        }
    }
    LaunchedEffect(current.key, resumed) {
        if (current.video && resumed) while (true) {
            delay(5000)
            if (player.currentPosition >= 2000 && player.isPlaying) vm.watched(current, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Text("/${current.board}/  #${current.thread}${if (current.localPath.isNotBlank()) "  / Offline" else ""}", fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp, color = Gray, modifier = Modifier.padding(top = 5.dp))
            }
            IconButton(onClick = onSaveThread) { Icon(Icons.Outlined.Download, "Save whole thread", Modifier.size(20.dp), tint = Sage) }
        }
        VerticalPager(state = pager, beyondViewportPageCount = 1, key = { posts[it].key }, modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) { index ->
            val p = posts[index]
            val isActive = index == pager.settledPage
            var imageError by remember(p.key) { mutableStateOf(false) }
            var thumbnailError by remember(p.key) { mutableStateOf(false) }
            var imageLoaded by remember(p.key) { mutableStateOf(false) }
            LaunchedEffect(p.key, isActive, imageLoaded, resumed) {
                if (isActive && !p.video && imageLoaded && resumed) { delay(2000); vm.watched(p) }
            }
            Box(Modifier.fillMaxSize()) {
                if (isActive && p.video) AndroidView(factory = { PlayerView(it).apply {
                    useController = true; controllerShowTimeoutMs = 2500; this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                } }, update = { it.player = player; it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
                    onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
                else AsyncImage(model = ImageRequest.Builder(context).data(when {
                    p.localPath.isNotBlank() -> vm.app.vault.file(p)
                    isActive -> if (imageError) p.thumbnail else p.url
                    else -> p.thumbnail
                }).size(1440, 2400).build(), contentDescription = p.filename,
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    onSuccess = { imageLoaded = true }, onError = { if (imageError) thumbnailError = true; imageError = true })
                if (isActive) {
                    if (p.video && !ready && playbackError == null || !p.video && !imageLoaded && !imageError)
                        CircularProgressIndicator(Modifier.align(Alignment.Center).size(25.dp), color = Sage, strokeWidth = 1.dp)
                    playbackError?.let { error -> Column(Modifier.align(Alignment.Center).padding(36.dp).background(Color(0xee22221b)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, fontSize = 12.sp); TextButton(onClick = { playbackError = null; player.prepare(); player.play() }) { Text("Retry") }
                    } }
                    if (!p.video && thumbnailError) Column(Modifier.align(Alignment.Center).padding(35.dp).background(Color(0xee22221b)).padding(18.dp)) {
                        Text("This image could not be opened. It may have expired.", fontSize = 12.sp)
                        TextButton(onClick = { imageError = false; thumbnailError = false; imageLoaded = false }) { Text("Retry image") }
                    }
                    Column(Modifier.align(Alignment.BottomEnd).padding(end = 13.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        if (p.video) Rail(if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, if (muted) "Unmute" else "Mute") { muted = !muted }
                        Rail(if (fill) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (fill) "Fit" else "Fill") { fill = !fill }
                        val saved = vm.saved.any { it.key == p.key }
                        Rail(if (saved) Icons.Outlined.Check else Icons.Outlined.Download, if (saved) "Saved" else "Save") { onSave(p) }
                        Rail(Icons.Outlined.ChatBubbleOutline, "Post") { onPost(p) }
                        if (p.localPath.isNotBlank()) Rail(Icons.Outlined.DeleteOutline, "Delete") { onDelete(p) }
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().heightIn(min = 79.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(current.filename + current.extension, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current.caption.ifBlank { "${current.width} × ${current.height} / ${size(current.bytes)}" }, fontSize = 10.sp,
                    color = Gray, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                if (nextReady) Text("Next ready", color = Sage, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text("${pager.settledPage + 1} / ${posts.size}", fontSize = 10.sp, color = Gray, modifier = Modifier.padding(start = 15.dp))
        }
    }
}

@Composable
private fun Rail(icon: ImageVector, label: String, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClickLabel = label, onClick = action)) {
        Box(Modifier.size(39.dp).clip(CircleShape).background(Color(0xd91c1d19)).border(1.dp, Color(0x44ffffff), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(18.dp), tint = Paper)
        }
        Text(label, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp), color = Paper)
    }
}

@Composable
private fun SettingsDialog(vm: FlowViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdates.Release?>(null) }
    var updateFile by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Settings", fontSize = 22.sp) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Preload next media", fontSize = 13.sp); Text("Prepare the next file while you watch.", color = Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) }
                Switch(checked = vm.preload, onCheckedChange = vm::preloading)
            }
            Spacer(Modifier.height(18.dp))
            Text("Downloads are stored privately inside ChanFlow. Uninstalling the app removes them. Install updates over the existing app to keep your library.", fontSize = 11.sp, color = Gray, lineHeight = 17.sp)
            Spacer(Modifier.height(20.dp))
            Text("ChanFlow ${BuildConfig.VERSION_NAME}", fontSize = 12.sp)
            TextButton(enabled = !checking, onClick = {
                scope.launch { checking = true; status = "Checking GitHub Releases…"
                    try { update = AppUpdates.latest(vm.app); status = if (update == null) "You have the latest version." else "Version ${update!!.version} is available." }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; status = e.message ?: "Could not check for updates." }
                    finally { checking = false }
                }
            }) { Text(if (checking) "Working…" else "Check for updates", fontSize = 12.sp) }
            status?.let { Text(it, color = Gray, fontSize = 11.sp, lineHeight = 16.sp) }
            update?.let { release -> TextButton(enabled = !checking, onClick = {
                scope.launch { checking = true; status = "Downloading update…"
                    try { updateFile = AppUpdates.download(vm.app, release); status = "Update is ready to install. Your saved files will be kept." }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; status = e.message ?: "Could not download the update." }
                    finally { checking = false }
                }
            }) { Text("Download update", fontSize = 12.sp) } }
            updateFile?.let { file -> TextButton(onClick = { AppUpdates.install(context, file) }) { Text("Install downloaded update", fontSize = 12.sp) } }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun Loading(text: String) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    CircularProgressIndicator(Modifier.size(22.dp), color = Sage, strokeWidth = 1.dp); Text(text, color = Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
} }
@Composable
private fun Empty(text: String, action: String? = null, onAction: () -> Unit = {}) { Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, color = Gray, fontSize = 13.sp, lineHeight = 20.sp); if (action != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = 15.dp)) { Text(action) }
} }
private fun size(bytes: Long): String = if (bytes >= 1048576) String.format(Locale.US, "%.1f MB", bytes / 1048576.0) else "${bytes / 1024} KB"
