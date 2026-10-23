package com.kuet.gymtest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import com.kuet.gymtest.ui.components.GLASS_BACKGROUND
import com.kuet.gymtest.ui.components.AppBackground
import com.kuet.gymtest.ui.components.GlassBackdropProvider
import com.kuet.gymtest.ui.components.GlassCard
import com.kuet.gymtest.ui.components.LocalGlassBackdrop
import com.kuet.gymtest.ui.theme.GymTestTheme
import com.kuet.gymtest.ui.theme.Ivory
import com.kuet.gymtest.ui.theme.MutedInk
import dev.chrisbanes.haze.hazeSource
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.math.sqrt

/** High-frequency backdrop distinguishes real blur from a low-opacity white fill. */
class GlassBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun appBackgroundIsSolidIvory() {
        compose.setContent { GymTestTheme { AppBackground {} } }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        for (fractionX in listOf(.1f, .5f, .9f)) {
            for (fractionY in listOf(.1f, .5f, .9f)) {
                val color = pixels[(pixels.width * fractionX).toInt(), (pixels.height * fractionY).toInt()]
                assertEquals(Ivory.red, color.red, .01f)
                assertEquals(Ivory.green, color.green, .01f)
                assertEquals(Ivory.blue, color.blue, .01f)
            }
        }
    }

    @Test
    fun neutralCardsAreWarmAndDeeperThanTheSolidBackground() {
        compose.setContent {
            GymTestTheme {
                AppBackground {
                    GlassCard(Modifier.align(Alignment.Center).size(220.dp, 160.dp).testTag("warm-card")) {
                        Text("Warm glass")
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("warm-card").captureToImage().toPixelMap()
        val color = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue("Card should be warm beige, not white", color.red > color.green && color.green > color.blue)
        assertTrue("Card should be deeper than ivory", color.green < Ivory.green - .02f && color.blue < Ivory.blue - .03f)
        val contrast = (color.luminance() + .05f) / (MutedInk.luminance() + .05f)
        assertTrue("Supporting text must retain readable contrast", contrast >= 4.5f)
    }

    @Test
    @SdkSuppress(minSdkVersion = 32)
    fun glassBlursBackdropWithoutObscuringForeground() {
        compose.setContent {
            GymTestTheme {
                GlassBackdropProvider {
                    val state = checkNotNull(LocalGlassBackdrop.current)
                    Box(Modifier.size(320.dp).testTag("backdrop")) {
                        Canvas(Modifier.fillMaxSize().hazeSource(state, key = GLASS_BACKGROUND)) {
                            val stripe = 8.dp.toPx()
                            var x = 0f
                            var index = 0
                            while (x < size.width) {
                                drawRect(
                                    if (index % 2 == 0) Color.Black else Color.White,
                                    topLeft = Offset(x, 0f), size = Size(stripe, size.height)
                                )
                                x += stripe
                                index++
                            }
                        }
                        GlassCard(
                            Modifier.align(Alignment.Center).size(220.dp),
                            padding = PaddingValues(16.dp)
                        ) {
                            Text("Sharp foreground", color = Color.Black, modifier = Modifier.testTag("label"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("backdrop").captureToImage().toPixelMap()
        fun samples(y: Int) = (pixels.width / 4 until pixels.width * 3 / 4).map { x ->
            val color = pixels[x, y]
            (color.red + color.green + color.blue) / 3.0
        }
        fun deviation(values: List<Double>): Double {
            val mean = values.average()
            return sqrt(values.map { (it - mean) * (it - mean) }.average())
        }
        val outside = samples(pixels.height / 10)
        val inside = samples(pixels.height * 2 / 3)
        assertTrue("Backdrop stripes must be visible outside the card", deviation(outside) > .4)
        assertTrue("Glass must blur stripes, not merely tint them", deviation(inside) < deviation(outside) * .3)
        assertTrue("Glass must retain the backdrop, not paint opaque white", inside.average() in 0.3..0.85)

        val label = compose.onNodeWithTag("label").captureToImage().toPixelMap()
        val darkest = (0 until label.height).minOf { y ->
            (0 until label.width).minOf { x -> label[x, y].red }
        }
        assertTrue("Foreground text must remain crisp black", darkest < .08f)
    }
}
