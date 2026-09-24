package ru.saymonren.pdfcon.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.saymonren.pdfcon.core.image.ImageFx
import ru.saymonren.pdfcon.core.image.MagicDust
import ru.saymonren.pdfcon.model.PageItem
import kotlin.math.roundToInt

/**
 * Честное превью страницы: обрезка + эффект + виньетка + блёстки —
 * так же посчитает и PDF (см. PdfBuilder).
 * Пропорции кадра сохраняются: длинное фото останется длинным.
 */
@Composable
fun PagePreview(
    page: PageItem,
    modifier: Modifier = Modifier,
    forceBw: Boolean = false,
    maxSize: Int = 1024
) {
    val context = LocalContext.current
    var bmp by remember(page.uri, page.scanQuad) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(page.uri, page.scanQuad) {
        withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(page.uri)
                    .size(Size(maxSize, maxSize))
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(req)
                val drawable = (result as? coil.request.SuccessResult)?.drawable
                val loaded = (drawable as? BitmapDrawable)?.bitmap
                // Скан: выпрямляем тем же warp, что и PDF (копию, не трогаем кэш Coil)
                bmp = if (loaded != null && page.scanQuad != null) {
                    val copy = loaded.copy(Bitmap.Config.ARGB_8888, false)
                    if (copy != null) {
                        ru.saymonren.pdfcon.core.pdf.PdfBuilder.warp(copy, page.scanQuad, true)
                    } else loaded
                } else loaded
            } catch (_: Exception) {
                bmp = null
            }
        }
    }

    val bitmap = bmp
    if (bitmap == null) {
        Box(
            modifier.height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Кадр после обрезки (в координатах исходника, до поворота)
    val cr = page.cropRect
    val sw = (((cr?.r ?: 1f) - (cr?.l ?: 0f)).coerceIn(0.01f, 1f) * bitmap.width)
    val sh = (((cr?.b ?: 1f) - (cr?.t ?: 0f)).coerceIn(0.01f, 1f) * bitmap.height)
    val rotated = ((page.rotationDeg % 180) + 180) % 180 != 0
    val ratio = if (rotated) sh / sw else sw / sh

    val matrix = remember(page, forceBw) {
        ImageFx.combinedCompose(page, forceBw)
    }
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    val seed = remember(page.id) { page.id.hashCode() }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.2f, 5f))
            .clip(MaterialTheme.shapes.medium)
            .rotate(page.rotationDeg.toFloat())
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val iw = bitmap.width
            val ih = bitmap.height
            val sx = ((cr?.l ?: 0f).coerceIn(0f, 1f) * iw).roundToInt().coerceIn(0, iw - 1)
            val sy = ((cr?.t ?: 0f).coerceIn(0f, 1f) * ih).roundToInt().coerceIn(0, ih - 1)
            val ex = ((cr?.r ?: 1f).coerceIn(0f, 1f) * iw).roundToInt().coerceIn(sx + 1, iw)
            val ey = ((cr?.b ?: 1f).coerceIn(0f, 1f) * ih).roundToInt().coerceIn(sy + 1, ih)
            drawImage(
                image = img,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(ex - sx, ey - sy),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                colorFilter = matrix?.let { ColorFilter.colorMatrix(it) }
            )
            if (page.vignette) {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color(0x6E000000),
                        center = center,
                        radius = maxOf(size.width, size.height) * 0.72f
                    )
                )
            }
            if (page.sparkles) {
                val unit = minOf(size.width, size.height) / 800f
                // Тот же фильтр, что и на фото: Ч/Б и эффекты красят и магию
                val fx = matrix?.let { ColorFilter.colorMatrix(it) }

                // 1. Световой наплыв из верхней кромки
                val light = MagicDust.light(seed.toLong())
                val la = light.alpha
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color(1f, 0.886f, 0.686f, la),
                        0.45f to Color(1f, 0.863f, 0.686f, la * 0.45f),
                        1f to Color.Transparent,
                        center = Offset(light.x * size.width, 0f),
                        radius = light.r * unit
                    ),
                    colorFilter = fx
                )

                // 2. Туманные орбы — только мягкие пятна
                for (o in MagicDust.orbs(seed.toLong())) {
                    val center = Offset(o.x * size.width, o.y * size.height)
                    val r = (o.r * unit).coerceAtLeast(1f)
                    val base = Color(o.color)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to base.copy(alpha = o.alpha),
                            0.45f to base.copy(alpha = o.alpha * 0.45f),
                            1f to Color.Transparent,
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center,
                        colorFilter = fx
                    )
                }

                // 3. Пыль — крошечные мягкие точки
                for (s in MagicDust.specks(seed.toLong())) {
                    val center = Offset(s.x * size.width, s.y * size.height)
                    val r = (s.r * unit).coerceAtLeast(1f)
                    val base = if (s.warm) Color(0xFFE8C87F) else Color.White
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to base.copy(alpha = s.alpha),
                            1f to Color.Transparent,
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center,
                        colorFilter = fx
                    )
                }
            }
        }
    }
}
