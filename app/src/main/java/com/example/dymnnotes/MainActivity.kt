package com.example.dymnnotes

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DymnNotesTheme {
                DymnNotesApp()
            }
        }
    }
}

@Composable
private fun DymnNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DymnBlue,
            secondary = DymnCyan,
            background = DymnBlack,
            surface = DymnGraphite,
            onBackground = DymnText,
            onSurface = DymnText,
            onSurfaceVariant = DymnMuted,
        ),
        content = content,
    )
}

@Composable
private fun DymnNotesApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var notes by remember { mutableStateOf(loadNotes(context)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NoteFilter.Active) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var isCreateMenuOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var tileOpacity by remember { mutableStateOf(loadTileOpacity(context)) }
    var wallpaperKey by remember { mutableStateOf(loadWallpaperKey(context)) }
    var customWallpaperUri by remember { mutableStateOf(loadCustomWallpaperUri(context)) }
    var tileColorKey by remember { mutableStateOf(loadTileColorKey(context)) }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customWallpaperUri = uri.toString()
            saveCustomWallpaperUri(context, customWallpaperUri)
        }
    }

    val tileFill = tileColorFor(tileColorKey).copy(alpha = tileOpacity)
    val wallpaper = wallpaperOptionFor(wallpaperKey)

    fun persist(next: List<Note>) {
        notes = next.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        saveNotes(context, notes)
    }

    fun upsert(note: Note) {
        val next = if (notes.any { it.id == note.id }) {
            notes.map { if (it.id == note.id) note else it }
        } else {
            notes + note
        }
        persist(next)
    }

    fun createNote(kind: NoteKind) {
        val newNote = Note(
            id = UUID.randomUUID().toString(),
            title = "",
            body = if (kind == NoteKind.List) "- [ ] " else "",
            tags = "",
            kind = kind,
            pinned = false,
            archived = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        persist(notes + newNote)
        editingId = newNote.id
    }

    val visibleNotes = notes.filter { note ->
        val inFilter = when (filter) {
            NoteFilter.Active -> !note.archived
            NoteFilter.Pinned -> note.pinned && !note.archived
            NoteFilter.Tasks -> note.body.contains("- [ ]") || note.body.contains("- [x]", ignoreCase = true)
            NoteFilter.Archive -> note.archived
        }
        val q = query.trim().lowercase()
        val inSearch = q.isBlank() || listOf(note.title, note.body, note.tags).joinToString(" ")
            .lowercase()
            .contains(q)
        inFilter && inSearch
    }

    CompositionLocalProvider(LocalTileFill provides tileFill) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(wallpaper.brush)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                CustomWallpaperBackground(customWallpaperUri)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        HeaderTileStable2(
                            notesCount = visibleNotes.size,
                            onSettingsClick = { isSettingsOpen = true },
                        )
                    }

                    item {
                        SearchTile(
                            query = query,
                            onQueryChange = { query = it },
                            filter = filter,
                            onFilterChange = { filter = it },
                        )
                    }

                    if (visibleNotes.isEmpty()) {
                        item {
                            EmptyTileStable(onAddClick = { isCreateMenuOpen = true })
                        }
                    } else {
                        items(
                        items = visibleNotes,
                        key = { it.id },
                        contentType = { it.kind },
                    ) { note ->
                        NoteTile(
                            note = note,
                            isEditing = note.id == editingId,
                            onEdit = { editingId = note.id },
                            onSave = { updated ->
                                upsert(updated)
                                editingId = null
                            },
                            onCancel = {
                                if (note.title.isBlank() && note.body.isBlank()) {
                                    persist(notes.filterNot { it.id == note.id })
                                }
                                editingId = null
                            },
                            onDelete = {
                                persist(notes.filterNot { item -> item.id == note.id })
                                editingId = null
                            },
                            onPin = { upsert(note.copy(pinned = !note.pinned, updatedAt = System.currentTimeMillis())) },
                            onArchive = {
                                upsert(note.copy(archived = !note.archived, updatedAt = System.currentTimeMillis()))
                            },
                            onUpdate = { updated ->
                                upsert(updated.copy(updatedAt = System.currentTimeMillis()))
                            },
                        )
                    }
                        item {
                            AddMoreTile(onAddClick = { isCreateMenuOpen = true })
                        }
                    }

                    item {
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }

        if (isCreateMenuOpen) {
            CreateTypeDialog(
                onDismiss = { isCreateMenuOpen = false },
                onSelect = { kind ->
                    isCreateMenuOpen = false
                    createNote(kind)
                },
            )
        }

        if (isSettingsOpen) {
            AppSettingsDialog(
                tileOpacity = tileOpacity,
                wallpaperKey = wallpaperKey,
                customWallpaperUri = customWallpaperUri,
                tileColorKey = tileColorKey,
                onTileOpacityChange = {
                    tileOpacity = it
                    saveTileOpacity(context, it)
                },
                onWallpaperChange = {
                    wallpaperKey = it
                    customWallpaperUri = ""
                    saveWallpaperKey(context, it)
                    saveCustomWallpaperUri(context, "")
                },
                onPickCustomWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                onClearCustomWallpaper = {
                    customWallpaperUri = ""
                    saveCustomWallpaperUri(context, "")
                },
                onTileColorChange = {
                    tileColorKey = it
                    saveTileColorKey(context, it)
                },
                onDismiss = { isSettingsOpen = false },
            )
        }
    }
}

@Composable
private fun CustomWallpaperBackground(uri: String) {
    if (uri.isBlank()) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val image = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun HeaderTile(notesCount: Int, onAddClick: () -> Unit) {
    var isMenuOpen by remember { mutableStateOf(false) }

    DymnTile {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Dymn Notes",
                    color = DymnText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = notesInFocus(notesCount),
                    color = DymnMuted,
                    fontSize = 14.sp,
                )
            }
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DymnBlue),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("+", color = DymnText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                containerColor = DymnTileFill,
            ) {
                DropdownMenuItem(
                    text = { Text(t("Нотатка", "Note"), color = DymnText) },
                    onClick = {
                        isMenuOpen = false
                        onAddClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(t("Список", "List"), color = DymnText) },
                    onClick = {
                        isMenuOpen = false
                        onAddClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun HeaderTileStable(notesCount: Int) {
    DymnTile {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Dymn Notes",
                    color = DymnText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = notesInFocus(notesCount),
                    color = DymnMuted,
                    fontSize = 14.sp,
                )
            }
            Button(
                onClick = { },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.22f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("⚙", color = DymnText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeaderTileStable2(notesCount: Int, onSettingsClick: () -> Unit) {
    DymnTile {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Dymn Notes",
                    color = DymnText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = notesInFocus(notesCount),
                    color = DymnMuted,
                    fontSize = 14.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = t("Налаштування", "Settings"),
                    tint = DymnMuted.copy(alpha = 0.7f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsGearIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val gearColor = Color(0xFFC2C8D2)
        val outer = size.minDimension * 0.33f
        val inner = size.minDimension * 0.13f
        val toothStart = size.minDimension * 0.29f
        val toothEnd = size.minDimension * 0.45f

        repeat(8) { index ->
            val angle = (PI * 2.0 * index / 8.0).toFloat()
            drawLine(
                color = gearColor,
                start = Offset(center.x + cos(angle) * toothStart, center.y + sin(angle) * toothStart),
                end = Offset(center.x + cos(angle) * toothEnd, center.y + sin(angle) * toothEnd),
                strokeWidth = 5.2f,
                cap = StrokeCap.Square,
            )
        }
        drawCircle(
            color = gearColor,
            radius = outer,
            center = center,
        )
        drawCircle(
            color = DymnTileFill,
            radius = inner,
            center = center,
        )
    }
}

@Composable
private fun AddMoreTile(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .border(0.6.dp, DymnLightBorder.copy(alpha = 0.24f), RoundedCornerShape(26.dp))
            .clickable(onClick = onAddClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(DymnBlue.copy(alpha = 0.82f))
                    .border(0.5.dp, DymnLightBorder.copy(alpha = 0.42f), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = DymnText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(t("Додати", "Add"), color = DymnText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchTile(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: NoteFilter,
    onFilterChange: (NoteFilter) -> Unit,
) {
    DymnTile {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(t("Пошук", "Search")) },
            placeholder = { Text(t("текст, тег або задача", "text, tag, or task")) },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoteFilter.entries.forEach { item ->
                FilterChip(
                    title = item.title,
                    selected = item == filter,
                    onClick = { onFilterChange(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterChip(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) DymnBlue.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.2f))
            .border(
                width = 0.6.dp,
                color = if (selected) DymnLightBorder else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = DymnText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoteTile(
    note: Note,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onSave: (Note) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onUpdate: (Note) -> Unit,
) {
    val tileFill = LocalTileFill.current
    DymnTile(
        fill = if (note.pinned && !isEditing) tileFill.copy(alpha = (tileFill.alpha + 0.1f).coerceAtMost(1f)) else tileFill,
        modifier = Modifier.clickable(enabled = !isEditing, onClick = onEdit)
    ) {
        if (isEditing) {
            var title by remember(note.id) { mutableStateOf(note.title) }
            var body by remember(note.id) { mutableStateOf(if (note.kind == NoteKind.List) "" else note.body) }
            var todoItems by remember(note.id) { mutableStateOf(parseTodoItems(note.body)) }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(t("Назва", "Title")) },
                )
                if (note.kind == NoteKind.List) {
                    TodoListEditor(
                        items = todoItems,
                        onItemsChange = { todoItems = it },
                    )
                } else {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 300.dp),
                        label = { Text(t("Текст", "Text")) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDelete) {
                        Text(t("Видалити", "Delete"), color = DymnRed)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) {
                        Text(t("Скасувати", "Cancel"), color = DymnMuted)
                    }
                    Button(
                        onClick = {
                            onSave(note.copy(
                                title = title.trim(),
                                body = if (note.kind == NoteKind.List) serializeTodoItems(todoItems) else body.trim(),
                                updatedAt = System.currentTimeMillis()
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DymnCyan),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(t("Готово", "Done"), color = DymnText)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (note.pinned) {
                            Text("★", color = DymnYellow, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = note.title.ifBlank { t("Без назви", "Untitled") },
                            color = DymnText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (note.kind == NoteKind.List) {
                        TodoPreviewList(
                            note = note,
                            onUpdate = onUpdate,
                        )
                    } else {
                        Text(
                            text = note.body.ifBlank { t("Порожня нотатка", "Empty note") },
                            color = DymnMuted,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NoteMetaPill(if (note.kind == NoteKind.List) t("Список", "List") else t("Нотатка", "Note"), DymnBlue)
                        NoteMetaPill(relativeTime(note.updatedAt), DymnCyan)
                        if (todoTotal(note.body) > 0) {
                            NoteMetaPill("${todoDone(note.body)}/${todoTotal(note.body)}", DymnYellow)
                        }
                        Spacer(Modifier.weight(1f))
                        NoteMetaPill(t("Редагувати", "Edit"), DymnCyan, onClick = onEdit)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 8.dp)) {
                    SmallAction(text = if (note.pinned) "★" else "☆", color = DymnYellow, onClick = onPin)
                    ArchiveAction(archived = note.archived, onClick = onArchive)
                }
            }
        }
    }
}

@Composable
private fun TodoPreviewList(
    note: Note,
    onUpdate: (Note) -> Unit,
) {
    val items = parseTodoItems(note.body).filter { it.text.isNotBlank() }
    if (items.isEmpty()) {
        Text(
            text = t("Порожній список", "Empty list"),
            color = DymnMuted,
            fontSize = 14.sp,
            maxLines = 1,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            TodoPreviewRow(
                item = item,
                onToggle = {
                    val next = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                    onUpdate(note.copy(body = serializeTodoItems(next)))
                },
                onDelete = {
                    val next = items.filterNot { it.id == item.id }
                    onUpdate(note.copy(body = serializeTodoItems(next)))
                },
            )
        }
    }
}

@Composable
private fun TodoPreviewRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (item.done) DymnGreen.copy(alpha = 0.78f) else Color.Transparent)
                .border(1.dp, DymnLightBorder.copy(alpha = 0.58f), RoundedCornerShape(11.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) {
                Text("✓", color = DymnText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.text,
            modifier = Modifier.weight(1f),
            color = if (item.done) DymnMuted.copy(alpha = 0.62f) else DymnMuted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
        )
        if (item.done) {
            Text(
                text = "×",
                color = DymnRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 7.dp),
            )
        }
    }
}

@Composable
private fun ArchiveAction(archived: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DymnCyan.copy(alpha = 0.78f))
            .border(0.5.dp, DymnLightBorder.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(DymnText.copy(alpha = if (archived) 0.96f else 0.78f))
            )
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .border(1.4.dp, DymnText.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun NoteMetaPill(text: String, color: Color, onClick: (() -> Unit)? = null) {
    val modifier = Modifier
        .clip(RoundedCornerShape(14.dp))
        .background(color.copy(alpha = 0.22f))
        .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 10.dp, vertical = 6.dp)

    Box(
        modifier = modifier
    ) {
        Text(text = text, color = DymnText, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SmallAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.78f))
            .border(0.5.dp, DymnLightBorder.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = DymnText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyTile(onAdd: (NoteKind) -> Unit) {
    var isMenuOpen by remember { mutableStateOf(false) }

    DymnTile {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(t("Немає нотаток", "No notes"), color = DymnText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(t("Створи першу думку, список або швидку ідею.", "Create your first thought, list, or quick idea."), color = DymnMuted)
            Spacer(Modifier.height(16.dp))
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                containerColor = DymnTileFill,
            ) {
                DropdownMenuItem(
                    text = { Text(t("Нотатка", "Note"), color = DymnText) },
                    onClick = {
                        isMenuOpen = false
                        onAdd(NoteKind.Note)
                    },
                )
                DropdownMenuItem(
                    text = { Text(t("Список", "List"), color = DymnText) },
                    onClick = {
                        isMenuOpen = false
                        onAdd(NoteKind.List)
                    },
                )
            }
            Button(
                onClick = { isMenuOpen = true },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DymnBlue),
            ) {
                Text(t("Нова нотатка", "New note"))
            }
        }
    }
}

@Composable
private fun EmptyTileStable(onAddClick: () -> Unit) {
    DymnTile {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(t("Немає нотаток", "No notes"), color = DymnText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(t("Створи першу думку, список або швидку ідею.", "Create your first thought, list, or quick idea."), color = DymnMuted)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DymnBlue),
            ) {
                Text(t("Створити", "Create"))
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) DymnBlue.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.18f))
            .border(
                0.6.dp,
                if (selected) DymnLightBorder.copy(alpha = 0.68f) else DymnLightBorder.copy(alpha = 0.18f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DymnCyan.copy(alpha = if (selected) 0.76f else 0.36f))
                .border(0.5.dp, DymnLightBorder.copy(alpha = 0.34f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = DymnText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = DymnText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = DymnMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TileColorOptionRow(
    option: TileColorOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) DymnBlue.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.18f))
            .border(
                0.6.dp,
                if (selected) DymnLightBorder.copy(alpha = 0.68f) else DymnLightBorder.copy(alpha = 0.18f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(option.color.copy(alpha = 0.92f))
                .border(0.5.dp, DymnLightBorder.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(12.dp))
        Text(option.title, color = DymnText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CreateTypeDialog(
    onDismiss: () -> Unit,
    onSelect: (NoteKind) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DymnTileFill,
        shape = RoundedCornerShape(32.dp),
        title = {
            Text(
                text = t("Що створити?", "What to create?"),
                color = DymnText,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CreateTypeAction(
                    title = t("Нотатка", "Note"),
                    subtitle = t("Назва і вільний текст", "Title and free text"),
                    color = DymnCyan,
                    onClick = { onSelect(NoteKind.Note) },
                )
                CreateTypeAction(
                    title = t("Список", "List"),
                    subtitle = t("Назва і пункти з чекбоксами", "Title and checkbox items"),
                    color = DymnGreen,
                    onClick = { onSelect(NoteKind.List) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Скасувати", "Cancel"), color = DymnMuted)
            }
        },
    )
}

@Composable
private fun CreateTypeAction(
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(0.6.dp, DymnLightBorder.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.78f))
                .border(0.5.dp, DymnLightBorder.copy(alpha = 0.42f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (title == t("Список", "List")) "☑" else "✎", color = DymnText, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = DymnText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DymnMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AppSettingsDialog(
    tileOpacity: Float,
    wallpaperKey: String,
    customWallpaperUri: String,
    tileColorKey: String,
    onTileOpacityChange: (Float) -> Unit,
    onWallpaperChange: (String) -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onClearCustomWallpaper: () -> Unit,
    onTileColorChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalTileFill.current.copy(alpha = 0.95f),
        shape = RoundedCornerShape(32.dp),
        title = {
            Text(t("Налаштування", "Settings"), color = DymnText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Фонові шпалери", "Wallpaper"), color = DymnMuted, fontSize = 13.sp)
                    WallpaperOptions.forEach { option ->
                        WallpaperOptionRow(
                            option = option,
                            selected = option.key == wallpaperKey && customWallpaperUri.isBlank(),
                            onClick = { onWallpaperChange(option.key) },
                        )
                    }
                    SettingsActionRow(
                        title = if (customWallpaperUri.isBlank()) t("Обрати свою шпалеру", "Choose custom wallpaper") else t("Своя шпалера вибрана", "Custom wallpaper selected"),
                        subtitle = if (customWallpaperUri.isBlank()) t("Зображення з телефону", "Image from your phone") else t("Натисни, щоб замінити", "Tap to replace"),
                        selected = customWallpaperUri.isNotBlank(),
                        onClick = onPickCustomWallpaper,
                    )
                    if (customWallpaperUri.isNotBlank()) {
                        SettingsActionRow(
                            title = t("Прибрати свою шпалеру", "Remove custom wallpaper"),
                            subtitle = t("Повернутись до стандартного фону", "Return to the default background"),
                            selected = false,
                            onClick = onClearCustomWallpaper,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Колір плиток", "Tile color"), color = DymnMuted, fontSize = 13.sp)
                    TileColorOptions.forEach { option ->
                        TileColorOptionRow(
                            option = option,
                            selected = option.key == tileColorKey,
                            onClick = { onTileColorChange(option.key) },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("Прозорість плиток", "Tile opacity"), color = DymnMuted, fontSize = 13.sp)
                        Text("${(tileOpacity * 100).toInt()}%", color = DymnText, fontSize = 13.sp)
                    }
                    Slider(
                        value = tileOpacity,
                        onValueChange = onTileOpacityChange,
                        valueRange = 0.35f..0.95f,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Готово", "Done"), color = DymnCyan, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun WallpaperOptionRow(
    option: WallpaperOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) DymnBlue.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.18f))
            .border(
                0.6.dp,
                if (selected) DymnLightBorder.copy(alpha = 0.72f) else DymnLightBorder.copy(alpha = 0.18f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(option.previewBrush)
                .border(0.5.dp, DymnLightBorder.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(12.dp))
        Text(option.title, color = DymnText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NoteEditorDialogFinal(
    initial: Note,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var body by remember(initial.id) { mutableStateOf(if (initial.kind == NoteKind.List) "" else initial.body) }
    var todoItems by remember(initial.id) { mutableStateOf(parseTodoItems(initial.body)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalTileFill.current,
        shape = RoundedCornerShape(32.dp),
        title = {
            Text(
                text = when {
                    initial.title.isNotBlank() -> t("Редагувати", "Edit")
                    initial.kind == NoteKind.List -> t("Новий список", "New list")
                    else -> t("Нова нотатка", "New note")
                },
                color = DymnText,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(t("Назва", "Title")) },
                )
                if (initial.kind == NoteKind.List) {
                    TodoListEditor(
                        items = todoItems,
                        onItemsChange = { todoItems = it },
                    )
                    Text(
                        text = t("Задачі", "Tasks") + " ${todoItems.count { it.done }}/${todoItems.size}",
                        color = DymnMuted,
                        fontSize = 12.sp,
                    )
                } else {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        label = { Text(t("Текст", "Text")) },
                        placeholder = { Text(t("Текст нотатки", "Note text")) },
                    )
                    Text(
                        text = wordCountLabel(wordCount(body)),
                        color = DymnMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            body = if (initial.kind == NoteKind.List) serializeTodoItems(todoItems) else body.trim(),
                            tags = initial.tags,
                        )
                    )
                }
            ) {
                Text(t("Зберегти", "Save"), color = DymnCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(t("Видалити", "Delete"), color = DymnRed, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun TodoListEditor(
    items: List<TodoItem>,
    onItemsChange: (List<TodoItem>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .border(0.6.dp, DymnLightBorder.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            TodoItemRow(
                item = item,
                onToggle = {
                    onItemsChange(items.map { if (it.id == item.id) it.copy(done = !it.done) else it })
                },
                onTextChange = { text ->
                    onItemsChange(items.map { if (it.id == item.id) it.copy(text = text) else it })
                },
                onDelete = {
                    val next = items.filterNot { it.id == item.id }
                    onItemsChange(next.ifEmpty { listOf(TodoItem()) })
                },
            )
        }
        TextButton(
            onClick = { onItemsChange(items + TodoItem()) },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(t("+ Додати пункт", "+ Add item"), color = DymnCyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TodoItemRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (item.done) DymnGreen.copy(alpha = 0.78f) else Color.Transparent)
                .border(1.dp, DymnLightBorder.copy(alpha = 0.62f), RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) {
                Text("✓", color = DymnText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = item.text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = DymnText,
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            placeholder = { Text(t("Пункт списку", "List item")) },
        )
        Spacer(Modifier.width(8.dp))
        if (item.done) {
            Text(
                text = "×",
                color = DymnRed,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DymnTile(
    modifier: Modifier = Modifier,
    fill: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tileFill = fill ?: LocalTileFill.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(tileFill)
            .border(0.6.dp, DymnLightBorder.copy(alpha = 0.72f), RoundedCornerShape(32.dp))
            .padding(18.dp),
        content = content,
    )
}

private fun loadNotes(context: Context): List<Note> {
    val raw = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).getString(NotesKey, null)
    if (raw.isNullOrBlank()) return sampleNotes()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Note(
                id = item.optString("id"),
                title = item.optString("title"),
                body = item.optString("body"),
                tags = item.optString("tags"),
                kind = NoteKind.fromStorage(item.optString("kind")),
                pinned = item.optBoolean("pinned"),
                archived = item.optBoolean("archived"),
                createdAt = item.optLong("createdAt"),
                updatedAt = item.optLong("updatedAt"),
            )
        }.filter { it.id.isNotBlank() }
    }.getOrElse { sampleNotes() }
}

private fun saveNotes(context: Context, notes: List<Note>) {
    val array = JSONArray()
    notes.forEach { note ->
        array.put(
            JSONObject()
                .put("id", note.id)
                .put("title", note.title)
                .put("body", note.body)
                .put("tags", note.tags)
                .put("kind", note.kind.storageKey)
                .put("pinned", note.pinned)
                .put("archived", note.archived)
                .put("createdAt", note.createdAt)
                .put("updatedAt", note.updatedAt)
        )
    }
    context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit {
        putString(NotesKey, array.toString())
    }

    // Refresh Widget
    val intent = Intent(context, NoteWidget::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    }
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidget::class.java))
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    context.sendBroadcast(intent)
}

private fun loadTileOpacity(context: Context): Float {
    return context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        .getFloat(TileOpacityKey, 0.82f)
        .coerceIn(0.35f, 0.95f)
}

private fun saveTileOpacity(context: Context, opacity: Float) {
    context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit {
        putFloat(TileOpacityKey, opacity.coerceIn(0.35f, 0.95f))
    }
}

private fun loadWallpaperKey(context: Context): String {
    val saved = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        .getString(WallpaperKey, DefaultWallpaperKey)
        .orEmpty()
    return saved.takeIf { key -> WallpaperOptions.any { it.key == key } } ?: DefaultWallpaperKey
}

private fun saveWallpaperKey(context: Context, key: String) {
    context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit {
        putString(WallpaperKey, key.takeIf { value -> WallpaperOptions.any { it.key == value } } ?: DefaultWallpaperKey)
    }
}

private fun wallpaperOptionFor(key: String): WallpaperOption {
    return WallpaperOptions.firstOrNull { it.key == key } ?: WallpaperOptions.first()
}

private fun loadCustomWallpaperUri(context: Context): String {
    return context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        .getString(CustomWallpaperUriKey, "")
        .orEmpty()
}

private fun saveCustomWallpaperUri(context: Context, uri: String) {
    context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit {
        putString(CustomWallpaperUriKey, uri)
    }
}

private fun loadTileColorKey(context: Context): String {
    val saved = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        .getString(TileColorKey, DefaultTileColorKey)
        .orEmpty()
    return saved.takeIf { key -> TileColorOptions.any { it.key == key } } ?: DefaultTileColorKey
}

private fun saveTileColorKey(context: Context, key: String) {
    context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit {
        putString(TileColorKey, key.takeIf { value -> TileColorOptions.any { it.key == value } } ?: DefaultTileColorKey)
    }
}

private fun tileColorFor(key: String): Color {
    return TileColorOptions.firstOrNull { it.key == key }?.color ?: DymnGraphite
}

private fun sampleNotes(): List<Note> {
    val now = System.currentTimeMillis()
    return listOf(
        Note(
            id = UUID.randomUUID().toString(),
            title = t("План на сьогодні", "Plan for today"),
            body = t("- [ ] Записати головну ідею\n- [x] Перевірити нотатки\n- [ ] Виділити 20 хв на фокус", "- [ ] Capture the main idea\n- [x] Review notes\n- [ ] Set aside 20 minutes for focus"),
            tags = t("план, фокус", "plan, focus"),
            kind = NoteKind.List,
            pinned = true,
            archived = false,
            createdAt = now - 86_400_000,
            updatedAt = now - 3_600_000,
        ),
        Note(
            id = UUID.randomUUID().toString(),
            title = t("Ідеї для Dymn", "Ideas for Dymn"),
            body = t("Плитки, темний фон, швидкі дії, мінімум зайвого шуму. Нотатки мають відкриватися миттєво.", "Tiles, dark background, quick actions, and minimal noise. Notes should open instantly."),
            tags = t("ідеї, продукт", "ideas, product"),
            kind = NoteKind.Note,
            pinned = false,
            archived = false,
            createdAt = now - 172_800_000,
            updatedAt = now - 7_200_000,
        ),
    )
}

private fun parseTodoItems(body: String): List<TodoItem> {
    val items = body
        .lines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("- [x]", ignoreCase = true) -> {
                    TodoItem(text = trimmed.removePrefix("- [x]").removePrefix("- [X]").trim(), done = true)
                }
                trimmed.startsWith("- [ ]") -> {
                    TodoItem(text = trimmed.removePrefix("- [ ]").trim(), done = false)
                }
                trimmed.isNotBlank() -> TodoItem(text = trimmed, done = false)
                else -> null
            }
        }
    return items.ifEmpty { listOf(TodoItem()) }
}

private fun serializeTodoItems(items: List<TodoItem>): String {
    return items
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { item ->
            val marker = if (item.done) "- [x]" else "- [ ]"
            "$marker ${item.text.trim()}"
        }
}

private fun t(uk: String, en: String): String = if (Locale.getDefault().language.equals("en", ignoreCase = true)) en else uk

private fun notesInFocus(count: Int): String = t("$count нотаток у фокусі", "$count notes in focus")

private fun wordCountLabel(count: Int): String = t("$count слів", if (count == 1) "1 word" else "$count words")

private fun wordCount(text: String): Int = Regex("\\S+").findAll(text.trim()).count()

private fun todoTotal(text: String): Int = Regex("- \\[[ xX]]").findAll(text).count()

private fun todoDone(text: String): Int = Regex("- \\[[xX]]").findAll(text).count()

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minute = 60_000L
    val hour = minute * 60
    val day = hour * 24
    return when {
        diff < minute -> t("щойно", "just now")
        diff < hour -> t("${diff / minute} хв", "${diff / minute} min")
        diff < day -> t("${diff / hour} год", "${diff / hour} hr")
        else -> t("${diff / day} дн", "${diff / day} d")
    }
}

private data class Note(
    val id: String,
    val title: String,
    val body: String,
    val tags: String,
    val kind: NoteKind,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val done: Boolean = false,
)

private enum class NoteKind(val storageKey: String) {
    Note("note"),
    List("list");

    companion object {
        fun fromStorage(value: String): NoteKind = entries.firstOrNull { it.storageKey == value } ?: Note
    }
}

private enum class NoteFilter(private val ukTitle: String, private val enTitle: String) {
    Active("Усі", "All"),
    Pinned("★", "★"),
    Tasks("Задачі", "Tasks"),
    Archive("Архів", "Archive");

    val title: String get() = t(ukTitle, enTitle)
}

private const val PrefsName = "dymn_notes"
private const val NotesKey = "notes"
private const val TileOpacityKey = "tile_opacity"
private const val WallpaperKey = "wallpaper_key"
private const val DefaultWallpaperKey = "blue"
private const val CustomWallpaperUriKey = "custom_wallpaper_uri"
private const val TileColorKey = "tile_color_key"
private const val DefaultTileColorKey = "graphite"

private val DymnBlack = Color(0xFF0B0B10)
private val DymnText = Color(0xFFF7F7FA)
private val DymnMuted = Color(0xFFD1D1D6)
private val DymnGraphite = Color(0xFF77777C)
private val DymnBlue = Color(0xFF0A84FF)
private val DymnCyan = Color(0xFF32ADE6)
private val DymnGreen = Color(0xFF30D158)
private val DymnYellow = Color(0xFFFF9F0A)
private val DymnRed = Color(0xFFFF453A)
private val DymnTileFill = DymnGraphite.copy(alpha = 0.82f)
private val DymnLightBorder = Color.White
private val LocalTileFill = staticCompositionLocalOf { DymnTileFill }

private data class WallpaperOption(
    val key: String,
    val title: String,
    val brush: Brush,
    val previewBrush: Brush,
)

private data class TileColorOption(
    val key: String,
    val title: String,
    val color: Color,
)

private val TileColorOptions = listOf(
    TileColorOption("graphite", t("Графіт", "Graphite"), DymnGraphite),
    TileColorOption("charcoal", t("Вугілля", "Charcoal"), Color(0xFF34343A)),
    TileColorOption("mist", t("Туман", "Mist"), Color(0xFFA2A2A6)),
    TileColorOption("stone", t("Камінь", "Stone"), Color(0xFF5F625F)),
    TileColorOption("blue", "Dymn Blue", DymnBlue),
    TileColorOption("cyan", "Cyan", DymnCyan),
)

private val WallpaperOptions = listOf(
    WallpaperOption(
        key = "blue",
        title = "Dymn Blue",
        brush = Brush.radialGradient(
            colors = listOf(DymnBlue.copy(alpha = 0.45f), DymnBlack),
            radius = 2800f,
        ),
        previewBrush = Brush.radialGradient(
            colors = listOf(DymnBlue.copy(alpha = 0.6f), DymnBlack),
            radius = 120f,
        ),
    ),
    WallpaperOption(
        key = "midnight",
        title = "Midnight",
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF050508), Color(0xFF1B1E2B), Color(0xFF0A1829)),
        ),
        previewBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF050508), Color(0xFF1B1E2B), Color(0xFF0A1829)),
        ),
    ),
    WallpaperOption(
        key = "cyan",
        title = "Cyan Mist",
        brush = Brush.radialGradient(
            colors = listOf(DymnCyan.copy(alpha = 0.38f), Color(0xFF080B12), DymnBlack),
            radius = 2600f,
        ),
        previewBrush = Brush.radialGradient(
            colors = listOf(DymnCyan.copy(alpha = 0.5f), Color(0xFF080B12), DymnBlack),
            radius = 120f,
        ),
    ),
    WallpaperOption(
        key = "green",
        title = "Soft Green",
        brush = Brush.radialGradient(
            colors = listOf(DymnGreen.copy(alpha = 0.32f), Color(0xFF10120F), DymnBlack),
            radius = 2600f,
        ),
        previewBrush = Brush.radialGradient(
            colors = listOf(DymnGreen.copy(alpha = 0.45f), Color(0xFF10120F), DymnBlack),
            radius = 120f,
        ),
    ),
)
