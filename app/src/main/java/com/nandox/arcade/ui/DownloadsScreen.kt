package com.nandox.arcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandox.arcade.downloads.DownloadItem
import com.nandox.arcade.downloads.DownloadState
import com.nandox.arcade.downloads.DownloadsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val repo = remember { DownloadsRepository(context) }
    var items by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            items = withContext(Dispatchers.IO) { runCatching { repo.list() }.getOrDefault(emptyList()) }
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Downloads", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Os arquivos ficam na pasta Download do armazenamento do app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("Nenhum download.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (d.state) {
                                DownloadState.PENDING -> "Pendente"
                                DownloadState.RUNNING -> d.percent?.let { "Baixando $it%" } ?: "Baixando"
                                DownloadState.PAUSED -> "Pausado"
                                DownloadState.DONE -> "Concluído"
                                DownloadState.FAILED -> "Falhou"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    OutlinedButton(onClick = { pendingRemoval = d }) {
                        Text(if (d.state == DownloadState.DONE) "Apagar" else "Cancelar")
                    }
                }
            }
        }
    }

    pendingRemoval?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(if (d.state == DownloadState.DONE) "Apagar arquivo?" else "Cancelar download?") },
            text = { Text(d.title) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    repo.remove(d.id)
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Voltar") } },
        )
    }
}
