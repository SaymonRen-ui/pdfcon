package ru.saymonren.pdfcon.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.saymonren.pdfcon.core.pdf.PdfBuilder
import ru.saymonren.pdfcon.model.Orientation
import ru.saymonren.pdfcon.model.PageSize
import ru.saymonren.pdfcon.ui.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    vm: PdfViewModel,
    onBack: () -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages by vm.pages.collectAsState()
    val settings by vm.settings.collectAsState()
    val build by vm.build.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сборка PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Страниц: ${pages.size}", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(
                onClick = onPreview,
                enabled = pages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Text("  Предпросмотр — как будет в PDF")
            }

            OutlinedTextField(
                value = settings.fileName,
                onValueChange = { vm.updateSettings { s -> s.copy(fileName = it) } },
                label = { Text("Имя файла") },
                placeholder = { Text(PdfBuilder.resolveFileName(settings.copy(fileName = ""))) },
                supportingText = { Text("Пусто — назову по дате и времени") },
                suffix = { Text(".pdf") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Размер страницы")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.pageSize == PageSize.A4,
                    onClick = { vm.updateSettings { it.copy(pageSize = PageSize.A4) } },
                    label = { Text("A4") }
                )
                FilterChip(
                    selected = settings.pageSize == PageSize.ORIGINAL,
                    onClick = { vm.updateSettings { it.copy(pageSize = PageSize.ORIGINAL) } },
                    label = { Text("По фото") }
                )
            }

            if (settings.pageSize == PageSize.A4) {
                Text("Ориентация")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.orientation == Orientation.PORTRAIT,
                        onClick = { vm.updateSettings { it.copy(orientation = Orientation.PORTRAIT) } },
                        label = { Text("Книжная") }
                    )
                    FilterChip(
                        selected = settings.orientation == Orientation.LANDSCAPE,
                        onClick = { vm.updateSettings { it.copy(orientation = Orientation.LANDSCAPE) } },
                        label = { Text("Альбомная") }
                    )
                    FilterChip(
                        selected = settings.orientation == Orientation.AUTO,
                        onClick = { vm.updateSettings { it.copy(orientation = Orientation.AUTO) } },
                        label = { Text("Авто") }
                    )
                }
            }

            Text("Поля: ${settings.marginMm} мм")
            Slider(
                value = settings.marginMm.toFloat(),
                onValueChange = { vm.updateSettings { s -> s.copy(marginMm = it.toInt()) } },
                valueRange = 0f..25f
            )

            Text("Качество: ${settings.quality}")
            Slider(
                value = settings.quality.toFloat(),
                onValueChange = { vm.updateSettings { s -> s.copy(quality = it.toInt()) } },
                valueRange = 10f..100f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Весь документ Ч/Б")
                Switch(
                    checked = settings.grayscaleAll,
                    onCheckedChange = { vm.updateSettings { s -> s.copy(grayscaleAll = it) } }
                )
            }

            if (build.building) {
                val (cur, total) = build.progress ?: (0 to pages.size)
                LinearProgressIndicator(
                    progress = { if (total > 0) cur / total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Собираю $cur / $total…")
                }
            }

            build.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        // Реклама + сборка PDF параллельно
                        ru.saymonren.pdfcon.core.ads.AdManager.showAndBuild(activity) {
                            vm.buildPdf(context)
                        }
                    } else {
                        vm.buildPdf(context)
                    }
                },
                enabled = pages.isNotEmpty() && !build.building,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (build.building) "Собираю…" else "Собрать") }
            Text(
                "Сборка бесплатна — покажем короткую рекламу",
                style = MaterialTheme.typography.bodySmall
            )

            build.result?.let { file ->
                Text("Готово: ${file.name} (${file.length() / 1024} КБ)")
                var savedPath by remember(file) { mutableStateOf<String?>(null) }
                var savedError by remember(file) { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.sharePdf(context, file) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text(" Поделиться")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val uri = vm.openUri(context, file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Открыть PDF"))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Открыть") }
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val path = PdfBuilder.saveToPublicFolder(context, file)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (path != null) {
                                    savedPath = path
                                    savedError = false
                                } else {
                                    savedError = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("  В свою папку")
                }
                savedPath?.let { Text("Сохранено: $it") }
                if (savedError) {
                    Text(
                        "Не получилось сохранить",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
