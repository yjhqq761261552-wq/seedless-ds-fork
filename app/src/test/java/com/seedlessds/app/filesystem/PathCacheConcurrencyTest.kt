package com.seedlessds.app.filesystem

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [PathCacheConcurrencyTest.DetachableDescriptor::class])
class PathCacheConcurrencyTest {
    @Test fun fourthSlowSaveDoesNotBlockRomOrCartridgeBackupReads() {
        val context = RuntimeEnvironment.getApplication()
        val data = File(context.cacheDir, "provider-data.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val provider = SlowSaveProvider(data)
        provider.attachInfo(context, ProviderInfo().apply { authority = AUTHORITY })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, PARENT)
        val rom = DocumentsContract.buildDocumentUriUsingTree(tree, "$PARENT/game.nds")
        PathCache.setAppContext(context)
        PathCache.setDataRoot(null)
        PathCache.activeUserSub = ""
        PathCache.changeRom("test-rom", rom, null, "game.nds")
        val executor = Executors.newFixedThreadPool(3)
        try {
            repeat(3) {
                provider.names += "temp.dss"
                assertTrue(PathCache.rename(PathCache.USER_PREFIX + "savestates/temp.dss",
                    PathCache.USER_PREFIX + "savestates/slot.dss"))
            }
            provider.names += "temp.dss"
            val saving = executor.submit<Boolean> {
                PathCache.rename(PathCache.USER_PREFIX + "savestates/temp.dss",
                    PathCache.USER_PREFIX + "savestates/slot.dss")
            }
            assertTrue("Fourth save reached simulated slow storage", provider.blocked.await(5, TimeUnit.SECONDS))

            // Finish both reads before releasing the fourth save's storage call.
            val romRead = executor.submit<String> { openAndClose("test-rom") }
            val backupRead = executor.submit<String> { openAndClose(PathCache.USER_PREFIX + "backup/game.dsv") }
            assertEquals("game.nds", romRead.get(1, TimeUnit.SECONDS))
            assertEquals("game.dsv", backupRead.get(1, TimeUnit.SECONDS))
            val attemptingLoad = CountDownLatch(1)
            val loading = executor.submit<String> {
                attemptingLoad.countDown()
                openAndClose(PathCache.USER_PREFIX + "savestates/slot.dss")
            }
            assertTrue(attemptingLoad.await(1, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { loading.get(100, TimeUnit.MILLISECONDS) }
            provider.resume.countDown()
            assertTrue(saving.get(5, TimeUnit.SECONDS))
            assertEquals("slot.dss", loading.get(5, TimeUnit.SECONDS))
            assertEquals(4, provider.saves)
            assertTrue("Committed slot remains present", "slot.dss" in provider.names)
        } finally {
            provider.resume.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
            PathCache.changeRom(null, null, null, null)
            provider.descriptors.forEach { it.close() }
            data.delete()
        }
    }

    private fun openAndClose(path: String): String {
        val handle = PathCache.open(path, "rb")
        assertNotNull(handle)
        assertTrue(handle.fileFd >= 0)
        // The fixture retains and closes the host descriptors after the test.
        return handle.fileName
    }

    private class SlowSaveProvider(private val data: File) : ContentProvider() {
        val names = ConcurrentHashMap.newKeySet<String>().apply {
            addAll(listOf("game.nds", "game.dsv", "slot.dss"))
        }
        val descriptors = ConcurrentLinkedQueue<ParcelFileDescriptor>()
        val blocked = CountDownLatch(1)
        val resume = CountDownLatch(1)
        var saves = 0
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = requireNotNull(projection)
            val result = MatrixCursor(columns)
            val selected = if (uri.lastPathSegment == "children") names.toList() else {
                val name = DocumentsContract.getDocumentId(uri).substringAfterLast('/')
                if (name !in names) throw IllegalArgumentException("Missing document")
                listOf(name)
            }
            for (name in selected) result.addRow(columns.map<String, Any?> {
                when (it) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> "$PARENT/$name"
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1L
                    DocumentsContract.Document.COLUMN_SIZE -> 3L
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> "application/octet-stream"
                    else -> null
                }
            }.toTypedArray())
            return result
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val name = DocumentsContract.getDocumentId(uri).substringAfterLast('/')
            if (name !in names) throw FileNotFoundException(name)
            return ParcelFileDescriptor.open(data, ParcelFileDescriptor.MODE_READ_ONLY).also { descriptors += it }
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            @Suppress("DEPRECATION")
            val uri = requireNotNull(extras?.getParcelable<Uri>("uri"))
            val from = DocumentsContract.getDocumentId(uri).substringAfterLast('/')
            if (method == "android:deleteDocument") {
                if (!names.remove(from)) throw FileNotFoundException(from)
                return Bundle.EMPTY
            }
            if (method == "android:renameDocument") {
                val to = requireNotNull(extras.getString("_display_name"))
                if (from == "slot.dss") {
                    saves++
                    if (saves == 4) {
                        blocked.countDown()
                        check(resume.await(10, TimeUnit.SECONDS))
                    }
                }
                check(to !in names && names.remove(from))
                names += to
                val moved = DocumentsContract.buildDocumentUriUsingTree(uri, "$PARENT/$to")
                return Bundle().apply { putParcelable("uri", moved) }
            }
            return super.call(method, arg, extras)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unused")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Unused")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Unused")
    }

    // Robolectric 4.14 does not shadow API 35's detachFd. The test exercises
    // routing and contention, while the provider retains descriptor ownership.
    @Implements(ParcelFileDescriptor::class)
    class DetachableDescriptor : ShadowParcelFileDescriptor() {
        @Implementation protected fun detachFd(): Int = 123
    }

    companion object {
        private const val AUTHORITY = "com.android.externalstorage.documents"
        private const val PARENT = "primary:Roms/NDS"
    }
}
