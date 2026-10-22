package com.seedlessds.app.emu

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

object EmuAssets {
    const val DATA_DIR = "SeedlessDS"

    private data class Item(val asset: String, val dest: String)

    private val ITEMS = listOf(
        Item("game_database.xml", "game_database.xml"),
        // Built-in cheat database (R4 format, CMP NDS Cheat Database). The
        // core loads it from save_dir, which resolves to "user/usrcheat.dat".
        Item("usrcheat.dat", "user/usrcheat.dat"),
        Item("LC_default.dat", "config/LC_default.dat"),
        Item("nds_bios_arm7_replacement.bin", "system/nds_bios_arm7_replacement.bin"),
        Item("nds_bios_arm9_replacement.bin", "system/nds_bios_arm9_replacement.bin"),
    )

    // Shader recipes that were bundled in earlier builds (ported from
    // PPSSPP) but are no longer part of the shipped DraStic set. Stale
    // deployed copies must go, otherwise they linger in the dynamically
    // discovered filter list. Runs before copyAssetDir so nothing deleted
    // here gets re-copied.
    private val OBSOLETE_SHADERS = listOf(
        "5xBR.dfx", "5xbr.dsd", "5xBR-lv2.dfx", "5xbr_lv2.dsd",
        "Cartoon.dfx", "cartoon.dsd", "CRT.dfx", "crt.dsd",
        "Sharpen.dfx", "sharpen.dsd", "Bloom.dfx", "bloom.dsd",
        "Vignette.dfx", "vignette.dsd", "ColorCorrection.dfx", "colorcorrection.dsd",
        "UpscaleBicubic.dfx", "upscale_bicubic.dsd", "UpscaleSpline36.dfx", "upscale_spline36.dsd",
        "VideoAA.dfx", "videoAA.dsd", "zfast_CRT.dfx", "zfast_crt.dsd",
    )

    fun systemDir(ctx: Context): File = File(ctx.filesDir, DATA_DIR).apply { mkdirs() }

    fun ensureInstalled(ctx: Context, sys: File = systemDir(ctx)) {
        for (it in ITEMS) {
            val out = File(sys, it.dest)
            val database = databaseHashes[it.asset]
            if (database != null) {
                installDatabase(ctx, it.asset, out, database)
                continue
            }
            val assetSize = try { ctx.assets.openFd(it.asset).length } catch (e: Exception) { -1L }
            if (out.exists() && (assetSize < 0 || out.length() == assetSize)) continue
            out.parentFile?.mkdirs()
            ctx.assets.open(it.asset).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        OBSOLETE_SHADERS.forEach { stale -> File(File(sys, "shaders"), stale).takeIf { it.exists() }?.delete() }
        copyAssetDir(ctx, "shaders", File(sys, "shaders"))
        listOf("savestates", "backup", "cheats", "user").forEach { File(sys, it).mkdirs() }
    }

    // Hashes identify the stock databases, so an upgrade also reaches APK-
    // compressed assets (openFd cannot report their size) without replacing
    // databases imported or edited by the user.
    private val databaseHashes = mapOf(
        "game_database.xml" to ("eb36227459ce65b47c2eff35378fe882652852160c6ec27f7c86e869f6c85efd" to
            "7df66ceac84991f9b8e9658a496bfc7c6e6f4712d5ecf0fcdb9af4947213a777"),
        "usrcheat.dat" to ("28ad3272d3578ba2f493e78990a3d6defe9dfebe37d9f53501679be9498eafae" to
            "a69264226312f584caf43343d27f0d38213d4fb01758ca01880f509c98fbeb29"),
    )

    private fun installDatabase(ctx: Context, asset: String, dest: File, hashes: Pair<String, String>) {
        val (bundled, legacy) = hashes
        val prefs = ctx.getSharedPreferences("database_asset_hashes", Context.MODE_PRIVATE)
        if (dest.exists() && prefs.getString("checked:$asset", null) == bundled) return
        val current = if (dest.exists()) {
            val hash = MessageDigest.getInstance("SHA-256")
            dest.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    hash.update(buffer, 0, n)
                }
            }
            hash.digest().joinToString("") { "%02x".format(it) }
        } else null
        val owned = current == null || current == bundled || current == legacy ||
            current == prefs.getString("owned:$asset", null)
        if (owned && current != bundled) {
            dest.parentFile?.mkdirs()
            val atomic = AtomicFile(dest)
            val output = atomic.startWrite()
            try {
                ctx.assets.open(asset).use { it.copyTo(output) }
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }
        prefs.edit().apply {
            putString("checked:$asset", bundled)
            if (owned) putString("owned:$asset", bundled)
        }.apply()
    }

    private val legacyShaderHashes = mapOf(
        "shaders/quilez.dsd" to "36d855f89b969822d2de863c7c2c763f07b765ba991525eef153a29dcd0ecde6",
        "shaders/fxaa.dsd" to "94c00e20ab3d32f059ec1c38a565fd2aa17839434a2b625df922e5183876667f",
        "shaders/fxaa/fxaa.dsd" to "c1b01e635df33dbd836f75a88808bf43aa469d0426b20b40be998d92e5aec13d",
        "shaders/smaa/SMAA.hlsl" to "98b1253398e950b7ab03203ab2b5360e9135deaea0c5b898dc2f6304f9504850",
        "shaders/SMAA.dfx" to "5e993edf959c1f21213300981c01d70692cf68014d30885dc7e10b6258e26fdb",
        "shaders/4XBR_v1.1_Low configuration.dsd" to "6a8ae60b708dda5c02e2e1e0cf245fa474040af8bfc307acbe418d13b95decab",
    )

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun copyAssetDir(ctx: Context, assetPath: String, dest: File) {
        val entries = try { ctx.assets.list(assetPath) } catch (e: Exception) { null } ?: return
        if (entries.isEmpty()) {
            dest.parentFile?.mkdirs()
            val prefs = ctx.getSharedPreferences("shader_asset_hashes", Context.MODE_PRIVATE)
            val bundled = ctx.assets.open(assetPath).use { it.readBytes() }
            val newHash = digest(bundled)
            val oldHash = prefs.getString(assetPath, null)
            val currentHash = if (dest.exists()) digest(dest.readBytes()) else null
            if (currentHash == newHash) {
                if (oldHash != newHash) prefs.edit().putString(assetPath, newHash).apply()
                return
            }
            // On the first migration, claim ownership only of identical bundled files.
            if (currentHash == null || currentHash == oldHash || currentHash == legacyShaderHashes[assetPath]) {
                val atomic = AtomicFile(dest)
                val output = atomic.startWrite()
                try {
                    output.write(bundled)
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
                prefs.edit().putString(assetPath, newHash).apply()
            }
            return
        }
        dest.mkdirs()
        for (e in entries) copyAssetDir(ctx, "$assetPath/$e", File(dest, e))
    }
}
