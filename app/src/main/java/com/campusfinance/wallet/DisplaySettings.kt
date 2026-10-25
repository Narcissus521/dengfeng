package com.campusfinance.wallet

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.Window
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object RefreshPolicy {
    /** A preference, not a forced display mode. Keep the current resolution. */
    fun preferredRate(rates: List<Float>, enabled: Boolean, powerSave: Boolean): Float =
        if (!enabled || powerSave) 0f else rates.filter { it.isFinite() && it >= 1f && it <= 120.5f }.maxOrNull()?.coerceAtMost(120f) ?: 0f
}
data class DisplayState(
    val highRefresh: Boolean = true, val animations: Boolean = true,
    val currentHz: Float = 0f, val availableHz: List<Float> = emptyList(), val requestedHz: Float = 0f,
    val powerSave: Boolean = false, val systemAnimations: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val layoutSize: LayoutSize = LayoutSize.STANDARD, val layoutPercent: Int = 100,
    val cardSizes: Map<OverviewCard,Int> = emptyMap(),
    val cardGrids: Map<OverviewCard,CardGridLayout> = emptyMap()
) {
    val motionEnabled get() = animations && systemAnimations
    fun duration(ms: Int) = if (motionEnabled) ms else 0
}
fun hzText(rate: Float): String = if (rate <= 0f) "未知" else String.format(Locale.CHINA,"%.1f Hz",rate)
val LocalDisplayState = staticCompositionLocalOf { DisplayState() }
val LocalDisplayController = staticCompositionLocalOf<DisplayController?> { null }

class DisplayController(private val context: Context, private val window: Window) {
    private val prefs = context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private fun readLayoutSize() = LayoutSize.from(prefs.all["layout_size"] as? String)
    private fun readLayoutPercent(size:LayoutSize) = LayoutPolicy.percent(size,(prefs.all["layout_percent"] as? Number)?.toInt() ?: 100)
    private fun readCardSizes():Map<OverviewCard,Int> {
        val values=prefs.all
        return OverviewCard.entries.mapNotNull { card -> (values["card_size_${card.name}"] as? Number)?.toInt()?.let {
            card to it.coerceIn(LayoutPolicy.MIN_PERCENT,LayoutPolicy.MAX_PERCENT)
        } }.toMap()
    }
    private fun readCardGrids():Map<OverviewCard,CardGridLayout> {
        val values=prefs.all
        return OverviewCard.entries.mapNotNull { card ->
            val key="card_grid_${card.name}_"
            val w=(values[key+"w"] as? Number)?.toInt()
            val h=(values[key+"h"] as? Number)?.toInt()
            if(w==null||h==null)null else card to CardGridLayout(w,h,(values[key+"x"] as? Number)?.toInt() ?: 0,
                (values[key+"y"] as? Number)?.toInt() ?: -1,(values[key+"p"] as? Number)?.toInt() ?: 0).normalized(12)
        }.toMap()
    }
    private val mutable = MutableStateFlow(DisplayState(themeMode=ThemeMode.from(prefs.getString("theme_mode",null)),
        layoutSize=readLayoutSize(),layoutPercent=readLayoutPercent(readLayoutSize()),cardSizes=readCardSizes(),cardGrids=readCardGrids()))
    val state = mutable.asStateFlow()
    private var started = false
    private val listener = object: DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId:Int) { refresh() }
        override fun onDisplayRemoved(displayId:Int) { refresh() }
        override fun onDisplayChanged(displayId:Int) { refresh() }
    }
    private val powerReceiver = object: BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) { refresh() }
    }
    private val animationObserver = object: ContentObserver(handler) {
        override fun onChange(selfChange:Boolean) { refresh() }
    }
    fun start() {
        if(started)return
        started=true
        displays.registerDisplayListener(listener,handler)
        val filter=IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if(Build.VERSION.SDK_INT>=33) context.registerReceiver(powerReceiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(powerReceiver,filter)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,animationObserver)
        refresh()
    }
    fun stop() {
        if(!started)return
        started=false
        displays.unregisterDisplayListener(listener)
        context.unregisterReceiver(powerReceiver)
        context.contentResolver.unregisterContentObserver(animationObserver)
        window.attributes=window.attributes.apply { preferredRefreshRate=0f }
    }
    fun setHighRefresh(enabled:Boolean) { prefs.edit().putBoolean("high_refresh",enabled).apply();refresh() }
    fun setAnimations(enabled:Boolean) { prefs.edit().putBoolean("motion_enabled",enabled).apply();refresh() }
    fun setThemeMode(mode:ThemeMode) { prefs.edit().putString("theme_mode",mode.name).apply();refresh() }
    fun setLayoutSize(size:LayoutSize,percent:Int=mutable.value.layoutPercent) {
        val normalized=LayoutPolicy.percent(size,percent)
        if(mutable.value.layoutSize==size && mutable.value.layoutPercent==normalized && prefs.all["layout_size"]==size.name && prefs.all["layout_percent"]==normalized)return
        prefs.edit().putString("layout_size",size.name).putInt("layout_percent",normalized).apply()
        mutable.value=mutable.value.copy(layoutSize=size,layoutPercent=normalized)
    }
    fun setCardSize(card:OverviewCard,percent:Int) {
        val normalized=percent.coerceIn(LayoutPolicy.MIN_PERCENT,LayoutPolicy.MAX_PERCENT)
        if(mutable.value.cardSizes[card]==normalized)return
        prefs.edit().putInt("card_size_${card.name}",normalized).apply()
        mutable.value=mutable.value.copy(cardSizes=mutable.value.cardSizes+(card to normalized))
    }
    fun resetCardSizes() {
        prefs.edit().apply {
            OverviewCard.entries.forEach { card->remove("card_size_${card.name}");listOf("w","h","x","y","p").forEach{remove("card_grid_${card.name}_$it")} }
            putString("layout_size",LayoutSize.STANDARD.name);putInt("layout_percent",100)
        }.apply()
        mutable.value=mutable.value.copy(layoutSize=LayoutSize.STANDARD,layoutPercent=100,cardSizes=emptyMap(),cardGrids=emptyMap())
    }
    fun setCardGrids(layouts:Map<OverviewCard,CardGridLayout>) {
        val normalized=layouts.mapValues{it.value.normalized(12)}
        if(normalized.all{mutable.value.cardGrids[it.key]==it.value})return
        prefs.edit().apply { normalized.forEach{(card,v)->val key="card_grid_${card.name}_";putInt(key+"w",v.columns);putInt(key+"h",v.rows);putInt(key+"x",v.column);putInt(key+"y",v.row);putInt(key+"p",v.page)} }.apply()
        mutable.value=mutable.value.copy(cardGrids=mutable.value.cardGrids+normalized)
    }
    fun refresh() {
        val display=window.decorView.display ?: displays.getDisplay(Display.DEFAULT_DISPLAY)
        val mode=display?.mode
        val modes=display?.supportedModes?.filter { it.physicalWidth==mode?.physicalWidth && it.physicalHeight==mode?.physicalHeight }.orEmpty()
        val rates=modes.flatMap { listOf(it.refreshRate) + if(Build.VERSION.SDK_INT>=31)it.alternativeRefreshRates.toList() else emptyList() }
            .filter { it.isFinite() && it>0f }.distinct().sorted()
        val high=prefs.getBoolean("high_refresh",true)
        val saving=power.isPowerSaveMode
        val requested=if(started)RefreshPolicy.preferredRate(rates,high,saving) else 0f
        val layoutSize=readLayoutSize()
        mutable.value=DisplayState(high,prefs.getBoolean("motion_enabled",true),display?.refreshRate ?: 0f,rates,requested,saving,ValueAnimator.areAnimatorsEnabled(),ThemeMode.from(prefs.getString("theme_mode",null)),layoutSize,readLayoutPercent(layoutSize),readCardSizes(),readCardGrids())
        if(window.attributes.preferredRefreshRate!=requested) window.attributes=window.attributes.apply { preferredRefreshRate=requested }
    }
}

@Composable fun DisplayEnvironment(controller: DisplayController,content:@Composable ()->Unit) {
    val settings by controller.state.collectAsState()
    CompositionLocalProvider(LocalDisplayState provides settings,LocalDisplayController provides controller) {
        // ARR votes only when content draws; this does not create a continuous render loop.
        Box(Modifier.fillMaxSize().preferredFrameRate(settings.requestedHz)) { content() }
    }
}

@Composable fun DisplayPreferencesForm(close:()->Unit,layout:()->Unit) {
    val state=LocalDisplayState.current
    val controller=LocalDisplayController.current
    Form("显示与动效",close=close,confirm=null) {
        Text("外观主题",fontWeight=FontWeight.SemiBold)
        Caption("当前配色：白茶柔雾 · 液态玻璃与茶元素")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(selected=state.themeMode==mode,onClick={controller?.setThemeMode(mode)},
                    label={Text(mode.label)},modifier=Modifier.testTag("theme-${mode.name}"))
            }
        }
        Caption("立即切换并自动保存。跟随系统会按照手机的深色模式设置变化。")
        TeaLink("卡片大小","长按移动位置，拖动边缘调大小",onClick=layout)
        HorizontalDivider(color=Pale)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("高刷优先",fontWeight=FontWeight.SemiBold);Caption("在设备支持时请求最高 120 Hz") }
            Switch(state.highRefresh,{controller?.setHighRefresh(it)},modifier=Modifier.testTag("highRefreshToggle"))
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("界面动效",fontWeight=FontWeight.SemiBold);Caption("页面、弹窗、按压和进度变化") }
            Switch(state.animations,{controller?.setAnimations(it)},modifier=Modifier.testTag("motionToggle"))
        }
        HorizontalDivider(color=Pale)
        Text("当前屏幕刷新率",fontSize=13.sp,color=Muted)
        Text(hzText(state.currentHz),fontSize=30.sp,fontWeight=FontWeight.SemiBold,color=Forest)
        Caption("由系统报告，代表屏幕刷新率，不等于每秒实际渲染帧数。")
        Caption("当前分辨率可用：${state.availableHz.joinToString(" / "){hzText(it)}.ifBlank{"等待系统提供"}}")
        Caption("应用刷新率偏好：${if(state.requestedHz>0f)hzText(state.requestedHz) else "跟随系统"}")
        if(state.powerSave)Notice("系统已开启省电模式，应用暂时释放高刷请求。")
        if(!state.systemAnimations)Notice("系统动画已关闭，应用将跟随减少动效。")
        Notice("高刷请求由系统最终决定，可能受省电、温度和厂商设置影响。静止页面按需绘制，不持续空转刷帧。")
    }
}
