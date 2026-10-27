@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.minimalpairs.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.minimalpairs.app.ui.reference.Mouth3dBridge
import com.minimalpairs.app.ui.reference.Mouth3dState
import kotlinx.cinterop.readValue
import kotlinx.coroutines.delay
import platform.CoreGraphics.CGRectZero
import platform.Foundation.*
import platform.UIKit.*
import platform.WebKit.*
import platform.darwin.NSObject

@Composable
actual fun mouth3dAvailable(): Boolean {
    var available by remember { mutableStateOf(!UIAccessibilityIsVoiceOverRunning()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(UIAccessibilityVoiceOverStatusDidChangeNotification, null, NSOperationQueue.mainQueue) {
            available = !UIAccessibilityIsVoiceOverRunning()
        }
        onDispose { center.removeObserver(token) }
    }
    return available
}

@Composable
actual fun Mouth3dViewport(state: Mouth3dState, modifier: Modifier, onError: () -> Unit) {
    val error by rememberUpdatedState(onError)
    val session = remember { MouthWebSession { error() } }
    var visible by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) }
    var reducedMotion by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    SideEffect { session.update(state.copy(active = visible && foreground, reducedMotion = reducedMotion)) }
    LaunchedEffect(session) {
        delay(12_000)
        if (!session.bridge.ready && !session.bridge.closed) session.fail()
    }
    DisposableEffect(session) {
        val center = NSNotificationCenter.defaultCenter
        val background = center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
            // Pause immediately, without waiting for a Compose frame in the background.
            foreground = false
            session.pause()
        }
        val resume = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { foreground = true }
        val motion = center.addObserverForName(UIAccessibilityReduceMotionStatusDidChangeNotification, null, NSOperationQueue.mainQueue) {
            reducedMotion = UIAccessibilityIsReduceMotionEnabled()
        }
        onDispose {
            listOf(background, resume, motion).forEach(center::removeObserver)
            session.dispose()
        }
    }
    UIKitView(
        factory = { session.webView },
        modifier = modifier.onGloballyPositioned {
            val bounds = it.boundsInRoot() // clipped by the native screen/Compose scroll viewport
            visible = bounds.width > 0 && bounds.height > 0
        },
        update = {},
        properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = false),
        onRelease = { session.dispose() }
    )
}

/** Owns WK delegates and one bundled document for the lifetime of a composed viewport. */
private class MouthWebSession(private val onError: () -> Unit) : NSObject(), WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol, WKUIDelegateProtocol {
    private val resource = NSBundle.mainBundle.URLForResource("mouth-position", "html")
    private val content = WKUserContentController()
    private var latest: Mouth3dState? = null
    val webView: WKWebView
    val bridge: Mouth3dBridge
    init {
        val configuration = WKWebViewConfiguration().apply {
            userContentController = content
            websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
            preferences.javaScriptCanOpenWindowsAutomatically = false
            // All subresources are embedded; disallow network and capture APIs as well as navigation.
        }
        webView = WKWebView(CGRectZero.readValue(), configuration)
        bridge = Mouth3dBridge { json ->
            webView.evaluateJavaScript("window.mouthPosition?.receive($json)") { _, error ->
                if (error != null && !bridge.closed) fail()
            }
        }
        content.addScriptMessageHandler(this, "mouthPosition")
        webView.navigationDelegate = this
        webView.UIDelegate = this
        webView.scrollView.scrollEnabled = false
        webView.userInteractionEnabled = false
        webView.accessibilityElementsHidden = true // native cue + illustration alternative supply descriptions
        if (resource != null) webView.loadFileURL(resource, resource)
    }
    fun update(state: Mouth3dState) {
        latest = state
        if (resource == null) { fail(); return }
        bridge.update(state)
    }
    fun pause() { latest?.let { bridge.update(it.copy(active = false)) } }
    fun fail() {
        if (bridge.closed) return
        dispose()
        onError()
    }
    fun dispose() {
        if (bridge.closed) return
        bridge.dispose()
        content.removeScriptMessageHandlerForName("mouthPosition")
        content.removeAllUserScripts()
        webView.stopLoading()
        webView.navigationDelegate = null
        webView.UIDelegate = null
        latest = null
    }
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        if (!didReceiveScriptMessage.frameInfo.mainFrame || bridge.closed) return
        val message = didReceiveScriptMessage.body as? Map<*, *> ?: return
        val version = (message["version"] as? Number)?.toInt()
            ?: (message["version"] as? NSNumber)?.intValue
        if (version != 1) return
        when (message["type"]) {
            "ready" -> bridge.onReady()
            "error" -> fail()
        }
    }
    override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
        val allowed = decidePolicyForNavigationAction.targetFrame?.mainFrame == true &&
            decidePolicyForNavigationAction.request.URL?.absoluteString == resource?.absoluteString
        decisionHandler(if (allowed) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }
    @kotlinx.cinterop.ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) = fail()
    @kotlinx.cinterop.ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) = fail()
    override fun webView(webView: WKWebView, requestMediaCapturePermissionForOrigin: WKSecurityOrigin, initiatedByFrame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: (WKPermissionDecision) -> Unit) {
        decisionHandler(WKPermissionDecision.WKPermissionDecisionDeny)
    }
    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) = fail()
}
