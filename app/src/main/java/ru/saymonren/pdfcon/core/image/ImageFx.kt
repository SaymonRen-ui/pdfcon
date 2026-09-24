package ru.saymonren.pdfcon.core.image

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import ru.saymonren.pdfcon.model.FxPreset
import ru.saymonren.pdfcon.model.PageItem

/**
 * Цветокоррекция: одна и та же математика для превью (Compose ColorMatrix)
 * и для PDF (android ColorMatrix через Canvas+Paint).
 * Порядок: сначала эффект, поверх — ручные яркость/контраст/насыщенность.
 */
object ImageFx {

    val FxPreset.title: String
        get() = when (this) {
            FxPreset.ORIGINAL -> "Оригинал"
            FxPreset.BW -> "Ч/Б"
            FxPreset.SEPIA -> "Сепия"
            FxPreset.VINTAGE -> "Винтаж"
            FxPreset.WARM -> "Тёплый"
            FxPreset.COLD -> "Холодный"
            FxPreset.VIVID -> "Яркий"
            FxPreset.FADE -> "Выцветший"
            FxPreset.NOIR -> "Нуар"
            FxPreset.INVERT -> "Инверсия"
            FxPreset.SUNSET -> "Закат"
            FxPreset.OCEAN -> "Океан"
            FxPreset.ROSE -> "Роза"
            FxPreset.FILM -> "Плёнка"
            FxPreset.NEON -> "Неон"
            FxPreset.GLOW -> "Сияние"
        }

    /** Матрица эффекта (null = без изменений). */
    fun effectMatrix(fx: FxPreset): ColorMatrix? = when (fx) {
        FxPreset.ORIGINAL -> null
        FxPreset.BW -> gray(1f, 0f)
        FxPreset.SEPIA -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.VINTAGE -> ColorMatrix(
            floatArrayOf(
                1.02f, 0.06f, 0f, 0f, 16f,
                0.04f, 0.98f, 0.04f, 0f, 10f,
                0f, 0.05f, 0.84f, 0f, 2f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.WARM -> ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 8f,
                0f, 1.03f, 0f, 0f, 2f,
                0f, 0f, 0.92f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.COLD -> ColorMatrix(
            floatArrayOf(
                0.92f, 0f, 0f, 0f, -4f,
                0f, 1f, 0.05f, 0f, 2f,
                0f, 0.05f, 1.12f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.VIVID -> satContrast(1.5f, 1.08f, 0f)
        FxPreset.FADE -> satContrast(0.7f, 0.85f, 32f)
        FxPreset.NOIR -> gray(1.35f, -18f)
        FxPreset.INVERT -> ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.SUNSET -> ColorMatrix(
            floatArrayOf(
                1.22f, 0.08f, 0f, 0f, 10f,
                0.06f, 1f, 0f, 0f, 0f,
                0f, 0f, 0.84f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.OCEAN -> ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, -6f,
                0f, 1.08f, 0.06f, 0f, 4f,
                0.06f, 0f, 1.14f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.ROSE -> ColorMatrix(
            floatArrayOf(
                1.1f, 0.05f, 0.05f, 0f, 10f,
                0f, 0.94f, 0.06f, 0f, 4f,
                0.05f, 0f, 1f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.FILM -> satContrast(1.12f, 1f, 4f).also {
            // лёгкий зелёный оттенок теней через тёплый сдвиг уже в матрице выше
        }
        FxPreset.NEON -> ColorMatrix(
            floatArrayOf(
                0.95f, 0f, 0.12f, 0f, 0f,
                0f, 1.18f, 0.1f, 0f, 6f,
                0.12f, 0f, 1.22f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        FxPreset.GLOW -> satContrast(1.1f, 1f, 24f)
    }

    private fun gray(contrast: Float, lift: Float): ColorMatrix = ColorMatrix(
        floatArrayOf(
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, lift,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, lift,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, lift,
            0f, 0f, 0f, 1f, 0f
        )
    )

    private fun satContrast(sat: Float, contrast: Float, lift: Float): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(sat)
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, lift,
                    0f, contrast, 0f, 0f, lift,
                    0f, 0f, contrast, 0f, lift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return m
    }

    /** Ручная коррекция: out = contrast * in + brightness*255, затем насыщенность. */
    fun adjustMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
        val m = ColorMatrix()
        val satM = ColorMatrix()
        satM.setSaturation(saturation.coerceIn(0f, 2f))
        val c = contrast.coerceIn(0.2f, 3f)
        val b = brightness.coerceIn(-1f, 1f) * 255f
        val contrastM = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, b,
                0f, c, 0f, 0f, b,
                0f, 0f, c, 0f, b,
                0f, 0f, 0f, 1f, 0f
            )
        )
        m.postConcat(satM)
        m.postConcat(contrastM)
        return m
    }

    fun isAdjustNeutral(page: PageItem): Boolean =
        page.brightness == 0f && page.contrast == 1f && page.saturation == 1f

    /**
     * Итоговая матрица для PDF (null = ничего не делать).
     * @param forceBw аналог старого grayscaleAll — весь документ в Ч/Б.
     */
    fun combinedAndroid(page: PageItem, forceBw: Boolean = false): ColorMatrix? {
        val fx = if (forceBw) FxPreset.BW else page.fx
        val effect = effectMatrix(fx)
        val neutral = isAdjustNeutral(page)
        if (effect == null && neutral && !page.scanBoost) return null
        val m = ColorMatrix(effect?.array ?: floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        if (!neutral) m.postConcat(adjustMatrix(page.brightness, page.contrast, page.saturation))
        // Усиление скана: дотянуть контраст как у сканера
        if (page.scanBoost) {
            m.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, 10f,
                        0f, 1.25f, 0f, 0f, 10f,
                        0f, 0f, 1.25f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return m
    }

    /** То же для Compose-превью (null = без фильтра). */
    fun combinedCompose(page: PageItem, forceBw: Boolean = false)
            : androidx.compose.ui.graphics.ColorMatrix? =
        combinedAndroid(page, forceBw)?.let {
            androidx.compose.ui.graphics.ColorMatrix(it.array)
        }

    fun androidFilter(page: PageItem, forceBw: Boolean = false): ColorMatrixColorFilter? =
        combinedAndroid(page, forceBw)?.let { ColorMatrixColorFilter(it) }
}
