package dev.chanflow.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import java.io.File

@Composable
internal fun Library(vm: FlowViewModel, jobs: List<WorkInfo>, onDelete: (List<MediaPost>, Boolean) -> Unit) {
    var recent by rememberSaveable { mutableStateOf(false) }
    var queries by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var oldest by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val groups = groupSavedThreads(vm.saved)
    val folder = groups.firstOrNull { it.key == vm.libraryThread }.takeUnless { recent }
    val root = !recent && folder == null
    val scope = if (recent) "history" else folder?.key ?: "threads"
    val query = queries[scope].orEmpty()
    val watched = vm.history.associateBy { it.post.key }
    val foldersScroll = rememberLazyStaggeredGridState()
    val fileStates = rememberSaveableStateHolder()
    LaunchedEffect(groups.map { it.key }, vm.libraryThread) {
        if (vm.libraryThread.isNotBlank() && groups.none { it.key == vm.libraryThread }) vm.libraryThread = ""
    }
    fun up() { vm.libraryThread = ""; selecting = false; selected = emptySet() }
    BackHandler(enabled = !recent && folder != null) { up() }
    fun matches(p: MediaPost) = "${p.title} ${p.filename} ${p.board} ${p.thread}".contains(query, true)
    val visibleGroups = groups.filter { g ->
        g.files.any(::matches) && when (filter) {
            "Unwatched" -> g.files.any { it.key !in watched }
            "Watched" -> g.files.all { it.key in watched }
            else -> true
        }
    }.let { if (oldest) it.reversed() else it }
    val source = if (recent) vm.history.map { h -> vm.saved.firstOrNull { it.key == h.post.key } ?: h.post } else folder?.files ?: vm.saved
    val files = source.filter { matches(it) && (recent || filter == "All" || (watched.containsKey(it.key) == (filter == "Watched"))) }
    val visible = if (root) visibleGroups.flatMap { it.files } else files

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", fontSize = 25.sp, letterSpacing = (-.8).sp)
                Text("${groups.size} threads / ${vm.saved.size} files / ${size(vm.saved.sumOf { it.bytes })}", fontSize = 10.sp, color = Gray, modifier = Modifier.padding(top = 6.dp))
            }
            if (folder != null) TextButton(onClick = { vm.openSaved(folder.files) }) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(17.dp))
                Text(if (folder.files.any { it.key in watched }) "Resume thread" else "Play thread", fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOf(false to "Downloads", true to "Recently watched").forEach { (value, text) ->
                Text(text, color = if (recent == value) Paper else Gray, fontSize = 12.sp,
                    modifier = Modifier.clickable { recent = value; if (!value) up(); selected = emptySet(); selecting = false }.padding(bottom = 15.dp))
            }
        }
        HorizontalDivider(color = Line)
        if (folder != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::up, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(14.dp)); Text(" All threads", fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(folder.files, false) }) { Icon(Icons.Outlined.DeleteOutline, "Delete saved thread ${folder.key}", Modifier.size(18.dp), tint = Gray) }
            }
            Text(folder.title, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("/${folder.board}/  #${folder.thread} / ${folder.files.size} files / ${size(folder.bytes)}", color = Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
        TextField(value = query, onValueChange = { queries = queries + (scope to it) },
            placeholder = { Text(if (root) "Search saved threads…" else "Search files…", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Ink, focusedContainerColor = Ink, unfocusedIndicatorColor = Color.Transparent))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { selecting = !selecting; selected = emptySet() }, contentPadding = PaddingValues(0.dp)) {
                Text(if (selecting) "Done" else if (root) "Select threads" else "Select files", fontSize = 11.sp)
            }
            if (selecting) {
                TextButton(onClick = { selected = if (visible.isNotEmpty() && visible.all { it.key in selected }) emptySet() else visible.map { it.key }.toSet() }) { Text("Select visible", fontSize = 10.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = selected.isNotEmpty(), onClick = { onDelete(vm.saved.filter { it.key in selected }.takeUnless { recent } ?: source.filter { it.key in selected }, recent); selected = emptySet(); selecting = false }) {
                    Text("Delete (${if (root) visibleGroups.count { it.files.all { p -> p.key in selected } } else selected.size})", fontSize = 11.sp, color = Color(0xffd6a58e))
                }
            } else {
                if (root) TextButton(onClick = { oldest = !oldest }) { Text(if (oldest) "Oldest threads" else "Newest threads", fontSize = 11.sp) }
                Spacer(Modifier.weight(1f))
                if (recent) TextButton(onClick = { onDelete(source, true) }, enabled = source.isNotEmpty()) { Text("Clear history", fontSize = 11.sp, color = Gray) }
            }
        }
        if (!recent && !selecting) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            listOf("All", "Unwatched", "Watched").forEach { label -> Text(label, color = if (filter == label) Sage else Gray, fontSize = 11.sp,
                modifier = Modifier.clickable { filter = label }.padding(vertical = 6.dp)) }
        }
        DownloadStatus(jobs, vm)
        if (visible.isEmpty()) Empty(if (recent) "Media you watch will appear here with its resume position."
            else if (vm.saved.isEmpty()) "Save a file or a whole thread. Your downloads will be organized here by thread." else "Nothing matches this filter.",
            if (!recent && vm.saved.isEmpty()) "Explore threads" else null) { vm.navigate("browse") }
        else if (root) LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(155.dp), state = foldersScroll,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 22.dp, contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.semantics { contentDescription = "Saved threads" }) {
            items(visibleGroups, key = { it.key }) { group ->
                SavedThreadCard(group, group.files.count { it.key in watched }, selecting, group.files.all { it.key in selected },
                    onClick = {
                        if (selecting) selected = if (group.files.all { it.key in selected }) selected - group.files.map { it.key }.toSet() else selected + group.files.map { it.key }
                        else vm.libraryThread = group.key
                    }, onDelete = { onDelete(group.files, false) })
            }
        } else fileStates.SaveableStateProvider(scope) {
            val scroll = rememberLazyStaggeredGridState()
            LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(155.dp), state = scroll,
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 20.dp, contentPadding = PaddingValues(bottom = 20.dp),
                modifier = Modifier.semantics { contentDescription = "Saved files" }) {
                items(files, key = { it.key }) { p ->
                    Column(Modifier.clickable {
                        if (selecting) selected = if (p.key in selected) selected - p.key else selected + p.key
                        else if (recent) vm.openHistory(p)
                        else vm.openSaved(folder!!.files, folder.files.indexOfFirst { it.key == p.key })
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
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedThreadCard(group: SavedThread, watched: Int, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).semantics { contentDescription = "Open saved thread ${group.key}" }) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xff22221e))) {
            val covers = group.files.take(4)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (column in 0 until if (covers.size == 1) 1 else 2) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    covers.filterIndexed { index, _ -> if (covers.size == 1) true else index % 2 == column }.forEach { p ->
                        AsyncImage(model = File(p.localPath), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
            if (selecting) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.align(Alignment.TopStart))
            else IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.DeleteOutline, "Delete saved thread ${group.key}", Modifier.size(17.dp).background(Color(0xcc111111)).padding(2.dp))
            }
            Text("${group.files.size} files", color = Paper, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xdd111111)).padding(5.dp))
        }
        Text(group.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.padding(top = 9.dp, bottom = 5.dp))
        Text("/${group.board}/  #${group.thread} / ${size(group.bytes)}", color = Gray, fontSize = 9.sp)
        Text("$watched / ${group.files.size} watched", color = Sage, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
    }
}
