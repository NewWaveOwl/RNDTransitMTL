package com.example.rnd_transit_mtl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Android entry activity for the shared transit application.
 */
class MainActivity : ComponentActivity() {
    /**
     * Enables edge-to-edge display and hosts the shared application.
     *
     * @param savedInstanceState Previously saved activity state, or null for a new activity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

/**
 * Previews the shared application inside Android Studio.
 */
@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
