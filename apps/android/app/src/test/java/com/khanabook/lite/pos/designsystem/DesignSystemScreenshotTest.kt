package com.khanabook.lite.pos.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.khanabook.lite.pos.core.designsystem.KhanaBookCard
import com.khanabook.lite.pos.core.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.designsystem.KhanaDestructiveButton
import com.khanabook.lite.pos.core.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaStatusBadge
import com.khanabook.lite.pos.core.designsystem.KhanaStatusKind
import com.khanabook.lite.pos.core.designsystem.StatItem
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Host-side screenshot tests for the KhanaBook design system.
 *
 * Each test renders a component inside the real [KhanaBookLiteTheme] and writes a PNG to
 * `app/src/test/screenshots/`. Those PNGs are committed: they are the only automated
 * enforcement of `md/docs/meta/ANDROID_UI_RULES.md` and `md/docs/meta/AGENTS.md` (dark-only
 * palette, gold accents, token spacing, KhanaRadii shapes, type tiers).
 *
 *   record goldens : ./gradlew.bat :app:recordRoborazziDebug
 *   verify         : ./gradlew.bat :app:verifyRoborazziDebug
 *   diff report    : app/build/reports/roborazzi/index.html
 *
 * A failing verify means the UI changed. If the change was intended, re-record and commit
 * the new PNGs in the same commit as the code change — never re-record to silence a
 * regression you did not read.
 *
 * Rendering caveats: Poppins is a Google-Fonts downloadable font and is not available to
 * Robolectric, so goldens show the platform fallback typeface. These tests lock layout,
 * colour, spacing, shape and state — not glyph metrics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DesignSystemScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            KhanaBookLiteTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBrown1) {
                    Column(
                        modifier = Modifier.padding(KhanaBookTheme.spacing.large),
                        verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.medium)
                    ) {
                        content()
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun card_states() = capture("card_states") {
        KhanaBookCard(onClick = {}) {
            Column(modifier = Modifier.padding(KhanaBookTheme.spacing.medium)) {
                Text(
                    text = "Butter Chicken",
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "\u20B9320 \u00B7 Non-veg",
                    color = TextGold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        // Non-clickable variant: no pressed-scale, no ripple target.
        KhanaBookCard {
            Column(modifier = Modifier.padding(KhanaBookTheme.spacing.medium)) {
                Text(
                    text = "Static card content",
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Test
    fun input_field_states() = capture("input_field_states") {
        KhanaBookInputField(
            value = "",
            onValueChange = {},
            label = "Restaurant name",
            placeholder = "e.g. Spice Route"
        )
        KhanaBookInputField(
            value = "98765",
            onValueChange = {},
            label = "Phone number",
            isError = true,
            supportingText = { Text("Enter a valid 10-digit number") }
        )
    }

    @Test
    fun switch_states() = capture("switch_states") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KhanaBookSwitch(checked = false, onCheckedChange = {})
            KhanaBookSwitch(checked = true, onCheckedChange = {})
        }
    }

    @Test
    fun button_states() = capture("button_states") {
        KhanaPrimaryButton(text = "Save bill", onClick = {})
        KhanaPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
        KhanaSecondaryButton(text = "Cancel", onClick = {})
        KhanaDestructiveButton(text = "Delete item", onClick = {})
    }

    @Test
    fun status_badge_states() = capture("status_badge_states") {
        KhanaStatusKind.entries.forEach { kind ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small)
            ) {
                KhanaStatusBadge(text = kind.name, kind = kind)
                KhanaStatusBadge(text = kind.name, kind = kind, filled = false)
            }
        }
    }

    @Test
    fun stat_item_states() = capture("stat_item_states") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "Bills", value = "42")
            StatItem(label = "Sales", value = "\u20B912,480", large = true)
            StatItem(label = "Average", value = "\u20B9297")
        }
    }
}
