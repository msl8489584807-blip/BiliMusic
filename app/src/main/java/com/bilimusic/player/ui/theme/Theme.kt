package com.bilimusic.player.ui.theme

import android.os.Build
import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bilimusic.player.data.AppSettings

private val DarkColors = darkColorScheme(primary=Color(0xFFF6BE62),onPrimary=Color(0xFF402C09),primaryContainer=Color(0xFF5A431E),secondary=Color(0xFF9BD2C9),secondaryContainer=Color(0xFF4D412C),onSecondaryContainer=Color(0xFFFFE0AD),background=Color(0xFF111110),surface=Color(0xFF111110),surfaceContainer=Color(0xFF1D1C19),surfaceContainerLow=Color(0xFF191814),surfaceContainerHigh=Color(0xFF292721),surfaceVariant=Color(0xFF282620),onBackground=Color(0xFFEEE7DB),onSurface=Color(0xFFEEE7DB),onSurfaceVariant=Color(0xFFCAC3B6))
private val LightColors = lightColorScheme(primary=Color(0xFF785500),onPrimary=Color.White,primaryContainer=Color(0xFFFFDFA6),secondary=Color(0xFF31695F),secondaryContainer=Color(0xFFFFE3AF),onSecondaryContainer=Color(0xFF402C09),background=Color(0xFFFFF8EE),surface=Color(0xFFFFF8EE),surfaceContainer=Color(0xFFF6ECD9),surfaceContainerLow=Color(0xFFFAF1E3),surfaceContainerHigh=Color(0xFFF1E4D1),surfaceVariant=Color(0xFFF1E4D1),onSurface=Color(0xFF211C14))
@Composable fun BiliMusicTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when(settings.theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val context=LocalContext.current
    SideEffect {
        var current:android.content.Context=context
        while(current is ContextWrapper && current !is Activity)current=current.baseContext
        (current as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
        )
    }
    val scheme = if(settings.dynamicColor && Build.VERSION.SDK_INT >= 31) { if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current) } else if(dark) DarkColors else LightColors
    MaterialTheme(colorScheme=scheme,typography=Typography(
        headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=32.sp,fontWeight=FontWeight.Bold),
        headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=24.sp,fontWeight=FontWeight.SemiBold),
        titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=21.sp,fontWeight=FontWeight.SemiBold),
        titleMedium=TextStyle(fontSize=16.sp,fontWeight=FontWeight.Medium),
        bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
        labelSmall=TextStyle(fontSize=11.sp,fontWeight=FontWeight.Medium)
    ),content=content)
}
