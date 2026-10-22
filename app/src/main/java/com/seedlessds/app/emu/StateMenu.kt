package com.seedlessds.app.emu

import com.seedlessds.app.R
import com.seedlessds.core.SeedlessCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StateMode { SAVE, LOAD }

class SaveStateInfo(
    private val ctx: android.content.Context,
    private val userSub: String,
    private val base: String,
    private val romUri: android.net.Uri?,
    private val romPath: String?,
) {
    private fun internalDir() = File(EmuAssets.systemDir(ctx), "user" + (if (userSub.isEmpty()) "" else "/$userSub") + "/savestates")
    private fun stateName(slot: Int) = "${base}_$slot.dss"
    private fun sidecarName(slot: Int) = "${base}_$slot.ra"
    private fun rel(name: String) = "savestates/$name"
    private fun stat(slot: Int) = SaveLocation.stat(ctx, romUri, romPath, rel(stateName(slot)), userSub, File(internalDir(), stateName(slot)))
    fun exists(slot: Int) = stat(slot) != null
    fun date(slot: Int): Long = stat(slot)?.lastModified ?: 0L
    fun sizeKb(slot: Int): Long = (stat(slot)?.size ?: 0L) / 1024
    fun delete(slot: Int) {
        SaveLocation.delete(ctx, romUri, romPath, rel(stateName(slot)), userSub, File(internalDir(), stateName(slot)))
        deleteSidecar(slot)
    }
    private fun stateDigest(slot: Int): ByteArray? = SaveLocation.readBytes(
        ctx, romUri, romPath, rel(stateName(slot)), userSub, File(internalDir(), stateName(slot))
    )?.let { java.security.MessageDigest.getInstance("SHA-256").digest(it) }
    fun readSidecar(slot: Int): ByteArray? = runCatching {
        val bytes = SaveLocation.readBytes(ctx, romUri, romPath, rel(sidecarName(slot)), userSub, File(internalDir(), sidecarName(slot)))
            ?: return@runCatching null
        // Legacy sidecars have no state identity: reset RA rather than restoring
        // progress for a different save after an interrupted write.
        if (bytes.size < 36 || !bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(83, 68, 82, 65))) return@runCatching null
        if (!bytes.copyOfRange(4, 36).contentEquals(stateDigest(slot))) return@runCatching null
        bytes.copyOfRange(36, bytes.size)
    }.getOrNull()
    fun writeSidecar(slot: Int, bytes: ByteArray): Boolean {
        val digest = stateDigest(slot) ?: return false
        return SaveLocation.writeBytes(ctx, romUri, romPath, rel(sidecarName(slot)), userSub,
            File(internalDir(), sidecarName(slot)), byteArrayOf(83, 68, 82, 65) + digest + bytes)
    }
    internal fun slots(): List<SlotView> {
        val entries = SaveLocation.listStates(ctx, romUri, romPath, userSub, internalDir())
        return (0 until 10).map { slot ->
            val entry = entries[stateName(slot)]
            SlotView(slot, entry != null, entry?.lastModified ?: 0, (entry?.size ?: 0) / 1024,
                if (entry != null) thumbnailTop(slot) else null)
        }
    }
    fun deleteSidecar(slot: Int) =
        SaveLocation.delete(ctx, romUri, romPath, rel(sidecarName(slot)), userSub, File(internalDir(), sidecarName(slot)))
    fun thumbnailTop(slot: Int): Bitmap? {
        return try {
            val top = IntArray(49152); val bottom = IntArray(49152)
            SeedlessCore.snapshotFile(com.seedlessds.app.filesystem.PathCache.USER_PREFIX + rel(stateName(slot)), top, bottom)
            Bitmap.createBitmap(top, 256, 192, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) { null }
    }
}

internal data class SlotView(val slot: Int, val occupied: Boolean, val date: Long, val sizeKb: Long, val thumb: Bitmap?)

@Composable
fun StateMenu(
    mode: StateMode,
    info: SaveStateInfo,
    onSave: suspend (Int) -> Boolean,
    onLoad: suspend (Int) -> Boolean,
    onBack: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    var status by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmSlot by remember { mutableStateOf<Int?>(null) }
    var actionSlot by remember { mutableStateOf<SlotView?>(null) }
    val saving = mode == StateMode.SAVE
    androidx.activity.compose.BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var listing by remember { mutableStateOf(true) }
    var slots by remember { mutableStateOf((0 until 10).map { SlotView(it, false, 0, 0, null) }) }
    LaunchedEffect(info, refresh) {
        listing = true
        try { slots = withContext(Dispatchers.IO) { info.slots() } }
        catch (_: Exception) { status = uiContext.getString(R.string.state_directory_error) }
        finally { listing = false }
    }
    val execute: (SlotView) -> Unit = { sv ->
        if (!busy && (saving || sv.occupied)) {
            busy = true
            status = if (saving) uiContext.getString(R.string.state_saving_progress) else uiContext.getString(R.string.state_loading_progress)
            scope.launch {
                try {
                    val ok = if (saving) onSave(sv.slot) else onLoad(sv.slot)
                    val label = slotLabel(uiContext, sv.slot)
                    status = when {
                        ok && saving -> uiContext.getString(R.string.state_saved_to, label)
                        ok -> uiContext.getString(R.string.state_loaded_from, label)
                        saving -> uiContext.getString(R.string.state_could_not_save_to, label)
                        else -> uiContext.getString(R.string.state_empty_or_corrupt_slot, label)
                    }
                    if (ok && saving) refresh++
                } finally { busy = false }
            }
        }
    }
    val activate: (SlotView) -> Unit = { sv ->
        val cfg = SettingsRepo.current
        if (!busy && !listing && actionSlot == null) {
            if (sv.occupied && (if (saving) cfg.overwriteNeedsConfirm else cfg.loadNeedsConfirm)) actionSlot = sv
            else execute(sv)
        }
    }
    val fmt = remember { SimpleDateFormat(uiContext.getString(R.string.state_dd_mm_yyyy_hh_mm), Locale.getDefault()) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ExternalInfoBus.clear() } }

    val columns = if (landscape) 2 else 1
    val cursor = rememberMenuCursor(
        count = slots.size,
        columns = columns,
        onAccept = { i -> activate(slots[i]) },
        onCancel = onBack,
    )

    SeedlessScreenScaffold(
        title = if (saving) uiContext.getString(R.string.common_save_state) else uiContext.getString(R.string.common_load_state),
        subtitle = status ?: if (saving) uiContext.getString(R.string.state_pick_a_slot) else uiContext.getString(R.string.state_pick_a_state),
        onBack = onBack,
        hints = listOf(
            GamepadHint(null, uiContext.getString(R.string.common_navigate)),
            GamepadHint(uiContext.getString(R.string.button_a), if (saving) uiContext.getString(R.string.common_save) else uiContext.getString(R.string.state_load)),
            GamepadHint(uiContext.getString(R.string.button_b), uiContext.getString(R.string.common_cancel)),
        ),
    ) { pad ->
        val gridState = rememberLazyGridState()
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .verticalScrollbar(gridState, colors.green.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(slots, key = { _, sv -> sv.slot }) { i, sv ->
                    SlotRow(
                        selected = i == cursor.index,
                        onFocused = {
                            ExternalInfoBus.show {
                                ExternalStateSlotInfo(
                                    label = slotLabel(uiContext, sv.slot),
                                    quick = sv.slot == 8,
                                    detail = if (sv.occupied) uiContext.getString(R.string.state_date_size, fmt.format(Date(sv.date)), sv.sizeKb) else null,
                                    thumbnail = sv.thumb?.asImageBitmap(),
                                    saving = saving,
                                )
                            }
                        },
                        sv = sv,
                        mode = mode,
                        dateText = if (sv.occupied) fmt.format(Date(sv.date)) else null,
                        onClick = { if (!busy) { cursor.select(i); activate(sv) } },
                        onDelete = { if (!busy && !listing) confirmSlot = sv.slot },
                    )
                }
                status?.let { msg ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = msg,
                            color = colors.green,
                            fontFamily = SeedlessMono,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        actionSlot?.let { sv ->
            SeedlessConfirmDialog(
                title = uiContext.getString(if (saving) R.string.common_save_state else R.string.common_load_state),
                message = uiContext.getString(if (saving) R.string.state_overwrite_question else R.string.state_load_question, slotLabel(uiContext, sv.slot)),
                confirmLabel = uiContext.getString(if (saving) R.string.common_save else R.string.state_load),
                onConfirm = { actionSlot = null; execute(sv) },
                onDismiss = { actionSlot = null },
            )
        }
        confirmSlot?.let { cs ->
            SeedlessConfirmDialog(
                title = uiContext.getString(R.string.common_delete_2, slotLabel(uiContext, cs)),
                message = uiContext.getString(R.string.state_this_permanently_deletes_the_savestate_in),
                confirmLabel = uiContext.getString(R.string.common_delete),
                onConfirm = {
                    if (!busy) {
                        busy = true; confirmSlot = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { info.delete(cs) }
                                refresh++; status = uiContext.getString(R.string.state_deleted, slotLabel(uiContext, cs))
                            } catch (_: Exception) { status = uiContext.getString(R.string.state_delete_error) }
                            finally { busy = false }
                        }
                    }
                },
                onDismiss = { confirmSlot = null },
            )
        }
    }
}

private fun slotLabel(uiContext: android.content.Context, slot: Int) = if (slot == 9) uiContext.getString(R.string.state_slot_10) else uiContext.getString(R.string.state_slot, slot + 1)

@Composable
private fun SlotRow(
    selected: Boolean,
    onFocused: () -> Unit,
    sv: SlotView,
    mode: StateMode,
    dateText: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val quick = sv.slot == 8
    val enabled = mode == StateMode.SAVE || sv.occupied
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val focused = selected
    androidx.compose.runtime.LaunchedEffect(focused) { if (focused) onFocused() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .alpha(if (enabled) 1f else 0.45f)
            .background(colors.surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> colors.red
                    quick -> colors.red.copy(alpha = 0.45f)
                    else -> colors.line
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(12.dp),
    ) {
        SlotThumb(sv.thumb, sv.occupied, mode, quick)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slotLabel(uiContext, sv.slot),
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (quick) {
                    Text(
                        text = uiContext.getString(R.string.common_quick),
                        color = colors.red,
                        fontFamily = SeedlessMono,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = when {
                    dateText != null -> uiContext.getString(R.string.state_kb_2, dateText, sv.sizeKb)
                    mode == StateMode.SAVE -> uiContext.getString(R.string.state_empty_press_to_save)
                    else -> uiContext.getString(R.string.common_empty)
                },
                color = colors.text3,
                fontFamily = SeedlessMono,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (sv.occupied) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Delete, uiContext.getString(R.string.common_delete), tint = colors.red, modifier = Modifier.size(17.dp)) }
        }
    }
}

@Composable
private fun SlotThumb(bmp: Bitmap?, occupied: Boolean, mode: StateMode, quick: Boolean) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val mod = Modifier.width(84.dp).aspectRatio(256f / 192f).clip(RoundedCornerShape(11.dp))
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), uiContext.getString(R.string.state_screenshot),
            mod.background(SeedlessColors.emulationBg),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(mod.background(colors.surface2), Alignment.Center) {
            val icon = when {
                quick -> Icons.Rounded.Bolt
                mode == StateMode.SAVE -> Icons.Rounded.SaveAs
                else -> Icons.Rounded.Download
            }
            Icon(icon, null, tint = colors.text3, modifier = Modifier.size(24.dp))
        }
    }
}
