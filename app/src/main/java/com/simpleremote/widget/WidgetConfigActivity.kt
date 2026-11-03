package com.simpleremote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.simpleremote.MainActivity
import com.simpleremote.data.AppServices
import com.simpleremote.data.DeviceCategory
import com.simpleremote.ui.SimpleRemoteTheme
import com.simpleremote.ui.categoryStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown when a widget is placed (or reconfigured): pick which saved remote it controls. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without picking cancels placing the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val remotes = AppServices.get(this).store.load()
        enableEdgeToEdge()
        setContent {
            SimpleRemoteTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                        Text("Choose a remote", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("The widget shows its power button and three more",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (remotes.isEmpty()) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("You have no remotes yet. Add one in the app first.")
                                Button(onClick = {
                                    startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java))
                                    finish()
                                }, modifier = Modifier.padding(top = 16.dp)) { Text("Open SimpleRemote") }
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(remotes, key = { it.uid }) { r ->
                                    val cat = DeviceCategory.of(r.type)
                                    val st = categoryStyle(cat)
                                    Surface(
                                        onClick = { pick(id, r.uid) },
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                                    .background(Brush.linearGradient(listOf(st.from, st.to))),
                                                contentAlignment = Alignment.Center,
                                            ) { Icon(st.icon, null, tint = Color.White) }
                                            Column(Modifier.padding(start = 14.dp)) {
                                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                                Text(cat.label, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pick(id: Int, uid: String) {
        WidgetPrefs.set(this, id, uid)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                RemoteWidget.render(applicationContext, AppWidgetManager.getInstance(applicationContext), id)
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }
}
