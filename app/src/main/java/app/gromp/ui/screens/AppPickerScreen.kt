package app.gromp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gromp.data.db.LockRule
import app.gromp.ui.components.AppIcon
import app.gromp.ui.components.GIcons
import app.gromp.ui.components.ListRow
import app.gromp.ui.components.LocalContainer
import app.gromp.ui.components.rememberLoaded
import app.gromp.ui.theme.G
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lists installed apps. In [forFocus] mode, tapping toggles "rests during focus hours";
 * otherwise it opens the app's detail page where its locks live.
 */
@Composable
fun AppPickerScreen(forFocus: Boolean, onBack: () -> Unit, onOpenApp: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val apps = rememberLoaded { withContext(Dispatchers.IO) { c.catalog.lockableApps() } }
    val rules by remember { c.repository.rulesFlow() }.collectAsState(initial = emptyList())
    val byPkg = rules.associateBy { it.pkg }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(GIcons.Back, contentDescription = "Back", tint = G.Text) }
            Text(if (forFocus) "Apps that rest" else "Add a lock", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = G.Text)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = G.Sage, unfocusedBorderColor = G.Line,
                focusedLabelColor = G.Sage, unfocusedLabelColor = G.Muted, cursorColor = G.Sage,
                focusedTextColor = G.Text, unfocusedTextColor = G.Text,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val list = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(list, key = { it.pkg }) { app ->
                val rule = byPkg[app.pkg]
                if (forFocus) {
                    val on = rule?.focusBlocked == true
                    val toggle = {
                        scope.launch { c.repository.saveRule((rule ?: LockRule(app.pkg)).copy(focusBlocked = !on)) }
                        Unit
                    }
                    ListRow(app.label, leading = { AppIcon(app.pkg, 36.dp, 10.dp) }, onClick = toggle) {
                        Checkbox(checked = on, onCheckedChange = { toggle() }, colors = CheckboxDefaults.colors(checkedColor = G.Sage, checkmarkColor = G.Bg))
                    }
                } else {
                    val summary = rule?.let {
                        listOfNotNull(
                            it.dailyLimitMin?.let { m -> "${m}m limit" },
                            if (it.pauseEnabled) "pause" else null,
                            if (it.focusBlocked) "focus" else null,
                        ).joinToString(" · ").ifEmpty { null }
                    }
                    ListRow(app.label, summary, leading = { AppIcon(app.pkg, 36.dp, 10.dp) }, onClick = { onOpenApp(app.pkg) }) {
                        Icon(GIcons.Chevron, null, tint = G.Muted)
                    }
                }
            }
        }
    }
}
