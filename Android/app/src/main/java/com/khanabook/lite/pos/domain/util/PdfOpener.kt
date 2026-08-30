package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import java.io.File

object PdfOpener {
    suspend fun open(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open document"))
        } catch (e: Exception) {
            KhanaToast.show("No app available to open PDF", ToastKind.Warning)
        }
    }
}
