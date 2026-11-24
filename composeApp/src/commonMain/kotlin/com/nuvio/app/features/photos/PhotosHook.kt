package com.nuvio.app.features.photos

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Lets the platform (Android) plug its Photos tab UI into the shared tab shell.
 */
object PhotosHook {
    var content: (@Composable (Modifier) -> Unit)? = null
}
