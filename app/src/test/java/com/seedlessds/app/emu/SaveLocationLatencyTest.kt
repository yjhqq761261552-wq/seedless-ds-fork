package com.seedlessds.app.emu

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class SaveLocationLatencyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: SaveProvider
    private lateinit var target: SaveLocation.Document
    private val parentId = "primary:Roms/NDS"

    @Before fun setUp() {
        provider = SaveProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "com.android.externalstorage.documents"
        })
        ShadowContentResolver.registerProviderInternal("com.android.externalstorage.documents", provider)
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", parentId)
        target = SaveLocation.Document(context, tree, parentId)
    }

    @Test fun multipleMissingRecoveryNamesShareOneSuccessfulDirectoryQuery() {
        assertNull(target.lookup("game_8.ra"))
        assertNull(target.lookup("game_8.ra.seedless-previous"))
        assertNull(target.lookup("game_8.dss.seedless-previous"))
        assertEquals(1, provider.directoryQueries)
    }

    @Test fun deletingMissingSidecarDoesNotQueryOrListGameDirectory() {
        assertFalse(target.delete("game_8.ra"))
        assertEquals(1, provider.deletes)
        assertEquals(0, provider.directoryQueries)
        assertEquals(0, provider.documentQueries)
    }

    @Test fun successfulMutationInvalidatesMissingNameCache() {
        provider.names += "game_8.ra"
        assertNull(target.lookup("missing"))
        assertEquals(1, provider.directoryQueries)
        org.junit.Assert.assertTrue(target.delete("game_8.ra"))
        assertNull(target.lookup("game_8.ra"))
        assertEquals(2, provider.directoryQueries)
    }

    @Test fun existingChildIsFoundEvenAfterCachingItsAbsence() {
        assertNull(target.lookup("game_8.ra"))
        provider.names += "game_8.ra"
        assertNotNull(target.lookup("game_8.ra"))
    }

    @Test fun revokedPermissionIsNotReportedAsMissingFile() {
        assertNull(target.lookup("missing"))
        provider.denyAccess = true
        assertThrows(SecurityException::class.java) { target.lookup("game_8.ra") }
    }

    @Test fun failedDirectoryQueryIsNotReportedAsMissingFile() {
        provider.failListing = true
        assertThrows(java.io.IOException::class.java) { target.lookup("game_8.ra") }
    }

    @Test fun streamedDigestMatchesExistingSidecarIdentity() {
        val file = File(context.cacheDir, "state-digest-test.dss")
        val contents = ByteArray(2 * 1024 * 1024 + 57) { (it * 37).toByte() }
        file.writeBytes(contents)
        try {
            val actual = SaveLocation.stateDigest(context, null, null, "savestates/test.dss", "", file)
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(contents), actual)
            assertArrayEquals(contents, SaveLocation.readBytes(context, null, null, "savestates/test.dss", "", file))
        } finally {
            file.delete()
        }
        assertNull(SaveLocation.stateDigest(context, null, null, "savestates/test.dss", "", file))
    }

    private class SaveProvider : ContentProvider() {
        val names = mutableSetOf("game_8.dss")
        var directoryQueries = 0
        var documentQueries = 0
        var deletes = 0
        var denyAccess = false
        var failListing = false
        override fun onCreate() = true
        override fun getType(uri: Uri): String = "application/octet-stream"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if (denyAccess) throw SecurityException("Permission revoked")
            val columns = requireNotNull(projection)
            val cursor = MatrixCursor(columns)
            val listing = uri.lastPathSegment == "children"
            val selectedNames = if (listing) {
                directoryQueries++
                if (failListing) throw java.io.IOException("Provider unavailable")
                names.toList()
            } else {
                documentQueries++
                val name = DocumentsContract.getDocumentId(uri).substringAfterLast('/')
                if (name !in names) throw IllegalArgumentException("Missing document")
                listOf(name)
            }
            for (name in selectedNames) {
                cursor.addRow(columns.map<String, Any?> { column ->
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> "primary:Roms/NDS/$name"
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1L
                        DocumentsContract.Document.COLUMN_SIZE -> 512L
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> "application/octet-stream"
                        else -> null
                    }
                }.toTypedArray())
            }
            return cursor
        }

        override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
            if (method != "android:deleteDocument") return super.call(method, arg, extras)
            deletes++
            @Suppress("DEPRECATION")
            val uri = requireNotNull(extras?.getParcelable<Uri>("uri"))
            val name = DocumentsContract.getDocumentId(uri).substringAfterLast('/')
            if (!names.remove(name)) throw FileNotFoundException("Missing document")
            return android.os.Bundle.EMPTY
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unused")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Unused")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Unused")
    }
}
