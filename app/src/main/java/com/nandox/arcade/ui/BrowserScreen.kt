package com.nandox.arcade.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nandox.arcade.browser.UrlResolver
import com.nandox.arcade.downloads.DownloadsRepository

private data class PendingDownload(val url: String, val name: String, val mime: String?, val agent: String?)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { DownloadsRepository(context) }
    var urlText by remember { mutableStateOf("") }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingDownload?>(null) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !UrlResolver.isAllowedNavigation(request.url.toString())

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    urlText = url
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    canBack = view.canGoBack()
                    canForward = view.canGoForward()
                }
            }
            setDownloadListener(DownloadListener { url, agent, disposition, mime, _ ->
                if (UrlResolver.isAllowedNavigation(url)) {
                    pending = PendingDownload(url, URLUtil.guessFileName(url, disposition, mime), mime, agent)
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { webView.destroy() } }

    BackHandler { if (webView.canGoBack()) webView.goBack() else onExit() }

    fun go() {
        webView.loadUrl(UrlResolver.resolve(urlText))
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Endereço ou busca") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
            )
            Button(onClick = { go() }) { Text("Ir") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { webView.goBack() }, enabled = canBack) { Text("Voltar") }
            OutlinedButton(onClick = { webView.goForward() }, enabled = canForward) { Text("Avançar") }
            OutlinedButton(onClick = { webView.reload() }) { Text("Recarregar") }
        }
        AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
    }

    pending?.let { d ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Baixar arquivo?") },
            text = { Text(d.name) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    runCatching { repo.enqueue(d.url, d.name, d.mime, d.agent) }
                        .onSuccess { Toast.makeText(context, "Download iniciado", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Não foi possível iniciar o download", Toast.LENGTH_LONG).show() }
                }) { Text("Baixar") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}
