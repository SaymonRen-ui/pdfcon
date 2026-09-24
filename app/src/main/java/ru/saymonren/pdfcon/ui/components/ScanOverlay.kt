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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import ru.saymonren.pdfcon.model.Pt
import ru.saymonren.pdfcon.model.ScanQuad

/**
 * Скан: 4 угла тянутся на углы документа прямо на фото.
 * Точки — в нормализованных координатах фото (0..1).
 */
@Composable
fun ScanOverlay(
    uri: android.net.Uri,
    initial: ScanQuad?,
    onChange: (ScanQuad) -> Unit,
    modifier: Modifier = Modifier
) {
    var quad by remember(uri) { mutableStateOf(initial ?: ScanQuad()) }
    var boxPx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val touch = remember(density) { with(density) { 30.dp.toPx() } }
    val painter = rememberAsyncImagePainter(model = uri)
    val intrinsic = (painter.state as? coil.compose.AsyncImagePainter.State.Success)
        ?.painter?.intrinsicSize

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

    fun toPx(p: Pt, img: Rect) = Offset(img.left + p.x * img.width, img.top + p.y * img.height)

    fun toNorm(o: Offset, img: Rect) = Pt(
        ((o.x - img.left) / img.width).coerceIn(0f, 1f),
        ((o.y - img.top) / img.height).coerceIn(0f, 1f)
    )

    fun pts(img: Rect) = listOf(
        toPx(quad.tl, img), toPx(quad.tr, img),
        toPx(quad.br, img), toPx(quad.bl, img)
    )

    Box(
        modifier = modifier
            .onSizeChanged { boxPx = it }
            .pointerInput(boxPx, intrinsic) {
                var active = -1
                detectDragGestures(
                    onDragStart = { down ->
                        val img = imageRect() ?: return@detectDragGestures
                        val ps = pts(img)
                        val nearest = ps.indices.minByOrNull { (ps[it] - down).getDistance() }
                        active = if (nearest != null && (ps[nearest] - down).getDistance() < touch * 2f) {
                            nearest
                        } else {
                            -1
                        }
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        if (active < 0) return@detectDragGestures
                        val img = imageRect() ?: return@detectDragGestures
                        // точка следует за пальцем в пикселях бокса
                        val cur = pts(img)[active] + change.position - change.previousPosition
                        val n = toNorm(cur, img)
                        quad = when (active) {
                            0 -> quad.copy(tl = n)
                            1 -> quad.copy(tr = n)
                            2 -> quad.copy(br = n)
                            else -> quad.copy(bl = n)
                        }
                        onChange(quad)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
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
        val ps = pts(img)
        Canvas(Modifier.fillMaxSize()) {
            // лёгкая вуаль + светлый контур документа
            drawRect(Color(0x55000000))
            val poly = Path().apply {
                moveTo(ps[0].x, ps[0].y)
                lineTo(ps[1].x, ps[1].y)
                lineTo(ps[2].x, ps[2].y)
                lineTo(ps[3].x, ps[3].y)
                close()
            }
            drawPath(poly, Color.White, style = Stroke(4f))
            // диагонали для ориентира
            drawLine(Color(0x88FFFFFF), ps[0], ps[2])
            drawLine(Color(0x88FFFFFF), ps[1], ps[3])
            ps.forEachIndexed { i, p ->
                drawCircle(Color(0xAA000000), 26f, p)
                drawCircle(Color.White, 20f, p)
                drawCircle(
                    Color(if (i % 2 == 0) 0xFF4CAF50 else 0xFF2196F3),
                    9f, p
                )
            }
        }
    }
}
