package ru.saymonren.pdfcon.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.saymonren.pdfcon.core.pdf.PdfBuilder
import ru.saymonren.pdfcon.ui.PdfViewModel

/**
 * Точный предпросмотр: собираем тот же PDF и рендерим его страницы
 * через PdfRenderer — пиксель в пиксель как в файле.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    vm: PdfViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages by vm.pages.collectAsState()
    val settings by vm.settings.collectAsState()

    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Ключ — всё что влияет на результат
    val buildKey = remember(pages, settings) { pages to settings }

    LaunchedEffect(buildKey) {
        withContext(Dispatchers.IO) {
            try {
                bitmaps = emptyList()
                error = null
                val file = PdfBuilder.build(context.applicationContext, buildKey.first, buildKey.second)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val out = ArrayList<Bitmap>(renderer.pageCount)
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val scale = 1080f / page.width
                                val w = 1080
                                val h = (page.height * scale).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                out.add(bmp)
                            }
                        }
                        bitmaps = out
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Не удалось собрать предпросмотр"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { bitmaps.forEach { it.recycle() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Предпросмотр PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                error != null -> {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onBack) { Text("Назад") }
                }
                bitmaps.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Рендерю страницы…", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
                else -> {
                    val pagerState = rememberPagerState(pageCount = { bitmaps.size })
                    Text(
                        "Страница ${pagerState.currentPage + 1} из ${bitmaps.size}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp)
                    ) { i ->
                        Image(
                            bitmap = bitmaps[i].asImageBitmap(),
                            contentDescription = "Стр. ${i + 1}",
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        (pagerState.currentPage - 1).coerceAtLeast(0)
                                    )
                                }
                            },
                            enabled = pagerState.currentPage > 0
                        ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Назад") }
                        Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                            Text("Всё так — к сборке")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        (pagerState.currentPage + 1).coerceAtMost(bitmaps.size - 1)
                                    )
                                }
                            },
                            enabled = pagerState.currentPage < bitmaps.size - 1
                        ) { Icon(Icons.Default.ChevronRight, contentDescription = "Вперёд") }
                    }
                }
            }
        }
    }
}
