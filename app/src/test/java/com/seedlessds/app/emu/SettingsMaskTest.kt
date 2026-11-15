package com.seedlessds.app.emu

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Interaction test for the mask settings page: picking an image stores it
 *  and updates the setting; removing clears both. Mirrors the fake-registry
 *  pattern the DSi import tests used. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class SettingsMaskTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeOwner : ActivityResultRegistryOwner {
        var uri: Uri? = null
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                val u = uri
                if (u == null) {
                    dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
                    return
                }
                @Suppress("UNCHECKED_CAST")
                val parsed = (contract as ActivityResultContract<I, Any?>)
                    .parseResult(Activity.RESULT_OK, Intent().setData(u))
                dispatchResult(requestCode, parsed)
            }
        }
    }

    private val owner = FakeOwner()
    private var held: DsSettings = DsSettings()

    private fun setUpPage() {
        held = DsSettings()
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme {
                    var s by remember { mutableStateOf(DsSettings()) }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        MaskSettingsPage(s) { next -> s = next; held = next }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun pngUri(): Uri {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        val f = File.createTempFile("mask-page-", ".png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f.deleteOnExit()
        return Uri.fromFile(f)
    }

    @Test
    fun pickStoresTopMaskAndRemoveClearsIt() {
        setUpPage()
        val app = RuntimeEnvironment.getApplication()
        assertFalse(ScreenMasks.file(app, ScreenMasks.Slot.TOP).isFile)

        owner.uri = pngUri()
        composeRule.onAllNodesWithText("Choose image")[0]
            .apply { performScrollTo() }
            .performClick()
        composeRule.waitForIdle()

        assertEquals("top.img", held.maskTop)
        assertTrue(ScreenMasks.file(app, ScreenMasks.Slot.TOP).isFile)
        composeRule.onNodeWithText("Remove mask").assertIsDisplayed()

        composeRule.onNodeWithText("Remove mask")
            .apply { performScrollTo() }
            .performClick()
        composeRule.waitForIdle()

        assertEquals("", held.maskTop)
        assertFalse(ScreenMasks.file(app, ScreenMasks.Slot.TOP).isFile)
    }

    @Test
    fun pickRejectsNonImage() {
        setUpPage()
        val bad = File.createTempFile("mask-page-", ".txt")
        bad.writeText("nope")
        bad.deleteOnExit()
        owner.uri = Uri.fromFile(bad)
        composeRule.onAllNodesWithText("Choose image")[1]
            .apply { performScrollTo() }
            .performClick()
        composeRule.waitForIdle()
        assertEquals("", held.maskBottom)
    }
}
