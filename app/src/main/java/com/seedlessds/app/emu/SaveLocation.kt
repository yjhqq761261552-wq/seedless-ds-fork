package com.seedlessds.app.emu

import com.seedlessds.app.R
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.seedlessds.app.filesystem.NativePathHandle
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object SaveLocation {
    const val NEXT_TO_ROM = 0
    const val APP_STORAGE = 1
    const val CUSTOM = 2

    fun labels(uiContext: android.content.Context) = listOf(uiContext.getString(R.string.settings_next_to_the_rom), uiContext.getString(R.string.settings_app_storage), uiContext.getString(R.string.settings_custom_folder))

    private const val MIME_BIN = "application/octet-stream"

    class Entry(val uri: Uri, val lastModified: Long, val size: Long, val isDir: Boolean)

    sealed class Target {
        abstract fun list(): Map<String, Entry>
        abstract fun lookup(name: String): Entry?
        abstract fun openFd(name: String, mode: String, create: Boolean): Int
        abstract fun delete(name: String): Boolean
        abstract fun rename(from: String, to: String): Boolean
        abstract fun subfolder(name: String): Target?
        open fun recover(name: String) {}
        abstract fun label(): String
    }

    class Folder(private val dir: File) : Target() {
        override fun list(): Map<String, Entry> =
            (dir.listFiles() ?: emptyArray()).associate { it.name to Entry(Uri.fromFile(it), it.lastModified(), it.length(), it.isDirectory) }
        override fun lookup(name: String): Entry? = File(dir, name).let { if (it.exists()) Entry(Uri.fromFile(it), it.lastModified(), it.length(), it.isDirectory) else null }
        override fun openFd(name: String, mode: String, create: Boolean): Int {
            val f = File(dir, name)
            if (!f.exists()) { if (!create) return -1; dir.mkdirs(); runCatching { f.createNewFile() } }
            val pfd = runCatching { android.os.ParcelFileDescriptor.open(f, pfdMode(mode)) }.getOrNull() ?: return -1
            return pfd.detachFd()
        }
        override fun delete(name: String) = File(dir, name).let { it.exists() && it.delete() }
        override fun rename(from: String, to: String): Boolean {
            val a = File(dir, from); if (!a.exists()) return false
            val b = File(dir, to)
            return runCatching { android.system.Os.rename(a.absolutePath, b.absolutePath); true }.getOrDefault(false)
        }
        override fun subfolder(name: String): Target? = File(dir, name).let { if (it.isDirectory || it.mkdirs()) Folder(it) else null }
        override fun label() = dir.absolutePath
    }

    private val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

    class Document(private val ctx: Context, private val treeUri: Uri, private val parentId: String) : Target() {
        private val parentUri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        @Volatile private var cache: Map<String, Entry>? = null
        @Volatile private var cacheAt = 0L

        private val pathIds = treeUri.authority == "com.android.externalstorage.documents"

        private fun entry(c: android.database.Cursor, uri: Uri) = Entry(
            uri,
            if (c.isNull(2)) 0L else c.getLong(2),
            if (c.isNull(3)) 0L else c.getLong(3),
            c.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR,
        )

        override fun lookup(name: String): Entry? {
            if (!pathIds) return list()[name]
            val id = if (parentId.endsWith(":")) parentId + name else "$parentId/$name"
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
            return try {
                (ctx.contentResolver.query(uri, columns, null, null, null)
                    ?: throw java.io.IOException("Save provider returned no cursor")).use { c ->
                    if (c.moveToFirst() && c.getString(1) == name) entry(c, uri) else null
                }
            } catch (e: Exception) {
                // ExternalStorageProvider can wrap a missing child in an
                // IllegalArgumentException. Verify against a fresh successful
                // directory query; never treat an arbitrary exception as absence.
                invalidate()
                try { list()[name] }
                catch (listError: Exception) { throw java.io.IOException("Cannot query save document", listError) }
            }
        }

        override fun list(): Map<String, Entry> {
            val now = android.os.SystemClock.elapsedRealtime()
            cache?.let { if (now - cacheAt < 1500) return it }
            val out = HashMap<String, Entry>()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            run {
                val cursor = ctx.contentResolver.query(children, columns, null, null, null)
                    ?: throw java.io.IOException("Cannot list save documents")
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val n = c.getString(1) ?: continue
                        out[n] = entry(c, DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                    }
                }
            }
            cache = out; cacheAt = now
            com.seedlessds.app.AppLog.i("saves", "listed ${out.size} entries of ${label()} in ${android.os.SystemClock.elapsedRealtime() - now} ms")
            return out
        }

        private fun invalidate() { cache = null }

        private fun find(name: String): Uri? = lookup(name)?.uri

        private fun create(name: String, mime: String): Uri? {
            invalidate()
            val made = runCatching { DocumentsContract.createDocument(ctx.contentResolver, parentUri, mime, name) }.getOrNull() ?: return null
            invalidate()
            val actual = runCatching { ctx.contentResolver.query(made, columns, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(1) else null } }.getOrNull()
            if (actual == name) return made
            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, made) }
            invalidate()
            return list()[name]?.uri
        }

        override fun openFd(name: String, mode: String, create: Boolean): Int {
            val uri = runCatching {
                if (name.endsWith(".dss") || name.endsWith(".ra")) RecoverableReplace.recover(operations, name)
                find(name) ?: (if (create) create(name, MIME_BIN) else null)
            }.getOrNull() ?: return -1
            val pfd = runCatching { ctx.contentResolver.openFileDescriptor(uri, pfdModeString(mode)) }.getOrNull() ?: return -1
            return pfd.detachFd()
        }

        override fun delete(name: String): Boolean {
            val uri = find(name) ?: return false
            invalidate()
            return runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }.getOrDefault(false)
        }

        private fun moveDocument(from: String, to: String): Boolean {
            val src = find(from) ?: return false
            invalidate()
            val moved = runCatching { DocumentsContract.renameDocument(ctx.contentResolver, src, to) }.getOrNull() ?: return false
            val actual = ctx.contentResolver.query(moved, columns, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(1) else null
            }
            if (actual == to) return true
            // Some providers silently append a number. Leave the old document recoverable.
            runCatching { DocumentsContract.renameDocument(ctx.contentResolver, moved, from) }
            return false
        }
        private val operations = object : RecoverableReplace.Files {
            override fun exists(name: String) = find(name) != null
            override fun move(from: String, to: String) = moveDocument(from, to)
            override fun delete(name: String) = this@Document.delete(name)
        }
        override fun recover(name: String) {
            val backup = name + RecoverableReplace.SUFFIX
            if (find(name) == null && find(backup) != null && !moveDocument(backup, name))
                throw java.io.IOException("Previous save is retained as $backup")
        }
        override fun rename(from: String, to: String): Boolean =
            runCatching { RecoverableReplace.commit(operations, from, to) }.getOrDefault(false)

        override fun subfolder(name: String): Target? {
            val e = lookup(name)
            val uri = when {
                e == null -> create(name, DocumentsContract.Document.MIME_TYPE_DIR)
                e.isDir -> e.uri
                else -> null
            } ?: return null
            return document(ctx, treeUri, DocumentsContract.getDocumentId(uri))
        }

        override fun label() = parentId.substringAfter(':')
    }

    private fun pfdMode(mode: String): Int {
        val m = mode.replace("b", "")
        return when {
            m.startsWith("w") -> android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_CREATE or android.os.ParcelFileDescriptor.MODE_TRUNCATE
            m.startsWith("a") -> android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_CREATE or android.os.ParcelFileDescriptor.MODE_APPEND
            m.contains("+") -> android.os.ParcelFileDescriptor.MODE_READ_WRITE
            else -> android.os.ParcelFileDescriptor.MODE_READ_ONLY
        }
    }

    private fun pfdModeString(mode: String): String {
        val m = mode.replace("b", "")
        return when {
            m.startsWith("w") -> "rwt"
            m.startsWith("a") -> "wa"
            m.contains("+") -> "rw"
            else -> "r"
        }
    }

    private fun isWrite(mode: String): Boolean {
        val m = mode.replace("b", "")
        return m.contains("w") || m.contains("+") || m.contains("a")
    }

    private val documents = java.util.concurrent.ConcurrentHashMap<String, Document>()
    private fun document(ctx: Context, treeUri: Uri, parentId: String): Document =
        documents.getOrPut("$treeUri|$parentId") { Document(ctx.applicationContext, treeUri, parentId) }

    private fun setting(): Int = SettingsRepo.global.saveLocation
    private fun customTree(): Uri? = SettingsRepo.global.saveFolder.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }


    fun target(ctx: Context, romUri: Uri?, romPath: String?): Target? = when (setting()) {
        APP_STORAGE -> null
        CUSTOM -> customTree()?.let { runCatching { document(ctx, it, DocumentsContract.getTreeDocumentId(it)) }.getOrNull() }
        else -> when {
            romUri != null && romUri.scheme == "content" -> runCatching {
                val docId = DocumentsContract.getDocumentId(romUri)
                val parent = if (docId.contains('/')) docId.substringBeforeLast('/') else docId.substringBefore(':') + ":"
                document(ctx, romUri, parent)
            }.getOrNull()
            romPath != null -> File(romPath).parentFile?.let { if (it.canWrite()) Folder(it) else null }
            else -> null
        }
    }


    fun place(ctx: Context, romUri: Uri?, romPath: String?, userRel: String, profile: String): Pair<Target, String>? {
        val parts = userRel.split('/').filter { it.isNotEmpty() }
        if (parts.size != 2 || (parts[0] != "backup" && parts[0] != "savestates")) return null
        var t = target(ctx, romUri, romPath) ?: return null
        if (profile.isNotEmpty()) t = t.subfolder(profile) ?: return null
        return t to parts[1]
    }

    private fun migrate(t: Target, name: String, internal: File?) {
        if (internal == null || !internal.exists() || internal.length() == 0L) return
        if (runCatching { t.lookup(name) != null }.getOrDefault(true)) return
        val fd = t.openFd(name, "w", true)
        if (fd < 0) return
        runCatching {
            android.os.ParcelFileDescriptor.adoptFd(fd).use { pfd ->
                FileInputStream(internal).use { i -> FileOutputStream(pfd.fileDescriptor).use { o -> i.copyTo(o) } }
            }
        }
    }


    @JvmStatic
    fun openHandle(ctx: Context, romUri: Uri?, romPath: String?, userRel: String, mode: String, profile: String, internal: File?): NativePathHandle? {
        val (t, name) = place(ctx, romUri, romPath, userRel, profile) ?: return null
        if (runCatching { t.recover(name) }.isFailure)
            return NativePathHandle(File(ctx.cacheDir, "missing/$name").absolutePath, name)
        migrate(t, name, internal)
        val fd = t.openFd(name, mode, isWrite(mode))
        if (fd < 0) return NativePathHandle(File(ctx.cacheDir, "missing/$name").absolutePath, name)
        return NativePathHandle("/proc/self/fd/$fd", fd, name)
    }

    @JvmStatic
    fun remove(ctx: Context, romUri: Uri?, romPath: String?, userRel: String, profile: String): Boolean? {
        val (t, name) = place(ctx, romUri, romPath, userRel, profile) ?: return null
        return t.delete(name)
    }

    @JvmStatic
    fun rename(ctx: Context, romUri: Uri?, romPath: String?, fromRel: String, toRel: String, profile: String): Boolean? {
        val (t, from) = place(ctx, romUri, romPath, fromRel, profile) ?: return null
        val (_, to) = place(ctx, romUri, romPath, toRel, profile) ?: return null
        return runCatching { t.rename(from, to) }.getOrDefault(false)
    }

    fun stat(ctx: Context, romUri: Uri?, romPath: String?, userRel: String, profile: String, internal: File): Entry? {
        val placed = place(ctx, romUri, romPath, userRel, profile)
        if (placed == null) return if (internal.exists()) Entry(Uri.fromFile(internal), internal.lastModified(), internal.length(), false) else null
        val (t, name) = placed
        runCatching { t.lookup(name) }.getOrNull()?.let { return it }
        return if (internal.exists()) Entry(Uri.fromFile(internal), internal.lastModified(), internal.length(), false) else null
    }

    fun listStates(ctx: Context, romUri: Uri?, romPath: String?, profile: String, internalDir: File): Map<String, Entry> {
        val placed = place(ctx, romUri, romPath, "savestates/metadata", profile)
        val out = (internalDir.listFiles() ?: emptyArray()).associate {
            it.name to Entry(Uri.fromFile(it), it.lastModified(), it.length(), it.isDirectory)
        }.toMutableMap()
        placed?.first?.let { target ->
            var entries = target.list()
            val interrupted = entries.keys.filter { it.endsWith(RecoverableReplace.SUFFIX) }
                .map { it.removeSuffix(RecoverableReplace.SUFFIX) }.filter { it !in entries }
            if (interrupted.isNotEmpty()) {
                interrupted.forEach { target.recover(it) }
                entries = target.list()
            }
            out.putAll(entries)
        }
        return out
    }

    fun readBytes(ctx: Context, romUri: Uri?, romPath: String?, userRel: String, profile: String, internal: File): ByteArray? {
        val placed = place(ctx, romUri, romPath, userRel, profile)
        if (placed == null) return if (internal.exists()) runCatching { internal.readBytes() }.getOrNull() else null
        val (t, name) = placed
        val fd = t.openFd(name, "r", false)
        if (fd < 0) return if (internal.exists()) runCatching { internal.readBytes() }.getOrNull() else null
        return runCatching { android.os.ParcelFileDescriptor.adoptFd(fd).use { pfd -> FileInputStream(pfd.fileDescriptor).use { it.readBytes() } } }.getOrNull()
    }

    fun writeBytes(ctx: Context, romUri: Uri?, romPath: String?, userRel: String, profile: String, internal: File, bytes: ByteArray): Boolean {
        val placed = place(ctx, romUri, romPath, userRel, profile)
        if (placed == null) return runCatching {
            internal.parentFile?.mkdirs()
            val temp = File(internal.parentFile, internal.name + ".pending")
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            android.system.Os.rename(temp.absolutePath, internal.absolutePath)
        }.isSuccess
        val (t, name) = placed
        val temp = "$name.pending"
        val fd = t.openFd(temp, "w", true)
        if (fd < 0) return false
        return runCatching {
            android.os.ParcelFileDescriptor.adoptFd(fd).use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { it.write(bytes); it.flush() }
            }
            t.rename(temp, name)
        }.getOrDefault(false)
    }

    fun delete(ctx: Context, romUri: Uri?, romPath: String?, userRel: String, profile: String, internal: File) {
        val placed = place(ctx, romUri, romPath, userRel, profile)
        if (placed != null) placed.first.delete(placed.second)
        runCatching { internal.delete() }
    }

    fun folderLabel(ctx: Context): String = when (setting()) {
        APP_STORAGE -> ctx.getString(R.string.settings_app_storage)
        CUSTOM -> customTree()?.let { runCatching { DocumentsContract.getTreeDocumentId(it).substringAfter(':') }.getOrNull() } ?: ctx.getString(R.string.settings_not_chosen)
        else -> ctx.getString(R.string.settings_same_folder_as_each_rom)
    }
}
