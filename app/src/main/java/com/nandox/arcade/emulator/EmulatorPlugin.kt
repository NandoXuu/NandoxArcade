package com.nandox.arcade.emulator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import org.json.JSONException
import org.json.JSONObject

class PluginManifestException(message: String) : Exception(message)

enum class PluginType { EXTERNAL_APP, NATIVE_CORE }

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val type: PluginType,
    val platforms: List<String>,
    val packageName: String?,
    val abis: List<String>,
    val validated: Boolean,
)

object PluginManifestParser {
    fun parse(json: String): PluginManifest {
        val o = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw PluginManifestException("JSON inválido")
        }
        fun text(key: String): String =
            o.optString(key).takeIf { it.isNotBlank() }
                ?: throw PluginManifestException("Campo obrigatório ausente: $key")

        val type = when (text("type")) {
            "external_app" -> PluginType.EXTERNAL_APP
            "native_core" -> PluginType.NATIVE_CORE
            else -> throw PluginManifestException("Tipo desconhecido")
        }
        val platforms = o.optJSONArray("platforms")?.let { a -> List(a.length()) { a.getString(it) } }
            ?.takeIf { it.isNotEmpty() }
            ?: throw PluginManifestException("Lista de plataformas vazia")
        val abis = o.optJSONArray("abis")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        val pkg = o.optString("packageName").takeIf { it.isNotBlank() }
        if (type == PluginType.EXTERNAL_APP && pkg == null) {
            throw PluginManifestException("packageName obrigatório para app externo")
        }
        if (type == PluginType.NATIVE_CORE && abis.isEmpty()) {
            throw PluginManifestException("abis obrigatório para core nativo")
        }
        return PluginManifest(
            id = text("id"),
            name = text("name"),
            version = text("version"),
            type = type,
            platforms = platforms,
            packageName = pkg,
            abis = abis,
            validated = o.optBoolean("validated", false),
        )
    }
}

enum class PluginStatus { READY, APP_NOT_INSTALLED, ABI_INCOMPATIBLE, RUNTIME_UNAVAILABLE, DISABLED }

data class PluginDiagnostic(val manifest: PluginManifest, val status: PluginStatus, val enabled: Boolean)

interface EmulatorAdapter {
    val manifest: PluginManifest
    fun diagnose(context: Context): PluginStatus
    fun launch(context: Context, gameUri: Uri): Boolean
}

class ExternalAppAdapter(override val manifest: PluginManifest) : EmulatorAdapter {
    private val pkg = requireNotNull(manifest.packageName)

    override fun diagnose(context: Context): PluginStatus =
        if (context.packageManager.getLaunchIntentForPackage(pkg) != null) {
            PluginStatus.READY
        } else {
            PluginStatus.APP_NOT_INSTALLED
        }

    override fun launch(context: Context, gameUri: Uri): Boolean {
        if (diagnose(context) != PluginStatus.READY) return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(pkg)
            setDataAndType(gameUri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

class PluginStore(context: Context) {
    private val prefs = context.getSharedPreferences("plugins", Context.MODE_PRIVATE)

    fun isEnabled(id: String): Boolean = id !in prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun setEnabled(id: String, enabled: Boolean) {
        val disabled = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(id) else disabled.add(id)
        prefs.edit().putStringSet(KEY, disabled).apply()
    }

    private companion object {
        const val KEY = "disabled"
    }
}

data class LoadResult(val manifests: List<PluginManifest>, val errors: List<Pair<String, String>>)

object PluginLoader {
    fun load(dir: File): LoadResult {
        val files = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        val manifests = mutableListOf<PluginManifest>()
        val errors = mutableListOf<Pair<String, String>>()
        for (f in files) {
            try {
                manifests += PluginManifestParser.parse(f.readText())
            } catch (e: PluginManifestException) {
                errors += f.name to (e.message ?: "inválido")
            } catch (e: java.io.IOException) {
                errors += f.name to "falha de leitura"
            }
        }
        return LoadResult(manifests, errors)
    }
}

class PluginRegistry(manifests: List<PluginManifest>, val rejected: List<Pair<String, String>> = emptyList()) {
    private val all: List<PluginManifest> = manifests.distinctBy { it.id }
    private val adapters: List<EmulatorAdapter> = all.mapNotNull {
        if (it.type == PluginType.EXTERNAL_APP) ExternalAppAdapter(it) else null
    }

    fun adaptersFor(platformId: String, store: PluginStore): List<EmulatorAdapter> =
        adapters.filter { platformId in it.manifest.platforms && store.isEnabled(it.manifest.id) }

    fun diagnostics(context: Context, store: PluginStore): List<PluginDiagnostic> = all.map { m ->
        val enabled = store.isEnabled(m.id)
        val status = when {
            !enabled -> PluginStatus.DISABLED
            m.type == PluginType.NATIVE_CORE ->
                if (m.abis.none { it in deviceAbis }) PluginStatus.ABI_INCOMPATIBLE
                else PluginStatus.RUNTIME_UNAVAILABLE
            else -> adapters.first { it.manifest.id == m.id }.diagnose(context)
        }
        PluginDiagnostic(m, status, enabled)
    }

    companion object {
        val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

        fun load(context: Context): PluginRegistry {
            val dir = File(context.filesDir, "plugins")
            val loaded = PluginLoader.load(dir)
            return PluginRegistry(builtin + loaded.manifests, loaded.errors)
        }

        private val builtin: List<PluginManifest> = listOf(
            external("ppsspp", "PPSSPP", "org.ppsspp.ppsspp", "psp"),
            external("duckstation", "DuckStation", "com.github.stenzek.duckstation", "ps1"),
            external("melonds", "melonDS", "me.magnum.melonds", "nds"),
            external("dolphin", "Dolphin", "org.dolphinemu.dolphinemu", "gamecube", "wii"),
        )

        private fun external(id: String, name: String, pkg: String, vararg platforms: String) =
            PluginManifest(id, name, "1", PluginType.EXTERNAL_APP, platforms.toList(), pkg, emptyList(), false)
    }
}
