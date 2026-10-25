package com.campusfinance.wallet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BrandingUiTest:TeaUiScenario() {
    @Test fun updatedBrandHasAnAdaptiveLauncherIconAndAThemedIconWhileExistingLedgerStillOpens() {
        ready()
        assertEquals("等风",context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        ui.onNodeWithText("等风").assertIsDisplayed();shot("dengfeng-home")
        ui.onNodeWithText("CampusFinance").assertDoesNotExist()
        val icon=context.packageManager.getApplicationIcon(context.applicationInfo)
        assertTrue(icon is AdaptiveIconDrawable)
        if(Build.VERSION.SDK_INT>=33)assertNotNull((icon as AdaptiveIconDrawable).monochrome)
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,512,512);icon.draw(Canvas(bitmap))
        val file=File(context.getExternalFilesDir(null),"screenshots/dengfeng-launcher.png")
        file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        navigate("记账");tapTag("leaf-SETTINGS");tap("共同设计与免责声明")
        find(hasText("等风 0.11.0 体验版"));ui.onNodeWithText("梨花怎黛玉").assertIsDisplayed();shot("dengfeng-about")
    }
}
