package com.example.rnd_transit_mtl

import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.ProvideAndroidTransitMap

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

        val showStartup = savedInstanceState == null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())
        val generationEndpoint = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.example.rnd_transit_mtl.GENERATION_ENDPOINT")?.takeIf { it.isNotBlank() }
        setContent {
            var mapReady by remember { mutableStateOf(false) }
            ProvideAndroidTransitMap(
                styleUrl = getString(R.string.map_style_url),
                onMapReady = { mapReady = true }
            ) {
                Box(Modifier.fillMaxSize()) {
                    App(
                        generationEndpoint = generationEndpoint
                    )
                    if (showStartup) AnimatedStartupOverlay(readyToReveal = { mapReady })
                }
            }
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
