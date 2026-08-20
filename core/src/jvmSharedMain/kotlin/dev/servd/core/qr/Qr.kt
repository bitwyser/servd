package dev.servd.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders text as a QR code, as a self-contained SVG string (no rasterization, works offline). */
object Qr {
    fun svg(text: String, quietZone: Int = 2): String {
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to quietZone,
        )
        // width/height 0 -> the writer uses one pixel per module (the raw grid).
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val w = matrix.width
        val h = matrix.height

        val path = StringBuilder()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (matrix.get(x, y)) path.append('M').append(x).append(' ').append(y).append("h1v1h-1z")
            }
        }
        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(w).append(' ').append(h)
            append("\" shape-rendering=\"crispEdges\">")
            append("<rect width=\"").append(w).append("\" height=\"").append(h).append("\" fill=\"#ffffff\"/>")
            append("<path fill=\"#000000\" d=\"").append(path).append("\"/>")
            append("</svg>")
        }
    }
}
