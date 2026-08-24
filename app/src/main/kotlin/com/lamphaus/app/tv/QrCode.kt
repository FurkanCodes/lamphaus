package com.lamphaus.app.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun PairingQrCode(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) { payload.toQrBitmap(384) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Pairing QR code",
        modifier = modifier,
    )
}

private fun String.toQrBitmap(size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        this,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        ),
    )
    return createBitmap(size, size, Bitmap.Config.RGB_565).also { bitmap ->
        for (x in 0 until size) for (y in 0 until size) {
            bitmap[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
}
