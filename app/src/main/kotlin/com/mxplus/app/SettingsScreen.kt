package com.mxplus.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var picker by remember { mutableIntStateOf(0) }
    var showLicenses by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 14.dp))

        Section("Appearance") {
            ValueRow(Icons.Rounded.Brightness6, "Theme", listOf("System", "Dark", "Light")[Prefs.theme.coerceIn(0, 2)]) { picker = 1 }
            Divider()
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBox(Icons.Rounded.Palette)
                Spacer(Modifier.width(14.dp))
                Text("Accent colour", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                AccentColors.forEachIndexed { i, (_, color) ->
                    Box(
                        Modifier.padding(start = 8.dp).size(if (Prefs.accent == i) 26.dp else 22.dp).clip(CircleShape).background(color)
                            .clickable { Prefs.accent = i; Prefs.save() },
                    )
                }
            }
            Divider()
            SwitchRow(Icons.Rounded.AutoAwesome, "Glass effects", Prefs.glass) { Prefs.glass = it; Prefs.save() }
        }

        Section("Player") {
            ValueRow(Icons.Rounded.FastForward, "Double-tap seek", "${Prefs.seekSeconds} sec") { picker = 2 }
            Divider()
            ValueRow(Icons.Rounded.Speed, "Default speed", speedText(Prefs.defaultSpeed)) { picker = 3 }
            Divider()
            SwitchRow(Icons.Rounded.History, "Resume playback", Prefs.resume) { Prefs.resume = it; Prefs.save() }
            Divider()
            SwitchRow(Icons.Rounded.TouchApp, "Swipe gestures", Prefs.gestures) { Prefs.gestures = it; Prefs.save() }
            Divider()
            SwitchRow(Icons.Rounded.SkipNext, "Play next video automatically", Prefs.autoNext) { Prefs.autoNext = it; Prefs.save() }
            Divider()
            SwitchRow(Icons.Rounded.ScreenLockPortrait, "Keep screen on while playing", Prefs.keepOn) { Prefs.keepOn = it; Prefs.save() }
        }

        Section("Access") {
            ValueRow(Icons.Rounded.Lock, "Photos and videos permission", "Open settings") {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                context.startActivity(i)
            }
        }

        Section("About") {
            ValueRow(Icons.Rounded.Info, "MX Plus", "v0.2.0") {}
            Divider()
            ValueRow(Icons.Rounded.Description, "Licenses and credits", "") { showLicenses = true }
        }
        Spacer(Modifier.height(120.dp))
    }

    when (picker) {
        1 -> Picker("Theme", listOf("System", "Dark", "Light"), Prefs.theme, { Prefs.theme = it; Prefs.save(); picker = 0 }) { picker = 0 }
        2 -> {
            val opts = listOf(5, 10, 30)
            Picker("Double-tap seek", opts.map { "$it sec" }, opts.indexOf(Prefs.seekSeconds), { Prefs.seekSeconds = opts[it]; Prefs.save(); picker = 0 }) { picker = 0 }
        }
        3 -> {
            val opts = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            Picker("Default speed", opts.map { speedText(it) }, opts.indexOf(Prefs.defaultSpeed), { Prefs.defaultSpeed = opts[it]; Prefs.save(); picker = 0 }) { picker = 0 }
        }
    }
    if (showLicenses) {
        Dialog(onDismissRequest = { showLicenses = false }) {
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
                Text("Licenses and credits", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "MX Plus is free software under the GNU General Public License v3.\n\n" +
                        "The player controls and the glass effect are based on NETMAX (github.com/devraj-99/netmax-v3), " +
                        "which is built on Nuvio, GPLv3.\n\n" +
                        "Playback uses AndroidX Media3 (Apache 2.0). Blur uses Haze by Chris Banes (Apache 2.0).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private fun speedText(s: Float) = (if (s % 1f == 0f) "%.0f".format(s) else "%.2f".format(s).trimEnd('0')) + "x"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))
}

@Composable
private fun IconBox(icon: ImageVector) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ValueRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(14.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).clickable { onChange(!checked) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon)
        Spacer(Modifier.width(14.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun Picker(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 14.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            options.forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth().height(52.dp).clickable { onPick(i) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
