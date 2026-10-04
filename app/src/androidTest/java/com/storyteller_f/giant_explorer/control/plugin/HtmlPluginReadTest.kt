package com.storyteller_f.giant_explorer.control.plugin

import android.content.Intent
import android.util.Base64
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.pluginManagerRegister
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class HtmlPluginReadTest {
    @Test
    fun bridgeReadsTheOriginalFileUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = File(context.cacheDir, "html-bridge-fixture.txt").apply {
            writeText("HTML bridge fixture")
        }
        val archive = File(context.cacheDir, "html-bridge-test.zip")
        writePlugin(archive, """<html><head><title>pending</title></head><body><script>
            function callback(id, result) {
                if (id === 0) plugin.base64(file.fullPath(), "1");
                else document.title = result;
            }
            plugin.base64(file.fullPath(), "0");
            </script></body></html>""")
        pluginManagerRegister.foundPlugin(archive)
        val intent = Intent(context, WebViewPluginActivity::class.java)
            .setData(FileSystemProviderResolver.share(false, original.toUri()))
            .putExtra("plugin-name", archive.name)
        val expected = "\"${Base64.encodeToString(original.readBytes(), Base64.NO_WRAP)}\""
        ActivityScenario.launch<WebViewPluginActivity>(intent).use { scenario ->
            // The instrumentation runner is synchronous; await the JavaScript callback with coroutines.
            runBlocking {
                withTimeout(60_000) {
                    while (true) {
                        val title = CompletableDeferred<String>()
                        scenario.onActivity { activity ->
                            activity.findViewById<WebView>(R.id.web_view)
                                .evaluateJavascript("document.title") { title.complete(it) }
                        }
                        val result = title.await()
                        if (result == expected) {
                            assertEquals(expected, result)
                            break
                        }
                        delay(100)
                    }
                }
            }
        }
        original.delete()
        archive.delete()
        File(context.filesDir, "plugins/${archive.nameWithoutExtension}").deleteRecursively()
    }

    @Test
    fun rendererCrashClosesPreviewWithoutCrashingHost() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.cacheDir, "html-renderer-test.zip")
        writePlugin(archive, "<html><head><title>renderer-ready</title></head><body>Preview</body></html>")
        pluginManagerRegister.foundPlugin(archive)
        val intent = Intent(context, WebViewPluginActivity::class.java)
            .putExtra("plugin-name", archive.name)
        try {
            ActivityScenario.launch<WebViewPluginActivity>(intent).use { scenario ->
                // Instrumentation is a synchronous boundary; wait off the UI thread.
                runBlocking {
                    withTimeout(60_000) {
                        while (true) {
                            val ready = CompletableDeferred<Boolean>()
                            scenario.onActivity { activity ->
                                val view = activity.findViewById<WebView>(R.id.web_view)
                                ready.complete(view.title == "renderer-ready")
                            }
                            if (ready.await()) break
                            delay(100)
                        }
                        scenario.onActivity { activity ->
                            activity.findViewById<WebView>(R.id.web_view).loadUrl("chrome://crash")
                        }
                        while (scenario.state != Lifecycle.State.DESTROYED) delay(100)
                    }
                }
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            }
        } finally {
            archive.delete()
            File(context.filesDir, "plugins/${archive.nameWithoutExtension}").deleteRecursively()
        }
    }

    private fun writePlugin(archive: File, html: String) {
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("config"))
            zip.write("1.0".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("index.html"))
            zip.write(html.toByteArray())
            zip.closeEntry()
        }
    }

}
