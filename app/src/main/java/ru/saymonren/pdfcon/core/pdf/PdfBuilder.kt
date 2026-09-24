package ru.saymonren.pdfcon.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import android.net.Uri
import ru.saymonren.pdfcon.core.image.ImageFx
import ru.saymonren.pdfcon.core.image.MagicDust
import ru.saymonren.pdfcon.model.CropMode
import ru.saymonren.pdfcon.model.CropRect
import ru.saymonren.pdfcon.model.Orientation
import ru.saymonren.pdfcon.model.PageItem
import ru.saymonren.pdfcon.model.PageSize
import ru.saymonren.pdfcon.model.PdfSettings
import ru.saymonren.pdfcon.model.ScanQuad
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object PdfBuilder {

    // A4 @ 150dpi ~= 1240x1754 px — достаточно для сканов, файл лёгкий
    private const val A4_W_150 = 1240
    private const val A4_H_150 = 1754

    fun build(
        context: Context,
        pages: List<PageItem>,
        settings: PdfSettings,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): File {
        require(pages.isNotEmpty()) { "Нет страниц" }
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                var bmp = loadBounded(context, page.uri, 2000)
                    ?: throw IllegalStateException("Не удалось прочитать фото ${index + 1}")
                bmp = applyCropRect(bmp, page.cropRect)
                // Скан раньше поворота: углы заданы в координатах как на экране
                if (page.scanQuad != null) bmp = warp(bmp, page.scanQuad)
                bmp = applyRotation(bmp, page.rotationDeg)
                // Магия ДО эффекта — чтобы Ч/Б, сепия и др. легли и на частицы
                if (page.sparkles) bmp = applySparkles(bmp, page.id.hashCode().toLong())
                bmp = applyAdjust(bmp, page, settings)
                bmp = applyAspectCrop(bmp, page.crop)
                if (page.vignette) bmp = applyVignette(bmp)

                val (pw, ph) = pageSizePx(bmp, settings)
                val info = PdfDocument.PageInfo.Builder(pw, ph, index + 1).create()
                val pdfPage = doc.startPage(info)
                drawFit(pdfPage.canvas, bmp, pw, ph, settings.marginMm)
                doc.finishPage(pdfPage)
                if (bmp.isRecycled.not()) bmp.recycle()
                onProgress(index + 1, pages.size)
            }
            val outDir = File(context.cacheDir, "pdf").apply { mkdirs() }
            val out = File(outDir, "${resolveFileName(settings)}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }
            return out
        } finally {
            doc.close()
        }
    }

    /** Имя файла: пользовательское или по дате/времени. */
    fun resolveFileName(s: PdfSettings): String {
        val raw = s.fileName.trim()
        if (raw.isNotEmpty()) {
            return raw.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9 _-]"), "_").trim().take(60)
        }
        return "PDF_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    /**
     * Копия готового PDF в свою папку «Загрузки/PDFcon» —
     * видно в файловом менеджере, не трётся при чистке кэша.
     * @return человекочитаемый путь для показа или null при ошибке.
     */
    fun saveToPublicFolder(context: Context, src: File): String? {
        val name = src.name.let { if (it.endsWith(".pdf")) it else "$it.pdf" }
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/PDFcon")
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    java.io.FileInputStream(src).use { it.copyTo(out) }
                }
                "Загрузки/PDFcon/$name"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ),
                    "PDFcon"
                ).apply { mkdirs() }
                var dst = File(dir, name)
                var i = 1
                while (dst.exists()) {
                    dst = File(dir, "${name.removeSuffix(".pdf")} ($i).pdf")
                    i++
                }
                src.copyTo(dst, overwrite = false)
                "Загрузки/PDFcon/${dst.name}"
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pageSizePx(bmp: Bitmap, s: PdfSettings): Pair<Int, Int> {
        if (s.pageSize == PageSize.ORIGINAL) {
            val maxSide = 2000
            val scale = minOf(1f, maxSide / maxOf(bmp.width, bmp.height).toFloat())
            return ((bmp.width * scale).roundToInt()) to ((bmp.height * scale).roundToInt())
        }
        val portrait = when (s.orientation) {
            Orientation.PORTRAIT -> true
            Orientation.LANDSCAPE -> false
            Orientation.AUTO -> bmp.height >= bmp.width
        }
        return if (portrait) A4_W_150 to A4_H_150 else A4_H_150 to A4_W_150
    }

    private fun drawFit(canvas: Canvas, bmp: Bitmap, pw: Int, ph: Int, marginMm: Int) {
        canvas.drawColor(Color.WHITE)
        val margin = (marginMm * 5.9f).roundToInt().coerceIn(0, minOf(pw, ph) / 4)
        val availW = pw - margin * 2
        val availH = ph - margin * 2
        val scale = minOf(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
        val dw = (bmp.width * scale).roundToInt()
        val dh = (bmp.height * scale).roundToInt()
        val left = (pw - dw) / 2f
        val top = (ph - dh) / 2f
        val scaled = Bitmap.createScaledBitmap(bmp, dw, dh, true)
        canvas.drawBitmap(scaled, left, top, Paint().apply { isFilterBitmap = true })
        scaled.recycle()
    }

    private fun loadBounded(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        // Кадры с камеры лежат боком (EXIF) — разворачиваем сразу,
        // чтобы рамка обрезки и превью совпадали с PDF
        val exifDeg = exifDegrees(context, uri)
        if (exifDeg == 0) return bmp
        val m = Matrix().apply { postRotate(exifDeg.toFloat()) }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        bmp.recycle()
        return out
    }

    private fun exifDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val exif = android.media.ExifInterface(ins)
                when (exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun applyRotation(src: Bitmap, deg: Int): Bitmap {
        val d = ((deg % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix().apply { postRotate(d.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        src.recycle()
        return out
    }

    /** Выпрямление перспективы по 4 углам документа. */
    fun warp(src: Bitmap, quad: ScanQuad, recycleSrc: Boolean = true): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val sx = floatArrayOf(
            quad.tl.x * w, quad.tl.y * h,
            quad.tr.x * w, quad.tr.y * h,
            quad.br.x * w, quad.br.y * h,
            quad.bl.x * w, quad.bl.y * h
        )
        fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = ax - bx
            val dy = ay - by
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val outW = maxOf(
            dist(sx[0], sx[1], sx[2], sx[3]),
            dist(sx[6], sx[7], sx[4], sx[5])
        ).roundToInt().coerceIn(1, 3000)
        val outH = maxOf(
            dist(sx[0], sx[1], sx[6], sx[7]),
            dist(sx[2], sx[3], sx[4], sx[5])
        ).roundToInt().coerceIn(1, 3000)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(sx, 0, dst, 0, 4)) return src
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint().apply { isFilterBitmap = true })
        if (recycleSrc) src.recycle()
        return out
    }

    /** Пользовательская рамка обрезки (нормализованные координаты исходного фото). */
    private fun applyCropRect(src: Bitmap, rect: CropRect?): Bitmap {
        if (rect == null || rect.isFull) return src
        val x = (rect.l.coerceIn(0f, 1f) * src.width).roundToInt().coerceIn(0, src.width - 1)
        val y = (rect.t.coerceIn(0f, 1f) * src.height).roundToInt().coerceIn(0, src.height - 1)
        val r = (rect.r.coerceIn(0f, 1f) * src.width).roundToInt().coerceIn(x + 1, src.width)
        val b = (rect.b.coerceIn(0f, 1f) * src.height).roundToInt().coerceIn(y + 1, src.height)
        val out = Bitmap.createBitmap(src, x, y, r - x, b - y)
        src.recycle()
        return out
    }

    /** Центрированное кадрирование под соотношение (A4 / квадрат). */
    private fun applyAspectCrop(src: Bitmap, mode: CropMode): Bitmap {
        if (mode == CropMode.ORIGINAL) return src
        val targetRatio = when (mode) {
            CropMode.SQUARE -> 1f
            CropMode.A4_PORTRAIT -> 1f / 1.4142f
            CropMode.ORIGINAL -> return src
        }
        val srcRatio = src.width / src.height.toFloat()
        val (cw, ch) = if (srcRatio > targetRatio) {
            val w = (src.height * targetRatio).roundToInt()
            w to src.height
        } else {
            val h = (src.width / targetRatio).roundToInt()
            src.width to h
        }
        if (cw <= 0 || ch <= 0) return src
        val x = (src.width - cw) / 2
        val y = (src.height - ch) / 2
        val out = Bitmap.createBitmap(src, x, y, cw, ch)
        src.recycle()
        return out
    }

    /** Эффект + яркость/контраст/насыщенность одной матрицей через Canvas. */
    private fun applyAdjust(src: Bitmap, page: PageItem, s: PdfSettings): Bitmap {
        val matrix = ImageFx.combinedAndroid(page, s.grayscaleAll) ?: return src
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isFilterBitmap = true
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return bmp
    }

    /** Затемнение углов. */
    private fun applyVignette(src: Bitmap): Bitmap {
        val bmp = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        if (bmp !== src) src.recycle()
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val paint = Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, maxOf(w, h) * 0.72f,
                intArrayOf(Color.TRANSPARENT, Color.argb(110, 0, 0, 0)),
                floatArrayOf(0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        Canvas(bmp).drawRect(0f, 0f, w, h, paint)
        return bmp
    }

    /**
     * Магия как атмосфера: световой наплыв + туманные орбы + пыль.
     * Только мягкие градиенты, ни одной резкой границы.
     */
    private fun applySparkles(src: Bitmap, seed: Long): Bitmap {
        val bmp = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        if (bmp !== src) src.recycle()
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val canvas = Canvas(bmp)
        val unit = minOf(w, h) / 800f

        // 1. Световой наплыв из верхней кромки — тёплая дымка
        val light = MagicDust.light(seed)
        val lx = light.x * w
        val la = (light.alpha * 255).roundToInt()
        val leak = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                lx, 0f, light.r * unit,
                intArrayOf(
                    Color.argb(la, 255, 226, 175),
                    Color.argb((la * 0.45f).roundToInt(), 255, 220, 175),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, leak)

        // 2. Туманные орбы — большие слабые пятна, гаснущие к краям
        for (o in MagicDust.orbs(seed)) {
            val cx = o.x * w
            val cy = o.y * h
            val r = (o.r * unit).coerceAtLeast(1f)
            val rgb = o.color.toInt() and 0x00FFFFFF
            val peak = (o.alpha * 255).roundToInt()
            val p = Paint().apply {
                isAntiAlias = true
                shader = RadialGradient(
                    cx, cy, r,
                    intArrayOf(
                        rgb or (peak shl 24),
                        rgb or ((peak * 0.45f).roundToInt() shl 24),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r, p)
        }

        // 3. Пыль — крошечные мягкие точки в воздухе
        for (s in MagicDust.specks(seed)) {
            val cx = s.x * w
            val cy = s.y * h
            val r = (s.r * unit).coerceAtLeast(1f)
            val peak = (s.alpha * 255).roundToInt()
            val rgb = if (s.warm) 0xFFE8C87F.toInt() else 0x00FFFFFF
            val p = Paint().apply {
                isAntiAlias = true
                shader = RadialGradient(
                    cx, cy, r,
                    intArrayOf(rgb or (peak shl 24), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r, p)
        }
        return bmp
    }
}
