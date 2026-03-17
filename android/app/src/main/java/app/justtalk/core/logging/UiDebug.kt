package app.justtalk.core.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

object UiDebug {
    @Composable
    fun logScreenOnce(tag: String) {
        val cfg = LocalConfiguration.current
        val density = LocalDensity.current.density
        LaunchedEffect(tag, cfg.screenWidthDp, cfg.screenHeightDp, cfg.orientation, density) {
            AppLog.i(
                "UI",
                "screen=$tag wDp=${cfg.screenWidthDp} hDp=${cfg.screenHeightDp} orient=${cfg.orientation} density=${density}"
            )
        }
    }
}

fun Modifier.logLayout(tag: String): Modifier = composed {
    val density = LocalDensity.current.density
    this.onGloballyPositioned { coords ->
        val wPx = coords.size.width
        val hPx = coords.size.height
        val wDp = (wPx / density).roundToInt()
        val hDp = (hPx / density).roundToInt()
        AppLog.d("UI", "layout=$tag sizePx=${wPx}x${hPx} sizeDp=${wDp}x${hDp}")
    }
}

