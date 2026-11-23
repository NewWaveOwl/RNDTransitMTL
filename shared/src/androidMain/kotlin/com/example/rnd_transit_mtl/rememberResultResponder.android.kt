package com.example.rnd_transit_mtl

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberResultResponder(): (resultData: String) -> Unit {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    return { resultData ->
        activity?.let { act ->
            val resultIntent = Intent().apply {
                putExtra("resultData", resultData)
            }
            act.setResult(Activity.RESULT_OK, resultIntent)
            act.finish()
        }
    }
}
