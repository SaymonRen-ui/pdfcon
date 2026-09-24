package ru.saymonren.pdfcon.model

import android.net.Uri
import java.util.UUID

/** Режим обрезки: свободная рамка или с фиксированным соотношением. */
enum class CropMode { ORIGINAL, A4_PORTRAIT, SQUARE }

/** Цветовые эффекты. Матрицы лежат в ImageFx. */
enum class FxPreset {
    ORIGINAL, BW, SEPIA, VINTAGE, WARM, COLD, VIVID, FADE,
    NOIR, INVERT, SUNSET, OCEAN, ROSE, FILM, NEON, GLOW
}

/** Нормализованная рамка обрезки в координатах исходного фото (0..1). */
data class CropRect(
    val l: Float = 0f,
    val t: Float = 0f,
    val r: Float = 1f,
    val b: Float = 1f
) {
    val isFull: Boolean
        get() = l <= 0f && t <= 0f && r >= 1f && b >= 1f
}

/** Точка четырёхугольника скана (0..1 от размеров фото). */
data class Pt(val x: Float = 0f, val y: Float = 0f)

/** Четырёхугольник документа для выпрямления перспективы. */
data class ScanQuad(
    val tl: Pt = Pt(0.08f, 0.08f),
    val tr: Pt = Pt(0.92f, 0.08f),
    val br: Pt = Pt(0.92f, 0.92f),
    val bl: Pt = Pt(0.08f, 0.92f)
)

data class PageItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val rotationDeg: Int = 0,          // 0, 90, 180, 270
    val brightness: Float = 0f,        // -0.5..0.5 (сдвиг)
    val contrast: Float = 1f,          // 0.5..1.5
    val saturation: Float = 1f,        // 0..2
    val fx: FxPreset = FxPreset.ORIGINAL,
    val vignette: Boolean = false,
    val sparkles: Boolean = false,
    val crop: CropMode = CropMode.ORIGINAL, // аспект рамки в редакторе обрезки
    val cropRect: CropRect? = null,         // null = всё фото
    val scanQuad: ScanQuad? = null,         // null = без выпрямления
    val scanBoost: Boolean = false          // усилить контраст как у сканера
)

enum class PageSize { A4, ORIGINAL }
enum class Orientation { PORTRAIT, LANDSCAPE, AUTO }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PdfSettings(
    val pageSize: PageSize = PageSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val marginMm: Int = 10,
    val quality: Int = 85,             // JPEG 10..100
    val grayscaleAll: Boolean = false,
    val fileName: String = ""          // пусто = сгенерировать по дате
)
