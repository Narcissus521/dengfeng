package com.campusfinance.wallet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");
    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class WalletColors(
    val dark: Boolean, val primary: Color, val ink: Color, val muted: Color,
    val page: Color, val card: Color, val lime: Color, val pale: Color,
    val amber: Color, val warning: Color, val red: Color, val blue: Color,
    val hero: Color, val promo: Color, val scheme: ColorScheme
)

private val lightScheme = lightColorScheme(
    primary=Color(0xFF863E4C),onPrimary=Color(0xFFFFFAF5),secondary=Color(0xFF706B5F),onSecondary=Color(0xFFFFFDF6),
    primaryContainer=Color(0xFFE9DADD),onPrimaryContainer=Color(0xFF68303D),secondaryContainer=Color(0xFFECE9E1),onSecondaryContainer=Color(0xFF47443C),
    tertiary=Color(0xFF526D85),onTertiary=Color(0xFFF7FAFC),tertiaryContainer=Color(0xFFE4EBF0),onTertiaryContainer=Color(0xFF304A62),
    background=Color(0xFFF2F1EB),onBackground=Color(0xFF343438),surface=Color(0xFFFFFDF6),onSurface=Color(0xFF343438),
    surfaceVariant=Color(0xFFE8E5DC),onSurfaceVariant=Color(0xFF625F56),surfaceDim=Color(0xFFDEDAD0),surfaceBright=Color(0xFFFFFDF8),
    surfaceContainerLowest=Color(0xFFFFFDF8),surfaceContainerLow=Color(0xFFF7F5EE),surfaceContainer=Color(0xFFEEECE4),
    surfaceContainerHigh=Color(0xFFE7E4DB),surfaceContainerHighest=Color(0xFFDDD9CE),surfaceTint=Color(0xFF863E4C),
    inverseSurface=Color(0xFF3E4042),inverseOnSurface=Color(0xFFF4F3EF),inversePrimary=Color(0xFFD8A4B0),
    outline=Color(0xFF918A7D),outlineVariant=Color(0xFFCCC6B8),error=Color(0xFFAC3D32),onError=Color(0xFFFFFAF6),
    errorContainer=Color(0xFFFFDAD3),onErrorContainer=Color(0xFF410600))
private val darkScheme = darkColorScheme(
    primary=Color(0xFFD8A4B0),onPrimary=Color(0xFF44232C),secondary=Color(0xFFCBC3AF),onSecondary=Color(0xFF353126),
    primaryContainer=Color(0xFF59404A),onPrimaryContainer=Color(0xFFF5D9E0),secondaryContainer=Color(0xFF403D35),onSecondaryContainer=Color(0xFFE5DFCE),
    tertiary=Color(0xFFB2C9DE),onTertiary=Color(0xFF24394D),tertiaryContainer=Color(0xFF34495C),onTertiaryContainer=Color(0xFFDDEAF5),
    background=Color(0xFF242523),onBackground=Color(0xFFECE8DD),surface=Color(0xFF36342E),onSurface=Color(0xFFECE8DD),
    surfaceVariant=Color(0xFF48443A),onSurfaceVariant=Color(0xFFC8C0B0),surfaceDim=Color(0xFF242320),surfaceBright=Color(0xFF454137),
    surfaceContainerLowest=Color(0xFF22221F),surfaceContainerLow=Color(0xFF2D2C27),surfaceContainer=Color(0xFF35322C),
    surfaceContainerHigh=Color(0xFF403B33),surfaceContainerHighest=Color(0xFF4B453A),surfaceTint=Color(0xFFD8A4B0),
    inverseSurface=Color(0xFFECE8DD),inverseOnSurface=Color(0xFF343438),inversePrimary=Color(0xFF863E4C),
    outline=Color(0xFFA59A86),outlineVariant=Color(0xFF645D4E),error=Color(0xFFFFB4A8),onError=Color(0xFF650F08),
    errorContainer=Color(0xFF702B23),onErrorContainer=Color(0xFFFFDAD3))
private val lightColors=WalletColors(false,lightScheme.primary,lightScheme.onSurface,Color(0xFF625F56),
    lightScheme.background,lightScheme.surface,Color(0xFFE9DADD),Color(0xFFECE9E1),Color(0xFF8A5C17),Color(0xFFFFF3DF),
    lightScheme.error,lightScheme.tertiary,Color(0xFFE4E0D6),Color(0xFFF0E4E4),lightScheme)
private val darkColors=WalletColors(true,darkScheme.primary,darkScheme.onSurface,Color(0xFFC8C0B0),
    darkScheme.background,darkScheme.surface,Color(0xFF59404A),Color(0xFF403D35),Color(0xFFF0CB8A),Color(0xFF3A3020),
    darkScheme.error,darkScheme.tertiary,Color(0xFF302F2A),Color(0xFF4A373D),darkScheme)

val LocalWalletColors = staticCompositionLocalOf { lightColors }
val Forest: Color @Composable get() = LocalWalletColors.current.primary
val Ink: Color @Composable get() = LocalWalletColors.current.ink
val Muted: Color @Composable get() = LocalWalletColors.current.muted
val Page: Color @Composable get() = LocalWalletColors.current.page
val Card: Color @Composable get() = LocalWalletColors.current.card
val Lime: Color @Composable get() = LocalWalletColors.current.lime
val Pale: Color @Composable get() = LocalWalletColors.current.pale
val Amber: Color @Composable get() = LocalWalletColors.current.amber
val Red: Color @Composable get() = LocalWalletColors.current.red
val Blue: Color @Composable get() = LocalWalletColors.current.blue

private tailrec fun Context.activity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable fun WindowAppearance(window: Window?) {
    val colors=LocalWalletColors.current
    SideEffect {
        window?.let {
            it.statusBarColor=(if(colors.dark)Color(0xFF303334) else Color(0xFF47494B)).toArgb()
            if(Build.VERSION.SDK_INT>=29)it.isStatusBarContrastEnforced=false
            // Android 8.0 only supports white navigation icons.
            it.navigationBarColor=if(Build.VERSION.SDK_INT<27)Color(0xFF20262E).toArgb() else colors.card.toArgb()
            if(Build.VERSION.SDK_INT<27) {
                val legacyWindow=it
                it.decorView.post {
                    legacyWindow.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                    legacyWindow.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    legacyWindow.navigationBarColor=Color(0xFF20262E).toArgb()
                }
            }
            WindowInsetsControllerCompat(it,it.decorView).apply {
                isAppearanceLightStatusBars=false
                isAppearanceLightNavigationBars=!colors.dark && Build.VERSION.SDK_INT>=27
            }
        }
    }
}

@Composable fun WalletTheme(content: @Composable () -> Unit) {
    val systemDark=isSystemInDarkTheme()
    val dark=when(LocalDisplayState.current.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors=if(dark)darkColors else lightColors
    val window=LocalContext.current.activity()?.window
    CompositionLocalProvider(LocalWalletColors provides colors) {
        WindowAppearance(window)
        MaterialTheme(colorScheme=colors.scheme,typography=Typography(), shapes=Shapes(extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(4.dp),small=androidx.compose.foundation.shape.RoundedCornerShape(6.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) {
            Surface(Modifier.fillMaxSize().testTag(if(dark)"theme-dark" else "theme-light"),color=Color.Transparent,contentColor=colors.ink) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    // Android 15+ draws transparent status bars: paint the actual inset without moving content.
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).ledgerHeader())
                }
            }
        }
    }
}
