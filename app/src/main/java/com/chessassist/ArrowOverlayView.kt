package com.chessassist

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ArrowOverlayView(context: Context) : View(context) {

    private data class Arrow(val move: String, val color: Int)

    private val arrows = mutableListOf<Arrow>()
    private var boardRect: Rect = Rect(0, 0, 0, 0)
    private var animFraction = 0f

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 12f
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 200
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            animFraction = anim.animatedValue as Float
            invalidate()
        }
    }

    fun setBoardRect(rect: Rect) {
        boardRect = rect
    }

    fun setMove(uci: String) {
        arrows.clear()
        if (uci.length >= 4) {
            arrows.add(Arrow(uci, Color.argb(200, 0, 200, 80)))
        }
        if (animator.isRunning) animator.cancel()
        animFraction = 0f
        animator.start()
    }

    fun setSecondMove(uci: String) {
        arrows.removeAll { it.color == Color.argb(200, 230, 200, 0) }
        if (uci.length >= 4) {
            arrows.add(Arrow(uci, Color.argb(200, 230, 200, 0)))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boardRect.isEmpty) return
        for (arrow in arrows) {
            drawArrow(canvas, arrow.move, arrow.color)
        }
    }

    private fun drawArrow(canvas: Canvas, uci: String, color: Int) {
        if (uci.length < 4) return
        val fromFile = uci[0] - 'a'
        val fromRank = 8 - (uci[1] - '0')
        val toFile = uci[2] - 'a'
        val toRank = 8 - (uci[3] - '0')

        if (fromFile !in 0..7 || fromRank !in 0..7 ||
            toFile !in 0..7 || toRank !in 0..7) return

        val cellW = boardRect.width() / 8f
        val cellH = boardRect.height() / 8f

        val startX = boardRect.left + (fromFile + 0.5f) * cellW
        val startY = boardRect.top + (fromRank + 0.5f) * cellH
        val endX = boardRect.left + (toFile + 0.5f) * cellW
        val endY = boardRect.top + (toRank + 0.5f) * cellH

        // Animate end point along the line
        val curEndX = startX + (endX - startX) * animFraction
        val curEndY = startY + (endY - startY) * animFraction

        arrowPaint.color = color
        arrowPaint.strokeWidth = minOf(cellW, cellH) * 0.18f

        // Shaft
        canvas.drawLine(startX, startY, curEndX, curEndY, arrowPaint)

        if (animFraction >= 0.95f) {
            // Arrowhead
            val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
            val headLen = minOf(cellW, cellH) * 0.45f
            val headAngle = Math.PI / 6
            val path = Path()
            path.moveTo(endX, endY)
            path.lineTo(
                (endX - headLen * cos(angle - headAngle)).toFloat(),
                (endY - headLen * sin(angle - headAngle)).toFloat()
            )
            path.lineTo(
                (endX - headLen * cos(angle + headAngle)).toFloat(),
                (endY - headLen * sin(angle + headAngle)).toFloat()
            )
            path.close()
            arrowPaint.style = Paint.Style.FILL
            canvas.drawPath(path, arrowPaint)
            arrowPaint.style = Paint.Style.FILL_AND_STROKE
        }
    }
}
