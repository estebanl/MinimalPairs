package com.minimalpairs.app.core.hal

import androidx.compose.runtime.Composable

@Composable
actual fun rememberMicrophonePermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }
