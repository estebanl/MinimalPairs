package com.minimalpairs.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import com.minimalpairs.app.createAndroidRootComponent
import com.minimalpairs.app.data.remote.PhonemeScorerConfiguration
import com.minimalpairs.app.ui.screens.RootScreen

/**
 * Launcher host for the shared Minimal Pairs library.
 *
 * The root component is created once per Activity instance from a lifecycle-aware
 * Decompose context, so it is destroyed with the Activity and restores state
 * across configuration changes.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ScorerConnectionStore.apply(this, intent)
        val root = createAndroidRootComponent(applicationContext, defaultComponentContext())
        setContent {
            RootScreen(root)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ScorerConnectionStore.apply(this, intent)
    }
}

/**
 * Optional scorer connection supplied at launch, for example:
 * `adb shell am start -n com.minimalpairs.android/.MainActivity --es scorer_base_url <url> --es scorer_function_key <key>`
 *
 * Values are kept in app-private preferences so later launches from the app icon keep
 * scoring. Pass `--ez scorer_clear true` to remove them. Without a connection, scoring
 * falls back to the app's offline behavior.
 */
internal object ScorerConnectionStore {
    private const val PREFS = "scorer_connection"
    private const val KEY_URL = "scorer_base_url"
    private const val KEY_FUNCTION_KEY = "scorer_function_key"
    private const val KEY_CLEAR = "scorer_clear"

    fun apply(activity: ComponentActivity, intent: Intent?) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (intent?.getBooleanExtra(KEY_CLEAR, false) == true) {
            prefs.edit().clear().apply()
        } else {
            val url = intent?.getStringExtra(KEY_URL)?.trim()?.takeIf { it.isNotEmpty() }
            val key = intent?.getStringExtra(KEY_FUNCTION_KEY)?.trim()?.takeIf { it.isNotEmpty() }
            if (url != null || key != null) {
                prefs.edit().apply {
                    if (url != null) putString(KEY_URL, url)
                    if (key != null) putString(KEY_FUNCTION_KEY, key)
                }.apply()
            }
        }
        PhonemeScorerConfiguration.baseUrl = prefs.getString(KEY_URL, null)
        PhonemeScorerConfiguration.functionKey = prefs.getString(KEY_FUNCTION_KEY, null)
    }
}
