package com.chessassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts a cropped board bitmap to FEN using colour-channel template matching.
 * Piece PNGs must be in assets/pieces/: wp.png, wr.png, wn.png, wb.png, wq.png, wk.png,
 * bp.png, br.png, bn.png, bb.png, bq.png, bk.png
 * Each PNG should be square, any size; they are scaled to the detected cell size at runtime.
 * If assets are absent, returns the standard starting FEN as fallback.
 */
class BoardReader(private val context: Context) {

    data class PieceTemplate(val label: String, val bitmap: Bitmap)

    private val templates = mutableListOf<PieceTemplate>()

    // Piece label → FEN char
    private val labelToFen = mapOf(
        "wp" to 'P', "wr" to 'R', "wn" to 'N', "wb" to 'B', "wq" to 'Q', "wk" to 'K',
        "bp" to 'p', "br" to 'r', "bn" to 'n', "bb" to 'b', "bq" to 'q', "bk" to 'k'
    )

    private val pieceNames = listOf("wp","wr","wn","wb","wq","wk","bp","br","bn","bb","bq","bk")

    init {
        loadTemplates()
    }

    private fun loadTemplates() {
        val am = context.assets
        try {
            val files = am.list("pieces") ?: return
            for (name in files) {
                if (!name.endsWith(".png")) continue
                val label = name.removeSuffix(".png")
                if (label !in pieceNames) continue
                am.open("pieces/$name").use { stream ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream) ?: return@use
                    templates.add(PieceTemplate(label, bmp))
                }
            }
        } catch (_: Exception) {}
    }

    fun bitmapToFen(board: Bitmap): String {
        if (templates.isEmpty()) return STARTING_FEN

        val cellW = board.width / 8
        val cellH = board.height / 8
        if (cellW < 4 || cellH < 4) return STARTING_FEN

        val scaledTemplates = templates.map { t ->
            t.copy(bitmap = Bitmap.createScaledBitmap(t.bitmap, cellW, cellH, true))
        }

        val ranks = StringBuilder()
        for (rank in 0 until 8) {
            var empty = 0
            for (file in 0 until 8) {
                val cell = Bitmap.createBitmap(board, file * cellW, rank * cellH, cellW, cellH)
                val piece = matchPiece(cell, scaledTemplates)
                cell.recycle()
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) { ranks.append(empty); empty = 0 }
                    ranks.append(labelToFen[piece] ?: '.')
                }
            }
            if (empty > 0) ranks.append(empty)
            if (rank < 7) ranks.append('/')
        }

        scaledTemplates.forEach { it.bitmap.recycle() }
        return "$ranks w KQkq - 0 1"
    }

    /**
     * Sum of absolute pixel-channel differences, normalised. Lower = better match.
     * Returns the label of the best matching template if score < threshold.
     */
    private fun matchPiece(cell: Bitmap, scaled: List<PieceTemplate>): String? {
        val w = cell.width
        val h = cell.height
        val sampleStep = maxOf(1, w / 16)
        var bestLabel: String? = null
        var bestScore = Double.MAX_VALUE
        val emptyScore = emptySquareScore(cell, sampleStep)

        for (tmpl in scaled) {
            var diff = 0.0
            var count = 0
            for (y in 0 until h step sampleStep) {
                for (x in 0 until w step sampleStep) {
                    val cp = cell.getPixel(x, y)
                    val tp = tmpl.bitmap.getPixel(x, y)
                    diff += abs(Color.red(cp) - Color.red(tp)) +
                            abs(Color.green(cp) - Color.green(tp)) +
                            abs(Color.blue(cp) - Color.blue(tp))
                    count++
                }
            }
            val score = if (count > 0) diff / count else Double.MAX_VALUE
            if (score < bestScore) { bestScore = score; bestLabel = tmpl.label }
        }

        // If the cell looks more like an empty square than any piece, return null
        val threshold = 40.0
        return if (bestScore < threshold && bestScore < emptyScore * 0.8) bestLabel else null
    }

    /**
     * Estimates how "uniform" a cell is — empty squares are mostly one colour.
     */
    private fun emptySquareScore(cell: Bitmap, step: Int): Double {
        val pixels = mutableListOf<Triple<Int,Int,Int>>()
        for (y in 0 until cell.height step step)
            for (x in 0 until cell.width step step) {
                val p = cell.getPixel(x, y)
                pixels.add(Triple(Color.red(p), Color.green(p), Color.blue(p)))
            }
        if (pixels.isEmpty()) return Double.MAX_VALUE
        val avgR = pixels.map { it.first }.average()
        val avgG = pixels.map { it.second }.average()
        val avgB = pixels.map { it.third }.average()
        return pixels.sumOf {
            abs(it.first - avgR) + abs(it.second - avgG) + abs(it.third - avgB)
        } / pixels.size
    }

    companion object {
        const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
