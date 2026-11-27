package ru.stasoft.mynotes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** «Заметка по фото»: снять или выбрать снимок, дальше его разбирает [AppViewModel.photoInput]. Возвращает функцию «открыть выбор». */
@Composable
fun rememberPhotoNote(vm: AppViewModel): () -> Unit {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.photoInput(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) vm.photoInput(u)
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Заметка по фото") },
        text = { Text("Если на снимке QR-код кассового чека, сумма и дата прочитаются на телефоне. Магазин, товары и обычный текст читает GigaChat (фото уходит в облако Сбера и сразу удаляется). Снимок можно прикрепить к заметке.") },
        confirmButton = {
            TextButton(onClick = {
                ask = false
                val dir = File(ctx.cacheDir, "cam").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "shot_${System.currentTimeMillis()}.jpg"))
                camUri = u
                try { camera.launch(u) } catch (_: Exception) { vm.message = "Камера недоступна" }
            }) { Text("Снять") }
        },
        dismissButton = {
            TextButton(onClick = {
                ask = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Из галереи") }
        },
    )
    return { ask = true }
}
