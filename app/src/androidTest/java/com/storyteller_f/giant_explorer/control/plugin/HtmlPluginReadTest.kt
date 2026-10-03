package com.storyteller_f.giant_explorer.control.plugin

import android.content.Intent
import android.util.Base64
import android.webkit.WebView
import androidx.core.net.toUri
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
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("config"))
            zip.write("1.0".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("index.html"))
            zip.write(
                """<html><head><title>pending</title></head><body><script>
                function callback(id, result) { document.title = result; }
                plugin.base64(file.fullPath(), "0");
                </script></body></html>""".toByteArray()
            )
            zip.closeEntry()
        }
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
}
