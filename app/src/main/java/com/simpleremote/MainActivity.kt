package com.simpleremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.simpleremote.ui.AppViewModel
import com.simpleremote.ui.SimpleRemoteApp
import com.simpleremote.widget.RemoteWidget

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { SimpleRemoteApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
    }

    /** Tapping a widget's title opens that remote. */
    private fun handle(intent: Intent?) {
        intent?.getStringExtra(RemoteWidget.EXTRA_OPEN_UID)?.let { vm.openUid(it) }
    }
}
