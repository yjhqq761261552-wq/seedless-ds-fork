package com.seedlessds.app.emu

import com.seedlessds.app.R
import com.seedlessds.app.LauncherIcon
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.seedlessds.app.ra.RaSettings
import com.seedlessds.app.ra.RetroAchievements
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class SettingsOptions(private val uiContext: android.content.Context) {
    val frameskipType = listOf(uiContext.getString(R.string.common_none), uiContext.getString(R.string.settings_manual), uiContext.getString(R.string.settings_automatic))
    val ffwdLabels = listOf(uiContext.getString(R.string.settings_0_5x), uiContext.getString(R.string.settings_1_5x), uiContext.getString(R.string.settings_2x), uiContext.getString(R.string.settings_3x), uiContext.getString(R.string.settings_4x), uiContext.getString(R.string.settings_unlimited), uiContext.getString(R.string.settings_5x), uiContext.getString(R.string.settings_6x), uiContext.getString(R.string.settings_8x), uiContext.getString(R.string.settings_10x), uiContext.getString(R.string.settings_12x), uiContext.getString(R.string.settings_16x), uiContext.getString(R.string.settings_20x))
    val ffwdOrder = listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 5)
    val ffwdSpeed = ffwdOrder.map { ffwdLabels[it] }
    val audioLatency = listOf(uiContext.getString(R.string.settings_low), uiContext.getString(R.string.settings_medium), uiContext.getString(R.string.settings_high), uiContext.getString(R.string.settings_very_high))
    val micLevel = listOf(uiContext.getString(R.string.settings_low), uiContext.getString(R.string.settings_medium), uiContext.getString(R.string.settings_high), uiContext.getString(R.string.settings_very_high))
    val language = listOf(uiContext.getString(R.string.language_japanese), uiContext.getString(R.string.language_english), uiContext.getString(R.string.language_french), uiContext.getString(R.string.language_german), uiContext.getString(R.string.language_italian), uiContext.getString(R.string.language_spanish))
    val slot2 = listOf(uiContext.getString(R.string.common_none), uiContext.getString(R.string.settings_gba_cartridge), uiContext.getString(R.string.settings_sram_cartridge), uiContext.getString(R.string.settings_rumble_pak),
        uiContext.getString(R.string.settings_motion_pack_official), uiContext.getString(R.string.settings_motion_pack_homebrew))
    val autosave = listOf(uiContext.getString(R.string.settings_off), uiContext.getString(R.string.settings_on_pause_suspend), uiContext.getString(R.string.settings_every_5_min), uiContext.getString(R.string.settings_every_15_min), uiContext.getString(R.string.settings_every_30_min))
    val autofire = listOf(uiContext.getString(R.string.settings_speed_1_fast), uiContext.getString(R.string.settings_speed_2), uiContext.getString(R.string.settings_speed_3), uiContext.getString(R.string.settings_speed_4), uiContext.getString(R.string.settings_speed_5_slow))
    val color = listOf(uiContext.getString(R.string.settings_slate_gray), uiContext.getString(R.string.settings_mahogany), uiContext.getString(R.string.settings_brown), uiContext.getString(R.string.settings_lavender), uiContext.getString(R.string.settings_persian_orange), uiContext.getString(R.string.settings_wood),
        uiContext.getString(R.string.settings_khaki), uiContext.getString(R.string.settings_mantis), uiContext.getString(R.string.settings_viridian), uiContext.getString(R.string.settings_sea_green), uiContext.getString(R.string.settings_moonstone), uiContext.getString(R.string.settings_tufts_blue), uiContext.getString(R.string.settings_cerulean),
        uiContext.getString(R.string.settings_toolbox), uiContext.getString(R.string.settings_deep_lavender), uiContext.getString(R.string.settings_mulberry))
    val irOrder: List<Int> = SettingsRepo.irScales.indices.sortedBy { SettingsRepo.irScales[it] }
    val internalRes: List<String> = irOrder.map {
        val n = SettingsRepo.irScales[it]
        if (n == 1) uiContext.getString(R.string.settings_1x_native) else n.toString() + uiContext.getString(R.string.settings_x)
    }
    // Shader filters are discovered from the deployed shader directory
    // (EmuAssets copies assets/shaders there), so every shipped .dfx
    // recipe is offered without hardcoding a list. The classic DraStic
    // filters keep their localized labels and float to the top; the rest
    // are listed alphabetically by file name.
    val knownFilters = listOf(
        "None" to uiContext.getString(R.string.common_none),
        "Linear" to uiContext.getString(R.string.settings_linear),
        "Scale2X" to uiContext.getString(R.string.settings_scale2x),
        "HQ2X" to uiContext.getString(R.string.settings_hq2x),
        "Quilez" to uiContext.getString(R.string.settings_quilez),
        "Scanline" to uiContext.getString(R.string.settings_scanline),
        "FXAA" to uiContext.getString(R.string.settings_fxaa),
        "FXAA HQ" to uiContext.getString(R.string.settings_fxaa_hq),
        "SMAA" to uiContext.getString(R.string.settings_smaa),
        "sharp_bilinear" to uiContext.getString(R.string.settings_sharp_bilinear),
        "sharp_bilinear+nds_color" to uiContext.getString(R.string.settings_sharp_bilinear_nds_color),
        "lcd1x" to uiContext.getString(R.string.settings_lcd1x),
        "lcd1x+nds_color" to uiContext.getString(R.string.settings_lcd1x_nds_color),
        "zfast_lcd" to uiContext.getString(R.string.settings_zfast_lcd),
        "zfast_lcd+nds_color" to uiContext.getString(R.string.settings_zfast_lcd_nds_color))
    val availableFilters: List<String> = java.io.File(EmuAssets.systemDir(uiContext), "shaders")
        .listFiles { f -> f.isFile && f.name.endsWith(".dfx") }
        ?.map { it.name.removeSuffix(".dfx") }
        ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
        .orEmpty()
    val knownValues = knownFilters.map { pair -> pair.first }.toSet()
    val filterPairs: List<Pair<String, String>> = if (availableFilters.isEmpty()) {
        listOf("None" to uiContext.getString(R.string.common_none))
    } else {
        val curated = knownFilters.filter { pair -> availableFilters.contains(pair.first) }
        val extras = availableFilters.filter { name -> !knownValues.contains(name) }
            .map { name -> name to name }
        curated + extras
    }
    val filter = filterPairs.map { it.second }
    val filterValues = filterPairs.map { it.first }
    val extraFuncs = listOf(uiContext.getString(R.string.settings_off), uiContext.getString(R.string.common_fast_forward_2), uiContext.getString(R.string.common_quick_save_2), uiContext.getString(R.string.common_quick_load_2),
        uiContext.getString(R.string.common_swap_screens), uiContext.getString(R.string.common_swap_layouts_1_2), uiContext.getString(R.string.settings_show_hide_buttons),
        uiContext.getString(R.string.common_microphone_noise), uiContext.getString(R.string.common_cycle_layouts), uiContext.getString(R.string.common_turbo_rapid_fire), uiContext.getString(R.string.rewind_hold))
    val menuPos = listOf(uiContext.getString(R.string.common_bottom), uiContext.getString(R.string.common_top), uiContext.getString(R.string.common_left), uiContext.getString(R.string.common_right), uiContext.getString(R.string.settings_hidden))
    val screenLayout = listOf(uiContext.getString(R.string.settings_landscape_1_1), uiContext.getString(R.string.settings_landscape_x_1), uiContext.getString(R.string.settings_vertical), uiContext.getString(R.string.common_integer_scale), uiContext.getString(R.string.settings_fullscreen), uiContext.getString(R.string.settings_pip_overlay))
    val rotation = listOf(uiContext.getString(R.string.settings_automatic), uiContext.getString(R.string.settings_landscape), uiContext.getString(R.string.settings_landscape_flipped), uiContext.getString(R.string.settings_portrait), uiContext.getString(R.string.settings_portrait_flipped))
    val rumbleDev = listOf(uiContext.getString(R.string.common_none), uiContext.getString(R.string.settings_controller), uiContext.getString(R.string.settings_internal))
    val analogStick = listOf(uiContext.getString(R.string.settings_off), uiContext.getString(R.string.settings_fps_mode), uiContext.getString(R.string.settings_stick_mode), uiContext.getString(R.string.settings_pointer_mode))
    val extDisplay = listOf(uiContext.getString(R.string.settings_clone_device), uiContext.getString(R.string.common_integer_scale), uiContext.getString(R.string.settings_fullscreen))
    val extScreen = listOf(uiContext.getString(R.string.common_top_screen), uiContext.getString(R.string.common_bottom_screen))
    val padStyle = listOf(uiContext.getString(R.string.settings_dark), uiContext.getString(R.string.settings_light), uiContext.getString(R.string.settings_outline))
    val singleScreen = listOf(uiContext.getString(R.string.settings_off), uiContext.getString(R.string.settings_top_only), uiContext.getString(R.string.settings_bottom_only))
}

private data class Cat(val icon: ImageVector, val title: String, val summary: String)

private fun settingsCategories(uiContext: android.content.Context) = listOf(
    Cat(Icons.Rounded.Movie, uiContext.getString(R.string.settings_video), uiContext.getString(R.string.settings_frameskip_fast_forward_3d_render_fps)),
    Cat(Icons.Rounded.Monitor, uiContext.getString(R.string.settings_screens), uiContext.getString(R.string.settings_layout_filter_rotation_external_display)),
    Cat(Icons.Rounded.Gamepad, uiContext.getString(R.string.settings_on_screen_controls), uiContext.getString(R.string.settings_virtual_pad_opacity_special_buttons)),
    Cat(Icons.Rounded.SportsEsports, uiContext.getString(R.string.settings_physical_controller), uiContext.getString(R.string.settings_button_mapping_rumble_sticks_triggers)),
    Cat(Icons.Rounded.VolumeUp, uiContext.getString(R.string.settings_audio), uiContext.getString(R.string.settings_volume_latency_microphone)),
    Cat(Icons.Rounded.Memory, uiContext.getString(R.string.settings_ds_system), uiContext.getString(R.string.settings_firmware_nickname_language_color_rtc)),
    Cat(Icons.Rounded.Save, uiContext.getString(R.string.settings_saves_and_states), uiContext.getString(R.string.settings_autosave_autoload_confirmations)),
    Cat(Icons.Rounded.Tune, uiContext.getString(R.string.settings_advanced), uiContext.getString(R.string.settings_performance_cheats_bios_data)),
    Cat(Icons.Rounded.EmojiEvents, uiContext.getString(R.string.common_retroachievements_2), uiContext.getString(R.string.settings_login_achievements_notifications_encore)),
    Cat(Icons.Rounded.Description, uiContext.getString(R.string.settings_licenses), uiContext.getString(R.string.settings_licenses_summary)),
)

private val SettingsMaxWidth = 680.dp

@Composable
fun SettingsScreen(
    initial: DsSettings,
    onSave: (DsSettings) -> Unit,
    onOpenKeyMapper: () -> Unit,
    onOpenMappingInfo: () -> Unit,
    onOpenSystemTools: () -> Unit,
    onBack: () -> Unit,
    subtitle: String? = null,
    onReset: (() -> Unit)? = null,
    gameRunning: Boolean = false,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val categories = settingsCategories(uiContext)
    val colors = seedless
    var s by remember { mutableStateOf(initial) }
    var openCat by remember { mutableStateOf(-1) }
    var lastCat by remember { mutableStateOf(-1) }
    var homeSelection by remember { mutableStateOf(0) }
    var subPage by remember { mutableStateOf<String?>(null) }
    var wifiDialog by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val setS: (DsSettings) -> Unit = { s = it }
    val perGame = subtitle != null

    androidx.compose.runtime.LaunchedEffect(openCat, perGame) {
        if (openCat < 0) {
            ExternalInfoBus.show {
                ExternalCrumbInfo(
                    crumb = if (perGame) uiContext.getString(R.string.settings_settings_per_game) else uiContext.getString(R.string.settings_settings_global),
                    title = uiContext.getString(R.string.common_settings),
                    description = if (perGame) {
                        uiContext.getString(R.string.settings_changes_here_apply_only_to, subtitle ?: uiContext.getString(R.string.settings_this_game))
                    } else {
                        uiContext.getString(R.string.settings_defaults_for_every_game_that_has_no_settings)
                    },
                )
            }
        } else {
            val cat = categories[openCat]
            ExternalInfoBus.show {
                ExternalCrumbInfo(
                    crumb = (if (perGame) uiContext.getString(R.string.settings_settings_per_game_2) else uiContext.getString(R.string.common_settings_2)) + cat.title,
                    title = cat.title,
                    description = cat.summary,
                    icon = cat.icon,
                )
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ExternalInfoBus.clear() } }

    fun leave() { if (s != initial) onSave(s); onBack() }
    androidx.activity.compose.BackHandler {
        if (subPage != null) subPage = null
        else if (openCat >= 0) { lastCat = openCat; openCat = -1 } else leave()
    }

    if (openCat == 9) {
        LicensesScreen {
            lastCat = openCat
            openCat = -1
        }
        return
    }

    if (wifiDialog) SeedlessConfirmDialog(
        title = uiContext.getString(R.string.settings_multiplayer_wifi),
        message = uiContext.getString(R.string.settings_the_emulator_does_not_support_local),
        confirmLabel = uiContext.getString(R.string.settings_got_it),
        destructive = false,
        onConfirm = { wifiDialog = false },
        onDismiss = { wifiDialog = false },
        cancelLabel = uiContext.getString(R.string.common_close),
    )
    if (confirmReset && onReset != null) SeedlessConfirmDialog(
        title = uiContext.getString(R.string.settings_reset_to_global_settings),
        message = uiContext.getString(R.string.settings_this_discards_this_game_s_own_settings_and),
        confirmLabel = uiContext.getString(R.string.common_reset),
        onConfirm = { confirmReset = false; onReset(); onBack() },
        onDismiss = { confirmReset = false },
    )

    if (openCat == 1 && subPage == LAYOUT_PAGE) {
        // The per-screen layout editor takes over the whole window: the DS
        // screens are drawn at the size and position they will really have on
        // this panel, with the parameters in a floating panel on top, instead
        // of a shrunk diagram inside the scrolling settings column.
        LayoutEditorScreen(s = s, onDone = { subPage = null })
        return
    }

    SeedlessScreenScaffold(
        title = if (openCat < 0) uiContext.getString(R.string.common_settings) else categories[openCat].title,
        subtitle = if (openCat < 0) (subtitle ?: uiContext.getString(R.string.settings_global)) else categories[openCat].summary,
        onBack = {
            when {
                subPage != null -> subPage = null
                openCat >= 0 -> { lastCat = openCat; openCat = -1 }
                else -> leave()
            }
        },
        hints = listOf(
            GamepadHint(null, uiContext.getString(R.string.common_navigate)),
            GamepadHint(uiContext.getString(R.string.button_a), if (openCat < 0) uiContext.getString(R.string.common_open) else uiContext.getString(R.string.common_change)),
            GamepadHint(uiContext.getString(R.string.button_b), uiContext.getString(R.string.common_back)),
        ),
    ) { pad ->
        val scroll = rememberScrollState()
        val center = remember { CenterScroll() }
        androidx.compose.runtime.LaunchedEffect(openCat) { if (openCat >= 0) scroll.scrollTo(0) }
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(
                Modifier
                    .widthIn(max = SettingsMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScrollbar(scroll, colors.green.copy(alpha = 0.4f))
                    .centerScrollViewport(center)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
            ) {
                if (openCat < 0) {
                    val homeCursor = rememberMenuCursor(
                        count = categories.size,
                        columns = if (LocalConfiguration.current.screenWidthDp >= 600) 2 else 1,
                        initial = lastCat.coerceAtLeast(0),
                        onAccept = { i -> openCat = i },
                        onCancel = { leave() },
                    )
                    androidx.compose.runtime.LaunchedEffect(lastCat) {
                        if (lastCat in categories.indices) homeCursor.select(lastCat)
                    }
                    homeSelection = homeCursor.index
                    center.Drive(scroll, homeCursor.index)
                    ScopeBanner(perGame, subtitle)
                    if (onReset != null) ResetToGlobalCard { confirmReset = true }
                    val twoColumns = LocalConfiguration.current.screenWidthDp >= 600
                    if (twoColumns) {
                        categories.chunked(2).forEachIndexed { rowIndex, pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEachIndexed { colIndex, c ->
                                    val i = rowIndex * 2 + colIndex
                                    CategoryCard(
                                        c, i,
                                        Modifier.weight(1f).centerScrollTarget(center, i == homeSelection),
                                        i == homeSelection,
                                    ) {
                                        openCat = i
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        categories.forEachIndexed { i, c ->
                            CategoryCard(
                                c, i,
                                Modifier.centerScrollTarget(center, i == homeSelection),
                                i == homeSelection,
                            ) { openCat = i }
                        }
                    }
                } else {
                    val rowCursor = remember(openCat, subPage) { SettingsRowCursor() }
                    val c = rememberMenuCursor(
                        count = rowCursor.total.coerceAtLeast(1),
                        onAccept = { rowCursor.actions[rowCursor.selected]?.invoke() },
                        onCancel = {
                            if (subPage != null) subPage = null
                            else { lastCat = openCat; openCat = -1 }
                        },
                    )
                    androidx.compose.runtime.LaunchedEffect(c.index) { rowCursor.selected = c.index }
                    center.Drive(scroll, rowCursor.selected)
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSettingsCrumb provides
                            (if (perGame) uiContext.getString(R.string.settings_settings_per_game_2) else uiContext.getString(R.string.common_settings_2)) + categories[openCat].title,
                        LocalSettingsRows provides rowCursor,
                        LocalCenterScroll provides center,
                    ) {
                    when (openCat) {
                        0 -> VideoSettings(s, setS, gameRunning)
                        1 -> ScreenSettings(s, setS, subPage, gameRunning) { subPage = it }
                        2 -> TouchSettings(s, setS)
                        3 -> PadSettings(s, setS, { onSave(s); onOpenKeyMapper() }, { onSave(s); onOpenMappingInfo() })
                        4 -> AudioSettings(s, setS)
                        5 -> SystemSettings(s, setS)
                        6 -> SaveSettings(s, setS)
                        7 -> AdvancedSettings(s, setS, !perGame, { wifiDialog = true }, { onSave(s); onOpenSystemTools() })
                        8 -> RaCategory()
                    }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable private fun ScopeBanner(perGame: Boolean, subtitle: String?) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val accent = if (perGame) colors.green else colors.text3
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (perGame) colors.greenDim else colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(accent))
        Text(
            text = if (perGame) uiContext.getString(R.string.settings_per_game_settings) else uiContext.getString(R.string.settings_global_settings),
            color = accent,
            fontFamily = SeedlessMono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 9.dp),
        )
        if (perGame && subtitle != null) {
            Text(
                text = uiContext.getString(R.string.detail_suffix, subtitle),
                color = colors.text3,
                fontFamily = SeedlessMono,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable private fun ResetToGlobalCard(onClick: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(shape)
            .background(colors.red.copy(alpha = 0.13f))
            .let { if (focused) it.border(2.dp, colors.red, shape) else it }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Rounded.RestartAlt, null, tint = colors.red, modifier = Modifier.size(19.dp))
        Text(
            text = uiContext.getString(R.string.settings_reset_to_global_settings_2),
            color = colors.red,
            fontFamily = Manrope,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable private fun VideoSettings(s: DsSettings, set: (DsSettings) -> Unit, gameRunning: Boolean = false) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val options = androidx.compose.runtime.remember(uiContext, androidx.compose.ui.platform.LocalConfiguration.current) { SettingsOptions(uiContext) }
    Group(uiContext.getString(R.string.settings_performance))
    DropdownRow(uiContext.getString(R.string.settings_frameskip),
        if (s.dynamicRes) uiContext.getString(R.string.settings_off_while_dynamic_3d_resolution_is_on_the_two_never_run_together) else uiContext.getString(R.string.settings_skips_frames_to_keep_up_to_speed_manual_uses),
        options.frameskipType, s.frameskipType, enabled = !s.dynamicRes) { set(s.withFrameskip(it)) }
    NumberFieldRow(uiContext.getString(R.string.settings_frameskip_value), uiContext.getString(R.string.settings_how_many_frames_to_skip_manual_mode_only),
        s.frameskipValue, 0, 9, "") { set(s.copy(frameskipValue = it)) }
    SwitchRow(uiContext.getString(R.string.settings_safe_frameskip), uiContext.getString(R.string.settings_avoids_skipping_frames_that_would_cause),
        s.frameskipSafe) { set(s.copy(frameskipSafe = it)) }
    DropdownRow(uiContext.getString(R.string.settings_max_fast_forward_speed), uiContext.getString(R.string.settings_speed_cap_when_speeding_up_the_game),
        options.ffwdSpeed, options.ffwdOrder.indexOf(s.ffwdSpeed).coerceAtLeast(0)) { pos -> set(s.copy(ffwdSpeed = options.ffwdOrder.getOrElse(pos) { 2 })) }
    SwitchRow(uiContext.getString(R.string.settings_fast_forward_as_toggle), uiContext.getString(R.string.settings_turns_on_off_with_a_tap_instead_of_holding),
        s.fastForwardToggle) { set(s.copy(fastForwardToggle = it)) }
    SwitchRow(uiContext.getString(R.string.settings_fast_forward_indicator), uiContext.getString(R.string.settings_shows_an_icon_when_the_game_is_sped_up),
        s.showFfwdIndicator) { set(s.copy(showFfwdIndicator = it)) }

    Group(uiContext.getString(R.string.settings_3d_render))
    DropdownRow(uiContext.getString(R.string.settings_internal_3d_resolution),
        if (gameRunning) uiContext.getString(R.string.settings_locked_while_a_game_is_running_close_the_game_to_change_it) else uiContext.getString(R.string.settings_renders_3d_above_native_sharper_but_heavier_applies_when_you_open_the_next),
        options.internalRes, options.irOrder.indexOf(s.internalRes).coerceAtLeast(0), enabled = !gameRunning) { pos ->
        val idx = options.irOrder.getOrElse(pos) { 1 }
        set(s.copy(internalRes = idx, hires3D = idx > 0))
    }
    SwitchRow(uiContext.getString(R.string.settings_dynamic_3d_resolution),
        if (s.frameskipType != 0) uiContext.getString(R.string.settings_off_while_frameskip_is_on_the_two_never_run_together) else uiContext.getString(R.string.settings_drops_the_3d_resolution_one_step_when_the_game_falls_behind_and_raises_it_b),
        s.dynamicRes, enabled = s.frameskipType == 0) { set(s.withDynamicRes(it)) }
    SwitchRow(uiContext.getString(R.string.settings_multi_threaded_3d_render), uiContext.getString(R.string.settings_spreads_3d_rasterizing_across_several_cores),
        s.threaded3D) { set(s.copy(threaded3D = it)) }
    SwitchRow(uiContext.getString(R.string.settings_16_bit_render), uiContext.getString(R.string.settings_uses_16_bit_color_in_3d_less_memory_slightly),
        s.gl16bit) { set(s.copy(gl16bit = it)) }
    SwitchRow(uiContext.getString(R.string.settings_disable_edge_marking), uiContext.getString(R.string.settings_removes_the_black_outline_the_ds_draws_around),
        s.disableEdgeMarking) { set(s.copy(disableEdgeMarking = it)) }
    SwitchRow(uiContext.getString(R.string.settings_interframe_blending), uiContext.getString(R.string.settings_blends_frames_to_smooth_the_flicker_of_some),
        s.blend) { set(s.copy(blend = it)) }
    SwitchRow(uiContext.getString(R.string.settings_keep_main_screen_on_top), uiContext.getString(R.string.settings_forces_the_game_s_main_screen_to_always_be),
        s.fixMainScreen) { set(s.copy(fixMainScreen = it)) }

    Group(uiContext.getString(R.string.settings_on_screen))
    SwitchRow(uiContext.getString(R.string.settings_show_fps), uiContext.getString(R.string.settings_shows_the_speed_counter_on_screen),
        s.showFps) { set(s.copy(showFps = it)) }
    SwitchRow(uiContext.getString(R.string.settings_show_3d_resolution), uiContext.getString(R.string.settings_shows_the_3d_resolution_in_use_and_the_configured_one_next_to_the_fps_count),
        s.showScale) { set(s.copy(showScale = it)) }
    SwitchRow(uiContext.getString(R.string.settings_fps_with_transparent_background), uiContext.getString(R.string.settings_removes_the_dark_background_of_the_fps_counter),
        s.fpsTransparent) { set(s.copy(fpsTransparent = it)) }
}

@Composable private fun ScreenSettings(
    s: DsSettings,
    set: (DsSettings) -> Unit,
    subPage: String?,
    gameRunning: Boolean = false,
    onSubPage: (String?) -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val options = androidx.compose.runtime.remember(uiContext, androidx.compose.ui.platform.LocalConfiguration.current) { SettingsOptions(uiContext) }
    val presetOn = s.dualScreenPreset != DualScreenPresets.OFF
    // A preset only actually owns the layout/single-screen/external rows while
    // an external display is attached. Without one those settings are live, so
    // they must stay selectable everywhere (global settings, in-game settings).
    val presetLive = presetOn && DualScreenPresets.live(s)
    val panelNote = DeviceProfiles.current(LocalContext.current)?.let { uiContext.getString(R.string.presets_panel_sizes_from_the_profile, it.name) } ?: ""
    // Per-screen free layout values live in ControlLayout (its own prefs file),
    // not in DsSettings. Load them once so this page edits the live values, and
    // re-read them whenever a slider on the layout subpage changes one.
    val clContext = remember(uiContext) { ControlLayout.load(uiContext); uiContext }
    var clTick by remember { mutableStateOf(0) }
    val clTickNow = clTick
    var layoutDrawerOpen by remember { mutableStateOf(false) }
    var layoutTab by remember { mutableStateOf(-1) }
    if (subPage == LAYOUT_PAGE) {
        val colors = seedless
        val unit = uiContext.getString(R.string.percent_unit)
        fun persist() { ControlLayout.saveScreens(clContext); clTick++ }
        val tab = if (layoutTab >= 0) layoutTab else if (presetOn) 1 else 0
        DropdownRow(uiContext.getString(R.string.layout_preview_mode), uiContext.getString(R.string.layout_preview_mode_desc),
            listOf(uiContext.getString(R.string.layout_tab_single), uiContext.getString(R.string.layout_tab_dual)), tab) { layoutTab = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                if (tab == 0) SingleLayoutPreview(s, clTickNow)
                else DualLayoutPreview(s, clTickNow)
            }
            if (layoutDrawerOpen) {
                Column(
                    Modifier.width(292.dp).height(432.dp).clip(RoundedCornerShape(14.dp))
                        .background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState()).padding(10.dp)
                ) {
                    if (tab == 0) {
                        Group(uiContext.getString(R.string.layout_single_group))
                        ScreenAdjustRows(
                            uiContext.getString(R.string.common_top_screen), unit,
                            ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY,
                        ) { sc, x, y -> ControlLayout.topScale = sc; ControlLayout.topOffX = x; ControlLayout.topOffY = y; persist() }
                        ScreenAdjustRows(
                            uiContext.getString(R.string.common_bottom_screen), unit,
                            ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY,
                        ) { sc, x, y -> ControlLayout.botScale = sc; ControlLayout.botOffX = x; ControlLayout.botOffY = y; persist() }
                    } else {
                        Group(uiContext.getString(R.string.layout_dual_group))
                        ScreenAdjustRows(
                            uiContext.getString(R.string.layout_internal_screen), unit,
                            ControlLayout.dualScale, ControlLayout.dualOffX, ControlLayout.dualOffY,
                        ) { sc, x, y -> ControlLayout.dualScale = sc; ControlLayout.dualOffX = x; ControlLayout.dualOffY = y; persist() }
                        ScreenAdjustRows(
                            uiContext.getString(R.string.menu_external_screen), unit,
                            ControlLayout.extScale, ControlLayout.extOffX, ControlLayout.extOffY,
                        ) { sc, x, y -> ControlLayout.extScale = sc; ControlLayout.extOffX = x; ControlLayout.extOffY = y; persist() }
                    }
                    NavRow(uiContext.getString(R.string.layout_reset), uiContext.getString(R.string.layout_reset_desc)) {
                        ControlLayout.resetScreens(clContext); clTick++
                    }
                }
            }
        }
        NavRow(
            uiContext.getString(if (layoutDrawerOpen) R.string.layout_close_params else R.string.layout_edit_params),
            uiContext.getString(R.string.layout_edit_desc)
        ) { layoutDrawerOpen = !layoutDrawerOpen }
        SectionNote(uiContext.getString(R.string.layout_preview_note))
        SectionNote(uiContext.getString(R.string.layout_dual_note))
        SectionNote(uiContext.getString(R.string.layout_drag_note))
        return
    }
    if (subPage == MASK_PAGE) {
        MaskSettingsPage(s, set)
        return
    }
    if (subPage == PRESETS_PAGE) {
        Group(uiContext.getString(R.string.common_scaling))
        SwitchRow(uiContext.getString(R.string.common_keep_ds_ratio), uiContext.getString(R.string.common_each_screen_holds_256_192_inside_its_panel),
            s.dsKeepRatio, enabled = presetOn) { set(s.copy(dsKeepRatio = it)) }
        SwitchRow(uiContext.getString(R.string.presets_same_screen_size), uiContext.getString(R.string.presets_both_screens_take_the_size_of_the_smaller_panel_so_they_line_up_exactly_fil) + panelNote + uiContext.getString(R.string.presets_scaling_priority_note),
            s.dsSameSize, enabled = presetOn) { set(s.copy(dsSameSize = it)) }
        SwitchRow(uiContext.getString(R.string.common_integer_scale), uiContext.getString(R.string.common_scales_by_a_whole_number_so_a_ds_pixel_is),
            s.integerScale, enabled = presetOn) { set(s.copy(integerScale = it)) }
        val fill = presetOn && s.dsKeepRatio && !s.integerScale && !s.dsSameSize
        val align = presetOn && (s.dsKeepRatio || s.integerScale || s.dsSameSize)
        Group(uiContext.getString(R.string.common_this_device))
        SwitchRow(uiContext.getString(R.string.common_fill_width), uiContext.getString(R.string.settings_let_this_screen_span_the_device_s_width_even),
            s.dsIntFillW, enabled = fill) { set(s.copy(dsIntFillW = it)) }
        SwitchRow(uiContext.getString(R.string.common_fill_height), uiContext.getString(R.string.settings_same_vertically),
            s.dsIntFillH, enabled = fill) { set(s.copy(dsIntFillH = it)) }
        DropdownRow(uiContext.getString(R.string.common_vertical_alignment), uiContext.getString(R.string.settings_where_the_leftover_space_goes_on_the_device),
            DualScreenPresets.alignLabels(uiContext), s.dsIntAlign, enabled = align) { set(s.copy(dsIntAlign = it)) }
        Group(uiContext.getString(R.string.common_external_display))
        SwitchRow(uiContext.getString(R.string.common_fill_width), uiContext.getString(R.string.settings_let_this_screen_span_the_external_display_s),
            s.dsExtFillW, enabled = fill) { set(s.copy(dsExtFillW = it)) }
        SwitchRow(uiContext.getString(R.string.common_fill_height), uiContext.getString(R.string.settings_same_vertically),
            s.dsExtFillH, enabled = fill) { set(s.copy(dsExtFillH = it)) }
        DropdownRow(uiContext.getString(R.string.common_vertical_alignment), uiContext.getString(R.string.settings_where_the_leftover_space_goes_on_the_external),
            DualScreenPresets.alignLabels(uiContext), s.dsExtAlign, enabled = align) { set(s.copy(dsExtAlign = it)) }
        return
    }

    Group(uiContext.getString(R.string.common_dual_screen_presets))
    if (presetLive) {
        SectionNote(uiContext.getString(R.string.settings_some_settings_are_hidden_the_preset_owns_them))
    }
    DropdownRow(uiContext.getString(R.string.common_preset), uiContext.getString(R.string.settings_which_ds_screen_stays_on_the_device_and_which),
        DualScreenPresets.labels(uiContext), s.dualScreenPreset) { set(DualScreenPresets.apply(s, it)) }
    NavRow(uiContext.getString(R.string.settings_dualscreen_presets_config), uiContext.getString(R.string.settings_scaling_fill_area_and_vertical_alignment_for)) {
        onSubPage(PRESETS_PAGE)
    }

    Group(uiContext.getString(R.string.settings_layout))
    NavRow(uiContext.getString(R.string.layout_adjust), uiContext.getString(R.string.layout_adjust_desc)) {
        onSubPage(LAYOUT_PAGE)
    }
    NavRow(uiContext.getString(R.string.mask_title), uiContext.getString(R.string.mask_desc)) {
        onSubPage(MASK_PAGE)
    }
    if (!presetLive) {
        DropdownRow(uiContext.getString(R.string.settings_screen_layout), uiContext.getString(R.string.settings_how_the_ds_s_two_screens_are_arranged),
            options.screenLayout, s.screenLayout) { set(s.copy(screenLayout = it)) }
        DropdownRow(uiContext.getString(R.string.settings_second_layout), uiContext.getString(R.string.settings_alternate_layout_for_the_special_button_swap),
            options.screenLayout, s.screenLayout2) { set(s.copy(screenLayout2 = it)) }
        if (s.screenLayout == 5) {
            SliderRow(uiContext.getString(R.string.settings_pip_alpha), uiContext.getString(R.string.settings_pip_alpha_desc),
                s.pipAlpha, 10, 60, uiContext.getString(R.string.percent_unit)) { set(s.copy(pipAlpha = it)) }
            SliderRow(uiContext.getString(R.string.settings_pip_size), uiContext.getString(R.string.settings_pip_size_desc),
                s.pipScale, 0, 25, uiContext.getString(R.string.percent_unit)) { set(s.copy(pipScale = it)) }
        }
    }
    DropdownRow(uiContext.getString(R.string.settings_rotation), uiContext.getString(R.string.settings_orientation_of_the_device_screen),
        options.rotation, s.rotationMode) { set(s.copy(rotationMode = it)) }
    val vulkanSelected = RendererPolicy.usesVulkan(s)
    val renderers = if (android.os.Build.VERSION.SDK_INT >= 24) listOf(RendererPolicy.OPENGL, RendererPolicy.VULKAN) else listOf(RendererPolicy.OPENGL)
    DropdownRow(uiContext.getString(R.string.settings_renderer),
        uiContext.getString(if (gameRunning) R.string.renderer_locked else R.string.renderer_summary),
        renderers, renderers.indexOf(s.renderer).coerceAtLeast(0), enabled = !gameRunning) {
        set(RendererPolicy.select(s, renderers[it], options.filterValues))
    }
    val filters = RendererPolicy.availableFilters(s.renderer, options.filterValues)
    val filterLabels = filters.map { value ->
        when (value) {
            "None" -> uiContext.getString(R.string.common_none)
            "Linear" -> uiContext.getString(R.string.settings_linear)
            "FXAA" -> uiContext.getString(R.string.settings_fxaa)
            "sharp_shimmerless" -> "Sharp Shimmerless"
            "sharp_shimmerless+nds_color" -> "Sharp Shimmerless + NDS Color"
            "pixel_aa_fast" -> "Pixel AA Fast"
            "LCD3x+nds_color" -> "LCD3x + NDS Color"
            "Quilez+nds_color" -> "Quilez + NDS Color"
            "rpduo_sharp" -> "RP Duo LCD 点对点"
            "rpduo_grain" -> "RP Duo DS Grid · 柔和颗粒"
            "rpduo_grain_plus" -> "RP Duo DS Grid · 柔和颗粒＋"
            else -> options.filterPairs.firstOrNull { it.first == value }?.second ?: value
        }
    }
    DropdownRow(uiContext.getString(R.string.settings_image_filter),
        uiContext.getString(if (vulkanSelected) R.string.vulkan_filter_summary else R.string.settings_image_smoothing_scaling_shader),
        filterLabels, filters.indexOf(s.filter).coerceAtLeast(0)) { set(s.copy(filter = filters[it])) }
    if (vulkanSelected) {
        SwitchRow(uiContext.getString(R.string.vulkan_low_latency),
            uiContext.getString(R.string.vulkan_low_latency_summary), s.vulkanLowLatency) { set(s.copy(vulkanLowLatency = it)) }
    }
    if (gameRunning) {
        val usingVulkan by DisplayBackendStatus.vulkan
        val fallback by DisplayBackendStatus.fallback
        val status = if (usingVulkan) R.string.vulkan_active else when (fallback) {
            VulkanPolicy.Fallback.ANDROID -> R.string.vulkan_fallback_android
            VulkanPolicy.Fallback.DIAGNOSTICS -> R.string.vulkan_fallback_diagnostics
            VulkanPolicy.Fallback.DRIVER -> R.string.vulkan_fallback_driver
            else -> if (vulkanSelected) R.string.vulkan_selecting else R.string.renderer_opengl_active
        }
        Text(uiContext.getString(status), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    SwitchRow(uiContext.getString(R.string.common_swap_screens), uiContext.getString(R.string.settings_puts_the_bottom_screen_on_top_and_vice_versa),
        s.swapScreens) { set(s.copy(swapScreens = it)) }
    if (!presetLive) {
        DropdownRow(uiContext.getString(R.string.settings_single_screen), uiContext.getString(R.string.settings_show_only_one_of_the_ds_screens_touch_works),
            options.singleScreen, if (!s.singleScreen) 0 else if (s.singleBottom) 2 else 1) {
            set(s.copy(singleScreen = it != 0, singleBottom = it == 2))
        }
        SwitchRow(uiContext.getString(R.string.common_integer_scale), uiContext.getString(R.string.settings_scales_both_screens_by_a_whole_number_so_a_ds),
            s.integerScale) { set(s.copy(integerScale = it)) }
    }
    Group(uiContext.getString(R.string.common_external_display))
    if (!presetLive) {
        DropdownRow(uiContext.getString(R.string.settings_external_screen_mode), uiContext.getString(R.string.settings_what_to_do_with_a_connected_external_monitor_tv),
            options.extDisplay, s.extDisplayType) { set(s.copy(extDisplayType = it)) }
        DropdownRow(uiContext.getString(R.string.settings_screen_on_external_display), uiContext.getString(R.string.settings_which_ds_screen_to_send_to_the_external),
            options.extScreen, s.extDisplayScreen) { set(s.copy(extDisplayScreen = it)) }
    }
    NumberFieldRow(uiContext.getString(R.string.settings_external_display_border), uiContext.getString(R.string.settings_margin_around_the_image_on_the_external),
        s.extDisplayBorder, 0, 20, uiContext.getString(R.string.percent_unit)) { set(s.copy(extDisplayBorder = it)) }
    if (vulkanSelected) SectionNote(uiContext.getString(R.string.renderer_delay_opengl_only))
    else NumberFieldRow(uiContext.getString(R.string.settings_internal_screen_delay), uiContext.getString(R.string.settings_frames_to_hold_back_the_built_in_screen_so_it_lines_up_with_a_tv_monitor_th),
        s.extDisplayDelay, 0, 2, uiContext.getString(R.string.settings_frames)) { set(s.copy(extDisplayDelay = it)) }

}

@Composable private fun TouchSettings(s: DsSettings, set: (DsSettings) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val options = androidx.compose.runtime.remember(uiContext, androidx.compose.ui.platform.LocalConfiguration.current) { SettingsOptions(uiContext) }
    Group(uiContext.getString(R.string.settings_appearance))
    DropdownRow(uiContext.getString(R.string.settings_pad_style), uiContext.getString(R.string.settings_look_of_the_touch_buttons),
        options.padStyle, s.padStyle) { set(s.copy(padStyle = it)) }
    SliderRow(uiContext.getString(R.string.settings_control_opacity), uiContext.getString(R.string.settings_transparency_of_the_buttons_at_0_they_re),
        (s.controllerAlpha * 100).toInt(), 0, 100, uiContext.getString(R.string.percent_unit)) { set(s.copy(controllerAlpha = it / 100f)) }
    SwitchRow(uiContext.getString(R.string.settings_show_start_select), uiContext.getString(R.string.settings_shows_the_start_and_select_buttons_on_screen),
        s.showStartSelect) { set(s.copy(showStartSelect = it)) }
    DropdownRow(uiContext.getString(R.string.settings_menu_button_position), uiContext.getString(R.string.settings_where_to_place_the_button_that_opens_the_game),
        options.menuPos, s.menuButtonPos) { set(s.copy(menuButtonPos = it)) }

    Group(uiContext.getString(R.string.settings_behavior))
    SwitchRow(uiContext.getString(R.string.settings_low_latency_touch), uiContext.getString(R.string.settings_low_latency_touch_desc),
        s.lowLatencyTouch) { set(s.copy(lowLatencyTouch = it)) }
    SwitchRow(uiContext.getString(R.string.settings_hide_when_a_gamepad_is_connected),
        uiContext.getString(R.string.settings_hides_the_on_screen_buttons_when_a_controller),
        s.hideControlsWithGamepad) { set(s.copy(hideControlsWithGamepad = it)) }
    SwitchRow(uiContext.getString(R.string.settings_vibrate_on_press), uiContext.getString(R.string.settings_vibrates_when_touching_the_touch_buttons),
        s.hapticFeedback) { set(s.copy(hapticFeedback = it)) }
    SwitchRow(uiContext.getString(R.string.settings_no_d_pad_diagonals), uiContext.getString(R.string.settings_the_d_pad_registers_only_4_directions_no),
        s.noDiagonals) { set(s.copy(noDiagonals = it)) }
    SwitchRow(uiContext.getString(R.string.settings_presses_touch_the_screen), uiContext.getString(R.string.settings_a_touch_button_also_registers_a_tap_on_the_ds),
        s.touchThrough) { set(s.copy(touchThrough = it)) }
    SwitchRow(uiContext.getString(R.string.settings_disable_back_button), uiContext.getString(R.string.settings_ignores_the_system_back_button_during_the_game),
        s.disableBackButton) { set(s.copy(disableBackButton = it)) }
    SliderRow(uiContext.getString(R.string.settings_d_pad_touch_zone), uiContext.getString(R.string.settings_enlarges_the_d_pad_s_sensitive_area_without),
        ((s.dpadModifier - 1f) * 50).toInt(), 0, 100, uiContext.getString(R.string.percent_unit)) { set(s.copy(dpadModifier = 1f + it / 50f)) }
    SliderRow(uiContext.getString(R.string.settings_button_touch_zone), uiContext.getString(R.string.settings_enlarges_the_sensitive_area_of_the_face),
        ((s.buttonModifier - 1f) * 50).toInt(), 0, 100, uiContext.getString(R.string.percent_unit)) { set(s.copy(buttonModifier = 1f + it / 50f)) }

    Group(uiContext.getString(R.string.settings_special_buttons))
    DropdownRow(uiContext.getString(R.string.settings_special_button_i), uiContext.getString(R.string.settings_function_of_the_1st_extra_on_screen_button),
        options.extraFuncs, s.extraFunc1) { set(s.copy(extraFunc1 = it)) }
    DropdownRow(uiContext.getString(R.string.settings_special_button_ii), uiContext.getString(R.string.settings_function_of_the_2nd_extra_on_screen_button),
        options.extraFuncs, s.extraFunc2) { set(s.copy(extraFunc2 = it)) }
    DropdownRow(uiContext.getString(R.string.settings_special_button_iii), uiContext.getString(R.string.settings_function_of_the_3rd_extra_on_screen_button),
        options.extraFuncs, s.extraFunc3) { set(s.copy(extraFunc3 = it)) }
}

@Composable private fun PadSettings(
    s: DsSettings, set: (DsSettings) -> Unit, openKeyMapper: () -> Unit, openMappingInfo: () -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val options = androidx.compose.runtime.remember(uiContext, androidx.compose.ui.platform.LocalConfiguration.current) { SettingsOptions(uiContext) }
    Group(uiContext.getString(R.string.settings_mapping))
    NavRow(uiContext.getString(R.string.settings_map_gamepad_buttons), uiContext.getString(R.string.settings_assign_each_ds_button_to_your_physical), openKeyMapper)
    NavRow(uiContext.getString(R.string.settings_view_controller_mapping), uiContext.getString(R.string.settings_check_the_default_mapping_for_known), openMappingInfo)

    Group(uiContext.getString(R.string.rewind_title))
    SwitchRow(uiContext.getString(R.string.rewind_enable), uiContext.getString(R.string.rewind_enable_description),
        s.rewindEnabled) { set(s.copy(rewindEnabled = it)) }
    if (s.rewindEnabled) {
        DropdownRow(uiContext.getString(R.string.rewind_duration), uiContext.getString(R.string.rewind_duration_description),
            listOf(10, 30, 60).map { uiContext.resources.getQuantityString(R.plurals.rewind_seconds, it, it) },
            listOf(10, 30, 60).indexOf(s.rewindSeconds).coerceAtLeast(0)) { set(s.copy(rewindSeconds = listOf(10, 30, 60)[it])) }
        DropdownRow(uiContext.getString(R.string.rewind_sampling_title), uiContext.getString(R.string.rewind_sampling_description),
            (1..3).map { uiContext.resources.getQuantityString(R.plurals.rewind_seconds, it, it) }, s.rewindInterval - 1) { set(s.copy(rewindInterval = it + 1)) }
        NavRow(uiContext.getString(R.string.rewind_bind), uiContext.getString(R.string.rewind_bind_description), openKeyMapper)
    }

    Group(uiContext.getString(R.string.settings_rumble_and_sticks))
    DropdownRow(uiContext.getString(R.string.settings_rumble_device), uiContext.getString(R.string.settings_which_motor_to_use_for_rumble),
        options.rumbleDev, s.rumbleDev) { set(s.copy(rumbleDev = it)) }
    DropdownRow(uiContext.getString(R.string.settings_right_stick_mode), uiContext.getString(R.string.settings_function_of_the_right_analog_stick),
        options.analogStick, s.analogStickMode + 1) { set(s.copy(analogStickMode = it - 1)) }
    SwitchRow(uiContext.getString(R.string.settings_analog_triggers), uiContext.getString(R.string.settings_uses_the_controller_s_l2_r2_triggers_as_the_ds_s_l_r),
        s.analogTriggers) { set(s.copy(analogTriggers = it)) }
    SliderRow(uiContext.getString(R.string.settings_stick_deadzone), uiContext.getString(R.string.settings_threshold_before_the_stick_registers_movement),
        (s.analogDeadzone * 100).toInt(), 0, 100, uiContext.getString(R.string.percent_unit)) { set(s.copy(analogDeadzone = it / 100f)) }

    Group(uiContext.getString(R.string.settings_overlay))
    SwitchRow(uiContext.getString(R.string.settings_hide_already_mapped_buttons), uiContext.getString(R.string.settings_removes_from_the_touch_pad_the_buttons_you),
        s.disableMapped) { set(s.copy(disableMapped = it)) }
}

@Composable private fun AudioSettings(s: DsSettings, set: (DsSettings) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val options = androidx.compose.runtime.remember(uiContext, androidx.compose.ui.platform.LocalConfiguration.current) { SettingsOptions(uiContext) }
    Group(uiContext.getString(R.string.settings_sound))
    SwitchRow(uiContext.getString(R.string.settings_sound_enabled), uiContext.getString(R.string.settings_enables_or_mutes_all_audio),
        s.soundEnabled) { set(s.copy(soundEnabled = it)) }
    SliderRow(uiContext.getString(R.string.settings_volume), uiContext.getString(R.string.settings_overall_emulator_volume),
        s.volume, 0, 10, "") { set(s.copy(volume = it)) }
    SwitchRow(uiContext.getString(R.string.audio_low_latency_title), uiContext.getString(R.string.audio_low_latency_description),
        s.audioLowLatency) { set(s.copy(audioLowLatency = it)) }
    DropdownRow(uiContext.getString(R.string.settings_audio_latency), uiContext.getString(R.string.settings_lower_more_responsive_higher_fewer_dropouts),
        options.audioLatency, s.audioLatency) { set(s.copy(audioLatency = it)) }

    Group(uiContext.getString(R.string.settings_microphone))
    SwitchRow(uiContext.getString(R.string.settings_use_device_microphone), uiContext.getString(R.string.settings_uses_the_real_microphone_for_games_that),
        s.micEnabled) { set(s.copy(micEnabled = it)) }
    DropdownRow(uiContext.getString(R.string.settings_microphone_level), uiContext.getString(R.string.settings_microphone_capture_sensitivity),
        options.micLevel, s.micLevel) { set(s.copy(micLevel = it)) }
}

@Composable private fun SystemSettings(s: DsSettings, set: (DsSettings) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val options = androidx.compose.runtime.remember(uiContext, androidx.compose.ui.platform.LocalConfiguration.current) { SettingsOptions(uiContext) }
    Group(uiContext.getString(R.string.settings_ds_firmware))
    TextFieldRow(uiContext.getString(R.string.settings_nickname), uiContext.getString(R.string.settings_the_user_s_name_in_the_ds_firmware),
        s.firmwareNick, 10) { set(s.copy(firmwareNick = it)) }
    DropdownRow(uiContext.getString(R.string.settings_system_language), uiContext.getString(R.string.settings_firmware_language_some_games_read_it),
        options.language, s.firmwareLanguage) { set(s.copy(firmwareLanguage = it)) }
    DropdownRow(uiContext.getString(R.string.settings_favorite_color), uiContext.getString(R.string.settings_the_ds_firmware_theme_color),
        options.color, s.firmwareColor) { set(s.copy(firmwareColor = it)) }
    MonthDayPickerRow(uiContext.getString(R.string.settings_birthday), uiContext.getString(R.string.settings_firmware_birthday_some_games_use_it_month_and),
        s.bdayMonth, s.bdayDay) { m, d -> set(s.copy(bdayMonth = m, bdayDay = d)) }

    Group(uiContext.getString(R.string.settings_hardware))
    DropdownRow(uiContext.getString(R.string.settings_slot_2_cartridge), uiContext.getString(R.string.settings_accessory_in_the_gba_slot_rumble_pak_etc),
        options.slot2, s.slot2Type) { set(s.copy(slot2Type = it)) }
    SwitchRow(uiContext.getString(R.string.settings_use_system_time_rtc), uiContext.getString(R.string.settings_the_ds_clock_uses_the_device_s_real_time),
        s.rtcSystemTime) { set(s.copy(rtcSystemTime = it)) }
    SwitchRow(uiContext.getString(R.string.settings_custom_ds_clock), uiContext.getString(R.string.settings_uses_a_fixed_date_time_instead_of_the_system_s),
        s.customClockEnable) { set(s.copy(customClockEnable = it)) }
    if (s.customClockEnable)
        DateTimeRow(uiContext.getString(R.string.settings_custom_date_time), uiContext.getString(R.string.settings_the_fixed_date_and_time_the_ds_clock_will_use),
            s.customClock, true) { set(s.copy(customClock = it)) }
}

@Composable private fun SaveSettings(s: DsSettings, set: (DsSettings) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val options = androidx.compose.runtime.remember(uiContext, androidx.compose.ui.platform.LocalConfiguration.current) { SettingsOptions(uiContext) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            set(s.copy(saveLocation = SaveLocation.CUSTOM, saveFolder = tree.toString()))
        }
    }
    Group(uiContext.getString(R.string.settings_save_files))
    DropdownRow(uiContext.getString(R.string.settings_location), uiContext.getString(R.string.settings_where_game_saves_and_savestates_are_kept_next_to_the_rom_makes_them_visible),
        SaveLocation.labels(uiContext), s.saveLocation) { set(s.copy(saveLocation = it)) }
    if (s.saveLocation == SaveLocation.CUSTOM)
        NavRow(uiContext.getString(R.string.settings_custom_folder), SaveLocation.folderLabel(ctx)) { folderPicker.launch(null) }

    Group(uiContext.getString(R.string.settings_autosave))
    DropdownRow(uiContext.getString(R.string.settings_autosave), uiContext.getString(R.string.settings_creates_an_automatic_savestate_periodically),
        options.autosave, s.autosaveMode) { set(s.copy(autosaveMode = it)) }
    SwitchRow(uiContext.getString(R.string.settings_autoload_last_game), uiContext.getString(R.string.settings_when_opening_a_game_automatically_loads_the),
        s.autoload) { set(s.copy(autoload = it)) }

    Group(uiContext.getString(R.string.settings_confirmations))
    SwitchRow(uiContext.getString(R.string.settings_confirm_state_overwrite), uiContext.getString(R.string.settings_asks_for_confirmation_before_overwriting_a),
        s.overwriteNeedsConfirm) { set(s.copy(overwriteNeedsConfirm = it)) }
    SwitchRow(uiContext.getString(R.string.settings_confirm_state_load), uiContext.getString(R.string.settings_asks_for_confirmation_before_loading_a),
        s.loadNeedsConfirm) { set(s.copy(loadNeedsConfirm = it)) }

    Group(uiContext.getString(R.string.settings_save_compatibility))
    DropdownRow(uiContext.getString(R.string.settings_rapid_fire_speed), uiContext.getString(R.string.settings_rate_of_the_turbo_rapid_fire),
        options.autofire, s.autofireSpeed) { set(s.copy(autofireSpeed = it)) }
    SwitchRow(uiContext.getString(R.string.settings_include_game_save_in_state), uiContext.getString(R.string.settings_saves_the_game_s_save_sav_inside_the_savestate),
        s.backupInSavestates) { set(s.copy(backupInSavestates = it)) }
    SwitchRow(uiContext.getString(R.string.settings_raw_sav_format), uiContext.getString(R.string.settings_saves_the_sav_without_a_header_for),
        s.rawSavFormat) { set(s.copy(rawSavFormat = it)) }
}

@Composable private fun AdvancedSettings(
    s: DsSettings, set: (DsSettings) -> Unit, globalSettings: Boolean,
    openWifi: () -> Unit, openTools: () -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val ctx = LocalContext.current
    var boxArt by remember { mutableStateOf(BoxArt.isEnabled(ctx)) }
    var boxArtSize by remember { mutableStateOf(BoxArt.cacheSizeBytes(ctx)) }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) SeedlessConfirmDialog(
        title = uiContext.getString(R.string.settings_clear_the_box_art_cache),
        message = uiContext.getString(R.string.settings_covers_will_be_downloaded_again_the_next_time),
        confirmLabel = uiContext.getString(R.string.common_clear),
        onConfirm = { BoxArt.clearCache(ctx); boxArtSize = 0L; confirmClear = false },
        onDismiss = { confirmClear = false },
    )

    Group(uiContext.getString(R.string.settings_diagnostics))
    SwitchRow(uiContext.getString(R.string.settings_diagnostic_log), uiContext.getString(R.string.settings_writes_seedlessds_log_in_your_first_rom_folder_device_library_screens_game),
        s.appLogFile) { set(s.copy(appLogFile = it)); if (it) com.seedlessds.app.AppLog.start(ctx, true) else com.seedlessds.app.AppLog.close() }

    Group(uiContext.getString(R.string.settings_performance))
    NumberFieldRow(uiContext.getString(R.string.settings_framerate_stabilizer), uiContext.getString(R.string.settings_keeps_the_cpu_at_high_frequency_0_off),
        s.cpuLoad, 0, 300, "", step = 10) { set(s.copy(cpuLoad = it)) }
    SwitchRow(uiContext.getString(R.string.settings_sleep_on_render_thread), uiContext.getString(R.string.settings_saves_battery_by_yielding_cpu_on_the_graphics),
        s.glThreadSleep) { set(s.copy(glThreadSleep = it)) }
    SwitchRow(uiContext.getString(R.string.settings_low_resolution_ui_textures), uiContext.getString(R.string.settings_reduces_the_memory_the_interface_uses),
        s.lowResTextures) { set(s.copy(lowResTextures = it)) }

    Group(uiContext.getString(R.string.settings_roms_and_data))
    SwitchRow(uiContext.getString(R.string.settings_unzip_zip_to_file), uiContext.getString(R.string.settings_unzips_to_a_temporary_file_instead_of_to_ram),
        s.zipCaching) { set(s.copy(zipCaching = it)) }
    SwitchRow(uiContext.getString(R.string.settings_rom_auto_trim), uiContext.getString(R.string.settings_trims_the_rom_s_padding_to_save_space),
        s.autoTrim) { set(s.copy(autoTrim = it)) }
    SwitchRow(uiContext.getString(R.string.settings_preload_rom_to_ram), uiContext.getString(R.string.settings_loads_the_entire_rom_into_memory_faster_uses),
        s.preloadRoms) { set(s.copy(preloadRoms = it)) }
    SwitchRow(uiContext.getString(R.string.settings_ignore_gamecard_limit), uiContext.getString(R.string.settings_allows_roms_that_exceed_the_standard),
        s.ignoreGamecardLimit) { set(s.copy(ignoreGamecardLimit = it)) }

    Group(uiContext.getString(R.string.settings_box_art))
    SwitchRow(
        uiContext.getString(R.string.settings_download_box_art),
        uiContext.getString(R.string.settings_fetches_covers_from_the_libretro_thumbnail),
        boxArt,
    ) { boxArt = it; BoxArt.setEnabled(ctx, it) }
    NavRow(
        uiContext.getString(R.string.settings_clear_box_art_cache),
        uiContext.getString(R.string.settings_currently, BoxArt.formatSize(uiContext, boxArtSize)),
    ) { confirmClear = true }

    Group(uiContext.getString(R.string.settings_cheats_and_scripting))
    SwitchRow(uiContext.getString(R.string.settings_enable_cheats), uiContext.getString(R.string.settings_enables_cheat_codes_action_replay),
        s.cheatsEnabled) { set(s.copy(cheatsEnabled = it)) }
    SwitchRow(uiContext.getString(R.string.settings_enable_lua_scripts), uiContext.getString(R.string.settings_enables_running_lua_scripts),
        s.luaEnabled) { set(s.copy(luaEnabled = it)) }
    SwitchRow(uiContext.getString(R.string.settings_auto_snap_in_control_editor), uiContext.getString(R.string.settings_aligns_controls_automatically_when_moving_them),
        s.smartEdit) { set(s.copy(smartEdit = it)) }

    Group(uiContext.getString(R.string.settings_system))
    NavRow(uiContext.getString(R.string.common_system_tools), uiContext.getString(R.string.settings_install_ds_bios_export_data), openTools)
    NavRow(uiContext.getString(R.string.settings_multiplayer_wifi), uiContext.getString(R.string.settings_local_multiplayer_status), openWifi)

    if (globalSettings) {
        var alternative by remember { mutableStateOf(LauncherIcon.isAlternative(ctx)) }
        var changing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Group(uiContext.getString(R.string.settings_appearance))
        SwitchRow(uiContext.getString(R.string.settings_use_alt_app_icon),
            uiContext.getString(R.string.settings_alt_app_icon_summary), alternative, enabled = !changing) { value ->
            changing = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { LauncherIcon.setAlternative(ctx, value) }
                    alternative = LauncherIcon.isAlternative(ctx)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.widget.Toast.makeText(ctx, R.string.settings_alt_app_icon_error, android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    changing = false
                }
            }
        }
    }
}

@Composable private fun RaCategory() {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val loggedIn by RetroAchievements.loggedIn
    val status = RetroAchievements.statusMsg.value
    var st by remember { mutableStateOf(RetroAchievements.settings) }
    fun save(n: RaSettings) { st = n; RetroAchievements.saveSettings(n) }

    val colors = seedless
    Group(uiContext.getString(R.string.settings_account))
    if (loggedIn) {
        RaProfileCard(
            username = RetroAchievements.username.value ?: uiContext.getString(R.string.unknown_symbol),
            avatarUrl = RetroAchievements.userAvatar.value,
            hardcore = RetroAchievements.scoreHardcore.value,
            casual = RetroAchievements.score.value,
            onSignOut = { RetroAchievements.logout() },
        )
    } else {
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        SettingSlab {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                RaField(user, { user = it }, uiContext.getString(R.string.settings_retroachievements_username))
                RaField(pass, { pass = it }, uiContext.getString(R.string.settings_password), password = true)
                SeedlessButton(
                    text = uiContext.getString(R.string.settings_sign_in),
                    accent = colors.green,
                    onClick = { RetroAchievements.login(user.trim(), pass) },
                    enabled = user.isNotBlank() && pass.isNotBlank(),
                    fillWidth = true,
                )
                Text(
                    text = uiContext.getString(R.string.settings_your_password_is_not_saved_only_an_encrypted),
                    color = colors.text3,
                    fontFamily = Manrope,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
    if (status.isNotEmpty() && !loggedIn) Text(
        text = status,
        color = colors.green,
        fontFamily = SeedlessMono,
        fontSize = 10.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
    )

    Group(uiContext.getString(R.string.common_achievements))
    SwitchRow(uiContext.getString(R.string.settings_enable_retroachievements), uiContext.getString(R.string.settings_identifies_the_game_and_tracks_your),
        st.enabled) { save(st.copy(enabled = it)) }
    SwitchRow(uiContext.getString(R.string.settings_notification_on_unlock), uiContext.getString(R.string.settings_shows_an_alert_when_you_earn_an_achievement),
        st.notifications) { save(st.copy(notifications = it)) }
    SwitchRow(uiContext.getString(R.string.settings_sound_on_unlock), uiContext.getString(R.string.settings_plays_a_sound_when_you_earn_an_achievement),
        st.sound) { save(st.copy(sound = it)) }

    Group(uiContext.getString(R.string.settings_advanced))
    SwitchRow(uiContext.getString(R.string.settings_encore_mode), uiContext.getString(R.string.settings_lets_you_re_unlock_achievements_you_already),
        st.encore) { save(st.copy(encore = it)) }
    SwitchRow(uiContext.getString(R.string.settings_unofficial_achievements), uiContext.getString(R.string.settings_includes_in_development_unverified_achievements),
        st.unofficial) { save(st.copy(unofficial = it)) }
    SwitchRow(uiContext.getString(R.string.settings_spectator_mode), uiContext.getString(R.string.settings_tracks_achievements_without_sending_unlocks),
        st.spectator) { save(st.copy(spectator = it)) }

    Group(uiContext.getString(R.string.settings_debug))
    SwitchRow(uiContext.getString(R.string.settings_show_rich_presence_on_screen),
        uiContext.getString(R.string.settings_overlays_your_current_retroachievements_rich),
        st.showRichPresence) { save(st.copy(showRichPresence = it)) }
}

@Composable private fun RaField(
    value: String, onChange: (String) -> Unit, label: String, password: Boolean = false
) {
    val colors = seedless
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Manrope, fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = colors.text),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = colors.surface2,
            unfocusedContainerColor = colors.surface2,
            focusedIndicatorColor = colors.green,
            unfocusedIndicatorColor = colors.line,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable private fun CategoryCard(
    c: Cat,
    index: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = seedless
    val interactionSource = remember { MutableInteractionSource() }
    val focusedByPointer by interactionSource.collectIsFocusedAsState()
    val focused = selected || focusedByPointer
    val shape = RoundedCornerShape(15.dp)
    val accent = if (index % 2 == 0) colors.green else colors.red
    val crumb = LocalSettingsCrumb.current
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused) ExternalInfoBus.show {
            ExternalSettingInfo(title = c.title, description = c.summary, crumb = crumb, icon = c.icon)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(colors.surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.red else colors.line, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(c.icon, null, tint = accent, modifier = Modifier.size(21.dp)) }
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(
                text = c.title,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = c.summary,
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = colors.text3, modifier = Modifier.size(19.dp),
        )
    }
}

@Composable private fun RaProfileCard(
    username: String,
    avatarUrl: String?,
    hardcore: Int,
    casual: Int,
    onSignOut: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val shape = RoundedCornerShape(15.dp)
    val fmt = remember { java.text.NumberFormat.getIntegerInstance() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, SeedlessColors.gold.copy(alpha = 0.35f), shape)
            .padding(14.dp),
    ) {
        coil.compose.AsyncImage(
            model = avatarUrl ?: "https://media.retroachievements.org/UserPic/$username.png",
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(SeedlessColors.gold.copy(alpha = 0.18f)),
        )
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.EmojiEvents, null,
                    tint = SeedlessColors.gold,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = uiContext.getString(R.string.common_retroachievements),
                    color = SeedlessColors.gold,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            Text(
                text = username,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ScorePill(uiContext.getString(R.string.settings_hardcore, fmt.format(hardcore)), SeedlessColors.gold)
                Box(Modifier.padding(start = 6.dp)) {
                    ScorePill(uiContext.getString(R.string.settings_casual, fmt.format(casual)), colors.green)
                }
            }
        }
        SeedlessButton(uiContext.getString(R.string.settings_sign_out), colors.surface2, onSignOut)
    }
}

@Composable private fun ScorePill(text: String, accent: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = accent,
        fontFamily = Manrope,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable private fun SectionNote(text: String) {
    val colors = seedless
    Text(
        text = text,
        color = colors.text3,
        fontFamily = Manrope,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
    )
}

@Composable private fun Group(title: String) {
    SeedlessSectionLabel(title, Modifier.fillMaxWidth())
}

@Composable private fun SettingSlab(
    onClick: (() -> Unit)? = null,
    external: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = seedless
    val rows = LocalSettingsRows.current
    val myIndex = remember { rows?.let { it.next++ } ?: -1 }
    androidx.compose.runtime.SideEffect {
        if (rows != null && rows.total < rows.next) rows.total = rows.next
        if (rows != null && myIndex >= 0 && onClick != null) rows.actions[myIndex] = onClick
    }
    val interactionSource = remember { MutableInteractionSource() }
    val focusedByPointer by interactionSource.collectIsFocusedAsState()
    val focused = if (rows != null) rows.selected == myIndex else focusedByPointer
    val center = LocalCenterScroll.current
    if (external != null) {
        androidx.compose.runtime.LaunchedEffect(focused) {
            if (focused) ExternalInfoBus.show(external)
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .centerScrollTarget(center, focused && rows != null)
            .padding(bottom = 5.dp)
            .clip(shape)
            .background(colors.surface)
            .let { if (focused) it.border(2.dp, colors.red, shape) else it }
            .let {
                if (onClick != null) {
                    it.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    it
                }
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        content = content,
    )
}

@Composable private fun externalFor(title: String, desc: String): @Composable () -> Unit {
    val defaultCrumb = androidx.compose.ui.res.stringResource(R.string.common_settings)
    val crumb = LocalSettingsCrumb.current.ifEmpty { defaultCrumb }
    return { ExternalSettingInfo(title = title, description = desc, crumb = crumb) }
}

private val LocalSettingsCrumb = androidx.compose.runtime.compositionLocalOf { "" }

private const val PRESETS_PAGE = "presets"
private const val LAYOUT_PAGE = "layout"
private const val MASK_PAGE = "masks"

private const val SCR_TOP = 0
private const val SCR_BOT = 1
private const val SCR_DUAL = 2
private const val SCR_EXT = 3

private class SettingsRowCursor {
    var next = 0
    var selected by androidx.compose.runtime.mutableIntStateOf(0)
    var total by androidx.compose.runtime.mutableIntStateOf(0)
    val actions = HashMap<Int, () -> Unit>()
}

private val LocalSettingsRows = androidx.compose.runtime.compositionLocalOf<SettingsRowCursor?> { null }

@Composable private fun RowText(title: String, desc: String, modifier: Modifier = Modifier) {
    val colors = seedless
    Column(modifier) {
        Text(
            text = title,
            color = colors.text,
            fontFamily = Manrope,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
        if (desc.isNotBlank()) {
            Text(
                text = desc,
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable private fun SwitchRow(
    title: String,
    desc: String,
    value: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingSlab(
        onClick = if (enabled) ({ onChange(!value) }) else null,
        external = externalFor(title, desc),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, desc, Modifier.weight(1f).padding(end = 12.dp).alpha(if (enabled) 1f else 0.4f))
            SeedlessSwitch(
                checked = value,
                onCheckedChange = if (enabled) onChange else ({}),
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
            )
        }
    }
}

@Composable private fun SliderRow(
    title: String, desc: String, value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit
) {
    val uiContext = LocalContext.current
    val colors = seedless
    SettingSlab(external = externalFor(title, desc)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, desc, Modifier.weight(1f).padding(end = 12.dp))
            Text(
                text = uiContext.getString(R.string.slider_value_unit, value, unit),
                color = colors.green,
                fontFamily = SeedlessMono,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.green,
                activeTrackColor = colors.green,
                inactiveTrackColor = colors.switchOff,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val PreviewTopColor = Color(0xFF43A047)
private val PreviewBotColor = Color(0xFFFB8C00)

private class PreviewRect(val rect: FloatArray, val label: String, val color: Color)

private fun previewScaleAboutCentre(r: FloatArray, f: Float): FloatArray {
    if (r.size < 4 || f == 1f) return r
    val w = r[2] * f; val h = r[3] * f
    return floatArrayOf(r[0] + (r[2] - w) / 2f, r[1] + (r[3] - h) / 2f, w, h)
}

/** Mirror of the single-surface geometry in DsGlView (rawRects + transforms), in preview units. */
private fun singlePreviewRects(s: DsSettings, w: Float, h: Float, topLabel: String, botLabel: String): List<PreviewRect> {
    fun fit(maxW: Float, maxH: Float) = ScreenGeometry.fitted(maxW, maxH, true, s.integerScale)
    if (DualScreenPresets.singleScreen(s)) {
        val base = if (s.dualScreenPreset != DualScreenPresets.OFF) {
            // No external display is attached on this page, so same-size has no
            // partner panel; the preset rect without it is the closest diagram.
            DualScreenPresets.rect(w, h, s.dsKeepRatio, s.integerScale, s.dsIntFillW, s.dsIntFillH, s.dsIntAlign, false, null, null)
        } else if (s.screenLayout == 4) floatArrayOf(0f, 0f, w, h)
        else { val (sw, sh) = fit(w, h); floatArrayOf((w - sw) / 2f, (h - sh) / 2f, sw, sh) }
        val showingBottom = DualScreenPresets.singleBottom(s) != s.swapScreens
        val r = if (showingBottom) ScreenGeometry.transform(base, w, h, ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY)
            else ScreenGeometry.transform(base, w, h, ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY)
        return listOf(PreviewRect(r, if (showingBottom) botLabel else topLabel, if (showingBottom) PreviewBotColor else PreviewTopColor))
    }
    var slot0: FloatArray; var slot1: FloatArray
    when (s.screenLayout) {
        2 -> { val (sw, sh) = fit(w, h / 2f); val x = (w - sw) / 2f
            slot0 = floatArrayOf(x, h / 2f - sh, sw, sh); slot1 = floatArrayOf(x, h / 2f, sw, sh) }
        1 -> { val (bw, bh) = fit(w * 0.72f, h); slot0 = floatArrayOf(0f, (h - bh) / 2f, bw, bh)
            val smW = w * 0.24f; val smH = smW / (256f / 192f); slot1 = floatArrayOf(w - smW - 8f, h - smH - 8f, smW, smH) }
        4 -> { val halfW = w / 2f; slot0 = floatArrayOf(0f, 0f, halfW, h); slot1 = floatArrayOf(halfW, 0f, halfW, h) }
        5 -> { slot0 = floatArrayOf(0f, 0f, w, h)
            val k = s.pipScale.coerceIn(0, 25) / 25f
            if (k <= 0f) { slot1 = floatArrayOf(w + 8f, h + 8f, 0f, 0f) } else {
                val maxW = (w / 2f - 16f) * k; val maxH = (h / 2f - 16f) * k; val a = 256f / 192f
                var bw = maxW; var bh = maxW / a; if (bh > maxH) { bh = maxH; bw = maxH * a }
                slot1 = floatArrayOf(w - bw - 8f, h - bh - 8f, bw, bh) } }
        else -> { val halfW = w / 2f; val (sw, sh) = fit(halfW, h); val y = (h - sh) / 2f
            slot0 = floatArrayOf((halfW - sw) / 2f, y, sw, sh); slot1 = floatArrayOf(halfW + (halfW - sw) / 2f, y, sw, sh) }
    }
    // No raw-rect exchange: swap only relabels which content each rect shows,
    // mirroring DsGlView (the final rects stay, the contents exchange).
    val topRect = ScreenGeometry.transform(slot0, w, h, ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY)
    val botRect = ScreenGeometry.transform(slot1, w, h, ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY)
    return listOf(
        PreviewRect(topRect, if (s.swapScreens) botLabel else topLabel, if (s.swapScreens) PreviewBotColor else PreviewTopColor),
        PreviewRect(botRect, if (s.swapScreens) topLabel else botLabel, if (s.swapScreens) PreviewTopColor else PreviewBotColor),
    )
}

@Composable private fun previewPanelAspect(): Float {
    val cfg = LocalConfiguration.current
    val ratio = if (cfg.screenHeightDp > 0) cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.toFloat() else 16f / 9f
    return if (ratio >= 1f) ratio.coerceAtMost(2.4f) else 16f / 9f
}

@Composable private fun PreviewPanel(w: Float, h: Float, caption: String, rects: List<PreviewRect>) {
    val colors = seedless
    Column {
        Box(
            Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(10.dp))
                .background(Color.Black).border(1.dp, colors.line, RoundedCornerShape(10.dp))
        ) {
            for (pr in rects) {
                if (pr.rect[2] <= 0f || pr.rect[3] <= 0f) continue
                Box(
                    Modifier.offset(x = pr.rect[0].dp, y = pr.rect[1].dp).size(pr.rect[2].dp, pr.rect[3].dp)
                        .background(pr.color.copy(alpha = 0.85f)).border(1.dp, Color.White.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) { Text(pr.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (caption.isNotEmpty())
            Text(caption, color = colors.text3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun SingleLayoutPreview(s: DsSettings, tick: Int) {
    val uiContext = LocalContext.current
    val aspect = previewPanelAspect()
    val topLabel = uiContext.getString(R.string.common_top_screen)
    val botLabel = uiContext.getString(R.string.common_bottom_screen)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth.value
        val h = (w / aspect).coerceAtMost(300f)
        val rects = singlePreviewRects(s, w, h, topLabel, botLabel)
        PreviewPanel(w, h, "", rects)
    }
}

@Composable private fun DualLayoutPreview(s: DsSettings, tick: Int) {
    val uiContext = LocalContext.current
    val aspect = previewPanelAspect()
    val topLabel = uiContext.getString(R.string.common_top_screen)
    val botLabel = uiContext.getString(R.string.common_bottom_screen)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val total = maxWidth.value
        val gap = 12f
        val pw = (total - gap) / 2f
        val intH = pw / aspect
        val extH = pw / (16f / 9f)
        val intPanel = floatArrayOf(pw, intH, 300f, 300f, 300f)
        val extPanel = floatArrayOf(pw, extH, 300f, 300f, 300f)
        val internalRects: List<PreviewRect> = when {
            s.dualScreenPreset != DualScreenPresets.OFF -> {
                val base = DualScreenPresets.rect(pw, intH, s.dsKeepRatio, s.integerScale,
                    s.dsIntFillW, s.dsIntFillH, s.dsIntAlign, s.dsSameSize, intPanel, extPanel)
                val r = ScreenGeometry.transform(base, pw, intH, ControlLayout.dualScale, ControlLayout.dualOffX, ControlLayout.dualOffY)
                val isBottom = s.dualScreenPreset == DualScreenPresets.INTERNAL_BOTTOM
                listOf(PreviewRect(r, if (isBottom) botLabel else topLabel, if (isBottom) PreviewBotColor else PreviewTopColor))
            }
            s.singleScreen -> {
                val base = if (s.screenLayout == 4) floatArrayOf(0f, 0f, pw, intH)
                    else { val (sw, sh) = ScreenGeometry.fitted(pw, intH, true, s.integerScale)
                        floatArrayOf((pw - sw) / 2f, (intH - sh) / 2f, sw, sh) }
                val r = ScreenGeometry.transform(base, pw, intH, ControlLayout.dualScale, ControlLayout.dualOffX, ControlLayout.dualOffY)
                listOf(PreviewRect(r, if (s.singleBottom) botLabel else topLabel, if (s.singleBottom) PreviewBotColor else PreviewTopColor))
            }
            else -> singlePreviewRects(s, pw, intH, topLabel, botLabel)
        }
        val externalRects: List<PreviewRect> = run {
            val contentBottom = ScreenGeometry.externalScreen(s.extDisplayScreen, s.swapScreens) == 1
            val base = when {
                s.dualScreenPreset != DualScreenPresets.OFF -> {
                    val r = DualScreenPresets.rect(pw, extH, s.dsKeepRatio, s.integerScale,
                        s.dsExtFillW, s.dsExtFillH, s.dsExtAlign, s.dsSameSize, extPanel, intPanel)
                    if (s.dsSameSize) r else previewScaleAboutCentre(r, 1f - s.extDisplayBorder / 100f)
                }
                s.extDisplayType == 2 -> floatArrayOf(0f, 0f, pw, extH)
                else -> {
                    val (sw, sh) = ScreenGeometry.fitted(pw, extH, true, s.integerScale)
                    previewScaleAboutCentre(floatArrayOf((pw - sw) / 2f, (extH - sh) / 2f, sw, sh), 1f - s.extDisplayBorder / 100f)
                }
            }
            val r = ScreenGeometry.transform(base, pw, extH, ControlLayout.extScale, ControlLayout.extOffX, ControlLayout.extOffY)
            listOf(PreviewRect(r, if (contentBottom) botLabel else topLabel, if (contentBottom) PreviewBotColor else PreviewTopColor))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
            PreviewPanel(pw, intH, uiContext.getString(R.string.layout_internal_screen), internalRects)
            PreviewPanel(pw, extH, uiContext.getString(R.string.menu_external_screen), externalRects)
        }
    }
}

/** One DS screen in the layout editor: its untransformed base rect, the
 * displayed rect (transformed, clamped inside the panel), and which
 * ControlLayout value set edits it. */
private class EditorScreen(val base: FloatArray, val rect: FloatArray, val key: Int, val label: String, val color: Color)

private fun screenValues(key: Int): FloatArray = when (key) {
    SCR_BOT -> floatArrayOf(ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY)
    SCR_DUAL -> floatArrayOf(ControlLayout.dualScale, ControlLayout.dualOffX, ControlLayout.dualOffY)
    SCR_EXT -> floatArrayOf(ControlLayout.extScale, ControlLayout.extOffX, ControlLayout.extOffY)
    else -> floatArrayOf(ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY)
}

private fun editorScreen(base: FloatArray, w: Float, h: Float, key: Int, label: String, color: Color): EditorScreen {
    val v = screenValues(key)
    val c = ScreenGeometry.clampInside(base, w, h, v[0], v[1], v[2])
    return EditorScreen(base, ScreenGeometry.transform(base, w, h, c[0], c[1], c[2]), key, label, color)
}

/** Mirror of the single-surface geometry in DsGlView (rawRects + transforms), in editor units. */
private fun singleEditorScreens(s: DsSettings, w: Float, h: Float, topLabel: String, botLabel: String): List<EditorScreen> {
    fun fit(maxW: Float, maxH: Float) = ScreenGeometry.fitted(maxW, maxH, true, s.integerScale)
    if (DualScreenPresets.singleScreen(s)) {
        val base = if (s.dualScreenPreset != DualScreenPresets.OFF) {
            DualScreenPresets.rect(w, h, s.dsKeepRatio, s.integerScale, s.dsIntFillW, s.dsIntFillH, s.dsIntAlign, false, null, null)
        } else if (s.screenLayout == 4) floatArrayOf(0f, 0f, w, h)
        else { val (sw, sh) = fit(w, h); floatArrayOf((w - sw) / 2f, (h - sh) / 2f, sw, sh) }
        val showingBottom = DualScreenPresets.singleBottom(s) != s.swapScreens
        return listOf(
            if (showingBottom) editorScreen(base, w, h, SCR_BOT, botLabel, PreviewBotColor)
            else editorScreen(base, w, h, SCR_TOP, topLabel, PreviewTopColor)
        )
    }
    var slot0: FloatArray; var slot1: FloatArray
    when (s.screenLayout) {
        2 -> { val (sw, sh) = fit(w, h / 2f); val x = (w - sw) / 2f
            slot0 = floatArrayOf(x, h / 2f - sh, sw, sh); slot1 = floatArrayOf(x, h / 2f, sw, sh) }
        1 -> { val (bw, bh) = fit(w * 0.72f, h); slot0 = floatArrayOf(0f, (h - bh) / 2f, bw, bh)
            val smW = w * 0.24f; val smH = smW / (256f / 192f); slot1 = floatArrayOf(w - smW - 8f, h - smH - 8f, smW, smH) }
        4 -> { val halfW = w / 2f; slot0 = floatArrayOf(0f, 0f, halfW, h); slot1 = floatArrayOf(halfW, 0f, halfW, h) }
        5 -> { slot0 = floatArrayOf(0f, 0f, w, h)
            val k = s.pipScale.coerceIn(0, 25) / 25f
            if (k <= 0f) { slot1 = floatArrayOf(w + 8f, h + 8f, 0f, 0f) } else {
                val maxW = (w / 2f - 16f) * k; val maxH = (h / 2f - 16f) * k; val a = 256f / 192f
                var bw = maxW; var bh = maxW / a; if (bh > maxH) { bh = maxH; bw = maxH * a }
                slot1 = floatArrayOf(w - bw - 8f, h - bh - 8f, bw, bh) } }
        else -> { val halfW = w / 2f; val (sw, sh) = fit(halfW, h); val y = (h - sh) / 2f
            slot0 = floatArrayOf((halfW - sw) / 2f, y, sw, sh); slot1 = floatArrayOf(halfW + (halfW - sw) / 2f, y, sw, sh) }
    }
    // Same as the preview: bases stay with their own transforms; swap only
    // changes which content label each editor screen carries.
    return listOf(
        editorScreen(slot0, w, h, SCR_TOP, if (s.swapScreens) botLabel else topLabel, if (s.swapScreens) PreviewBotColor else PreviewTopColor),
        editorScreen(slot1, w, h, SCR_BOT, if (s.swapScreens) topLabel else botLabel, if (s.swapScreens) PreviewTopColor else PreviewBotColor),
    )
}

/** Internal-panel screens for the dual setup, in a panel of w x h (editor units). */
private fun editorInternalScreens(s: DsSettings, w: Float, h: Float, topLabel: String, botLabel: String): List<EditorScreen> {
    val extArr = DualScreenPresets.panelExternal
    val extAspect = if (extArr != null && extArr[1] > 0f) extArr[0] / extArr[1] else 16f / 9f
    val own = floatArrayOf(w, h, 300f, 300f, 300f)
    val other = floatArrayOf(h * extAspect, h, 300f, 300f, 300f)
    return when {
        s.dualScreenPreset != DualScreenPresets.OFF -> {
            val base = DualScreenPresets.rect(w, h, s.dsKeepRatio, s.integerScale,
                s.dsIntFillW, s.dsIntFillH, s.dsIntAlign, s.dsSameSize, own, other)
            val isBottom = s.dualScreenPreset == DualScreenPresets.INTERNAL_BOTTOM
            listOf(editorScreen(base, w, h, SCR_DUAL, if (isBottom) botLabel else topLabel, if (isBottom) PreviewBotColor else PreviewTopColor))
        }
        s.singleScreen -> {
            val base = if (s.screenLayout == 4) floatArrayOf(0f, 0f, w, h)
                else { val (sw, sh) = ScreenGeometry.fitted(w, h, true, s.integerScale)
                    floatArrayOf((w - sw) / 2f, (h - sh) / 2f, sw, sh) }
            listOf(editorScreen(base, w, h, SCR_DUAL, if (s.singleBottom) botLabel else topLabel, if (s.singleBottom) PreviewBotColor else PreviewTopColor))
        }
        else -> singleEditorScreens(s, w, h, topLabel, botLabel)
    }
}

/** External-panel screens for the dual setup, in a panel of w x h (editor units). */
private fun editorExternalScreens(s: DsSettings, w: Float, h: Float, topLabel: String, botLabel: String): List<EditorScreen> {
    val intArr = DualScreenPresets.panelInternal
    val intAspect = if (intArr != null && intArr[1] > 0f) intArr[0] / intArr[1] else 16f / 9f
    val own = floatArrayOf(w, h, 300f, 300f, 300f)
    val other = floatArrayOf(h * intAspect, h, 300f, 300f, 300f)
    val contentBottom = ScreenGeometry.externalScreen(s.extDisplayScreen, s.swapScreens) == 1
    val base = when {
        s.dualScreenPreset != DualScreenPresets.OFF -> {
            val r = DualScreenPresets.rect(w, h, s.dsKeepRatio, s.integerScale,
                s.dsExtFillW, s.dsExtFillH, s.dsExtAlign, s.dsSameSize, own, other)
            if (s.dsSameSize) r else previewScaleAboutCentre(r, 1f - s.extDisplayBorder / 100f)
        }
        s.extDisplayType == 2 -> floatArrayOf(0f, 0f, w, h)
        else -> {
            val (sw, sh) = ScreenGeometry.fitted(w, h, true, s.integerScale)
            previewScaleAboutCentre(floatArrayOf((w - sw) / 2f, (h - sh) / 2f, sw, sh), 1f - s.extDisplayBorder / 100f)
        }
    }
    return listOf(editorScreen(base, w, h, SCR_EXT, if (contentBottom) botLabel else topLabel, if (contentBottom) PreviewBotColor else PreviewTopColor))
}

@Composable private fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = seedless
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) colors.green else colors.surface.copy(alpha = 0.92f))
            .border(1.dp, colors.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) Color.White else colors.text,
            fontFamily = Manrope, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One numeric pixel field: shows the live value, commits typed values at once. */
@Composable private fun PixelField(label: String, valuePx: Int, modifier: Modifier = Modifier, onCommit: (Int) -> Unit) {
    val colors = seedless
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var text by remember { mutableStateOf(valuePx.toString()) }
    var focused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(valuePx) {
        if (!focused && text != valuePx.toString()) text = valuePx.toString()
    }
    Column(modifier) {
        Text(label, color = colors.text3, fontFamily = Manrope, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 2.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                val clean = t.filter { ch -> ch.isDigit() || ch == '-' }.take(6)
                text = clean
                clean.toIntOrNull()?.let { onCommit(it) }
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SeedlessMono, fontSize = 12.5.sp, color = colors.text),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = colors.surface2,
                unfocusedContainerColor = colors.surface2,
                focusedIndicatorColor = colors.green,
                unfocusedIndicatorColor = colors.line,
            ),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

/** Pixel-exact size/position fields for one editor screen, in physical pixels of its panel. */
@Composable private fun ScreenPixelFields(
    es: EditorScreen?, densityF: Float,
    onSize: (Boolean, Int) -> Unit, onPos: (Boolean, Int) -> Unit,
) {
    if (es == null) return
    val uiContext = LocalContext.current
    val wPx = Math.round(es.rect[2] * densityF)
    val hPx = Math.round(es.rect[3] * densityF)
    val xPx = Math.round(es.rect[0] * densityF)
    val yPx = Math.round(es.rect[1] * densityF)
    Column(Modifier.padding(top = 2.dp, bottom = 6.dp)) {
        Group(uiContext.getString(R.string.layout_pixel_group))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelField(uiContext.getString(R.string.layout_px_w), wPx, Modifier.weight(1f)) { onSize(true, it) }
            PixelField(uiContext.getString(R.string.layout_px_h), hPx, Modifier.weight(1f)) { onSize(false, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            PixelField(uiContext.getString(R.string.layout_px_x), xPx, Modifier.weight(1f)) { onPos(true, it) }
            PixelField(uiContext.getString(R.string.layout_px_y), yPx, Modifier.weight(1f)) { onPos(false, it) }
        }
    }
}

/**
 * Full-screen per-screen layout editor. The whole window stands in for the
 * physical panel being edited, so the DS screens appear at exactly the size
 * and position they will have in game on this device. Editing is clamped to
 * the panel: [ScreenGeometry.clampInside] caps size and offsets for every
 * change, whether it comes from the floating parameter panel (collapsed by
 * default, tap outside to dismiss) or from dragging a screen directly —
 * drag moves it, the corner dot resizes it, and both write the same
 * ControlLayout values the sliders show. Changes bump [tick], which is read
 * in the plain Box composition below, so the picture follows immediately.
 */
@Composable private fun LayoutEditorScreen(s: DsSettings, onDone: () -> Unit) {
    val uiContext = LocalContext.current
    val colors = seedless
    val unit = uiContext.getString(R.string.percent_unit)
    val topLabel = uiContext.getString(R.string.common_top_screen)
    val botLabel = uiContext.getString(R.string.common_bottom_screen)
    val clContext = remember(uiContext) { ControlLayout.load(uiContext); uiContext }
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var scene by remember { androidx.compose.runtime.mutableIntStateOf(if (s.dualScreenPreset != DualScreenPresets.OFF) 1 else 0) }
    var panelOpen by remember { mutableStateOf(false) }
    var winSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    fun persist() { ControlLayout.saveScreens(clContext); tick++ }
    fun writeValues(key: Int, c: FloatArray) {
        when (key) {
            SCR_BOT -> { ControlLayout.botScale = c[0]; ControlLayout.botOffX = c[1]; ControlLayout.botOffY = c[2] }
            SCR_DUAL -> { ControlLayout.dualScale = c[0]; ControlLayout.dualOffX = c[1]; ControlLayout.dualOffY = c[2] }
            SCR_EXT -> { ControlLayout.extScale = c[0]; ControlLayout.extOffX = c[1]; ControlLayout.extOffY = c[2] }
            else -> { ControlLayout.topScale = c[0]; ControlLayout.topOffX = c[1]; ControlLayout.topOffY = c[2] }
        }
        persist()
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { winSize = it }) {
        val tickNow = tick
        val densityObj = androidx.compose.ui.platform.LocalDensity.current
        val wDp = if (winSize.width > 0) with(densityObj) { winSize.width.toDp().value } else 0f
        val hDp = if (winSize.height > 0) with(densityObj) { winSize.height.toDp().value } else 0f
        // Panel being edited: the internal scenes use the whole window
        // (that IS the physical panel); the external scene fits the real
        // external aspect into the window, since it cannot be 1:1 here.
        val extArr = DualScreenPresets.panelExternal
        var pw = wDp; var ph = hDp
        if (scene == 2 && wDp > 0f) {
            val extAspect = if (extArr != null && extArr[1] > 0f) extArr[0] / extArr[1] else 16f / 9f
            pw = minOf(wDp, hDp * extAspect); ph = pw / extAspect
            if (ph > hDp) { ph = hDp; pw = ph * extAspect }
        }
        val ox = (wDp - pw) / 2f; val oy = (hDp - ph) / 2f
        val screens: List<EditorScreen> = if (wDp > 0f && hDp > 0f) when (scene) {
            1 -> editorInternalScreens(s, pw, ph, topLabel, botLabel)
            2 -> editorExternalScreens(s, pw, ph, topLabel, botLabel)
            else -> singleEditorScreens(s, pw, ph, topLabel, botLabel)
        } else emptyList()
        fun sliderChange(key: Int, sc: Float, x: Float, y: Float) {
            val base = screens.firstOrNull { it.key == key }?.base
            val c = if (base != null && pw > 0f) ScreenGeometry.clampInside(base, pw, ph, sc, x, y)
                else floatArrayOf(sc, x, y)
            writeValues(key, c)
        }
        fun centreScreen(key: Int) {
            val es = screens.firstOrNull { it.key == key } ?: return
            val v = screenValues(key)
            val o = ScreenGeometry.offsetsFor(es.base, pw, ph, v[0], (pw - es.base[2] * v[0]) / 2f, (ph - es.base[3] * v[0]) / 2f)
            writeValues(key, ScreenGeometry.clampInside(es.base, pw, ph, v[0], o[0], o[1]))
        }
        fun pixelSize(key: Int, isWidth: Boolean, px: Int) {
            val es = screens.firstOrNull { it.key == key } ?: return
            val v = screenValues(key)
            val dpVal = px / densityObj.density
            val sc = if (isWidth) { if (es.base[2] > 0f) dpVal / es.base[2] else v[0] }
                else { if (es.base[3] > 0f) dpVal / es.base[3] else v[0] }
            writeValues(key, ScreenGeometry.clampInside(es.base, pw, ph, sc, v[1], v[2]))
        }
        fun pixelPos(key: Int, isX: Boolean, px: Int) {
            val es = screens.firstOrNull { it.key == key } ?: return
            val v = screenValues(key)
            val sc = if (es.base[2] > 0f && es.rect[2] > 0f) es.rect[2] / es.base[2] else v[0]
            val cur = ScreenGeometry.transform(es.base, pw, ph, sc, v[1], v[2])
            val o = ScreenGeometry.offsetsFor(es.base, pw, ph, sc,
                if (isX) px / densityObj.density else cur[0],
                if (isX) cur[1] else px / densityObj.density)
            writeValues(key, ScreenGeometry.clampInside(es.base, pw, ph, sc, o[0], o[1]))
        }
        fun snapBottom(edge: Int) {
            val top = screens.firstOrNull { it.key == SCR_TOP } ?: return
            val bot = screens.firstOrNull { it.key == SCR_BOT } ?: return
            val bw = bot.rect[2]; val bh = bot.rect[3]
            if (bw <= 0f || bh <= 0f || bot.base[2] <= 0f) return
            val sc = bw / bot.base[2]
            val tx: Float; val ty: Float
            when (edge) {
                0 -> { tx = top.rect[0]; ty = top.rect[1] - bh }
                2 -> { tx = top.rect[0] - bw; ty = top.rect[1] }
                3 -> { tx = top.rect[0] + top.rect[2]; ty = top.rect[1] }
                else -> { tx = top.rect[0]; ty = top.rect[1] + top.rect[3] }
            }
            val o = ScreenGeometry.offsetsFor(bot.base, pw, ph, sc, tx, ty)
            writeValues(SCR_BOT, ScreenGeometry.clampInside(bot.base, pw, ph, sc, o[0], o[1]))
        }
        // tickNow is intentionally consumed here so every persist()
        // recomposes this block even when the window size is unchanged.
        @Suppress("UNUSED_EXPRESSION") tickNow

        // A tap on the empty canvas dismisses the parameter panel.
        Box(Modifier.fillMaxSize().pointerInput(panelOpen) {
            detectTapGestures { if (panelOpen) panelOpen = false }
        })

        if (screens.isNotEmpty()) {
            Box(Modifier.offset(x = ox.dp, y = oy.dp).size(pw.dp, ph.dp)
                .background(Color(0xFF101418)).border(1.dp, Color.White.copy(alpha = 0.35f)))
            Box(Modifier.offset(x = (ox + pw / 2f).dp, y = oy.dp).size(1.dp, ph.dp)
                .background(Color.White.copy(alpha = 0.13f)))
            Box(Modifier.offset(x = ox.dp, y = (oy + ph / 2f).dp).size(pw.dp, 1.dp)
                .background(Color.White.copy(alpha = 0.13f)))
            Box(Modifier.offset(x = ox.dp, y = oy.dp).size(pw.dp, ph.dp).clipToBounds()) {
                for (es in screens) {
                    if (es.rect[2] <= 0f || es.rect[3] <= 0f) continue
                    Box(
                        Modifier.offset(x = es.rect[0].dp, y = es.rect[1].dp)
                            .size(es.rect[2].dp, es.rect[3].dp)
                            .background(es.color.copy(alpha = 0.88f))
                            .border(2.dp, Color.White.copy(alpha = 0.85f))
                            .pointerInput(es.key, pw, ph) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val v = screenValues(es.key)
                                    val c = ScreenGeometry.clampInside(es.base, pw, ph, v[0],
                                        v[1] + (drag.x / density) / pw, v[2] + (drag.y / density) / ph)
                                    writeValues(es.key, c)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxSize().padding(9.dp).border(1.dp, Color.White.copy(alpha = 0.5f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(es.label, color = Color.White, fontFamily = Manrope,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${es.rect[2].toInt()} × ${es.rect[3].toInt()} dp",
                                color = Color.White.copy(alpha = 0.9f), fontFamily = SeedlessMono, fontSize = 11.sp)
                        }
                        if (es.rect[2] >= 64f && es.rect[3] >= 64f) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(5.dp).size(26.dp)
                                    .clip(CircleShape).background(Color.White.copy(alpha = 0.4f))
                                    .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                                    .pointerInput(es.key, pw, ph) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            val v = screenValues(es.key)
                                            val curW = es.base[2] * v[0]
                                            if (curW > 1f) {
                                                val factor = ((curW + drag.x / density) / curW).coerceIn(0.85f, 1.18f)
                                                val c = ScreenGeometry.clampInside(es.base, pw, ph, v[0] * factor, v[1], v[2])
                                                writeValues(es.key, c)
                                            }
                                        }
                                    })
                        }
                    }
                }
            }
            val caption = when (scene) {
                2 -> uiContext.getString(R.string.layout_editor_caption_external,
                    if (extArr != null) "${extArr[0].toInt()}×${extArr[1].toInt()}" else "16:9")
                else -> uiContext.getString(R.string.layout_editor_caption_live)
            }
            Box(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(10.dp)
                .clip(RoundedCornerShape(8.dp)).background(colors.surface.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(caption, color = colors.text, fontFamily = Manrope, fontSize = 11.5.sp)
            }
        }

        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorChip(uiContext.getString(R.string.layout_editor_done), false) { onDone() }
            EditorChip(uiContext.getString(R.string.layout_tab_single), scene == 0) { scene = 0 }
            EditorChip(uiContext.getString(R.string.layout_tab_dual) + " · " + uiContext.getString(R.string.layout_internal_screen), scene == 1) { scene = 1 }
            EditorChip(uiContext.getString(R.string.layout_tab_dual) + " · " + uiContext.getString(R.string.menu_external_screen), scene == 2) { scene = 2 }
        }

        if (panelOpen) {
            Column(
                Modifier.align(Alignment.TopEnd).statusBarsPadding()
                    .padding(top = 54.dp, end = 10.dp, bottom = 10.dp)
                    .width(300.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                    .background(colors.surface.copy(alpha = 0.96f)).border(1.dp, colors.line, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState()).padding(10.dp)
            ) {
                when (scene) {
                    0 -> {
                        Group(uiContext.getString(R.string.layout_single_group))
                        ScreenAdjustRows(
                            topLabel, unit,
                            ControlLayout.topScale, ControlLayout.topOffX, ControlLayout.topOffY,
                        ) { sc, x, y -> sliderChange(SCR_TOP, sc, x, y) }
                        ScreenPixelFields(screens.firstOrNull { it.key == SCR_TOP }, densityObj.density,
                            onSize = { isW, px -> pixelSize(SCR_TOP, isW, px) },
                            onPos = { isX, px -> pixelPos(SCR_TOP, isX, px) })
                        Row(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                            EditorChip(uiContext.getString(R.string.presets_centre), false) { centreScreen(SCR_TOP) }
                        }
                        ScreenAdjustRows(
                            botLabel, unit,
                            ControlLayout.botScale, ControlLayout.botOffX, ControlLayout.botOffY,
                        ) { sc, x, y -> sliderChange(SCR_BOT, sc, x, y) }
                        ScreenPixelFields(screens.firstOrNull { it.key == SCR_BOT }, densityObj.density,
                            onSize = { isW, px -> pixelSize(SCR_BOT, isW, px) },
                            onPos = { isX, px -> pixelPos(SCR_BOT, isX, px) })
                        Row(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                            EditorChip(uiContext.getString(R.string.presets_centre), false) { centreScreen(SCR_BOT) }
                        }
                        if (screens.any { it.key == SCR_TOP } && screens.any { it.key == SCR_BOT }) {
                            Group(uiContext.getString(R.string.layout_snap_title))
                            SectionNote(uiContext.getString(R.string.layout_snap_desc))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                EditorChip(uiContext.getString(R.string.layout_snap_below), false) { snapBottom(1) }
                                EditorChip(uiContext.getString(R.string.layout_snap_above), false) { snapBottom(0) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                EditorChip(uiContext.getString(R.string.layout_snap_left), false) { snapBottom(2) }
                                EditorChip(uiContext.getString(R.string.layout_snap_right), false) { snapBottom(3) }
                            }
                        }
                    }
                    1 -> {
                        Group(uiContext.getString(R.string.layout_dual_group))
                        ScreenAdjustRows(
                            uiContext.getString(R.string.layout_internal_screen), unit,
                            ControlLayout.dualScale, ControlLayout.dualOffX, ControlLayout.dualOffY,
                        ) { sc, x, y -> sliderChange(SCR_DUAL, sc, x, y) }
                        ScreenPixelFields(screens.firstOrNull { it.key == SCR_DUAL }, densityObj.density,
                            onSize = { isW, px -> pixelSize(SCR_DUAL, isW, px) },
                            onPos = { isX, px -> pixelPos(SCR_DUAL, isX, px) })
                        Row(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                            EditorChip(uiContext.getString(R.string.presets_centre), false) { centreScreen(SCR_DUAL) }
                        }
                    }
                    else -> {
                        Group(uiContext.getString(R.string.layout_dual_group))
                        ScreenAdjustRows(
                            uiContext.getString(R.string.menu_external_screen), unit,
                            ControlLayout.extScale, ControlLayout.extOffX, ControlLayout.extOffY,
                        ) { sc, x, y -> sliderChange(SCR_EXT, sc, x, y) }
                        ScreenPixelFields(screens.firstOrNull { it.key == SCR_EXT }, densityObj.density,
                            onSize = { isW, px -> pixelSize(SCR_EXT, isW, px) },
                            onPos = { isX, px -> pixelPos(SCR_EXT, isX, px) })
                        Row(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                            EditorChip(uiContext.getString(R.string.presets_centre), false) { centreScreen(SCR_EXT) }
                        }
                    }
                }
                NavRow(uiContext.getString(R.string.layout_reset), uiContext.getString(R.string.layout_reset_desc)) {
                    ControlLayout.resetScreens(clContext); tick++
                }
                NavRow(uiContext.getString(R.string.layout_close_params), uiContext.getString(R.string.layout_editor_collapse_desc)) {
                    panelOpen = false
                }
                SectionNote(uiContext.getString(R.string.layout_editor_drag_hint))
                SectionNote(uiContext.getString(R.string.layout_drag_note))
            }
        } else {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
                EditorChip(uiContext.getString(R.string.layout_edit_params), false) { panelOpen = true }
            }
        }
    }
}

/** Size + horizontal/vertical position sliders for one screen's free layout transform. */
@Composable private fun ScreenAdjustRows(
    name: String, unit: String, scale: Float, offX: Float, offY: Float,
    onChange: (Float, Float, Float) -> Unit
) {
    val uiContext = LocalContext.current
    SliderRow(
        uiContext.getString(R.string.layout_row, name, uiContext.getString(R.string.layout_size)),
        uiContext.getString(R.string.layout_size_desc),
        (scale * 100).toInt().coerceIn(25, 200), 25, 200, unit
    ) { onChange(it / 100f, offX, offY) }
    SliderRow(
        uiContext.getString(R.string.layout_row, name, uiContext.getString(R.string.layout_horizontal)),
        uiContext.getString(R.string.layout_offset_desc),
        (offX * 100).toInt().coerceIn(-50, 50), -50, 50, unit
    ) { onChange(scale, it / 100f, offY) }
    SliderRow(
        uiContext.getString(R.string.layout_row, name, uiContext.getString(R.string.layout_vertical)),
        uiContext.getString(R.string.layout_offset_desc),
        (offY * 100).toInt().coerceIn(-50, 50), -50, 50, unit
    ) { onChange(scale, offX, it / 100f) }
}

@Composable private fun NumberFieldRow(
    title: String, desc: String, value: Int, min: Int, max: Int, unit: String,
    step: Int = 1, onChange: (Int) -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    SettingSlab(external = externalFor(title, desc)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, desc, Modifier.weight(1f).padding(end = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(Icons.Rounded.Remove, uiContext.getString(R.string.settings_decrease), value > min) {
                    onChange((value - step).coerceIn(min, max))
                }
                Text(
                    text = uiContext.getString(R.string.slider_value_unit, value, unit),
                    color = colors.text,
                    fontFamily = SeedlessMono,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp),
                )
                StepperButton(Icons.Rounded.Add, uiContext.getString(R.string.settings_increase), value < max) {
                    onChange((value + step).coerceIn(min, max))
                }
            }
        }
    }
}

@Composable private fun StepperButton(
    icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit
) {
    val colors = seedless
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surface2)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = colors.text2, modifier = Modifier.size(16.dp)) }
}

@Composable private fun DropdownRow(
    title: String, desc: String, options: List<String>, selected: Int,
    enabled: Boolean = true, onSelect: (Int) -> Unit
) {
    val colors = seedless
    var open by remember { mutableStateOf(false) }
    val idx = selected.coerceIn(0, options.lastIndex)
    SettingSlab(
        onClick = if (enabled) ({ open = true }) else null,
        external = externalFor(title, desc),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        ) {
            RowText(title, desc, Modifier.weight(1f).padding(end = 12.dp))
            Text(
                text = options[idx],
                color = colors.green,
                fontFamily = Manrope,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 128.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = colors.text3,
                modifier = Modifier.size(17.dp).padding(start = 2.dp),
            )
        }
    }
    if (open) SeedlessChoiceDialog(
        title = title,
        options = options,
        selectedIndex = idx,
        onSelect = onSelect,
        onDismiss = { open = false },
    )
}

@Composable private fun NavRow(title: String, desc: String, onClick: () -> Unit) {
    val colors = seedless
    SettingSlab(onClick = onClick, external = externalFor(title, desc)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, desc, Modifier.weight(1f).padding(end = 12.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = colors.text3, modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable private fun TextFieldRow(title: String, desc: String, value: String, maxLen: Int, onChange: (String) -> Unit) {
    val colors = seedless
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))) }
    androidx.compose.runtime.LaunchedEffect(value) { if (field.text != value) field = field.copy(text = value) }
    SettingSlab {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowText(title, desc, Modifier.weight(1f))
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= maxLen || it.text.length < field.text.length) { field = it; onChange(it.text) } },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = Manrope, fontSize = 13.sp, color = colors.text,
                ),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface2,
                    unfocusedContainerColor = colors.surface2,
                    focusedIndicatorColor = colors.green,
                    unfocusedIndicatorColor = colors.line,
                ),
                modifier = Modifier.width(150.dp),
            )
        }
    }
}

private fun months(uiContext: android.content.Context) = listOf(uiContext.getString(R.string.settings_january), uiContext.getString(R.string.settings_february), uiContext.getString(R.string.settings_march), uiContext.getString(R.string.settings_april), uiContext.getString(R.string.settings_may), uiContext.getString(R.string.settings_june),
    uiContext.getString(R.string.settings_july), uiContext.getString(R.string.settings_august), uiContext.getString(R.string.settings_september), uiContext.getString(R.string.settings_october), uiContext.getString(R.string.settings_november), uiContext.getString(R.string.settings_december))
private fun daysInMonth(m: Int) = when (m) { 2 -> 29; 4, 6, 9, 11 -> 30; else -> 31 }

@Composable private fun MonthDayPickerRow(
    title: String, desc: String, month: Int, day: Int, onChange: (Int, Int) -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val monthNames = months(uiContext)
    var open by remember { mutableStateOf(false) }
    val mCur = month.coerceIn(1, 12)
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(18.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, desc, Modifier.weight(1f).padding(end = 12.dp))
        Text(uiContext.getString(R.string.birthday_month_day, monthNames[mCur - 1], day), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(120.dp))
    }
    if (open) {
        var m by remember { mutableStateOf(mCur) }
        var d by remember { mutableStateOf(day.coerceIn(1, daysInMonth(mCur))) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Row(Modifier.height(240.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val mScroll = rememberScrollState()
                    Column(Modifier.weight(1f)
                        .verticalScrollbar(mScroll, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        .verticalScroll(mScroll)) {
                        monthNames.forEachIndexed { i, name ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    m = i + 1; if (d > daysInMonth(m)) d = daysInMonth(m)
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = m == i + 1, onClick = {
                                    m = i + 1; if (d > daysInMonth(m)) d = daysInMonth(m)
                                })
                                Text(name, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    val dScroll = rememberScrollState()
                    Column(Modifier.width(96.dp)
                        .verticalScrollbar(dScroll, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        .verticalScroll(dScroll)) {
                        for (dd in 1..daysInMonth(m)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { d = dd }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = d == dd, onClick = { d = dd })
                                Text(dd.toString(), fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onChange(m, d); open = false }) { Text(uiContext.getString(R.string.common_ok)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(uiContext.getString(R.string.common_cancel)) } }
        )
    }
}

@Composable private fun DateTimeRow(
    title: String, desc: String, epochMillis: Long, enabled: Boolean, onChange: (Long) -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val ctx = LocalContext.current
    val label = if (epochMillis <= 0L) uiContext.getString(R.string.settings_not_set) else {
        val date = java.util.Date(epochMillis)
        uiContext.getString(R.string.date_time_value, android.text.format.DateFormat.getDateFormat(ctx).format(date),
            android.text.format.DateFormat.getTimeFormat(ctx).format(date))
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) {
            val c = java.util.Calendar.getInstance().apply { if (epochMillis > 0L) timeInMillis = epochMillis }
            android.app.DatePickerDialog(ctx, { _, y, mo, dy ->
                android.app.TimePickerDialog(ctx, { _, h, mi ->
                    val out = java.util.Calendar.getInstance()
                    out.set(y, mo, dy, h, mi, 0)
                    out.set(java.util.Calendar.MILLISECOND, 0)
                    onChange(out.timeInMillis)
                }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(ctx)).show()
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH),
                c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }.padding(18.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, desc, Modifier.weight(1f).padding(end = 12.dp))
        Text(label, color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(120.dp))
    }
}

/** Per-screen mask images (WatermelonDS-style backgrounds): each DS screen
 *  has its own slot, plus one for single-surface layouts. Picking copies the
 *  image into app storage; the settings value is the slot's file name. */
@Composable
fun MaskSettingsPage(s: DsSettings, set: (DsSettings) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pendingSlot by remember { mutableStateOf<ScreenMasks.Slot?>(null) }
    var importFailed by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri != null && slot != null) {
            if (ScreenMasks.importImage(ctx, slot, uri)) {
                importFailed = false
                set(s.withMask(slot, slot.fileName, s.maskMode(slot)))
            } else {
                importFailed = true
            }
        }
    }
    val modes = listOf(
        uiContext.getString(R.string.mask_mode_stretch),
        uiContext.getString(R.string.mask_mode_fit_center),
        uiContext.getString(R.string.mask_mode_fit_top),
        uiContext.getString(R.string.mask_mode_fit_left),
        uiContext.getString(R.string.mask_mode_fit_bottom),
        uiContext.getString(R.string.mask_mode_fit_right),
    )
    for (slot in ScreenMasks.Slot.entries) {
        val label = when (slot) {
            ScreenMasks.Slot.TOP -> uiContext.getString(R.string.mask_top)
            ScreenMasks.Slot.BOTTOM -> uiContext.getString(R.string.mask_bottom)
            ScreenMasks.Slot.SINGLE -> uiContext.getString(R.string.mask_single)
        }
        val isSet = s.maskFile(slot).isNotEmpty()
        Group(label)
        NavRow(
            uiContext.getString(R.string.mask_pick),
            uiContext.getString(if (isSet) R.string.mask_set else R.string.mask_unset),
        ) {
            pendingSlot = slot
            picker.launch(arrayOf("image/*"))
        }
        DropdownRow(uiContext.getString(R.string.mask_mode), "", modes, s.maskMode(slot).coerceIn(0, modes.lastIndex)) {
            set(s.withMask(slot, s.maskFile(slot), it))
        }
        if (isSet) {
            NavRow(uiContext.getString(R.string.mask_clear), "") {
                ScreenMasks.clear(ctx, slot)
                set(s.withMask(slot, "", s.maskMode(slot)))
            }
        }
    }
    if (importFailed) SectionNote(uiContext.getString(R.string.mask_import_failed))
    SectionNote(uiContext.getString(R.string.mask_note))
}
