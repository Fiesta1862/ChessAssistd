package com.chessassist

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class AnalysisInfo(
    val bestMove: String,       // e.g. "e2e4"
    val secondMove: String,     // e.g. "d2d4" or empty
    val thirdMove: String,      // e.g. "g1f3" or empty
    val bestMoveSan: String,    // e.g. "e4" (derived from UCI if SAN not available)
    val scoreCP: Int?,          // centipawns, null if mate
    val mateIn: Int?,           // non-null if forced mate
    val depth: Int,
    val nodes: Long,
    val timeMs: Long,
    val multiPvLines: List<String>  // raw PV strings for display
)

class StockfishEngine {

    companion object {
        init {
            System.loadLibrary("stockfish")
        }
    }

    // JNI methods — implemented in libstockfish.so
    private external fun nativeInit(): Long
    private external fun nativeSendCommand(handle: Long, cmd: String)
    private external fun nativeReadLine(handle: Long, timeoutMs: Long): String?
    private external fun nativeQuit(handle: Long)

    private var handle: Long = 0
    private val executor = Executors.newSingleThreadExecutor()
    private var currentPosition = "startpos"
    private var analysisDepth = 18
    private var multiPV = 3

    fun init() {
        handle = nativeInit()
        send("uci")
        waitFor("uciok", 3000)
        send("setoption name MultiPV value $multiPV")
        send("isready")
        waitFor("readyok", 3000)
    }

    fun setPosition(fen: String) {
        currentPosition = fen
        send("position fen $fen")
    }

    fun setDepth(depth: Int) { analysisDepth = depth.coerceIn(10, 30) }

    fun go(depth: Int = analysisDepth, multiPV: Int = this.multiPV, callback: (AnalysisInfo) -> Unit) {
        executor.submit {
            send("setoption name MultiPV value $multiPV")
            send("go depth $depth")
            val info = collectInfo(depth, multiPV)
            callback(info)
        }
    }

    fun quit() {
        send("quit")
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
        if (handle != 0L) nativeQuit(handle)
    }

    private fun send(cmd: String) {
        if (handle != 0L) nativeSendCommand(handle, cmd)
    }

    private fun waitFor(token: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = nativeReadLine(handle, 100) ?: continue
            if (line.contains(token)) return
        }
    }

    private fun collectInfo(depth: Int, pvCount: Int): AnalysisInfo {
        // Collect lines until "bestmove"
        val pvLines = Array(pvCount) { "" }
        var scoreCP: Int? = null
        var mateIn: Int? = null
        var reachedDepth = 0
        var nodes = 0L
        var timeMs = 0L
        var bestMove = ""
        var secondMove = ""
        var thirdMove = ""
        val deadline = System.currentTimeMillis() + 30_000

        while (System.currentTimeMillis() < deadline) {
            val line = nativeReadLine(handle, 200) ?: continue
            when {
                line.startsWith("bestmove") -> {
                    val parts = line.split(" ")
                    if (parts.size >= 2) bestMove = parts[1].trim()
                    break
                }
                line.startsWith("info") -> {
                    val parts = line.split(" ")
                    var i = 0
                    var pvIndex = 0
                    var localScore: Int? = null
                    var localMate: Int? = null
                    var localDepth = 0
                    var localNodes = 0L
                    var localTime = 0L
                    var pvStr = ""

                    while (i < parts.size) {
                        when (parts[i]) {
                            "multipv" -> pvIndex = parts.getOrNull(i + 1)?.toIntOrNull()?.minus(1) ?: 0
                            "depth" -> localDepth = parts.getOrNull(i + 1)?.toIntOrNull() ?: 0
                            "nodes" -> localNodes = parts.getOrNull(i + 1)?.toLongOrNull() ?: 0L
                            "time" -> localTime = parts.getOrNull(i + 1)?.toLongOrNull() ?: 0L
                            "score" -> {
                                val type = parts.getOrNull(i + 1) ?: ""
                                val value = parts.getOrNull(i + 2)?.toIntOrNull() ?: 0
                                if (type == "cp") localScore = value
                                else if (type == "mate") localMate = value
                            }
                            "pv" -> {
                                pvStr = parts.drop(i + 1).joinToString(" ")
                                break
                            }
                        }
                        i++
                    }

                    if (pvIndex in 0 until pvCount) {
                        pvLines[pvIndex] = pvStr
                        if (pvIndex == 0) {
                            reachedDepth = localDepth
                            nodes = localNodes
                            timeMs = localTime
                            scoreCP = localScore
                            mateIn = localMate
                        }
                    }
                }
            }
        }

        // Extract moves from PV lines
        val pv0 = pvLines.getOrElse(0) { "" }.split(" ")
        val pv1 = pvLines.getOrElse(1) { "" }.split(" ")
        val pv2 = pvLines.getOrElse(2) { "" }.split(" ")

        if (bestMove.isEmpty()) bestMove = pv0.firstOrNull() ?: ""
        secondMove = pv1.firstOrNull() ?: ""
        thirdMove = pv2.firstOrNull() ?: ""

        return AnalysisInfo(
            bestMove = bestMove,
            secondMove = secondMove,
            thirdMove = thirdMove,
            bestMoveSan = uciToDisplaySan(bestMove),
            scoreCP = scoreCP,
            mateIn = mateIn,
            depth = reachedDepth,
            nodes = nodes,
            timeMs = timeMs,
            multiPvLines = pvLines.take(pvCount).filter { it.isNotEmpty() }
        )
    }

    private fun uciToDisplaySan(uci: String): String {
        // UCI is already human-readable enough for display: e2e4 → show as-is
        // Full SAN requires board state; UCI move is sufficient for overlay
        return uci
    }
}
