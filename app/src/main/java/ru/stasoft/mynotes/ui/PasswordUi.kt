package ru.stasoft.mynotes.ui

import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Включение и выключение пароля (Настройки → Безопасность). */
@Composable
fun PasswordToggleDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val turningOff = !vm.noPassword
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hide = androidx.compose.ui.text.input.PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (turningOff) "Выключить пароль?" else "Включить пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (turningOff) (if (vm.biometricEnabled) "Подтвердите отпечатком (если пароль забыт) или введите текущий пароль. " else "") + "Приложение будет открываться сразу, без пароля и автоблокировки. База останется зашифрованной, но ключ будет храниться в защищённой области телефона, а не вычисляться из пароля: любой, у кого в руках разблокированный телефон, увидит записи. Резервные копии в этом режиме тоже не защищены паролем."
                    else "Придумайте пароль: им снова будет защищён вход. Если забыть его, восстановить данные нельзя.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text(if (turningOff) "Текущий пароль" else "Новый пароль") }, singleLine = true, visualTransformation = hide, modifier = Modifier.fillMaxWidth())
                if (!turningOff) OutlinedTextField(value = pw2, onValueChange = { pw2 = it }, label = { Text("Повторите пароль") }, singleLine = true, visualTransformation = hide, modifier = Modifier.fillMaxWidth())
                if (turningOff && vm.biometricEnabled) {
                    val ctx = LocalContext.current
                    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        val activity = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstOrNull { it is androidx.fragment.app.FragmentActivity } as? androidx.fragment.app.FragmentActivity
                        val cipher = ru.stasoft.mynotes.crypto.BiometricVault.decryptCipher(ctx)
                        if (activity == null) {
                            err = "Не удалось показать запрос отпечатка: введите пароль"
                        } else if (cipher == null) {
                            err = "Ключ отпечатка недоступен: введите пароль"
                        } else {
                            showBiometricPrompt(
                                activity = activity, title = "Выключить пароль", negativeText = "Отмена", cipher = cipher,
                                onSuccess = { c ->
                                    busy = true
                                    scope.launch {
                                        val r = vm.disablePasswordWithBiometric(c)
                                        busy = false
                                        if (r == null) onDismiss() else err = r
                                    }
                                },
                                onFailure = { err = it },
                            )
                        }
                    }) { Text("Подтвердить отпечатком") }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && pw.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val r = if (turningOff) vm.disablePassword(pw) else vm.enablePassword(pw, pw2)
                    busy = false
                    if (r == null) onDismiss() else err = r
                }
            }) { Text(if (turningOff) "Выключить" else "Включить") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}
