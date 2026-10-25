package com.campusfinance.wallet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    primary=Color(0xFF5F7086), onPrimary=Color.White,
    secondary=Color(0xFF5F7086), onSecondary=Color.White,
    secondaryContainer=Color(0xFFF0F4ED), onSecondaryContainer=Color(0xFF50634D),
    primaryContainer=Color(0xFFE3EBDD), onPrimaryContainer=Color(0xFF354B68),
    tertiary=Color(0xFF577DAA), onTertiary=Color.White,
    tertiaryContainer=Color(0xFFE3EDFA), onTertiaryContainer=Color(0xFF334C6C),
    background=Color(0xFFF0F2ED), onBackground=Color(0xFF303D3B),
    surface=Color.White, onSurface=Color(0xFF303D3B),
    surfaceVariant=Color(0xFFE8EDF3), onSurfaceVariant=Color(0xFF596879),
    surfaceDim=Color(0xFFDDE3EB), surfaceBright=Color(0xFFFAFCFF),
    surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFF4F7FB),
    surfaceContainer=Color(0xFFEEF2F7), surfaceContainerHigh=Color(0xFFE8EDF3),
    surfaceContainerHighest=Color(0xFFE2E8F0),
    surfaceTint=Color(0xFF5F7086), inverseSurface=Color(0xFF303D3B),
    inverseOnSurface=Color(0xFFF0F4FA), inversePrimary=Color(0xFFBDCDE2),
    outline=Color(0xFF8492A3), outlineVariant=Color(0xFFDCE4DA),
    error=Color(0xFFB13F34), onError=Color.White,
    errorContainer=Color(0xFFFFDAD3), onErrorContainer=Color(0xFF410600)
)
private val darkScheme = darkColorScheme(
    primary=Color(0xFFBDCDE2), onPrimary=Color(0xFF253344),
    secondary=Color(0xFFBDCDE2), onSecondary=Color(0xFF253344),
    secondaryContainer=Color(0xFF30383C), onSecondaryContainer=Color(0xFFD6E3CC),
    primaryContainer=Color(0xFF3B4447), onPrimaryContainer=Color(0xFFE3EBDD),
    tertiary=Color(0xFFACC8EA), onTertiary=Color(0xFF233B59),
    tertiaryContainer=Color(0xFF334A67), onTertiaryContainer=Color(0xFFDCEAFF),
    background=Color(0xFF111A1C), onBackground=Color(0xFFE8EDE9),
    surface=Color(0xFF293938), onSurface=Color(0xFFE8EDE9),
    surfaceVariant=Color(0xFF434D5B), onSurfaceVariant=Color(0xFFBAC6BE),
    surfaceDim=Color(0xFF1B1E20), surfaceBright=Color(0xFF3B424C),
    surfaceContainerLowest=Color(0xFF111418), surfaceContainerLow=Color(0xFF20242A),
    surfaceContainer=Color(0xFF292F37), surfaceContainerHigh=Color(0xFF323A44),
    surfaceContainerHighest=Color(0xFF3D4652),
    surfaceTint=Color(0xFFBDCDE2), inverseSurface=Color(0xFFE8EDE9),
    inverseOnSurface=Color(0xFF303D3B), inversePrimary=Color(0xFF5F7086),
    outline=Color(0xFF8E9BAB), outlineVariant=Color(0xFF475158),
    error=Color(0xFFFFB4A8), onError=Color(0xFF650F08),
    errorContainer=Color(0xFF85251B), onErrorContainer=Color(0xFFFFDAD3)
)
private val lightColors = WalletColors(false,lightScheme.primary,lightScheme.onSurface,Color(0xFF465C55),
    lightScheme.background,lightScheme.surface,lightScheme.primaryContainer,lightScheme.secondaryContainer,
    Color(0xFF8A5C17),Color(0xFFFFF3DF),lightScheme.error,lightScheme.tertiary,
    Color(0xFFECF1EB),Color(0xFFE3EBDD),lightScheme)
private val darkColors = WalletColors(true,darkScheme.primary,darkScheme.onSurface,Color(0xFFBAC6BE),
    darkScheme.background,darkScheme.surface,darkScheme.primaryContainer,darkScheme.secondaryContainer,
    Color(0xFFF0CB8A),Color(0xFF3A3020),darkScheme.error,darkScheme.tertiary,
    Color(0xFF333A3D),Color(0xFF3B4447),darkScheme)

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
            it.statusBarColor=colors.page.toArgb()
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
                isAppearanceLightStatusBars=!colors.dark
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
        MaterialTheme(colorScheme=colors.scheme,typography=Typography()) {
            OpticalBackdrop { Surface(Modifier.fillMaxSize().testTag(if(dark)"theme-dark" else "theme-light"),color=Color.Transparent,contentColor=colors.ink,content=content) }
        }
    }
}
