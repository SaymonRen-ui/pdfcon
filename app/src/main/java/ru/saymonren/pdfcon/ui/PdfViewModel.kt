package ru.saymonren.pdfcon.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.saymonren.pdfcon.core.pdf.PdfBuilder
import ru.saymonren.pdfcon.model.PageItem
import ru.saymonren.pdfcon.model.PdfSettings
import ru.saymonren.pdfcon.model.ThemeMode
import java.io.File

data class BuildUiState(
    val building: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val result: File? = null,
    val error: String? = null
)

class PdfViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("pdfcon", Context.MODE_PRIVATE)

    private val _pages = MutableStateFlow<List<PageItem>>(emptyList())
    val pages: StateFlow<List<PageItem>> = _pages.asStateFlow()

    private val _settings = MutableStateFlow(PdfSettings())
    val settings: StateFlow<PdfSettings> = _settings.asStateFlow()

    private val _build = MutableStateFlow(BuildUiState())
    val build: StateFlow<BuildUiState> = _build.asStateFlow()

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _pages.update { it + uris.map { uri -> PageItem(uri = uri) } }
        _build.update { it.copy(result = null, error = null) }
    }

    fun remove(id: String) {
        _pages.update { it.filterNot { p -> p.id == id } }
    }

    fun clear() {
        _pages.update { emptyList() }
        _build.update { BuildUiState() }
    }

    fun move(id: String, delta: Int) {
        _pages.update { list ->
            val i = list.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j !in list.indices) return@update list
            list.toMutableList().also { m ->
                val tmp = m[i]; m[i] = m[j]; m[j] = tmp
            }
        }
    }

    fun moveTo(id: String, toIndex: Int) {
        _pages.update { list ->
            val from = list.indexOfFirst { it.id == id }
            if (from < 0 || toIndex !in list.indices || from == toIndex) return@update list
            list.toMutableList().also { m -> m.add(toIndex, m.removeAt(from)) }
        }
    }

    /** Перестановка по индексам сетки (для библиотеки Reorderable). */
    fun moveIndices(from: Int, to: Int) {
        _pages.update { list ->
            if (from !in list.indices || to !in list.indices || from == to) return@update list
            list.toMutableList().also { m -> m.add(to, m.removeAt(from)) }
        }
    }

    fun updatePage(id: String, transform: (PageItem) -> PageItem) {
        _pages.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun updateSettings(transform: (PdfSettings) -> PdfSettings) {
        _settings.update(transform)
    }

    fun getPage(id: String): PageItem? = _pages.value.firstOrNull { it.id == id }

    /**
     * Сборка PDF.
     * @param showAd вызывается ПЕРЕД тяжёлой работой: сюда подключается
     * межстраничная реклама РСЯ (этап 2). Пока заглушка — сразу строим.
     */
    fun buildPdf(context: Context, showAd: (onAdDone: () -> Unit) -> Unit = { done -> done() }) {
        val pages = _pages.value
        if (pages.isEmpty() || _build.value.building) return
        _build.update { it.copy(building = true, progress = 0 to pages.size, error = null, result = null) }
        showAd {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val s = _settings.value
                    val file = PdfBuilder.build(context.applicationContext, pages, s) { cur, total ->
                        _build.update { it.copy(progress = cur to total) }
                    }
                    _build.update { it.copy(building = false, result = file) }
                } catch (e: Exception) {
                    _build.update { it.copy(building = false, error = e.message ?: "Ошибка сборки") }
                }
            }
        }
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться PDF"))
    }

    suspend fun openUri(context: Context, file: File): Uri = withContext(Dispatchers.IO) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
