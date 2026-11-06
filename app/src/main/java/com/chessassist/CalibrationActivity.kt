package com.chessassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CalibrationActivity : AppCompatActivity() {

    private lateinit var calibView: CalibrationView
    private lateinit var saveBtn: Button
    private lateinit var hintText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        calibView = findViewById(R.id.calibView)
        saveBtn = findViewById(R.id.saveBtn)
        hintText = findViewById(R.id.hintText)

        hintText.text = getString(R.string.calibration_hint)

        saveBtn.setOnClickListener {
            val rect = calibView.getSelectionRect()
            if (rect != null) {
                val prefs = getSharedPreferences("chessassist", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("board_left", rect.left.toInt())
                    .putInt("board_top", rect.top.toInt())
                    .putInt("board_right", rect.right.toInt())
                    .putInt("board_bottom", rect.bottom.toInt())
                    .apply()
                hintText.text = getString(R.string.calibration_saved)
                finish()
            } else {
                hintText.text = getString(R.string.calibration_no_selection)
            }
        }
    }
}

class CalibrationView(context: android.content.Context, attrs: android.util.AttributeSet? = null) :
    View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint().apply {
        color = Color.argb(60, 255, 0, 0)
        style = Paint.Style.FILL
    }
    private val cornerPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var hasSelection = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = event.x
                endY = event.y
                hasSelection = false
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                hasSelection = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                endX = event.x
                endY = event.y
                hasSelection = true
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hasSelection) {
            val left = minOf(startX, endX)
            val top = minOf(startY, endY)
            val right = maxOf(startX, endX)
            val bottom = maxOf(startY, endY)
            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, paint)
            val cs = 20f
            canvas.drawLine(left, top, left + cs, top, cornerPaint)
            canvas.drawLine(left, top, left, top + cs, cornerPaint)
            canvas.drawLine(right, top, right - cs, top, cornerPaint)
            canvas.drawLine(right, top, right, top + cs, cornerPaint)
            canvas.drawLine(left, bottom, left + cs, bottom, cornerPaint)
            canvas.drawLine(left, bottom, left, bottom - cs, cornerPaint)
            canvas.drawLine(right, bottom, right - cs, bottom, cornerPaint)
            canvas.drawLine(right, bottom, right, bottom - cs, cornerPaint)
        }
    }

    fun getSelectionRect(): RectF? {
        if (!hasSelection) return null
        val left = minOf(startX, endX)
        val top = minOf(startY, endY)
        val right = maxOf(startX, endX)
        val bottom = maxOf(startY, endY)
        if (right - left < 50f || bottom - top < 50f) return null
        return RectF(left, top, right, bottom)
    }
}
