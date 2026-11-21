package dev.arun.chargecatch.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.arun.chargecatch.R

/**
 * Google Sans Flex (SIL Open Font License, bundled in res/font; license text in
 * assets/licenses). It is one variable font, so each weight is the same file at a different
 * "wght" setting.
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSans: FontFamily = FontFamily(
    listOf(250, 300, 400, 500, 600, 700).map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight))
        )
    }
)

/**
 * The same font pushed to its chunkiest: heaviest weight, fully rounded corners, a little wider,
 * display optical size. Used for the yellow-and-blue "Poké" clock.
 */
@OptIn(ExperimentalTextApi::class)
val PokeDisplay: FontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(1000),
            FontVariation.Setting("ROND", 100f),
            FontVariation.width(104f),
            FontVariation.Setting("opsz", 144f)
        )
    )
)

/** Material's type scale, set in Google Sans Flex. */
val AppTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = GoogleSans),
        displayMedium = t.displayMedium.copy(fontFamily = GoogleSans),
        displaySmall = t.displaySmall.copy(fontFamily = GoogleSans),
        headlineLarge = t.headlineLarge.copy(fontFamily = GoogleSans),
        headlineMedium = t.headlineMedium.copy(fontFamily = GoogleSans),
        headlineSmall = t.headlineSmall.copy(fontFamily = GoogleSans),
        titleLarge = t.titleLarge.copy(fontFamily = GoogleSans),
        titleMedium = t.titleMedium.copy(fontFamily = GoogleSans),
        titleSmall = t.titleSmall.copy(fontFamily = GoogleSans),
        bodyLarge = t.bodyLarge.copy(fontFamily = GoogleSans),
        bodyMedium = t.bodyMedium.copy(fontFamily = GoogleSans),
        bodySmall = t.bodySmall.copy(fontFamily = GoogleSans),
        labelLarge = t.labelLarge.copy(fontFamily = GoogleSans),
        labelMedium = t.labelMedium.copy(fontFamily = GoogleSans),
        labelSmall = t.labelSmall.copy(fontFamily = GoogleSans)
    )
}
