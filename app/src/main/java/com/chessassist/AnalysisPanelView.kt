package com.chessassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class AnalysisPanelView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams
) : LinearLayout(context) {

    var onRefreshClick: (() -> Unit)? = null
    var onAutoRefreshToggle: ((Boolean) -> Unit)? = null

    private val fenText: TextView
    private val bestMoveText: TextView
    private val evalText: TextView
    private val pv1Text: TextView
    private val pv2Text: TextView
    private val pv3Text: TextView
    private val depthText: TextView
    private val evalBar: EvalBarView
    private val refreshBtn: Button
    private val autoSwitch: Switch
    private val collapseBtn: Button

    private var collapsed = false
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(220, 20, 20, 20))
        setPadding(dp(8), dp(8), dp(8), dp(8))

        collapseBtn = Button(context).apply {
            text = "▼ ChessAssist"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(255, 40, 40, 40))
            textSize = 12f
        }
        addView(collapseBtn)

        fenText = textView("FEN: —", 9f)
        bestMoveText = textView("Best: —", 14f).apply { setTextColor(Color.GREEN) }
        evalText = textView("Eval: —", 13f)
        evalBar = EvalBarView(context)
        pv1Text = textView("1. —", 11f)
        pv2Text = textView("2. —", 11f).apply { setTextColor(Color.argb(255, 220, 200, 0)) }
        pv3Text = textView("3. —", 11f).apply { setTextColor(Color.LTGRAY) }
        depthText = textView("depth — | nodes — | time —", 9f).apply { setTextColor(Color.GRAY) }

        val btnRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        refreshBtn = Button(context).apply {
            text = "⟳"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            layoutParams = LayoutParams(dp(48), dp(36))
        }
        autoSwitch = Switch(context).apply {
            text = "Auto"
            setTextColor(Color.WHITE)
            isChecked = true
        }
        btnRow.addView(refreshBtn)
        btnRow.addView(autoSwitch)

        listOf(fenText, bestMoveText, evalText, evalBar, pv1Text, pv2Text, pv3Text, depthText, btnRow)
            .forEach { addView(it) }

        collapseBtn.setOnClickListener { toggleCollapse() }
        refreshBtn.setOnClickListener { onRefreshClick?.invoke() }
        autoSwitch.setOnCheckedChangeListener { _, checked -> onAutoRefreshToggle?.invoke(checked) }

        setupDrag()
    }

    private fun toggleCollapse() {
        collapsed = !collapsed
        val visibility = if (collapsed) GONE else VISIBLE
        listOf(fenText, bestMoveText, evalText, evalBar, pv1Text, pv2Text, pv3Text, depthText)
            .forEach { it.visibility = visibility }
        collapseBtn.text = if (collapsed) "▶ ChessAssist" else "▼ ChessAssist"
    }

    fun update(fen: String, info: AnalysisInfo) {
        fenText.text = "FEN: ${fen.take(40)}…"
        bestMoveText.text = "Best: ${info.bestMove} (${info.bestMoveSan})"
        evalText.text = when {
            info.mateIn != null -> "Eval: M${info.mateIn}"
            info.scoreCP != null -> "Eval: ${"%.2f".format(info.scoreCP / 100.0)}"
            else -> "Eval: —"
        }
        evalBar.setScore(info.scoreCP, info.mateIn)
        pv1Text.text = "1. ${info.multiPvLines.getOrElse(0) { "—" }.take(40)}"
        pv2Text.text = "2. ${info.multiPvLines.getOrElse(1) { "—" }.take(40)}"
        pv3Text.text = "3. ${info.multiPvLines.getOrElse(2) { "—" }.take(40)}"
        depthText.text = "d${info.depth} | ${formatNodes(info.nodes)} | ${info.timeMs}ms"
    }

    private fun formatNodes(n: Long) = when {
        n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
        n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}K"
        else -> "$n"
    }

    private fun textView(text: String, sp: Float) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun setupDrag() {
        var dragStartX = 0f
        var dragStartY = 0f
        var paramStartX = 0
        var paramStartY = 0

        collapseBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    paramStartX = layoutParams.x
                    paramStartY = layoutParams.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragStartX).toInt()
                    val dy = (event.rawY - dragStartY).toInt()
                    if (dx * dx + dy * dy > 25) {
                        layoutParams.x = paramStartX + dx
                        layoutParams.y = paramStartY + dy
                        windowManager.updateViewLayout(this@AnalysisPanelView, layoutParams)
                        true
                    } else false
                }
                else -> false
            }
        }
    }
}

class EvalBarView(context: Context) : View(context) {

    private val whitePaint = Paint().apply { color = Color.WHITE }
    private val blackPaint = Paint().apply { color = Color.argb(255, 30, 30, 30) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 24f
    }

    private var whiteFraction = 0.5f

    fun setScore(cp: Int?, mateIn: Int?) {
        whiteFraction = when {
            mateIn != null -> if (mateIn > 0) 0.95f else 0.05f
            cp != null -> (0.5f + cp.coerceIn(-800, 800) / 1600f).coerceIn(0.05f, 0.95f)
            else -> 0.5f
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(20, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val split = w * whiteFraction
        canvas.drawRect(0f, 0f, split, h, whitePaint)
        canvas.drawRect(split, 0f, w, h, blackPaint)
    }
}
