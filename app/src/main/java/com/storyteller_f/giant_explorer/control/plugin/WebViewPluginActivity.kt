@file:Suppress("unused", "ImportOrdering")

package com.storyteller_f.giant_explorer.control.plugin

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.widget.Toast
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.storyteller_f.file_system.ensureFile
import com.storyteller_f.file_system.getFileInstance
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.ActivityWebviewPluginBinding
import com.storyteller_f.giant_explorer.pluginManagerRegister
import com.storyteller_f.giant_explorer.view.applyScreenInsets
import com.storyteller_f.plugin_core.GiantExplorerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class WebViewPluginActivity : AppCompatActivity() {
    private var binding: ActivityWebviewPluginBinding? = null
    private val webView get() = requireNotNull(binding).webView
    private val webViewJob = SupervisorJob()
    private val webViewScope = CoroutineScope(Dispatchers.Main.immediate + webViewJob)
    private var pluginBridge: WebViewPluginObject? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = ActivityWebviewPluginBinding.inflate(layoutInflater)
        binding = content
        setContentView(content.root)
        content.root.applyScreenInsets()
        val uriData = intent.data
        val pluginName = intent.getStringExtra("plugin-name")!!
        bindApi(webView, uriData)
        webViewScope.launch {
            val (configuration, html) = withContext(Dispatchers.IO) {
                val configuration = pluginManagerRegister.resolvePluginName(
                    pluginName,
                    this@WebViewPluginActivity
                ) as HtmlPluginConfiguration
                configuration to File(configuration.extractedPath, "index.html").readText()
            }
            setupWebView(configuration.extractedPath)
            webView.loadDataWithBaseURL(BASE_URL, html, null, null, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(extractedPath: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                "test",
                setOf(BASE_URL)
            ) { view, message, sourceOrigin, isMainFrame, replyProxy ->
                Log.d(
                    TAG,
                    "onCreate() called with: view = $view, " +
                        "message = ${message.data}, " +
                        "sourceOrigin = $sourceOrigin, " +
                        "isMainFrame = $isMainFrame, " +
                        "replyProxy = $replyProxy"
                )
                replyProxy.postMessage("from android")
            }
        }

        // WebKit 1.17.1 flags Kotlin super constructors even when this callback is implemented.
        // Remove after upgrading to a stable release containing b/548989591.
        @SuppressLint("MissingOnRenderProcessGone")
        val client = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "Plugin renderer exited; crashed=${detail.didCrash()}")
                releaseWebView()
                Toast.makeText(
                    this@WebViewPluginActivity,
                    R.string.html_plugin_renderer_stopped,
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return true
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                Log.i(TAG, "shouldOverrideUrlLoading: ${request?.url}")
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                Log.d(TAG, "shouldInterceptRequest() called with: request = ${request?.url}")
                if (request != null) {
                    val url = request.url
                    val path = url.path
                    if (url.toString().startsWith(BASE_URL) && path?.endsWith(".js") == true) {
                        return WebResourceResponse(
                            "text/javascript",
                            "utf-8",
                            FileInputStream(File(extractedPath, path))
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                Log.d(
                    TAG,
                    "onReceivedHttpError() called with: view = $view, request = ${request?.url}, " +
                        "errorResponse = ${errorResponse?.reasonPhrase}"
                )
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.d(
                    TAG,
                    "onReceivedError() called with: view = $view, request = ${request?.url}, " +
                        "error = ${error?.description} ${error?.errorCode}"
                )
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.d(
                    TAG,
                    "onReceivedSslError() called with: view = $view, handler = $handler, error = $error"
                )
                super.onReceivedSslError(view, handler, error)
            }
        }
        webView.webViewClient = client
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.i(TAG, "onConsoleMessage: ${consoleMessage?.message()}")
                return super.onConsoleMessage(consoleMessage)
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
        }
    }

    override fun onDestroy() {
        releaseWebView()
        super.onDestroy()
    }

    private fun releaseWebView() {
        webViewJob.cancel()
        val content = binding ?: return
        binding = null
        pluginBridge?.close()
        pluginBridge = null
        content.webView.apply {
            (parent as? ViewGroup)?.removeView(this)
            removeJavascriptInterface("plugin")
            removeJavascriptInterface("file")
            webChromeClient = null
            destroy()
        }
    }

    private fun bindApi(webView: WebView, data: Uri?) {
        val defaultPluginManager = object : DefaultPluginManager(this) {
            override suspend fun requestPath(initUri: Uri?): Uri {
                TODO("Not yet implemented")
            }

            override fun runInService(block: suspend GiantExplorerService.() -> Boolean) {
                TODO("Not yet implemented")
            }
        }
        val messageChannel = if (WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL)) {
            WebViewCompat.createWebMessageChannel(webView)
        } else {
            null
        }
        val bridge = WebViewPluginObject(webView, defaultPluginManager, webViewScope, messageChannel)
        pluginBridge = bridge
        webView.addJavascriptInterface(bridge, "plugin")
        webView.addJavascriptInterface(object : WebViewFilePlugin {
            @JavascriptInterface
            override fun fullPath(): String {
                val u = data ?: return ""
                return FileSystemProviderResolver.resolve(u)?.toString().orEmpty()
            }

            @JavascriptInterface
            override fun fileName(): String {
                return runBlocking {
                    defaultPluginManager.getName(data!!)
                }
            }
        }, "file")
    }

    class WebViewPluginObject(
        private val webView: WebView,
        private val defaultPluginManager: DefaultPluginManager,
        private val scope: CoroutineScope,
        messageChannel: Array<WebMessagePortCompat>?,
    ) {

        private val context = webView.context

        // Accessed only on Main. A successful transfer gives ownership to JavaScript.
        private var ownedPorts = messageChannel

        @MainThread
        fun close() {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_CLOSE)) {
                ownedPorts?.forEach { it.close() }
            }
            ownedPorts = null
        }

        @JavascriptInterface
        fun requestPath(initUriString: String, callbackId: String) {
            scope.launch {
                webView.callback(
                    callbackId,
                    defaultPluginManager.requestPath(initUriString.toUri()).toString()
                )
            }
        }

        @JavascriptInterface
        fun base64(path: String, callbackId: String) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    requireNotNull(getFileInstance(context, path.toUri())) {
                        "Unsupported image URI"
                    }.getFileInputStream().use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
                }
                withContext(Dispatchers.Main.immediate) {
                    webView.callback(callbackId, "'$result'")
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
                        val message = WebMessageCompat(result, ownedPorts)
                        WebViewCompat.postWebMessage(webView, message, BASE_URL.toUri())
                        ownedPorts = null
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "WebViewPluginActivity"
        const val BASE_URL = "http://www.example.com"
    }
}

interface WebViewFilePlugin {
    fun fullPath(): String
    fun fileName(): String
}

fun WebView.callback(callbackId: String, parameters: String?) {
    evaluateJavascript("callback($callbackId, $parameters)") { }
}

suspend fun File.ensureExtract(extracted: String) {
    withContext(Dispatchers.IO) {
        ZipInputStream(FileInputStream(this@ensureExtract)).use {
            val byteArray = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val nextEntry = it.nextEntry
                if (nextEntry != null) {
                    val name = nextEntry.name
                    val fileOutputStream = FileOutputStream(File(extracted, name).ensureFile())
                    fileOutputStream.use { out ->
                        while (true) {
                            val read = it.read(byteArray)
                            if (read != -1) {
                                out.write(byteArray, 0, read)
                            } else {
                                break
                            }
                        }
                    }
                } else {
                    break
                }
            }
        }
    }
}
