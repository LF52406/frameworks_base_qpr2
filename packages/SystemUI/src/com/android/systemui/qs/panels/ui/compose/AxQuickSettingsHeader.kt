/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.qs.panels.ui.compose

import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.SceneKey
import com.android.settingslib.Utils
import com.android.systemui.battery.BatteryMeterView
import com.android.systemui.battery.BatteryMeterViewController
import com.android.systemui.common.ui.compose.windowinsets.CutoutLocation
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.qs.panels.shared.model.AxQsLayoutPadding
import com.android.systemui.res.R
import com.android.systemui.shade.ui.composable.LocalStatusIconContext
import com.android.systemui.shade.ui.composable.ShadeHeader
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.ui.composable.BatteryWithEstimate
import com.android.systemui.statusbar.policy.Clock as ClockView
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIcons
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIconsLegacy
import kotlin.math.max

private val AxQuickSettingsHeaderContent = SceneKey("AxQuickSettingsHeader")
private val AxLandscapeStatusRowContent = SceneKey("AxLandscapeStatusRow")

@Composable
fun AxQuickSettingsHeader(
    viewModel: ShadeHeaderViewModel,
    isTransitioning: Boolean,
    modifier: Modifier = Modifier,
) {
    val foregroundColor = MaterialTheme.colorScheme.onSurface
    val clockStyle = rememberClockStyle()
    val clockHasEmbeddedDate = clockStyle in 1..3
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val landscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sidePadding =
            maxWidth *
                if (landscape) {
                    AxQuickSettingsLayoutDefaults.LANDSCAPE_SIDE_PADDING_FRACTION
                } else {
                    AxQuickSettingsLayoutDefaults.PORTRAIT_SIDE_PADDING_FRACTION
                }
        val startContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier.padding(start = sidePadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AxQuickSettingsClock(viewModel = viewModel, clockStyle = clockStyle)
                }
                if (!clockHasEmbeddedDate) {
                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AxQuickSettingsDate(viewModel = viewModel)
                    }
                }
            }
        }
        val endContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier.padding(end = sidePadding),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AxCarrierText(
                    viewModel = viewModel,
                    textColor = foregroundColor,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AxStatusIcons(
                        viewModel = viewModel,
                        isTransitioning = isTransitioning,
                        foregroundColor = foregroundColor.toArgb(),
                        backgroundColor = Color.Transparent.toArgb(),
                    )
                    val context = LocalContext.current
                    AxBatteryInfo(
                        viewModel = viewModel,
                        showIcon = true,
                        useExpandedFormat = false,
                        textColor = foregroundColor,
                        iconTint = foregroundColor,
                        iconBackgroundColor = Color.Transparent,
                        onClick = {
                            try {
                                val intent =
                                    Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                    )
                    if (viewModel.isPrivacyChipVisible) {
                        AxPrivacyChip(
                            privacyList = viewModel.privacyItems,
                            onClick = viewModel::onPrivacyChipClicked,
                        )
                    }
                }
            }
        }
        if (landscape) {
            val statusBarHeightDp = with(LocalDensity.current) { viewModel.statusBarHeightPx.toDp() }
            // Keep the clock/date and carrier/status icons in separate measured regions.
            // Independent overlaid boxes can collide on narrow landscape displays.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = statusBarHeightDp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                    startContent()
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                    endContent()
                }
            }
        } else {
            AxCutoutAwareShadeHeader(
                statusBarHeightPx = viewModel.statusBarHeightPx,
                modifier = Modifier.fillMaxWidth(),
                startContent = startContent,
                endContent = endContent,
            )
        }
    }
}

/**
 * Status information for the expanded LANDSCAPE control center.
 *
 * The original, two-line AxQuickSettingsHeader remains in QQS, where it is
 * faded out by QSFragmentCompose during expansion. This is an independent,
 * single-line header measured in the QS content column; it does not overlay
 * tiles, media, or notifications and deliberately contains no edit actions.
 *
 * Clock/date and cellular/Wi-Fi/battery use the existing live SystemUI views,
 * not polling timers or manufactured status icons.
 */
@Composable
fun AxLandscapeQuickSettingsStatusRow(
    viewModel: ShadeHeaderViewModel,
    isTransitioning: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val showCarrier = maxWidth >= 530.dp
        // Landscape phones can have a camera cutout on the LEFT or RIGHT.
        // The status row must respect physical cutout insets on both sides.
        val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val leftPadding = maxOf(12.dp, cutoutPadding.calculateLeftPadding(layoutDirection))
        val rightPadding = maxOf(12.dp, cutoutPadding.calculateRightPadding(layoutDirection))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(left = leftPadding, right = rightPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(0.44f).clipToBounds(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AxHeaderClock(
                    onClick = viewModel::onClockClicked,
                    textColor = foreground,
                    fontSizeSp = 16f,
                )
                AxQuickSettingsDate(
                    viewModel = viewModel,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.weight(0.56f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showCarrier) {
                    Box(
                        modifier = Modifier.weight(0.35f).clipToBounds(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        AxCarrierText(
                            viewModel = viewModel,
                            textColor = foreground,
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).clipToBounds(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    AxStatusIcons(
                        viewModel = viewModel,
                        isTransitioning = isTransitioning,
                        foregroundColor = foreground.toArgb(),
                        backgroundColor = Color.Transparent.toArgb(),
                        modifier = Modifier.fillMaxWidth(),
                        contentKey = AxLandscapeStatusRowContent,
                    )
                }
                AxBatteryInfo(
                    viewModel = viewModel,
                    showIcon = true,
                    useExpandedFormat = false,
                    textColor = foreground,
                    iconTint = foreground,
                    iconBackgroundColor = Color.Transparent,
                )
                if (viewModel.isPrivacyChipVisible) {
                    AxPrivacyChip(
                        privacyList = viewModel.privacyItems,
                        onClick = viewModel::onPrivacyChipClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun AxStatusIcons(
    viewModel: ShadeHeaderViewModel,
    isTransitioning: Boolean,
    @ColorInt foregroundColor: Int,
    @ColorInt backgroundColor: Int,
    modifier: Modifier = Modifier,
    contentKey: SceneKey = AxQuickSettingsHeaderContent,
) {
    if (SystemStatusIconsInCompose.isEnabled) {
        SystemStatusIcons(
            viewModelFactory = viewModel.systemStatusIconsViewModelFactory,
            systemStatusIconBlocklistInteractor = viewModel.systemStatusIconsBlockListInteractor,
            tint = Color(foregroundColor),
            modifier = modifier,
        )
    } else {
        val statusIconContext = LocalStatusIconContext.current
        val iconContainer = statusIconContext.iconContainer(contentKey).apply {
            // Do not replace an eighth icon with a dot if all icons actually fit.
            // Overflow remains enabled when the measured width is insufficient.
            setShouldRestrictIcons(false)
        }
        val iconManager = statusIconContext.iconManager(contentKey)
        val movableContent =
            remember(statusIconContext, iconManager) {
                statusIconContext.movableContent(iconManager)
            }
        val configuration = LocalConfiguration.current
        LaunchedEffect(configuration) {
            viewModel.statusBarIconController.refreshIconGroup(iconManager)
        }
        SystemStatusIconsLegacy(
            iconContainer = iconContainer,
            iconManager = iconManager,
            statusBarIconController = viewModel.statusBarIconController,
            // The carrier label is drawn separately. Preserve real mobile network
            // icons, including with two SIM subscriptions.
            useExpandedFormat = false,
            isTransitioning = isTransitioning,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            isSingleCarrier = viewModel.isSingleCarrier,
            isMicCameraIndicationEnabled = viewModel.isMicCameraIndicationEnabled,
            isPrivacyChipEnabled = viewModel.isPrivacyChipVisible,
            isLocationIndicationEnabled = viewModel.isLocationIndicationEnabled,
            modifier = modifier,
            content = movableContent,
        )
    }
}

@Composable
private fun AxPrivacyChip(
    privacyList: List<PrivacyItem>,
    onClick: (OngoingPrivacyChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            OngoingPrivacyChip(context, null).also { chip ->
                chip.privacyList = privacyList
                chip.setOnClickListener { onClick(chip) }
            }
        },
        update = { it.privacyList = privacyList },
        modifier = modifier,
    )
}

@Composable
fun AxCarrierText(
    viewModel: ShadeHeaderViewModel,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val carrierText = viewModel.carrierText?.toString()?.trim()
    if (!carrierText.isNullOrEmpty()) {
        Text(
            text = carrierText,
            color = textColor,
            textAlign = TextAlign.End,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier =
                modifier
                    .basicMarquee(iterations = 1)
                    .clickable { viewModel.onShadeCarrierGroupClicked() },
        )
    }
}

@Composable
fun rememberClockStyle(): Int {
    val context = LocalContext.current
    var clockStyle by remember {
        mutableIntStateOf(
            Settings.System.getIntForUser(
                context.contentResolver,
                "qs_header_clock_style",
                0,
                UserHandle.USER_CURRENT,
            )
        )
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                clockStyle = Settings.System.getIntForUser(
                    context.contentResolver,
                    "qs_header_clock_style",
                    0,
                    UserHandle.USER_CURRENT,
                )
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("qs_header_clock_style"),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return clockStyle
}

@Composable
fun AxQuickSettingsClock(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
    clockStyle: Int = rememberClockStyle(),
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    when (clockStyle) {
        0 -> AxHeaderClock(onClick = viewModel::onClockClicked, textColor = textColor, modifier = modifier)
        1 -> AxCustomClockView(layoutRes = R.layout.qs_header_clock_chip, onClick = viewModel::onClockClicked, modifier = modifier)
        2 -> AxCustomClockView(layoutRes = R.layout.qs_header_clock_oos, onClick = viewModel::onClockClicked, modifier = modifier)
        3 -> AxCustomClockView(layoutRes = R.layout.qs_header_clock_analog, onClick = viewModel::onClockClicked, modifier = modifier)
        else -> AxCustomClockView(layoutRes = R.layout.qs_header_clock_simple, onClick = viewModel::onClockClicked, modifier = modifier)
    }
}

@Composable
private fun AxCustomClockView(
    @LayoutRes layoutRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(layoutRes) {
        AndroidView(
            factory = { context ->
                val themedContext =
                    ContextThemeWrapper(context, R.style.Theme_SystemUI_QuickSettings_Header)
                val view = LayoutInflater.from(themedContext).inflate(layoutRes, null, false)
                view.setOnClickListener { onClick() }
                view
            },
            modifier = modifier.wrapContentWidth(unbounded = true).clickable(onClick = onClick),
        )
    }
}

@Composable
fun AxQuickSettingsDate(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    AxHeaderDate(
        longerDateText = viewModel.longerDateText,
        shorterDateText = viewModel.shorterDateText,
        textColor = MaterialTheme.colorScheme.onSurface,
        fontSizeSp = if (compact) 13f else AX_DATE_TEXT_SIZE,
        modifier =
            modifier.clickable {
                try {
                    val intent =
                        Intent.makeMainSelectorActivity(
                            Intent.ACTION_MAIN,
                            Intent.CATEGORY_APP_CALENDAR,
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
    )
}

@Composable
private fun AxHeaderClock(
    onClick: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = AX_CLOCK_TEXT_SIZE,
) {
    val configuration = LocalConfiguration.current
    key(configuration.densityDpi, configuration.fontScale) {
        AndroidView(
            factory = { context ->
                val clockEndPadding =
                    context.resources.getDimensionPixelSize(
                        R.dimen.status_bar_clock_end_padding
                    )
                ClockView(
                        ContextThemeWrapper(context, R.style.Theme_SystemUI_QuickSettings_Header),
                        null,
                    )
                    .apply {
                        isSingleLine = true
                        setIncludeFontPadding(true)
                        setUseBoundsForWidth(true)
                        setShiftDrawingOffsetForStartOverhang(true)
                        textDirection = View.TEXT_DIRECTION_LOCALE
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setTypeface(typeface, Typeface.NORMAL)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                        setPaddingRelative(0, 0, clockEndPadding, 0)
                    }
            },
            update = { view ->
                val clockEndPadding =
                    view.context.resources.getDimensionPixelSize(
                        R.dimen.status_bar_clock_end_padding
                    )
                view.setTextColor(textColor.toArgb())
                view.setTypeface(view.typeface, Typeface.NORMAL)
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                view.setPaddingRelative(0, 0, clockEndPadding, 0)
            },
            modifier = modifier.wrapContentWidth(unbounded = true).clickable(onClick = onClick),
        )
    }
}

@Composable
private fun AxHeaderDate(
    longerDateText: String,
    shorterDateText: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = AX_DATE_TEXT_SIZE,
) {
    val textStyle =
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSizeSp.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        )
    Layout(
        contents =
            listOf(
                {
                    Text(
                        text = longerDateText,
                        style = textStyle,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                },
                {
                    Text(
                        text = shorterDateText,
                        style = textStyle,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                },
            ),
        modifier = modifier,
    ) { measurables, constraints ->
        val longer = measurables[0][0]
        val shorter = measurables[1][0]
        val intrinsicHeight =
            if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val longerFits = longer.maxIntrinsicWidth(intrinsicHeight) <= constraints.maxWidth
        val shorterFits = shorter.maxIntrinsicWidth(intrinsicHeight) <= constraints.maxWidth
        val selected = if (longerFits) longer else shorter
        val selectedConstraints =
            if (!longerFits && !shorterFits) {
                constraints.copy(minWidth = 0, minHeight = 0)
            } else {
                constraints.copy(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
            }
        val placeable = selected.measure(selectedConstraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}

private const val AX_CLOCK_TEXT_SIZE = 22f
private const val AX_DATE_TEXT_SIZE = 18f

object AxQuickSettingsLayoutDefaults {
    const val PORTRAIT_SIDE_PADDING_FRACTION = AxQsLayoutPadding.PORTRAIT_SIDE_FRACTION
    const val LANDSCAPE_SIDE_PADDING_FRACTION = AxQsLayoutPadding.LANDSCAPE_SIDE_FRACTION
    val LandscapeGridSpacing = 16.dp
    val LandscapeSplitGridSpacing = AxQsLayoutPadding.LANDSCAPE_SPLIT_GRID_SPACING_DP.dp
    val LandscapeHeaderContentSpacing = 8.dp
}

@Composable
private fun AxCutoutAwareShadeHeader(
    statusBarHeightPx: Int,
    modifier: Modifier = Modifier,
    startContent: @Composable () -> Unit,
    endContent: @Composable () -> Unit,
) {
    val cutoutProvider = LocalDisplayCutout.current
    Layout(
        modifier = modifier.sysuiResTag(ShadeHeader.TestTags.Root),
        contents = listOf(startContent, endContent),
    ) { measurables, constraints ->
        val cutout = cutoutProvider()

        val cutoutWidth = cutout.width
        val cutoutHeight = cutout.height
        val cutoutTop = cutout.top
        val cutoutLocation = cutout.location

        check(constraints.hasBoundedWidth)
        check(measurables.size == 2)
        check(measurables[0].size == 1)
        check(measurables[1].size == 1)

        val screenWidth = constraints.maxWidth
        val height = max(cutoutHeight + (cutoutTop * 2), statusBarHeightPx)
        val sideWidth = (screenWidth - cutoutWidth) / 2
        val contentMaxWidth =
            when (cutoutLocation) {
                CutoutLocation.CENTER -> sideWidth
                CutoutLocation.NONE,
                CutoutLocation.LEFT,
                CutoutLocation.RIGHT -> screenWidth - cutoutWidth
            }
        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = contentMaxWidth.coerceAtLeast(0),
                minHeight = height,
                maxHeight = Constraints.Infinity,
            )

        val startMeasurable = measurables[0][0]
        val endMeasurable = measurables[1][0]

        val startPlaceable = startMeasurable.measure(childConstraints)
        val endPlaceable = endMeasurable.measure(childConstraints)

        val layoutHeight = max(height, max(startPlaceable.height, endPlaceable.height))

        layout(screenWidth, layoutHeight) {
            when (cutoutLocation) {
                CutoutLocation.NONE,
                CutoutLocation.RIGHT -> {
                    startPlaceable.placeRelative(x = 0, y = 0)
                    endPlaceable.placeRelative(
                        x = screenWidth - cutoutWidth - endPlaceable.width,
                        y = 0,
                    )
                }
                CutoutLocation.CENTER -> {
                    startPlaceable.placeRelative(x = 0, y = 0)
                    endPlaceable.placeRelative(
                        x = sideWidth + cutoutWidth + sideWidth - endPlaceable.width,
                        y = 0,
                    )
                }
                CutoutLocation.LEFT -> {
                    startPlaceable.placeRelative(x = cutoutWidth, y = 0)
                    endPlaceable.placeRelative(x = screenWidth - endPlaceable.width, y = 0)
                }
            }
        }
    }
}

@Composable
private fun AxBatteryInfo(
    viewModel: ShadeHeaderViewModel,
    showIcon: Boolean,
    useExpandedFormat: Boolean,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val batteryModifier =
        if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        }

    if (NewStatusBarIcons.isEnabled) {
        val useDarkTheme = iconTint?.let { it.luminance() > 0.5f }
        val staticAreaDark =
            remember(useDarkTheme) {
                useDarkTheme?.let { isDarkTheme -> IsAreaDark { isDarkTheme } }
            }
        BatteryWithEstimate(
            viewModelFactory = viewModel.batteryViewModelFactory,
            isDarkProvider = { staticAreaDark ?: viewModel.isShadeAreaDark },
            showIcon = showIcon,
            showEstimate = useExpandedFormat,
            textColor = textColor,
            modifier = batteryModifier.sysuiResTag(ShadeHeader.TestTags.BatteryTestTag),
        )
    } else {
        AxBatteryIconLegacy(
            createBatteryMeterViewController = viewModel.createBatteryMeterViewController,
            useExpandedFormat = useExpandedFormat,
            modifier = batteryModifier.sysuiResTag(ShadeHeader.TestTags.BatteryTestTag),
            isHighlighted = isHighlighted,
            foregroundColor = iconTint,
            backgroundColor = iconBackgroundColor,
        )
    }
}

@Composable
private fun AxBatteryIconLegacy(
    createBatteryMeterViewController: (ViewGroup, StatusBarLocation) -> BatteryMeterViewController,
    useExpandedFormat: Boolean,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    foregroundColor: Color? = null,
    backgroundColor: Color? = null,
) {
    NewStatusBarIcons.assertInLegacyMode()

    val localContext = LocalContext.current
    val themedContext =
        ContextThemeWrapper(localContext, R.style.Theme_SystemUI_QuickSettings_Header)
    val primaryColor =
        Utils.getColorAttrDefaultColor(themedContext, android.R.attr.textColorPrimary)
    val inverseColor =
        Utils.getColorAttrDefaultColor(themedContext, android.R.attr.textColorPrimaryInverse)
    val foregroundColorArgb = foregroundColor?.toArgb() ?: primaryColor
    val backgroundColorArgb = backgroundColor?.toArgb() ?: inverseColor
    val singleToneColorArgb =
        foregroundColor?.toArgb() ?: if (isHighlighted) inverseColor else primaryColor

    AndroidView(
        factory = { context ->
            val batteryIcon = BatteryMeterView(context, null)
            batteryIcon.setPercentShowMode(BatteryMeterView.MODE_ON)
            batteryIcon.updateColors(foregroundColorArgb, backgroundColorArgb, singleToneColorArgb)

            val batteryMaterViewController =
                createBatteryMeterViewController(batteryIcon, StatusBarLocation.QS)
            batteryMaterViewController.init()
            batteryMaterViewController.ignoreTunerUpdates()

            batteryIcon
        },
        update = { batteryIcon ->
            batteryIcon.updateColors(
                foregroundColorArgb,
                backgroundColorArgb,
                singleToneColorArgb,
            )
        },
        modifier = modifier,
    )
}
