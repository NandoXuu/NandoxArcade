package com.nandox.arcade.emulator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

enum class PluginStatus { READY, APP_NOT_INSTALLED, ABI_INCOMPATIBLE }

data class PluginDiagnostic(val manifest: PluginManifest, val status: PluginStatus)

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

class PluginRegistry(manifests: List<PluginManifest>) {
    private val adapters: List<EmulatorAdapter> = manifests.mapNotNull {
        if (it.type == PluginType.EXTERNAL_APP) ExternalAppAdapter(it) else null
    }

    fun adaptersFor(platformId: String): List<EmulatorAdapter> =
        adapters.filter { platformId in it.manifest.platforms }

    fun diagnostics(context: Context): List<PluginDiagnostic> =
        adapters.map { PluginDiagnostic(it.manifest, it.diagnose(context)) }

    companion object {
        val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

        fun builtin(): PluginRegistry = PluginRegistry(
            listOf(
                external("ppsspp", "PPSSPP", "org.ppsspp.ppsspp", "psp"),
                external("duckstation", "DuckStation", "com.github.stenzek.duckstation", "ps1"),
                external("melonds", "melonDS", "me.magnum.melonds", "nds"),
                external("dolphin", "Dolphin", "org.dolphinemu.dolphinemu", "gamecube", "wii"),
            )
        )

        private fun external(id: String, name: String, pkg: String, vararg platforms: String) =
            PluginManifest(id, name, "1", PluginType.EXTERNAL_APP, platforms.toList(), pkg, emptyList(), false)
    }
}
