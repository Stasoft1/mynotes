package ru.stasoft.mynotes.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import ru.stasoft.mynotes.data.PhotoEntity
import ru.stasoft.mynotes.data.PhotoStore
import java.io.File

/** Выбор источника: галерея (несколько снимков) или камера. Возвращает функцию «открыть выбор». */
@Composable
fun rememberPhotoPicker(vm: AppViewModel, noteId: Long): () -> Unit {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.addPhotos(noteId, uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) vm.addPhotos(noteId, listOf(u))
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Добавить фото") },
        text = { Text("Снимок уменьшится и сохранится в защищённом хранилище приложения, база не вырастет.") },
        confirmButton = {
            TextButton(onClick = {
                ask = false
                val dir = File(ctx.cacheDir, "cam").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
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

/** Лента миниатюр фото заметки; нажатие открывает снимок на весь экран. */
@Composable
fun PhotoStrip(vm: AppViewModel, noteId: Long, readOnly: Boolean) {
    val photos by remember(noteId) { vm.photosOf(noteId) }.collectAsState(emptyList())
    var open by remember { mutableStateOf<PhotoEntity?>(null) }
    if (photos.isEmpty()) return
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        items(photos, key = { it.id }) { p ->
            Thumb(p.file, Modifier.padding(end = 6.dp).size(76.dp).clip(RoundedCornerShape(10.dp)).clickable { open = p })
        }
    }
    open?.let { p ->
        PhotoViewer(p, readOnly, onDelete = { vm.removePhoto(p); open = null }, onDismiss = { open = null })
    }
}

@Composable
private fun Thumb(file: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(file) { PhotoStore.bitmap(ctx, file, 240) }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun PhotoViewer(p: PhotoEntity, readOnly: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val bmp = remember(p.file) { PhotoStore.bitmap(ctx, p.file, 2000) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize().clickable { onDismiss() }, contentScale = ContentScale.Fit)
            else Text("Не удалось открыть фото", color = Color.White, modifier = Modifier.align(Alignment.Center))
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(8.dp)) {
                TextButton(onClick = {
                    val b = PhotoStore.bytes(ctx, p.file) ?: return@TextButton
                    val dir = File(ctx.cacheDir, "cam").apply { mkdirs() }
                    val f = File(dir, "photo_${p.id}.jpg").also { it.writeBytes(b) }
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Фото").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Отправить", color = Color.White) }
                if (!readOnly) TextButton(onClick = { confirm = true }) { Text("Удалить", color = Color(0xFFFF8A80)) }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Закрыть", color = Color.White) }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Удалить фото?") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
    )
}
