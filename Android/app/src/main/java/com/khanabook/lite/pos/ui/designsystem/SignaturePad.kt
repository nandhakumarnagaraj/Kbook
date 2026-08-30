package com.khanabook.lite.pos.ui.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable
fun SignaturePad(
    modifier: Modifier = Modifier,
    clearTrigger: Int = 0,
    onSignatureChange: (Bitmap?) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasRef by remember { mutableStateOf<AndroidCanvas?>(null) }
    var lastPoint by remember { mutableStateOf<Offset?>(null) }
    var redrawKey by remember { mutableStateOf(0) }

    val paint = remember {
        Paint().apply {
            color = AndroidColor.BLACK
            strokeWidth = 4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
    }

    LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            canvasRef?.drawColor(AndroidColor.WHITE)
            lastPoint = null
            redrawKey++
            onSignatureChange(null)
        }
    }

    Box(
        modifier = modifier
            .background(Color.White)
            .onSizeChanged { size: IntSize ->
                if (bitmap == null && size.width > 0 && size.height > 0) {
                    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    val c = AndroidCanvas(bmp)
                    c.drawColor(AndroidColor.WHITE)
                    bitmap = bmp
                    canvasRef = c
                    redrawKey++
                }
            }
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        val down = awaitFirstDown()
                        val pos = down.position
                        canvasRef?.drawPoint(pos.x, pos.y, paint)
                        lastPoint = pos
                        redrawKey++
                        bitmap?.let { onSignatureChange(it) }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            lastPoint?.let { lp ->
                                canvasRef?.drawLine(
                                    lp.x, lp.y,
                                    change.position.x, change.position.y,
                                    paint
                                )
                            }
                            lastPoint = change.position
                            redrawKey++
                        }
                        lastPoint = null
                    }
                }
            }
    ) {
        bitmap?.let { bmp: Bitmap ->
            key(redrawKey) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Signature"
                )
            }
        }
    }
}
