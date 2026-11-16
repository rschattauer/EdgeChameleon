package dev.schattauer.edgechameleon

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A WebView goes blank or half drawn when a second renderer draws it, which is what sampling the
 * content does. So while one is shown, the bars are read from the window, and in a window that can
 * not be read, they are not read at all.
 */
@OptIn(ExperimentalEdgeChameleonApi::class)
class WebViewDeviceTest {
    private val state = EdgeChameleonState()
    private var showsWebView by mutableStateOf(true)
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun launch(secure: Boolean = false) {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            activity.enableEdgeToEdge()
            activity.setContent {
                Box(
                    Modifier.edgeChameleon(navigationBar = false, state = state)
                        .fillMaxSize()
                        .background(Color.White),
                ) {
                    if (showsWebView) {
                        // Covers the whole status bar, and is dark, so the icons must be light
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    setBackgroundColor(AndroidColor.BLACK)
                                    loadData(DarkPage, "text/html", "utf-8")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(WebViewHeight),
                        )
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun aWebViewBehindTheStatusBarDecidesTheIconsFromTheWindow() {
        launch()
        val reached = awaitCondition(TimeoutMillis) { state.statusBarIcons == BarIcons.Light }
        assertTrue("Status bar icons: ${state.statusBarIcons}", reached)
        assertTrue("Nothing was read", state.sampleCount > 0)
    }

    @Test
    fun aSecureWindowWithAWebViewIsNotReadUntilTheWebViewIsGone() {
        launch(secure = true)
        // The window can not be copied, and the WebView must not be drawn a second time
        Thread.sleep(IdleMillis)
        assertEquals("Samples while the WebView is shown", 0, state.sampleCount)

        scenario.onActivity { showsWebView = false }
        val read = awaitCondition(TimeoutMillis) { state.sampleCount > 0 }
        assertTrue("Nothing was read once the WebView was gone", read)
    }

    private companion object {
        const val TimeoutMillis = 10_000L
        const val IdleMillis = 3_000L
        const val DarkPage = "<html><body style=\"background:#000\"></body></html>"
        val WebViewHeight = 200.dp
    }
}
