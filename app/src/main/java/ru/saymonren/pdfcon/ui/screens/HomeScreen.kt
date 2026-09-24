package ru.saymonren.pdfcon.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import ru.saymonren.pdfcon.core.image.ImageFx
import ru.saymonren.pdfcon.model.FxPreset
import ru.saymonren.pdfcon.model.ThemeMode
import ru.saymonren.pdfcon.ui.PdfViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: PdfViewModel,
    onEdit: (String) -> Unit,
    onBuild: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val pages by vm.pages.collectAsState()
    val theme by vm.theme.collectAsState()
    var themeMenu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris -> vm.addPhotos(uris) }

    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) pendingPhoto?.let { vm.addPhotos(listOf(it)) }
        pendingPhoto = null
    }
    fun takePhoto() {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        camera.launch(uri)
    }
    fun pickGallery() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Перетаскивание — библиотека Reorderable (long-press по всей карточке)
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        vm.moveIndices(from.index, to.index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фото в PDF") },
                actions = {
                    if (pages.isNotEmpty()) {
                        OutlinedButton(onClick = { vm.clear() }) { Text("Очистить") }
                    }
                    IconButton(onClick = { themeMenu = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                    DropdownMenu(
                        expanded = themeMenu,
                        onDismissRequest = { themeMenu = false }
                    ) {
                        Text(
                            "Тема",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        ThemeOption("Как в системе", ThemeMode.SYSTEM, theme) {
                            vm.setTheme(it); themeMenu = false
                        }
                        ThemeOption("Светлая", ThemeMode.LIGHT, theme) {
                            vm.setTheme(it); themeMenu = false
                        }
                        ThemeOption("Тёмная", ThemeMode.DARK, theme) {
                            vm.setTheme(it); themeMenu = false
                        }
                        DropdownMenuItem(
                            text = { Text("Политика конфиденциальности") },
                            onClick = {
                                themeMenu = false
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(
                                                ru.saymonren.pdfcon.core.AppLinks.PRIVACY_POLICY
                                            )
                                        )
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(onClick = ::takePhoto) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Сфотографировать")
                }
                FloatingActionButton(onClick = ::pickGallery) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить фото")
                }
            }
        },
        bottomBar = {
            if (pages.isNotEmpty()) {
                Column {
                    Text(
                        "Долгое нажатие — тянуть чтобы менять порядок",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Button(
                        onClick = onBuild,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    ) { Text("Собрать PDF (${pages.size})") }
                }
            }
        }
    ) { pad ->
        if (pages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Фото из галереи или сразу с камеры,", style = MaterialTheme.typography.titleMedium)
                Text("нажмите «Собрать» — получите один PDF.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = ::pickGallery) { Text("Выбрать фото") }
                    OutlinedButton(onClick = ::takePhoto) { Text("Сфотографировать") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                    ReorderableItem(reorderState, key = page.id) { isDragging ->
                        PageCard(
                            page = page,
                            index = index,
                            isDragging = isDragging,
                            onEdit = { onEdit(page.id) },
                            onRemove = { vm.remove(page.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Отдельный компонент: при перестановке Compose пропускает карточки
 * с неизменившимися данными, фильтр считается один раз на страницу.
 */
@Composable
private fun sh.calvin.reorderable.ReorderableCollectionItemScope.PageCard(
    page: ru.saymonren.pdfcon.model.PageItem,
    index: Int,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    // Плавное поднятие/опускание вместо резкого скачка
    val elevation by animateDpAsState(
        if (isDragging) 8.dp else 1.dp, label = "dragElevation"
    )
    val scale by animateFloatAsState(
        if (isDragging) 1.05f else 1f, label = "dragScale"
    )
    val filter = remember(page) {
        ImageFx.combinedCompose(page)?.let { ColorFilter.colorMatrix(it) }
    }
    Card(
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        ),
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .longPressDraggableHandle(
                onDragStarted = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragStopped = {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
            )
    ) {
        Column(Modifier.padding(8.dp)) {
            AsyncImage(
                model = page.uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .rotate(page.rotationDeg.toFloat()),
                contentScale = ContentScale.Crop,
                colorFilter = filter
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}" + when {
                        page.cropRect != null -> " ✂"
                        page.scanQuad != null -> " ▣"
                        page.fx != FxPreset.ORIGINAL -> " ✦"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Удалить")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { RadioButton(selected = current == mode, onClick = { onPick(mode) }) },
        onClick = { onPick(mode) }
    )
}
