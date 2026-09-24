package ru.saymonren.pdfcon.core.image

import kotlin.random.Random

/**
 * «Магия» как атмосфера, а не наклейки: ни одной резкой границы.
 * Только мягкие градиенты — световой наплыв, туманные орбы, пыль.
 * Чистый kotlin, без Android/Compose — одни и те же координаты
 * используют PdfBuilder (PDF) и PagePreview (превью), поэтому
 * результат совпадает пиксель в пиксель.
 */
object MagicDust {

    const val ORB_COUNT = 18
    const val DUST_COUNT = 90

    /** ARGB палитра. */
    val palette = longArrayOf(
        0xFFFFD54FL, // золото
        0xFFFFAB40L, // янтарь
        0xFF80DEEAL, // лёд
        0xFFF48FB1L, // розовый
        0xFFCE93D8L, // сирень
        0xFFFFFFFFL  // белый
    )

    /** Туманный орб: большой, слабый, только градиент. */
    data class Orb(
        val x: Float, // 0..1 от ширины
        val y: Float, // 0..1 от высоты
        val r: Float, // радиус в единицах unit = min(w,h)/800
        val color: Long,
        val alpha: Float // пиковая прозрачность в центре
    )

    /** Пылинка: крошечная мягкая точка. */
    data class Speck(
        val x: Float,
        val y: Float,
        val r: Float,
        val warm: Boolean, // золотая или белая
        val alpha: Float
    )

    /** Световой наплыв из верхней кромки. */
    data class Light(
        val x: Float, // 0..1, точка на верхней кромке
        val r: Float, // радиус в единицах unit
        val alpha: Float
    )

    fun orbs(seed: Long): List<Orb> {
        val rnd = Random(seed)
        return List(ORB_COUNT) {
            Orb(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                r = 18f + rnd.nextFloat() * 55f,
                color = palette[rnd.nextInt(palette.size)],
                alpha = 0.22f + rnd.nextFloat() * 0.20f
            )
        }
    }

    fun specks(seed: Long): List<Speck> {
        val rnd = Random(seed xor 0x9E3779B9L)
        return List(DUST_COUNT) {
            Speck(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                r = 1f + rnd.nextFloat() * 2.6f,
                warm = rnd.nextFloat() < 0.45f,
                alpha = 0.30f + rnd.nextFloat() * 0.45f
            )
        }
    }

    fun light(seed: Long): Light {
        val rnd = Random(seed xor 0x51ED27AFL)
        return Light(
            x = 0.15f + rnd.nextFloat() * 0.7f,
            r = 700f + rnd.nextFloat() * 500f,
            alpha = 0.20f + rnd.nextFloat() * 0.10f
        )
    }
}
