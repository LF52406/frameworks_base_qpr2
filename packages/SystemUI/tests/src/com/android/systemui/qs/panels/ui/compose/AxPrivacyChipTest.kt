/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType.TYPE_CAMERA
import com.android.systemui.privacy.PrivacyType.TYPE_MICROPHONE
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AxPrivacyChipTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()

    private val application = PrivacyApplication("com.android.systemui.test", 10000)
    private val microphone = PrivacyItem(TYPE_MICROPHONE, application)
    private val camera = PrivacyItem(TYPE_CAMERA, application)
    private var chipHeight = 0.dp

    @Test
    fun boundedHeader_privacyChangesKeepTilesVisible() {
        val items = mutableStateOf(emptyList<PrivacyItem>())
        composeRule.setContent { TestHeader(items.value) }

        // Both QS styles use this chip; landscape supplies a finite panel height.
        for (privacyItems in listOf(listOf(microphone), listOf(camera), listOf(camera, microphone))) {
            composeRule.runOnIdle { items.value = privacyItems }
            assertCompactHeader()
            composeRule.runOnIdle { items.value = emptyList() }
            composeRule.onNodeWithTag(HEADER).assertHeightIsEqualTo(36.dp)
            composeRule.onNodeWithTag(TILES).assertIsDisplayed()
        }
    }

    @Test
    fun switchingBetweenBoundedAndUnboundedHeight_keepsChipAtContentHeight() {
        val unboundedHeight = mutableStateOf(false)
        composeRule.setContent {
            TestHeader(listOf(camera, microphone), unboundedHeight = unboundedHeight.value)
        }

        // Portrait's custom header measures children with an unbounded max height.
        for (unbounded in listOf(false, true, false)) {
            composeRule.runOnIdle { unboundedHeight.value = unbounded }
            assertCompactHeader()
        }
    }

    @Test
    fun compactChip_stillDeliversClickAndCurrentPrivacyItems() {
        val items = mutableStateOf(listOf(microphone))
        var clickedChip: OngoingPrivacyChip? = null
        composeRule.setContent { TestHeader(items.value, onClick = { clickedChip = it }) }
        composeRule.runOnIdle { items.value = listOf(camera, microphone) }

        composeRule.onNodeWithTag(CHIP).performTouchInput { click() }

        composeRule.runOnIdle {
            val chip = checkNotNull(clickedChip)
            assertThat(chip.privacyList).containsExactly(camera, microphone)
            assertThat(chip.iconsContainer.childCount).isEqualTo(2)
            assertThat(chip.contentDescription?.toString()).isNotEmpty()
        }
        assertCompactHeader()
    }

    private fun assertCompactHeader() {
        composeRule.onNodeWithTag(CHIP).assertHeightIsEqualTo(chipHeight)
        composeRule.onNodeWithTag(HEADER).assertHeightIsEqualTo(maxOf(36.dp, chipHeight))
        composeRule.onNodeWithTag(TILES).assertHeightIsEqualTo(48.dp).assertIsDisplayed()
    }

    @Composable
    private fun TestHeader(
        items: List<PrivacyItem>,
        unboundedHeight: Boolean = false,
        onClick: (OngoingPrivacyChip) -> Unit = {},
    ) {
        chipHeight = dimensionResource(R.dimen.ongoing_appops_chip_height)
        Column(Modifier.width(360.dp).height(240.dp)) {
            val contentModifier =
                if (unboundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(contentModifier) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).testTag(HEADER),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp))
                    Spacer(Modifier.weight(1f))
                    if (items.isNotEmpty()) {
                        AxPrivacyChip(items, onClick, Modifier.testTag(CHIP))
                    }
                }
                Box(Modifier.fillMaxWidth().height(48.dp).testTag(TILES))
            }
        }
    }

    private companion object {
        const val HEADER = "header"
        const val CHIP = "privacy_chip"
        const val TILES = "tiles"
    }
}
