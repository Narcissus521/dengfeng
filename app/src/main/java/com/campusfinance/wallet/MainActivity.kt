package com.campusfinance.wallet

import android.os.Bundle
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    internal var activeCardMotion:CardMotionState?=null
    internal var activePageMotion:GlassPageMotion?=null
    internal var activeOpticalScene:OpticalScene?=null
    internal var activeDockSlot:Int?=null
    internal var foregroundEpoch by mutableIntStateOf(0)
    lateinit var displayController: DisplayController
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode=ThemeMode.from(getSharedPreferences("wallet_settings",MODE_PRIVATE).getString("theme_mode",null))
        val systemDark=resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        setTheme(if(themeMode==ThemeMode.DARK || themeMode==ThemeMode.SYSTEM && systemDark)R.style.Theme_Wallet_Dark else R.style.Theme_Wallet)
        super.onCreate(savedInstanceState)
        displayController=DisplayController(this,window)
        val prefs = getSharedPreferences("wallet_settings", MODE_PRIVATE)
        if(!prefs.getBoolean(ConsentPolicy.KEY,false)) {
            setContent { DisplayEnvironment(displayController){WalletTheme{FirstConsentScreen(
                agree={prefs.edit().putBoolean(ConsentPolicy.KEY,true).commit();recreate()},decline={finish()})}} }
            return
        }
        val mode = prefs.getString("mode", null)
        fun choose(demo: Boolean) { prefs.edit().putString("mode", if(demo) "demo" else "personal").apply(); recreate() }
        if (mode == null) {
            setContent { DisplayEnvironment(displayController) { WalletTheme { Welcome { choose(false) } } } }
            return
        }
        val demo = mode == "demo"
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WalletViewModel(WalletRepository(WalletDatabase.open(this@MainActivity,demo)),demo) as T
        })["wallet_$mode", WalletViewModel::class.java]
        setContent { DisplayEnvironment(displayController) { WalletTheme { WalletApp(vm,this) { } } } }
    }
    override fun onResume() { super.onResume();foregroundEpoch++;displayController.start();lifecycleScope.launch(Dispatchers.IO){WalletAutomation.run(this@MainActivity)} }
    override fun onPause() { displayController.stop();super.onPause() }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus);if(hasFocus && ::displayController.isInitialized)displayController.refresh() }
}
data class Editor(val type: String, val id: String? = null, val kind: String? = null)

@Composable fun Welcome(onChoose:(Boolean)->Unit) {
    Column(Modifier.fillMaxSize().teaWallpaper().systemBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Forest),contentAlignment=Alignment.Center) { Glyph("wallet",MaterialTheme.colorScheme.onPrimary,Modifier.size(38.dp)) }
        Text(stringResource(R.string.app_name),fontSize=12.sp,letterSpacing=3.sp,color=Forest,fontWeight=FontWeight.Bold)
        Text("让现在的每一笔，\n照顾未来的自己。",fontSize=31.sp,lineHeight=46.sp,color=Ink,fontWeight=FontWeight.Bold)
        Text("大学生的个人财务与消费规划工具",fontSize=14.sp,color=Muted)
        CardBox {
            Row(horizontalArrangement=Arrangement.spacedBy(15.dp)) { TileIcon("book");Column { Text("从记录开始",fontWeight=FontWeight.Medium);Caption("账户、生活费、支出与退款，清清楚楚。") } }
            Row(horizontalArrangement=Arrangement.spacedBy(15.dp)) { TileIcon("plan");Column { Text("给未来留一份计划",fontWeight=FontWeight.Medium);Caption("预算、负债与储蓄目标，一起查看。") } }
            Row(horizontalArrangement=Arrangement.spacedBy(15.dp)) { TileIcon("bag");Column { Text("想买之前，先看影响",fontWeight=FontWeight.Medium);Caption("现金和预算分别模拟，消费更从容。") } }
        }
        Button(onClick={onChoose(false)},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(17.dp)) { Text("创建我的账本",fontSize=16.sp) }
        Caption("无需注册 · 数据保存在本机 · 人民币 / 北京时间\n暖纸酒红 · 支持固定收支、记账提醒和购买模拟。分期金额由你填写，不自动推算利息。")
    }
}

@Composable fun WalletApp(vm: WalletViewModel, activity: MainActivity, onSwitch:()->Unit) {
    val ui by vm.state.collectAsState()
    val quickState=remember{LedgerQuickState()}
    var quickRoute by rememberSaveable{mutableStateOf<ArrayList<String>?>(null)}
    val quickEditor=quickRoute?.let{Editor(it[0],it[1].ifEmpty{null},it[2].ifEmpty{null})}
    val showQuick:(Editor)->Unit={e->quickRoute=arrayListOf(e.type,e.id ?: "",e.kind ?: "")}

    val s = ui.ledger
    var editor by remember { mutableStateOf<Editor?>(null) }
    var leafMenu by remember { mutableStateOf(false) }
    var pendingPrompt by remember{mutableStateOf(false)}
    var pendingCheckedEpoch by remember{mutableIntStateOf(-1)}
    val cardMotion=remember {CardMotionState()}
    DisposableEffect(activity,cardMotion){activity.activeCardMotion=cardMotion;onDispose{activity.activeCardMotion=null}}
    val keyboard=LocalSoftwareKeyboardController.current
    val showLeaf:()->Unit={keyboard?.hide();cardMotion.pending=null;leafMenu=true}
    val editorHistory=remember { mutableStateListOf<Editor>() }
    var editorDirection by remember {mutableIntStateOf(1)}
    val analysisState=rememberSaveableStateHolder()
    // The state holder survives recreation, so its key registry must survive too.
    // Otherwise a completed expense draft can reappear as a new expense after a theme/recreation.
    var analysisKeys by rememberSaveable{mutableStateOf(arrayListOf<String>())}
    val motion=LocalDisplayState.current
    val scene=LocalOpticalScene.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pageMotion=remember{GlassPageMotion(tab)}
    DisposableEffect(activity,scene,pageMotion){activity.activePageMotion=pageMotion;activity.activeOpticalScene=scene;onDispose{activity.activePageMotion=null;activity.activeOpticalScene=null}}
    var planSection by rememberSaveable { mutableIntStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(currentMonth()) }
    var ioMessage by remember { mutableStateOf<String?>(null) }
    var exportPayload by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<Ledger?>(null) }
    val scope = rememberCoroutineScope()
    val saveFeedback=remember{SnackbarHostState()}
    LaunchedEffect(vm){vm.confirmations.collect {
        saveFeedback.currentSnackbarData?.dismiss()
        scope.launch{saveFeedback.showSnackbar("保存成功",duration=SnackbarDuration.Short)}
    }}
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) scope.launch {
            try { withContext(Dispatchers.IO) { activity.contentResolver.openOutputStream(uri)?.use { it.write(exportPayload.toByteArray(Charsets.UTF_8)) } ?: error("无法写入所选文件") }; ioMessage="备份已保存。请妥善保管，其中包含完整账本。" }
            catch(e:Exception) { ioMessage="备份失败：${e.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            try {
                pendingRestore = withContext(Dispatchers.IO) {
                    val text = activity.contentResolver.openInputStream(uri)?.use { input ->
                        val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true) { val n=input.read(buffer);if(n<0)break;require(output.size()+n<=5_000_000){"备份超过 5 MB 限制"};output.write(buffer,0,n) }
                        output.toString("UTF-8")
                    } ?: error("无法读取所选文件")
                    Backup.decode(text)
                }
            } catch(e:Exception) { ioMessage="备份无法恢复：${e.message}" }
        }
    }
    if(ui.fatal!=null) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),verticalArrangement=Arrangement.Center) {
            EmptyState("账本已保护性停止打开",ui.fatal ?: "数据异常")
            Spacer(Modifier.height(16.dp));Notice("原数据库已保留，未清空或重置。请保留应用数据，并交由开发者排查。",true)
        }
        return
    }
    if(s==null) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator(color=Forest) };return }
    val month=YearMonth.parse(monthText)
    LaunchedEffect(activity.foregroundEpoch,editor==null,quickEditor==null) {
        if(pendingCheckedEpoch!=activity.foregroundEpoch&&editor==null&&quickEditor==null){
            pendingCheckedEpoch=activity.foregroundEpoch
            if(pendingRecurring(s,java.time.ZonedDateTime.now(financeZone)).isNotEmpty())pendingPrompt=true
        }
    }
    LaunchedEffect(cardMotion.phase){scene?.pulse()}
    LaunchedEffect(tab){scene?.pulse()}
    LaunchedEffect(motion.motionEnabled){if(!motion.motionEnabled&&cardMotion.phase==CardPhase.CLOSING){cardMotion.reset();editorHistory.clear();editor=null}}
    val open:(Editor)->Unit = { next ->
        if(editor==null||cardMotion.phase==CardPhase.CLOSING) {
            val continuing=editor!=null&&cardMotion.phase==CardPhase.CLOSING
            if(!continuing){analysisKeys.forEach{analysisState.removeState(it)};analysisKeys=arrayListOf()}
            cardMotion.job?.cancel();editorHistory.clear();cardMotion.origin=cardMotion.takeOrigin() ?: cardMotion.leafOrigin
            cardMotion.entrancePrepared=continuing;cardMotion.openingReady=continuing||editor==next;cardMotion.phase=CardPhase.OPENING
            cardMotion.job=scope.launch {
                if(!continuing)cardMotion.progress.snapTo(0f)
                cardMotion.entrancePrepared=true
                // Start the growth clock after the new form has completed its first layout.
                snapshotFlow{cardMotion.openingReady}.first{it}
                withFrameNanos{}
                cardMotion.progress.animateTo(1f,tween(motion.duration(360),easing=NavigationEasing));cardMotion.phase=CardPhase.OPEN
            }
        } else if(editor!=next){keyboard?.hide();editorHistory.add(editor!!);editorDirection=1}
        editor=next
    }
    val closeEditor:()->Unit = {
        if(cardMotion.phase!=CardPhase.CLOSING) {
            if(editorHistory.isNotEmpty()){keyboard?.hide();editorDirection=-1;editor=editorHistory.removeAt(editorHistory.lastIndex)}
            else {
                keyboard?.hide();cardMotion.job?.cancel()
                cardMotion.origin?.key?.let {key->cardMotion.rectangles[key]?.let{cardMotion.origin=it}}
                cardMotion.phase=CardPhase.CLOSING
                cardMotion.job=scope.launch {
                    cardMotion.progress.animateTo(0f,tween(motion.duration(320),easing=NavigationEasing))
                    editor=null;cardMotion.reset()
                }
            }
        }
    }
    val navigate:(Int)->Unit={next->pageMotion.syncDock=false;keyboard?.hide();cardMotion.reset();editorHistory.clear();editor=null;tab=next}
    val revealedDockAction by rememberUpdatedState<(Int)->Unit> { slot->if(slot==2)showLeaf() else navigate(if(slot<2)slot else slot-1) }
    LaunchedEffect(s.reminder){withContext(Dispatchers.IO){WalletAutomation.run(activity,notify=false)}}
    val overviewState=rememberSaveableStateHolder()
    CompositionLocalProvider(LocalCardMotion provides cardMotion,LocalEmbeddedForm provides true,LocalPageMotion provides pageMotion,LocalLedgerQuickState provides quickState,LocalQuickEditor provides showQuick) {
      Box(Modifier.fillMaxSize().pointerInput(cardMotion) {
          awaitEachGesture {
              val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
              // Forward taps on the revealed Dock while the returning page still covers it.
              val target=if(cardMotion.phase==CardPhase.CLOSING)cardMotion.dockRectangles.entries.firstOrNull{it.value.contains(down.position)} else null
              if(target!=null) {
                  down.consume()
                  var last=down.position;var singlePointer=true
                  do {val event=awaitPointerEvent(PointerEventPass.Initial);if(event.changes.size>1)singlePointer=false;event.changes.firstOrNull{it.id==down.id}?.let{last=it.position};event.changes.forEach{it.consume()}} while(event.changes.any{it.pressed})
                  if(singlePointer&&target.value.contains(last)&&(last-down.position).getDistance()<=viewConfiguration.touchSlop)revealedDockAction(target.key)
              }
          }
      }) {
      Column(Modifier.fillMaxSize().teaWallpaper().systemBarsPadding().then(if(android.os.Build.VERSION.SDK_INT>=30)Modifier.imePadding() else Modifier)
          .revealBehindEditor(cardMotion)
          .then(if(editor!=null&&cardMotion.phase!=CardPhase.CLOSING)Modifier.clearAndSetSemantics{} else Modifier)) {
        // Content starts directly below the system status inset; no separate app-name bar.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("main-page-viewport")) {
          GlassPages(tab,pageMotion,editor==null,navigate,Modifier.fillMaxSize()) { page ->
            overviewState.SaveableStateProvider("overview-$page") {
              Column(Modifier.fillMaxSize().teaWallpaper().testTag("main-page-$page")
                  .then(if(page!=tab)Modifier.clearAndSetSemantics{} else Modifier)) {
                Box(Modifier.weight(1f).fillMaxWidth()
                    .then(if(editor!=null)Modifier.clearAndSetSemantics{} else Modifier)) {
                    TeaShell(s,monthText,{monthText=it},page,navigate,open,showLeaf,embedded=true,active=page==tab)
                }
              }
            }
          }
        }
        TeaDock(tab,navigate,showLeaf,leafMenu)
      }
      // Fade a cached solid layer, instead of invalidating all paper/card drawing every frame.
      Box(Modifier.matchParentSize().systemBarsPadding().graphicsLayer {
          alpha=if(cardMotion.phase==CardPhase.CLOSED)0f else .09f*cardMotion.fraction
      }.background(Color.Black))
      Box(Modifier.fillMaxSize().systemBarsPadding().then(if(android.os.Build.VERSION.SDK_INT>=30)Modifier.imePadding() else Modifier)
          .onGloballyPositioned{cardMotion.bodyOffset=it.positionInRoot();cardMotion.destinationBounds=it.boundsInRoot()}) {
        editor?.let { current -> CardEditorLayer(cardMotion,current) {
          AnimatedContent(targetState=current,modifier=Modifier.fillMaxSize().clipToBounds().testTag("full-screen-destination"),label="destination navigation",transitionSpec={
              val duration=motion.duration(360);val direction=editorDirection
              (slideInHorizontally(tween(duration,easing=NavigationEasing)){if(direction>0)it else -it/4} togetherWith
                  slideOutHorizontally(tween(duration,easing=NavigationEasing)){if(direction>0)-it/4 else it})
                  .apply{targetContentZIndex=if(direction>0)1f else -1f}.using(SizeTransform(clip=true))
          }) { e ->
            val active=e==editor&&cardMotion.phase!=CardPhase.CLOSING
            CompositionLocalProvider(LocalEditorPageActive provides active) {
              Box(Modifier.fillMaxSize().graphicsLayer {
                  compositingStrategy=if(transition.isRunning)CompositingStrategy.Offscreen else CompositingStrategy.Auto
              }.background(Page).then(if(!active)Modifier.clearAndSetSemantics{} else Modifier)) {
                val key="destination:${e.type}:${e.id}:${e.kind}";if(key !in analysisKeys)analysisKeys=ArrayList(analysisKeys+key)
                analysisState.SaveableStateProvider(key) {
                  EditorHost(e,s,monthText,vm,ui.busy,close=closeEditor,open=open,
                    export={exportPayload=Backup.encode(s);exportLauncher.launch("等风-${if(s.profile.demo) "demo" else "personal"}-${today()}.json")},
                    restore={importLauncher.launch(arrayOf("application/json","text/plain","application/octet-stream"))},onSwitch=onSwitch)
                }
              }
            }
          }
        }}
      }
      SnackbarHost(saveFeedback,Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top=6.dp,end=18.dp)) { feedback ->
          Box(Modifier.testTag("save-confirmation").semantics{liveRegion=LiveRegionMode.Polite}
              .teaGlass(Card).clickable{feedback.dismiss()}.padding(horizontal=14.dp,vertical=9.dp)) {
              Text("✓ ${feedback.visuals.message}",fontSize=12.sp,lineHeight=18.sp,color=Ink)
          }
      }
      }
    }
    LedgerQuickMenu(quickState,s,monthText){action->
        val target=quickState.target
        quickState.dismiss()
        if(action.layout){target?.card?.let{c->if(c.name.startsWith("HOME_"))activity.displayController.setCustomHome(true);quickState.editCard=c}}
        else action.editor?.let{if(action.overlay)showQuick(it) else open(it)}
    }
    quickEditor?.let{q->
        Dialog(onDismissRequest={if(!ui.busy)quickRoute=null},properties=DialogProperties(usePlatformDefaultWidth=false)){
            var confirmation by remember(q){mutableStateOf(false)}
            LaunchedEffect(vm,q){vm.confirmations.collect{confirmation=true}}
            BoxWithConstraints(Modifier.fillMaxWidth()){
            val preferred=when(q.type){"budget"->520.dp;"quickSaving"->340.dp;"quickRecurring","removeDebt","restoreDebt"->460.dp;else->maxHeight*.88f}
            Column(Modifier.padding(12.dp).widthIn(max=480.dp).fillMaxWidth().height(minOf(maxHeight*.88f,preferred)).ledgerMaterial(Card,10f).testTag("quick-edit-dialog")){
                CompositionLocalProvider(LocalEmbeddedForm provides true,LocalQuickForm provides true,LocalCardMotion provides null,LocalLedgerQuickState provides null,LocalEditorPageActive provides true){
                    Box(Modifier.weight(1f)){
                        EditorHost(q,s,monthText,vm,ui.busy,close={quickRoute=null},open={quickRoute=null;open(it)},export={},restore={},onSwitch=onSwitch)
                    }
                }
                if(confirmation)Text("保存成功",Modifier.fillMaxWidth().padding(12.dp).testTag("quick-save-confirmation").semantics{liveRegion=LiveRegionMode.Polite},fontSize=12.sp,color=Forest)
            }
            }
        }
    }
    if(leafMenu)LeafActionMenu(close={leafMenu=false}) { next -> leafMenu=false;open(next) }
    if(pendingPrompt)AlertDialog(onDismissRequest={pendingPrompt=false},modifier=Modifier.testTag("pending-recurring-dialog"),
        title={Text("固定收支待确认")},text={Text("有 ${pendingRecurring(s,java.time.ZonedDateTime.now(financeZone)).size} 笔计划已到期。你可以修改本次金额与日期，再确认记账或跳过；未确认不会改变余额。")},
        confirmButton={TextButton(onClick={pendingPrompt=false;open(Editor("pendingRecurring"))}){Text("查看待确认清单")}},
        dismissButton={TextButton(onClick={pendingPrompt=false},modifier=Modifier.testTag("pending-recurring-later")){Text("稍后处理")}})
    if(ui.error!=null) AlertDialog(onDismissRequest={vm.clearError()},title={Text("未能保存")},text={Text(ui.error ?: "请重试")},confirmButton={TextButton(onClick={vm.clearError()}){Text("知道了")}})
    if(ioMessage!=null) AlertDialog(onDismissRequest={ioMessage=null},title={Text("本地备份")},text={Text(ioMessage ?: "")},confirmButton={TextButton(onClick={ioMessage=null}){Text("完成")}})
    pendingRestore?.let { restored -> AlertDialog(onDismissRequest={pendingRestore=null},title={Text("确认恢复备份")},
        text={Text("${restored.profile.name}\n${restored.accounts.size} 个账户 · ${restored.entries.size} 笔记录\n${restored.goals.size} 个目标 · ${restored.debts.size} 笔负债\n\n所有金额与关联已校验。只允许恢复到空账本，已有账本不会被覆盖。")},
        confirmButton={TextButton(enabled=!ui.busy,onClick={vm.execute({restore(restored,s.profile.demo)}){pendingRestore=null;editor=null}}){Text("恢复到空账本")}},
        dismissButton={TextButton(onClick={pendingRestore=null}){Text("取消")}}) }
}
