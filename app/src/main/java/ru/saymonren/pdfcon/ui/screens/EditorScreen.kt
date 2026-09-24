package ru.saymonren.pdfcon.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.saymonren.pdfcon.core.image.ImageFx
import ru.saymonren.pdfcon.core.image.ImageFx.title
import ru.saymonren.pdfcon.model.CropMode
import ru.saymonren.pdfcon.model.CropRect
import ru.saymonren.pdfcon.model.FxPreset
import ru.saymonren.pdfcon.ui.PdfViewModel
import ru.saymonren.pdfcon.ui.components.CropOverlay
import ru.saymonren.pdfcon.ui.components.PagePreview
import ru.saymonren.pdfcon.ui.components.ScanOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: PdfViewModel,
    pageId: String,
    onBack: () -> Unit
) {
    val pages by vm.pages.collectAsState()
    val page = pages.firstOrNull { it.id == pageId }

    var cropMode by remember(pageId) { mutableStateOf(false) }
    var draftRect by remember(pageId) { mutableStateOf<CropRect?>(null) }
    var draftAspect by remember(pageId) { mutableStateOf(CropMode.ORIGINAL) }

    var scanMode by remember(pageId) { mutableStateOf(false) }
    var draftQuad by remember(pageId) { mutableStateOf<ru.saymonren.pdfcon.model.ScanQuad?>(null) }
    var draftBoost by remember(pageId) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            scanMode -> "Скан"
                            cropMode -> "Обрезка"
                            else -> "Редактор"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            scanMode -> scanMode = false
                            cropMode -> cropMode = false
                            else -> onBack()
                        }
                    }) {
                        Icon(
                            if (scanMode || cropMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    if (!cropMode && !scanMode) {
                        IconButton(onClick = {
                            vm.updatePage(pageId) { it.copy(rotationDeg = (it.rotationDeg + 90) % 360) }
                        }) { Icon(Icons.Default.RotateRight, contentDescription = "Повернуть") }
                    }
                }
            )
        }
    ) { pad ->
        if (page == null) {
            Column(Modifier.fillMaxSize().padding(pad), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Фото удалено")
                Button(onClick = onBack) { Text("Назад") }
            }
            return@Scaffold
        }

        // ---------- РЕЖИМ ОБРЕЗКИ: рамка прямо на фото ----------
        if (cropMode) {
            Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
                Text(
                    "Выдели рамкой то, что оставить. Углы — тянуть, середину — двигать.",
                    style = MaterialTheme.typography.bodyMedium
                )
                CropOverlay(
                    uri = page.uri,
                    initial = draftRect,
                    aspect = draftAspect,
                    onChange = { draftRect = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = draftAspect == CropMode.ORIGINAL,
                        onClick = { draftAspect = CropMode.ORIGINAL },
                        label = { Text("Свободно") }
                    )
                    FilterChip(
                        selected = draftAspect == CropMode.A4_PORTRAIT,
                        onClick = { draftAspect = CropMode.A4_PORTRAIT; draftRect = null },
                        label = { Text("A4") }
                    )
                    FilterChip(
                        selected = draftAspect == CropMode.SQUARE,
                        onClick = { draftAspect = CropMode.SQUARE; draftRect = null },
                        label = { Text("Квадрат") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { cropMode = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Отмена") }
                    Button(
                        onClick = {
                            val r = draftRect
                            val final = if ((r == null || r.isFull) && draftAspect == CropMode.ORIGINAL) null else r
                            vm.updatePage(pageId) {
                                it.copy(
                                    cropRect = final, crop = draftAspect,
                                    scanQuad = null, scanBoost = false
                                )
                            }
                            cropMode = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Обрезать")
                    }
                }
            }
            return@Scaffold
        }

        // ---------- РЕЖИМ СКАНА: 4 угла на углы документа ----------
        if (scanMode) {
            Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
                Text(
                    "Потяни 4 кружка на углы документа — перспектива выпрямится.",
                    style = MaterialTheme.typography.bodyMedium
                )
                ScanOverlay(
                    uri = page.uri,
                    initial = draftQuad,
                    onChange = { draftQuad = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Усилить как скан")
                    Switch(
                        checked = draftBoost,
                        onCheckedChange = { draftBoost = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { scanMode = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Отмена") }
                    Button(
                        onClick = {
                            vm.updatePage(pageId) {
                                it.copy(
                                    scanQuad = draftQuad ?: ru.saymonren.pdfcon.model.ScanQuad(),
                                    scanBoost = draftBoost,
                                    cropRect = null, crop = CropMode.ORIGINAL
                                )
                            }
                            scanMode = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сканировать")
                    }
                }
            }
            return@Scaffold
        }

        // ---------- ОБЫЧНЫЙ РЕЖИМ ----------
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Живое превью в пропорциях кадра. Так будет в PDF.
            PagePreview(
                page = page,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draftRect = page.cropRect
                        draftAspect = page.crop
                        cropMode = true
                    },
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (page.cropRect != null || page.crop != CropMode.ORIGINAL) "Рамка ✓"
                        else "Обрезка"
                    )
                }
                OutlinedButton(
                    onClick = {
                        draftQuad = page.scanQuad
                        draftBoost = page.scanBoost
                        scanMode = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (page.scanQuad != null) "Скан ✓" else "Скан")
                }
            }

            Text("Эффект", style = MaterialTheme.typography.titleSmall)
            val presets = remember { FxPreset.entries }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(presets, key = { it.name }) { fx ->
                    val selected = page.fx == fx
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                vm.updatePage(pageId) { p -> p.copy(fx = fx) }
                            }
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(4.dp)
                    ) {
                        val m = remember(fx) {
                            ImageFx.effectMatrix(fx)?.let {
                                androidx.compose.ui.graphics.ColorMatrix(it.array)
                            }
                        }
                        AsyncImage(
                            model = page.uri,
                            contentDescription = fx.title,
                            modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop,
                            colorFilter = m?.let { ColorFilter.colorMatrix(it) }
                        )
                        Text(fx.title, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            SliderRow("Яркость", page.brightness, -0.5f..0.5f) {
                vm.updatePage(pageId) { p -> p.copy(brightness = it) }
            }
            SliderRow("Контраст", page.contrast, 0.5f..1.5f) {
                vm.updatePage(pageId) { p -> p.copy(contrast = it) }
            }
            SliderRow("Насыщенность", page.saturation, 0f..2f) {
                vm.updatePage(pageId) { p -> p.copy(saturation = it) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Виньетка (затемнить углы)")
                Switch(
                    checked = page.vignette,
                    onCheckedChange = { vm.updatePage(pageId) { p -> p.copy(vignette = it) } }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Магия (свечение)")
                Switch(
                    checked = page.sparkles,
                    onCheckedChange = { vm.updatePage(pageId) { p -> p.copy(sparkles = it) } }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.updatePage(pageId) {
                            it.copy(
                                brightness = 0f, contrast = 1f, saturation = 1f,
                                fx = FxPreset.ORIGINAL, vignette = false, sparkles = false,
                                rotationDeg = 0, crop = CropMode.ORIGINAL, cropRect = null,
                                scanQuad = null, scanBoost = false
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Сбросить") }
                Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Готово") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit
) {
    Column {
        Text("$label: ${"%.2f".format(value)}")
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}
