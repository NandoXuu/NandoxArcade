package com.nandox.arcade.ui

import android.os.Build
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import com.nandox.arcade.controller.ControlsController
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.data.SourceEntity
import com.nandox.arcade.scanner.GameScanner
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nandox.arcade.BuildConfig
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.emulator.LaunchResult
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStore
import com.nandox.arcade.emulator.PluginStatus

@Composable
fun ArcadeApp(controls: ControlsController) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val registry = remember { PluginRegistry.load(context) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onOpenLibrary = { nav.navigate("library/$it") },
                    onPlugins = { nav.navigate("plugins") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable(
                "library/{platform}",
                arguments = listOf(navArgument("platform") { type = NavType.StringType }),
            ) {
                LibraryScreen(
                    platformId = it.arguments?.getString("platform").orEmpty(),
                    registry = registry,
                    onOpenUnknown = { nav.navigate("library/${PlatformClassifier.UNKNOWN}") },
                )
            }
            composable("plugins") { PluginsScreen(registry) }
            composable("settings") { SettingsScreen(onControls = { nav.navigate("controls") }) }
            composable("controls") { ControlsScreen(controls) }
        }
    }
}

@Composable
private fun HomeScreen(onOpenLibrary: (String) -> Unit, onPlugins: () -> Unit, onSettings: () -> Unit) {
    val platforms = Platforms.all
    var selected by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(selected) { listState.animateScrollToItem(selected) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Nandox Arcade", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Column {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionRight -> { selected = (selected + 1).coerceAtMost(platforms.lastIndex); true }
                            Key.DirectionLeft -> { selected = (selected - 1).coerceAtLeast(0); true }
                            Key.DirectionCenter, Key.Enter, Key.ButtonA -> { onOpenLibrary(platforms[selected].id); true }
                            else -> false
                        }
                    },
            ) {
                items(platforms.size) { i ->
                    val p = platforms[i]
                    val active = i == selected
                    Column(
                        Modifier
                            .size(width = if (active) 190.dp else 150.dp, height = if (active) 190.dp else 150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                            .border(
                                if (active) 3.dp else 0.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable {
                                if (active) onOpenLibrary(p.id) else selected = i
                            }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(p.maker, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                platforms[selected].name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onOpenLibrary(platforms[selected].id) }) { Text("Biblioteca") }
            OutlinedButton(onClick = onPlugins) { Text("Plugins") }
            OutlinedButton(onClick = onSettings) { Text("Configurações") }
        }
    }
}

@Composable
private fun LibraryScreen(platformId: String, registry: PluginRegistry, onOpenUnknown: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { ArcadeDb.get(context).games() }
    val store = remember { PluginStore(context) }
    val scope = rememberCoroutineScope()
    val games by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val sources by remember { dao.observeSources() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch { dao.insertSource(SourceEntity(uri = uri.toString())) }
        }
    }

    fun scanAll() {
        scope.launch {
            scanning = true
            var added = 0
            for (src in dao.sources()) {
                val found = GameScanner.scan(context, Uri.parse(src.uri))
                added += dao.insertGames(found).count { it != -1L }
            }
            scanning = false
            message = "$added jogo(s) novo(s) adicionado(s)"
        }
    }

    val platform = Platforms.byId(platformId)
    val filtered = games.filter { it.platformId == platformId }
    val unknownCount = games.count { it.platformId == PlatformClassifier.UNKNOWN }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(platform?.name ?: "Não classificados", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { picker.launch(null) }) { Text("Adicionar pasta") }
            Button(onClick = { scanAll() }, enabled = sources.isNotEmpty() && !scanning) {
                Text(if (scanning) "Escaneando…" else "Escanear")
            }
        }
        Text(
            "${sources.size} pasta(s) configurada(s)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        message?.let { Text(it, fontSize = 13.sp) }
        if (unknownCount > 0 && platform != null) {
            Text(
                "$unknownCount jogo(s) sem plataforma definida",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onOpenUnknown),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Text(
                "Nenhum jogo aqui. Adicione uma pasta e escaneie.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { g ->
                    Text(
                        g.title,
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable {
                                val adapter = registry.adaptersFor(g.platformId, store)
                                    .firstOrNull { it.diagnose(context) == PluginStatus.READY }
                                val result = adapter?.launch(context, Uri.parse(g.uri)) ?: LaunchResult.FAILED
                                val text = when (result) {
                                    LaunchResult.STARTED_WITH_GAME -> null
                                    LaunchResult.OPENED_APP_ONLY -> "Emulador aberto sem o jogo. Abra o arquivo por dentro dele."
                                    LaunchResult.FAILED -> "Nenhum emulador disponível para este jogo"
                                }
                                if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                            }
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginsScreen(registry: PluginRegistry) {
    val context = LocalContext.current
    val store = remember { PluginStore(context) }
    var diagnostics by remember { mutableStateOf(registry.diagnostics(context, store)) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Plugins de emulação", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "ABI do aparelho: ${PluginRegistry.deviceAbis.joinToString()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(diagnostics, key = { it.manifest.id }) { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.manifest.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            d.manifest.platforms.mapNotNull { Platforms.byId(it)?.name }.joinToString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        val status = when (d.status) {
                            PluginStatus.READY -> "App instalado"
                            PluginStatus.APP_NOT_INSTALLED -> "App não instalado"
                            PluginStatus.ABI_INCOMPATIBLE -> "ABI incompatível"
                            PluginStatus.RUNTIME_UNAVAILABLE -> "Runtime nativo indisponível"
                            PluginStatus.DISABLED -> "Desativado"
                        }
                        val validation = if (d.manifest.validated) "validado" else "não validado"
                        Text("$status · $validation", fontSize = 13.sp)
                    }
                    Switch(
                        checked = d.enabled,
                        onCheckedChange = {
                            store.setEnabled(d.manifest.id, it)
                            diagnostics = registry.diagnostics(context, store)
                        },
                    )
                }
            }
            items(registry.rejected, key = { it.first }) { (file, reason) ->
                Text(
                    "Manifesto rejeitado: $file ($reason)",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onControls: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Configurações", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Versão do app: ${BuildConfig.VERSION_NAME}")
        Text("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Text("ABIs: ${PluginRegistry.deviceAbis.joinToString()}")
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onControls) { Text("Controles") }
    }
}
