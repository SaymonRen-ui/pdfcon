package ru.saymonren.pdfcon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import ru.saymonren.pdfcon.model.CropMode
import ru.saymonren.pdfcon.model.CropRect

private enum class DragPart { NONE, MOVE, TL, TR, BL, BR }

/**
 * Обрезка прямо на фото: тяни белые углы, середину — двигай.
 * Рамка живёт в нормализованных координатах фото (0..1),
 * наружу отдаётся через onChange при каждом движении.
 */
@Composable
fun CropOverlay(
    uri: android.net.Uri,
    initial: CropRect?,
    aspect: CropMode,
    onChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var norm by remember(uri, aspect) { mutableStateOf(initial ?: CropRect()) }
    var boxPx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val touch = remember(density) { with(density) { 28.dp.toPx() } }
    val painter = rememberAsyncImagePainter(model = uri)
    val intrinsic = (painter.state as? coil.compose.AsyncImagePainter.State.Success)
        ?.painter?.intrinsicSize

    fun ratio(): Float? = when (aspect) {
        CropMode.ORIGINAL -> null
        CropMode.SQUARE -> 1f
        CropMode.A4_PORTRAIT -> 1f / 1.4142f
    }

    fun imageRect(): Rect? {
        val s = intrinsic ?: return null
        if (boxPx.width == 0) return null
        val iw = s.width
        val ih = s.height
        if (!iw.isFinite() || !ih.isFinite() || iw <= 0 || ih <= 0) return null
        val k = minOf(boxPx.width / iw, boxPx.height / ih)
        val dw = iw * k
        val dh = ih * k
        val ox = (boxPx.width - dw) / 2f
        val oy = (boxPx.height - dh) / 2f
        return Rect(ox, oy, ox + dw, oy + dh)
    }

    fun toPx(n: CropRect, img: Rect) = Rect(
        img.left + n.l * img.width,
        img.top + n.t * img.height,
        img.left + n.r * img.width,
        img.top + n.b * img.height
    )

    fun toNorm(r: Rect, img: Rect) = CropRect(
        ((r.left - img.left) / img.width).coerceIn(0f, 1f),
        ((r.top - img.top) / img.height).coerceIn(0f, 1f),
        ((r.right - img.left) / img.width).coerceIn(0f, 1f),
        ((r.bottom - img.top) / img.height).coerceIn(0f, 1f)
    )

    fun enforce(r: Rect, fixed: DragPart): Rect {
        val rt = ratio() ?: return r
        var w = r.width
        var h = r.height
        if (w <= 1f || h <= 1f) return r
        if (w / h > rt) w = h * rt else h = w / rt
        return when (fixed) {
            DragPart.TL -> Rect(r.right - w, r.bottom - h, r.right, r.bottom)
            DragPart.TR -> Rect(r.left, r.bottom - h, r.left + w, r.bottom)
            DragPart.BL -> Rect(r.right - w, r.top, r.right, r.top + h)
            else -> Rect(r.left, r.top, r.left + w, r.top + h)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxPx = it }
            .pointerInput(boxPx, aspect, intrinsic) {
                val img = imageRect() ?: return@pointerInput
                var part = DragPart.NONE
                var cur = toPx(norm, img)
                detectDragGestures(
                    onDragStart = { down ->
                        cur = toPx(norm, imageRect() ?: return@detectDragGestures)
                        part = when {
                            (down - cur.topLeft).getDistance() < touch * 1.6f -> DragPart.TL
                            (down - Offset(cur.right, cur.top)).getDistance() < touch * 1.6f -> DragPart.TR
                            (down - Offset(cur.left, cur.bottom)).getDistance() < touch * 1.6f -> DragPart.BL
                            (down - cur.bottomRight).getDistance() < touch * 1.6f -> DragPart.BR
                            cur.contains(down) -> DragPart.MOVE
                            else -> DragPart.NONE
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (part == DragPart.NONE) return@detectDragGestures
                        val img2 = imageRect() ?: return@detectDragGestures
                        val minPx = img2.width * 0.05f
                        cur = when (part) {
                            DragPart.MOVE -> {
                                val dx = drag.x.coerceIn(img2.left - cur.left, img2.right - cur.right)
                                val dy = drag.y.coerceIn(img2.top - cur.top, img2.bottom - cur.bottom)
                                cur.translate(dx, dy)
                            }
                            DragPart.TL -> enforce(
                                Rect(
                                    (cur.left + drag.x).coerceIn(img2.left, cur.right - minPx),
                                    (cur.top + drag.y).coerceIn(img2.top, cur.bottom - minPx),
                                    cur.right, cur.bottom
                                ), DragPart.TL
                            )
                            DragPart.TR -> enforce(
                                Rect(
                                    cur.left,
                                    (cur.top + drag.y).coerceIn(img2.top, cur.bottom - minPx),
                                    (cur.right + drag.x).coerceIn(cur.left + minPx, img2.right),
                                    cur.bottom
                                ), DragPart.TR
                            )
                            DragPart.BL -> enforce(
                                Rect(
                                    (cur.left + drag.x).coerceIn(img2.left, cur.right - minPx),
                                    cur.top, cur.right,
                                    (cur.bottom + drag.y).coerceIn(cur.top + minPx, img2.bottom)
                                ), DragPart.BL
                            )
                            DragPart.BR -> enforce(
                                Rect(
                                    cur.left, cur.top,
                                    (cur.right + drag.x).coerceIn(cur.left + minPx, img2.right),
                                    (cur.bottom + drag.y).coerceIn(cur.top + minPx, img2.bottom)
                                ), DragPart.BR
                            )
                            DragPart.NONE -> cur
                        }
                        norm = toNorm(cur, img2)
                        onChange(norm)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // ВАЖНО: рисуем тем же painter, у которого читаем intrinsicSize —
        // иначе Coil не стартует загрузку и размер никогда не появится.
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        if (intrinsic == null) {
            CircularProgressIndicator()
            return@Box
        }
        val img = imageRect() ?: return@Box
        val r = toPx(norm, img)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0x99000000), Offset.Zero, Size(size.width, r.top))
            drawRect(Color(0x99000000), Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
            drawRect(Color(0x99000000), Offset(0f, r.top), Size(r.left, r.height))
            drawRect(Color(0x99000000), Offset(r.right, r.top), Size(size.width - r.right, r.height))
            drawRect(Color.White, r.topLeft, r.size, style = Stroke(3f))
            val gx1 = r.left + r.width / 3f
            val gx2 = r.left + r.width * 2f / 3f
            val gy1 = r.top + r.height / 3f
            val gy2 = r.top + r.height * 2f / 3f
            drawLine(Color(0xAAFFFFFF), Offset(gx1, r.top), Offset(gx1, r.bottom))
            drawLine(Color(0xAAFFFFFF), Offset(gx2, r.top), Offset(gx2, r.bottom))
            drawLine(Color(0xAAFFFFFF), Offset(r.left, gy1), Offset(r.right, gy1))
            drawLine(Color(0xAAFFFFFF), Offset(r.left, gy2), Offset(r.right, gy2))
            listOf(r.topLeft, Offset(r.right, r.top), Offset(r.left, r.bottom), r.bottomRight)
                .forEach { drawCircle(Color.White, 14f, it) }
        }
    }
}
