package ru.stasoft.mynotes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import ru.stasoft.mynotes.data.SnippetEntity
import ru.stasoft.mynotes.data.expandSnippet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.stasoft.mynotes.crypto.BiometricVault
import ru.stasoft.mynotes.data.NoteEntity
import ru.stasoft.mynotes.data.displayTitle

@Composable
fun AppRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.unlocked) { vm.goUp() }
    val appCtx = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm.unlocked) {
        if (vm.unlocked && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !vm.unlocked -> LockScreen(vm)
            vm.props != null -> PropertiesScreen(vm, vm.props!!)
            vm.editing != null -> EditorScreen(vm, vm.editing!!)
            vm.showReminders -> ReminderListScreen(vm)
            else -> TreeScreen(vm)
        }
    }
}

// ------------------------------------------------------------------ вход

@Composable
fun LockScreen(vm: AppViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val canBio = vm.biometricEnabled && !vm.needSetup && vm.biometricAvailable()
    val promptBio: () -> Unit = {
        val cipher = BiometricVault.decryptCipher(ctx)
        if (cipher == null) {
            vm.disableBiometric()
        } else if (activity != null) {
            showBiometricPrompt(
                activity = activity,
                title = "Вход в МоиЗаметки",
                negativeText = "Ввести пароль",
                cipher = cipher,
                onSuccess = { vm.unlockWithBiometric(it) },
                onFailure = { vm.onBiometricFailure(it) },
            )
        }
    }
    // Сразу показываем запрос отпечатка при открытии и после автоблокировки.
    LaunchedEffect(canBio, vm.unlocked) {
        if (canBio && !vm.unlocked) promptBio()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("МоиЗаметки", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (vm.needSetup) "Придумайте пароль. Им шифруется вся база. Если забыть его, восстановить данные нельзя."
            else "Введите пароль",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (vm.needSetup) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Повторите пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    vm.submitPassword(password, if (vm.needSetup) confirm else null)
                    password = ""
                    confirm = ""
                },
                enabled = !vm.busy,
            ) { Text(if (vm.needSetup) "Создать" else "Открыть") }
            if (canBio) {
                TextButton(onClick = promptBio, modifier = Modifier.padding(start = 8.dp)) { Text("Отпечаток") }
            }
            if (vm.busy) CircularProgressIndicator(modifier = Modifier.padding(start = 16.dp))
        }
    }
}
