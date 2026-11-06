package com.chessassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var projectionResultCode: Int = 0
        var projectionData: Intent? = null
        const val CHANNEL_ID = "chess_assist_channel"
        const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private lateinit var arrowView: ArrowOverlayView
    private lateinit var panelView: AnalysisPanelView
    private lateinit var boardReader: BoardReader
    private lateinit var stockfish: StockfishEngine

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler = Handler(Looper.getMainLooper())
    private var autoRefresh = true
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val refreshRunnable = object : Runnable {
        override fun run() {
            captureAndAnalyze()
            if (autoRefresh) handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        windowManager = wm
        boardReader = BoardReader(this)
        stockfish = StockfishEngine()
        stockfish.init()

        setupOverlayViews()
        setupMediaProjection()
        handler.postDelayed(refreshRunnable, 500)
    }

    private fun setupOverlayViews() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val arrowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        arrowView = ArrowOverlayView(this)
        windowManager.addView(arrowView, arrowParams)

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        panelParams.x = 0
        panelParams.y = 200
        panelView = AnalysisPanelView(this, windowManager, panelParams)
        windowManager.addView(panelView, panelParams)
        panelView.onRefreshClick = { captureAndAnalyze() }
        panelView.onAutoRefreshToggle = { enabled -> autoRefresh = enabled }
    }

    private fun setupMediaProjection() {
        val projData = projectionData ?: return
        val projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projManager.getMediaProjection(projectionResultCode, projData)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ChessAssist",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun captureAndAnalyze() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val prefs = getSharedPreferences("chessassist", Context.MODE_PRIVATE)
            val boardRect = Rect(
                prefs.getInt("board_left", 0),
                prefs.getInt("board_top", 0),
                prefs.getInt("board_right", screenWidth),
                prefs.getInt("board_bottom", screenHeight)
            )

            val cropped = Bitmap.createBitmap(
                bitmap,
                boardRect.left.coerceIn(0, bitmap.width - 1),
                boardRect.top.coerceIn(0, bitmap.height - 1),
                (boardRect.width()).coerceIn(1, bitmap.width - boardRect.left),
                (boardRect.height()).coerceIn(1, bitmap.height - boardRect.top)
            )
            bitmap.recycle()

            val fen = boardReader.bitmapToFen(cropped)
            cropped.recycle()

            stockfish.setPosition(fen)
            stockfish.go(depth = 18, multiPV = 3) { info ->
                handler.post {
                    arrowView.setBoardRect(boardRect)
                    arrowView.setMove(info.bestMove)
                    arrowView.setSecondMove(info.secondMove)
                    panelView.update(fen, info)
                }
            }
        } finally {
            image.close()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChessAssist Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Chess overlay running" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(android.R.drawable.ic_delete, getString(R.string.stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        stockfish.quit()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        runCatching { windowManager.removeView(arrowView) }
        runCatching { windowManager.removeView(panelView) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
