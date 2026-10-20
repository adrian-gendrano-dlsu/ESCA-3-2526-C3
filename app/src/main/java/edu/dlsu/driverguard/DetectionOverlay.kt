package edu.dlsu.driverguard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

class DetectionOverlay(context: Context) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37F3AC.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14f * resources.displayMetrics.scaledDensity
    }
    var mirrorX = true
    private var boxes: List<Detection> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1

    fun update(detections: List<Detection>, sourceWidth: Int, sourceHeight: Int) {
        boxes = detections
        imageWidth = sourceWidth.coerceAtLeast(1)
        imageHeight = sourceHeight.coerceAtLeast(1)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val xOffset = (width - imageWidth * scale) / 2f
        val yOffset = (height - imageHeight * scale) / 2f
        for (item in boxes) {
            // Mirror detections only for the front camera preview.
            val r = item.box
            val box = RectF(
                xOffset + (if (mirrorX) imageWidth - r.right else r.left) * scale,
                yOffset + r.top * scale,
                xOffset + (if (mirrorX) imageWidth - r.left else r.right) * scale,
                yOffset + r.bottom * scale
            )
            canvas.drawRect(box, stroke)
            val caption = "${item.label} ${(item.score * 100).toInt()}%"
            canvas.drawText(caption, box.left, (box.top - 8f).coerceAtLeast(textPaint.textSize), textPaint)
        }
    }
}
