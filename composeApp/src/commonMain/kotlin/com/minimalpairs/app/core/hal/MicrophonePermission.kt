package com.minimalpairs.app.core.hal

import androidx.compose.runtime.Composable

/** Requests access before opening the recorder. iOS requests access in prepare(). */
@Composable
expect fun rememberMicrophonePermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
