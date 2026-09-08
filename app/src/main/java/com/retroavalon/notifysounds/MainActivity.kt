package com.retroavalon.notifysounds

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(val label: String, val packageName: String, val icon: android.graphics.Bitmap?)

class MainActivity : ComponentActivity() {

    private var previewPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = appColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotifySoundsScreen(
                        onPlayPreview = { uri -> playPreview(uri) }
                    )
                }
            }
        }
    }

    private fun playPreview(uri: Uri) {
        try {
            previewPlayer?.release()
            val player = MediaPlayer()
            previewPlayer = player
            player.setDataSource(this, uri)
            player.setOnCompletionListener { it.release() }
            player.prepare()
            player.start()
        } catch (_: Exception) {
            // Bad file or revoked permission — silently ignore, the UI stays responsive.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.release()
    }
}

@Composable
fun appColorScheme() = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    secondary = Color(0xFF00D1B2),
    background = Color(0xFF12142B),
    surface = Color(0xFF1B1F3B),
    surfaceVariant = Color(0xFF232748)
)

@Composable
fun NotifySoundsScreen(onPlayPreview: (Uri) -> Unit) {
    val context = LocalContextHolder()
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    var targetPackage by remember { mutableStateOf(Prefs.getTargetPackage(context)) }
    var timeoutSeconds by remember { mutableStateOf(Prefs.getTimeoutSeconds(context).toFloat()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var slotRefreshTick by remember { mutableStateOf(0) }

    val refreshListenerState: () -> Unit = { listenerEnabled = isListenerEnabled(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "NotifySounds",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Different sound per consecutive message, like a kill-streak.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // --- Notification access status ---
        StatusCard(
            enabled = listenerEnabled,
            onGrantClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        Spacer(Modifier.height(16.dp))

        // --- Target app ---
        SectionCard(title = "Track notifications from", icon = Icons.Filled.Apps) {
            val label = targetPackage?.let { appLabelFor(context, it) } ?: "No app selected"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Medium)
                    if (targetPackage != null) {
                        Text(
                            targetPackage!!,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                TextButton(onClick = { showAppPicker = true }) { Text("Choose") }
            }
            if (targetPackage != null) {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, targetPackage)
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Filled.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mute its default sound", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Reset timeout ---
        SectionCard(title = "Reset streak after", icon = Icons.Filled.Timer) {
            Text(
                "${timeoutSeconds.toInt()}s of no new messages",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
            Slider(
                value = timeoutSeconds,
                onValueChange = { timeoutSeconds = it },
                onValueChangeFinished = {
                    Prefs.setTimeoutSeconds(context, timeoutSeconds.toInt())
                },
                valueRange = 5f..120f,
                steps = 22
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- Sound slots ---
        SectionCard(title = "Sounds per streak count", icon = Icons.Filled.MusicNote) {
            Column {
                for (slot in 1..Prefs.MAX_SLOTS) {
                    key(slot, slotRefreshTick) {
                        SlotRow(
                            slot = slot,
                            isLast = slot == Prefs.MAX_SLOTS,
                            onPreview = onPlayPreview,
                            onChanged = { slotRefreshTick++ }
                        )
                    }
                    if (slot != Prefs.MAX_SLOTS) {
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelected = {
                targetPackage = it
                Prefs.setTargetPackage(context, it)
                showAppPicker = false
            }
        )
    }

    // Re-check listener permission whenever the screen resumes (e.g. back from Settings).
    DisposableEffectOnResume(refreshListenerState)
}

@Composable
fun StatusCard(enabled: Boolean, onGrantClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (enabled) "Notification access granted" else "Notification access needed",
                    fontWeight = FontWeight.Medium
                )
                if (!enabled) {
                    Text(
                        "Required so the app can see incoming messages",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (!enabled) {
                Button(onClick = onGrantClick) { Text("Grant") }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun SlotRow(slot: Int, isLast: Boolean, onPreview: (Uri) -> Unit, onChanged: () -> Unit) {
    val context = LocalContextHolder()
    val currentUri = Prefs.getSlotSound(context, slot)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* some providers don't support persisting, still usable this session */ }
            Prefs.setSlotSound(context, slot, uri.toString())
            onChanged()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isLast) "$slot+" else "$slot",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isLast) "Message $slot and beyond" else "Message $slot",
                fontSize = 14.sp
            )
            Text(
                currentUri?.let { fileNameFor(context, Uri.parse(it)) } ?: "No sound set",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (currentUri != null) {
            IconButton(onClick = { onPreview(Uri.parse(currentUri)) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Preview")
            }
        }
        TextButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
            Text(if (currentUri == null) "Set" else "Change")
        }
    }
}

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val context = LocalContextHolder()
    val apps = remember { loadInstalledApps(context) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filtered) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableSimple { onSelected(app.packageName) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(app.label)
                        }
                    }
                }
            }
        }
    )
}

// --- small helpers kept at the bottom so the UI code above reads top-to-bottom ---

@Composable
fun LocalContextHolder(): Context = androidx.compose.ui.platform.LocalContext.current

fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

fun isListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ) ?: return false
    val myListener = ComponentName(context, NotifySoundService::class.java).flattenToString()
    return enabledListeners.contains(myListener)
}

fun appLabelFor(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: Exception) {
    packageName
}

fun fileNameFor(context: Context, uri: Uri): String {
    return try {
        var name = "sound"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        name
    } catch (_: Exception) {
        "sound"
    }
}

fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, 0)
    return resolved
        .distinctBy { it.activityInfo.packageName }
        .map {
            val info: ApplicationInfo = it.activityInfo.applicationInfo
            AppInfo(
                label = pm.getApplicationLabel(info).toString(),
                packageName = info.packageName,
                icon = try { pm.getApplicationIcon(info).toBitmap() } catch (_: Exception) { null }
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun DisposableEffectOnResume(onResume: () -> Unit) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
