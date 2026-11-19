package io.github.akiig.moodlog.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** The same colours as Moodlog on the PC (checked for colour-blind readability there). */
object Palette {
    private val moodLight = mapOf(1 to 0xffcf3f3f, 2 to 0xffeb9a94, 3 to 0xffbdbbb3, 4 to 0xff8fbcf0, 5 to 0xff2a78d6)
    private val moodDark = mapOf(1 to 0xffe05252, 2 to 0xffeb9a94, 3 to 0xffcfcdc5, 4 to 0xff8fbcf0, 5 to 0xff3987e5)

    @Composable
    @ReadOnlyComposable
    fun mood(value: Int): Color {
        val map = if (isSystemInDarkTheme()) moodDark else moodLight
        return Color(map[value] ?: map[3]!!)
    }

    fun faceInk(value: Int): Color = if (value == 1 || value == 5) Color.White else Color(0xff1b1b1b)

    val energy @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xffc08418) else Color(0xffb87c10)
    val anxiety @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xff9a7ef0) else Color(0xff7b5ad6)
}

@Composable
fun MoodlogTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xff8fbcf0))
        else -> lightColorScheme(primary = Color(0xff2a78d6))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
