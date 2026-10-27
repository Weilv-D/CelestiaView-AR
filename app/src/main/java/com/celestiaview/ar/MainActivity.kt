package com.celestiaview.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.media.SkySnapshotExporter
import com.celestiaview.ar.data.catalog.CultureMode
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.data.model.DsoType
import com.celestiaview.ar.data.model.ObservingSite
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.components.CelestialDetailSheet
import com.celestiaview.ar.ui.components.GlassCard
import com.celestiaview.ar.ui.components.NakedTextButton
import com.celestiaview.ar.ui.components.SearchAndFilterModal
import com.celestiaview.ar.ui.components.TargetReticleOverlay
import com.celestiaview.ar.ui.components.TimeMachineScrubber
import com.celestiaview.ar.ui.components.TonightSkyGuideSheet
import com.celestiaview.ar.ui.render.SkyCanvasRenderer
import com.celestiaview.ar.ui.theme.CelestialPalette
import com.celestiaview.ar.ui.theme.DeepSpacePalette
import com.celestiaview.ar.ui.theme.DeepSpaceTheme
import com.celestiaview.ar.ui.theme.redLightSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 无框悬浮文字的柔和阴影（保证星空上的可读性）。 */
private val HudTextShadow = Shadow(color = Color(0xCC000000), blurRadius = 6f)

/** HUD 交互层统一安全区：系统栏与显示凹口的并集；背景渲染层保持全屏穿透，不经此避让。 */
@Composable
private fun Modifier.hudSafeInsets(): Modifier = windowInsetsPadding(
    WindowInsets.systemBars.union(WindowInsets.displayCutout)
)

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                skyViewModelBridge?.startEngines()
            }
        }

    private var skyViewModelBridge: SkyViewModel? = null

    /** 深度链接导航总线：冷启动与 onNewIntent 统一经此投递（replay=1 防冷启动订阅前丢失）。 */
    val navBus = kotlinx.coroutines.flow.MutableSharedFlow<android.content.Intent>(
        replay = 1, extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 渲染层铺满凹口两侧；物理遮挡由 HUD 的 displayCutout insets 避让
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            DeepSpaceTheme {
                SkyApp(
                    onNeedsPermissions = { perms ->
                        permissionLauncher.launch(perms.toTypedArray())
                    },
                    onViewModelReady = { skyViewModelBridge = it }
                )
            }
        }
        navBus.tryEmit(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navBus.tryEmit(intent)
    }

    /** instrumented 测试用：停止传感器/GPS 引擎，杜绝后台状态写入造成的重组流。 */
    internal fun stopEnginesForTest() {
        skyViewModelBridge?.stopEngines()
    }

    /** instrumented 测试用：访问 ViewModel 验证深度链接与状态机。 */
    internal val vmForTest: SkyViewModel? get() = skyViewModelBridge
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkyApp(
    onNeedsPermissions: (Set<String>) -> Unit,
    onViewModelReady: (SkyViewModel) -> Unit
) {
    val vm: SkyViewModel = viewModel()
    val context = LocalContext.current
    val activity = context as? MainActivity
    val renderer = remember { SkyCanvasRenderer(vm) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var canvasSizePx by remember { mutableStateOf(IntSize(1080, 2340)) }

    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showTonight by remember {
        mutableStateOf(activity?.intent?.getBooleanExtra("open_guide", false) == true)
    }
    var showLocation by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var timeMachineExpanded by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }

    // 深度链接导航：widget / QA / 外部直达各面板（navigate extra）
    LaunchedEffect(vm) {
        activity?.navBus?.collect { intent ->
            when (intent.getStringExtra("navigate")) {
                "detail" -> {
                    vm.targetByName(intent.getStringExtra("target") ?: "")?.let {
                        vm.focusOn(it)
                        showDetail = true
                    }
                }
                // 聚焦直达：focus + target（可选 fov 浮点参数驱动望远视场）
                "focus" -> {
                    vm.targetByName(intent.getStringExtra("target") ?: "")?.let {
                        vm.focusOn(it)
                        val fov = intent.getFloatExtra("fov", -1f)
                        if (fov > 0) vm.orbit.zoomTo(fov.toDouble())
                    }
                }
                "guide" -> {
                    vm.targetByName(intent.getStringExtra("target") ?: "")?.let {
                        vm.startGuide(it)
                    }
                }
                "tonight" -> showTonight = true
                "search" -> showSearch = true
                "time" -> timeMachineExpanded = true
                "layers" -> showLayers = true
                "location" -> showLocation = true
                "redlight" -> if (!vm.redLight) vm.toggleRedLight()
                "culture" -> vm.cultureMode = CultureMode.CHINESE
                "culture_dual" -> vm.cultureMode = CultureMode.DUAL
                "reset" -> {
                    vm.select(null); showDetail = false; showTonight = false
                    showSearch = false; showLayers = false; showLocation = false
                    showMenu = false
                    timeMachineExpanded = false; vm.clearGuide()
                    if (vm.redLight) vm.toggleRedLight()
                }
            }
        }
    }

    // 系统返回：二级状态逐级收起，纯主星图才放行退出
    BackHandler(
        enabled = showMenu || showDetail || showSearch || showTonight || showLocation ||
            showLayers || timeMachineExpanded || vm.selected != null || vm.guideTarget != null
    ) {
        when {
            showMenu -> showMenu = false
            showDetail -> { vm.select(null); showDetail = false }
            showSearch -> showSearch = false
            showTonight -> showTonight = false
            showLocation -> showLocation = false
            showLayers -> showLayers = false
            timeMachineExpanded -> timeMachineExpanded = false
            vm.selected != null -> vm.select(null)
            else -> vm.clearGuide()
        }
    }

    // 观测地点同步到桌面小组件偏好，并触发其刷新
    LaunchedEffect(vm.site) {
        context.getSharedPreferences("aetheris_widget", Context.MODE_PRIVATE)
            .edit()
            .putFloat("lat", vm.site.latitudeDeg.toFloat())
            .putFloat("lon", vm.site.longitudeEastDeg.toFloat())
            .apply()
        context.sendBroadcast(
            android.content.Intent(context, com.celestiaview.ar.widget.TonightSkyWidgetProvider::class.java)
                .setAction("com.celestiaview.ar.widget.REFRESH")
        )
    }

    // 权限与引擎启动
    LaunchedEffect(Unit) {
        onViewModelReady(vm)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onNeedsPermissions(setOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            vm.startEngines()
        }
    }

    LaunchedEffect(vm.arMode) {
        if (vm.arMode && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onNeedsPermissions(setOf(Manifest.permission.CAMERA))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    vm.startEngines()
                    vm.resumeAudio()
                    // 观星场景：亮屏期间保持屏幕常亮，避免观测中息屏
                    (context as? ComponentActivity)?.window?.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
                Lifecycle.Event.ON_PAUSE -> {
                    vm.stopEngines()
                    vm.pauseAudio()
                    (context as? ComponentActivity)?.window?.clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 60fps 帧循环（framesSuspended=true 时放行前 30 帧完成首次组合后挂起，
    // 供 instrumented 测试进入空闲态；计数用普通局部变量，不得在帧回调里写
    // Compose 状态——挂起后的悬挂重组会让 ComposeIdlingResource 永不空闲）
    LaunchedEffect(Unit) {
        var framesDrawn = 0
        while (true) {
            if (SkyViewModel.framesSuspended && framesDrawn > 30) {
                kotlinx.coroutines.delay(120)
                continue
            }
            withFrameNanos { ns -> vm.onFrame(ns) }
            framesDrawn++
            if (renderer.consumeGuideLockEvent()) {
                vm.haptics.lockPulse()
                if (vm.soundOn) vm.soundEngine.playLockChime()
                // 寻星箭头到位：自动平滑变焦至天体特写（今夜简报“到位即特写”链路）
                if (vm.guideAutoFocus) {
                    vm.guideAutoFocus = false
                    val g = vm.guideTarget
                    if (g != null && !vm.focusMode) vm.focusOn(g)
                }
            }
        }
    }

    // 屏幕旋转回传（传感器重映射）
    LaunchedEffect(Unit) {
        vm.attitudeTracker.displayRotation =
            activity?.windowManager?.defaultDisplay?.rotation ?: 0
    }

    val red = vm.redLight
    val accent = if (red) DeepSpacePalette.RedSafePrimary else CelestialPalette.AccentCyan

    fun captureSnapshot() {
        val size = canvasSizePx
        scope.launch {
            val result: Uri? = withContext(Dispatchers.Default) {
                var bitmap: Bitmap? = null
                try {
                    val plaque = vm.snapshotPlaqueLines(
                        vm.selected?.displayName ?: vm.snapped?.target?.displayName ?: "自由巡天"
                    )
                    val w = size.width.coerceAtLeast(720)
                    val h = size.height.coerceAtLeast(1280)
                    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val composeCanvas =
                        androidx.compose.ui.graphics.Canvas(android.graphics.Canvas(bitmap))
                    CanvasDrawScope().draw(
                        density, LayoutDirection.Ltr, composeCanvas,
                        Size(w.toFloat(), h.toFloat())
                    ) { renderer.renderInto(this) }
                    SkySnapshotExporter.export(
                        context, bitmap,
                        SkySnapshotExporter.Plaque(
                            plaque.siteName, plaque.latitudeDeg, plaque.longitudeEastDeg,
                            plaque.utcTime, plaque.localTime, plaque.lst, plaque.centerTarget
                        )
                    )
                } catch (e: Exception) {
                    null
                } finally {
                    // 输入位图在海报合成后即无用，立即回收原生内存
                    bitmap?.recycle()
                }
            }
            vm.haptics.shutter()
            Toast.makeText(
                context,
                if (result != null) "星空海报已保存至相册 Pictures/CelestiaView" else "保存失败",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF03050C))
    ) {
        // ---------------- AR 相机层 ----------------
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (vm.arMode && cameraGranted && !vm.redLight) {
            // 相机提供器持有槽（非 Compose 状态，避免重组回写循环）
            val cameraHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
            DisposableEffect(Unit) {
                onDispose {
                    // 退出 AR/红光模式：立即解绑相机，释放摄像头与隐私指示灯
                    try {
                        cameraHolder[0]?.unbindAll()
                    } catch (_: Exception) {
                    }
                    cameraHolder[0] = null
                    vm.cameraBindFailed = false
                }
            }
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { view ->
                    if (view.tag != "bound") {
                        view.tag = "bound"
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            try {
                                val provider = future.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(view.surfaceProvider)
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview
                                )
                                cameraHolder[0] = provider
                                vm.cameraBindFailed = false
                            } catch (_: Exception) {
                                // 相机被占用/无相机设备：明确降级为纯星空模式并提示
                                vm.cameraBindFailed = true
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ---------------- 星空渲染层 + 手势 ----------------
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSizePx = it }
                .pointerInput(vm.sensorMode) {
                    if (vm.sensorMode) return@pointerInput
                    val degPerPxY = vm.fovYDeg / size.height.toDouble()
                    // 左右边缘 16dp 让位于系统返回手势，起点落在边带内的拖拽不驱动星图
                    val edgeExclusionPx = 16.dp.toPx()
                    var tracking = true
                    var lastTimeMs = 0L
                    var velX = 0f
                    var velY = 0f
                    detectDragGestures(
                        onDragStart = { start ->
                            lastTimeMs = 0L; velX = 0f; velY = 0f
                            tracking = start.x >= edgeExclusionPx &&
                                start.x <= size.width - edgeExclusionPx
                        },
                        onDrag = { change, drag ->
                            if (tracking) {
                                val dtSec = if (lastTimeMs == 0L) 0.016f
                                else ((change.uptimeMillis - lastTimeMs).coerceAtLeast(1L)) / 1000f
                                lastTimeMs = change.uptimeMillis
                                velX = drag.x / dtSec * 0.65f + velX * 0.35f
                                velY = drag.y / dtSec * 0.65f + velY * 0.35f
                                vm.orbit.onDrag(drag.x, drag.y, degPerPxY, degPerPxY)
                            }
                        },
                        onDragEnd = {
                            if (tracking) vm.orbit.onFling(velX, velY, degPerPxY, degPerPxY)
                        }
                    )
                }
                // 点击星图天体：只触发相机平滑聚焦 + 轨道光带 + 左下铭牌，
                // 绝不自动弹出百科大卡片（百科由点击铭牌主动展开）
                .pointerInput(vm.snapped) {
                    detectTapGestures {
                        val snap = vm.snapped ?: return@detectTapGestures
                        vm.focusOn(snap.target)
                    }
                }
        ) {
            renderer.renderInto(this)
        }

        // 双指缩放
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (!vm.sensorMode && zoom != 1f) vm.orbit.onZoom(zoom)
                    }
                }
        ) { }

        // ---------------- 准星 HUD ----------------
        TargetReticleOverlay(vm = vm, renderer = renderer, modifier = Modifier.fillMaxSize())

        // ---------------- 硬件降级提示（无框悬浮文字） ----------------
        val degradeHint = when {
            vm.arMode && !cameraGranted -> "相机未授权 · 已切换纯深空大气模式"
            vm.arMode && vm.cameraBindFailed -> "相机不可用 · 已切换纯深空大气模式"
            !vm.sensorAvailable -> "未检测到陀螺仪 · 触摸拖动巡天"
            else -> null
        }
        if (degradeHint != null) {
            Text(
                degradeHint,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .hudSafeInsets()
                    .padding(top = 64.dp),
                fontSize = 11.sp,
                color = accent.redLightSafe(red),
                style = TextStyle(shadow = HudTextShadow)
            )
        }

        // ================= 要素一：左下角唯一双行天体铭牌 =================
        // 全屏唯一的天体标题位置（对标 SkyView 左下角）：
        // 第一行 28sp 天蓝标题，第二行 15sp 纯白微光副标；
        // 点击铭牌才展开深度百科面板。（内容 ~4Hz 节流重建）
        val plate = remember(vm.selected, vm.frameTick / 15) { rememberNameplate(vm) }
        if (plate != null) {
            val slide = vm.focusProgress.coerceIn(0f, 1f)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .hudSafeInsets()
                    .padding(start = 24.dp, bottom = 42.dp)
                    .graphicsLayer {
                        alpha = (0.35f + 0.65f * slide).coerceAtMost(1f)
                        translationY = (1f - slide) * 12.dp.toPx()
                    }
                    .clickable { showDetail = true }
            ) {
                Text(
                    plate.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5AC8FA).redLightSafe(red),
                    style = TextStyle(shadow = HudTextShadow)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    plate.subtitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xE6F1F5F9).redLightSafe(red),
                    style = TextStyle(shadow = HudTextShadow)
                )
            }
        }

        // ================= 要素二：右侧边缘 3 个无框悬浮图标 =================
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Menu, "菜单",
                tint = Color(0x88CBD5E1).redLightSafe(red),
                modifier = Modifier
                    .size(26.dp)
                    .clickable { showMenu = !showMenu }
            )
            Icon(
                Icons.Outlined.Search, "搜索寻星",
                tint = Color(0x88CBD5E1).redLightSafe(red),
                modifier = Modifier
                    .size(26.dp)
                    .clickable { showSearch = !showSearch }
            )
            Icon(
                Icons.Outlined.Share, "星空快门",
                tint = Color(0x88CBD5E1).redLightSafe(red),
                modifier = Modifier
                    .size(26.dp)
                    .clickable { captureSnapshot() }
            )
        }

        // ---------------- 时间机器刻度尺（内嵌浮层） ----------------
        AnimatedVisibility(
            visible = timeMachineExpanded,
            enter = fadeIn(tween(240)) +
                slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(tween(200)) +
                slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TimeMachineScrubber(vm, true) { timeMachineExpanded = false }
        }

        // ================= 要素三：主菜单控制抽屉（应用内浮层，与主窗同窗） =================
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xEA04070F))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { showMenu = false }
            ) {
                GlassCard(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                        .width(248.dp)
                        .verticalScroll(rememberScrollState()),
                    cornerRadius = 24.dp,
                    redLight = red,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    MenuRow("实景相机", if (vm.arMode) "开" else "关", red) { vm.toggleArMode() }
                    MenuRow("姿态源", if (vm.sensorMode) "陀螺仪" else "手势", red) {
                        vm.toggleSensorMode()
                    }
                    MenuRow("时间机器", "", red) {
                        showMenu = false; timeMachineExpanded = true
                    }
                    MenuRow("今夜简报", "", red) {
                        showMenu = false; showTonight = true
                    }
                    MenuRow("图层开关", "", red) {
                        showMenu = false; showLayers = true
                    }
                    MenuRow("观测地点", vm.site.nameZh, red) {
                        showMenu = false; showLocation = true
                    }
                    MenuRow("红光夜视", if (vm.redLight) "开" else "关", red) {
                        vm.toggleRedLight()
                    }
                    MenuRow("环境音", if (vm.soundOn) "开" else "关", red) {
                        vm.toggleSound()
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "中西星官",
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = Color(0xFF94A3B8).redLightSafe(red)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        CultureMode.entries.forEach { mode ->
                            NakedTextButton(
                                mode.labelZh,
                                selected = vm.cultureMode == mode,
                                redLight = red
                            ) { vm.cultureMode = mode }
                        }
                    }
                }
            }
        }

        // ---------------- 抽屉层：应用内浮层（与主界面同窗，暗色导航栏保持） ----------------
        AnimatedVisibility(
            visible = showDetail && vm.selected != null,
            enter = fadeIn(tween(240)) +
                slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 },
            exit = fadeOut(tween(200)) +
                slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CelestialDetailSheet(vm) { vm.select(null); showDetail = false }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .hudSafeInsets()
                .padding(bottom = 110.dp)
        ) {
            // 面板过渡统一缓动：FastOutSlowIn + 240/280ms，杜绝生硬直切
            AnimatedVisibility(
                visible = showTonight,
                enter = fadeIn(tween(240)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it / 2 }
            ) {
                TonightSkyGuideSheet(vm, { showTonight = false }) { t ->
                    vm.startGuide(t, autoFocus = true); showTonight = false
                }
            }
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(tween(240)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it / 2 }
            ) {
                SearchAndFilterModal(vm, { showSearch = false }) { t ->
                    vm.startGuide(t); showSearch = false
                }
            }
            AnimatedVisibility(
                visible = showLocation,
                enter = fadeIn(tween(240)), exit = fadeOut(tween(200))
            ) {
                LocationPickerPanel(vm, red) { showLocation = false }
            }
            AnimatedVisibility(
                visible = showLayers,
                enter = fadeIn(tween(240)), exit = fadeOut(tween(200))
            ) {
                LayerSettingsPanel(vm, red, onCapture = { captureSnapshot() },
                    onOpenLocation = { showLocation = true }) { showLayers = false }
            }
        }
    }
}

// ----------------------------------------------------------------------
// 左下角铭牌内容构建（目标变化时重建，避免逐帧字符串分配）
// ----------------------------------------------------------------------

private data class Nameplate(val title: String, val subtitle: String)

private fun rememberNameplate(vm: SkyViewModel): Nameplate? {
    val target = vm.selected ?: return null
    val f = vm.frame
    return when (target) {
        is CelestialTarget.MessierTarget -> {
            val o = target.dso
            val typeName = when (o.type) {
                DsoType.GALAXY -> "旋涡星系"
                DsoType.NEBULA -> "弥漫星云"
                DsoType.GLOBULAR_CLUSTER -> "球状星团"
                DsoType.OPEN_CLUSTER -> "疏散星团"
                DsoType.PLANETARY_NEBULA -> "行星状星云"
                DsoType.SUPERNOVA_REMNANT -> "超新星遗迹"
            }
            val dist = when {
                o.distanceKly <= 0 -> ""
                o.distanceKly < 10 -> "距离 %.0f 光年 · ".format(o.distanceKly * 1000.0)
                else -> "距离 %.0f 万光年 · ".format(o.distanceKly / 10.0)
            }
            Nameplate(
                "${o.catalogId} · ${o.nameZh}",
                "$typeName · ${dist}视星等 ${"%+.1f".format(o.mag)}"
            )
        }
        is CelestialTarget.PlanetTarget -> {
            val meta = f.planets.firstOrNull { it.meta.key == target.key }?.meta
            val en = meta?.nameEn ?: target.key.lowercase().replaceFirstChar { it.uppercase() }
            val desc = meta?.description?.substringBefore('，')?.substringBefore('。') ?: ""
            val sub = if (desc.isNotBlank()) "$desc · ${target.displayName}"
            else "${target.displayName} · 太阳系行星"
            Nameplate("$en · ${target.displayName}", sub)
        }
        is CelestialTarget.MoonTarget -> Nameplate(
            "Moon · 月球",
            "${f.moonEph.phaseNameZh} · 照明率 ${(f.moonEph.illuminationFraction * 100).toInt()}%"
        )
        is CelestialTarget.SunTarget -> Nameplate(
            "Sun · 太阳", "G2V 主序星 · 太阳系中心恒星"
        )
        is CelestialTarget.StarTarget -> {
            val s = target.star
            val dist = if (s.distLy > 0) "距离 %.0f 光年 · ".format(s.distLy) else ""
            Nameplate(
                "${s.nameEn} · ${s.nameZh}",
                "${s.spectral} 型 · ${dist}视星等 ${"%+.2f".format(s.mag)}"
            )
        }
    }
}

/** 菜单抽屉行：标签 + 状态值，无框点击区域。 */
@Composable
private fun MenuRow(label: String, value: String, red: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = Color(0xE6F1F5F9).redLightSafe(red),
            modifier = Modifier.weight(1f)
        )
        if (value.isNotBlank()) {
            Text(
                value,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8).redLightSafe(red)
            )
        }
    }
}

// ----------------------------------------------------------------------
// 地点选择面板
// ----------------------------------------------------------------------

@Composable
private fun LocationPickerPanel(vm: SkyViewModel, red: Boolean, onClose: () -> Unit) {
    val sectionColor = Color(0xFF94A3B8).redLightSafe(red)
    val context = LocalContext.current
    var manualLat by remember { mutableStateOf("") }
    var manualLon by remember { mutableStateOf("") }

    GlassCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 24.dp, redLight = red,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "观测地点 · 暗夜保护区",
                    color = sectionColor,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, "关闭", tint = sectionColor)
                }
            }
            vm.locationProvider.presets.forEach { site ->
                SiteRow(site, vm.site.nameZh == site.nameZh, red) { vm.selectSite(site) }
            }
            Spacer(Modifier.padding(4.dp))
            Text(
                "手动输入坐标", color = MaterialTheme.colorScheme.onSurface.redLightSafe(red),
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Row(Modifier.padding(top = 6.dp)) {
                OutlinedTextField(
                    value = manualLat, onValueChange = { manualLat = it },
                    label = { Text("纬度 -90~90") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = manualLon, onValueChange = { manualLon = it },
                    label = { Text("东经 -180~180") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.padding(top = 8.dp)) {
                NakedTextButton("应用坐标", selected = false, redLight = red) {
                    val lat = manualLat.toDoubleOrNull()
                    val lon = manualLon.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        vm.manualSite("手动坐标", lat, lon)
                    }
                }
            }

            // ---------------- 罗盘方位角微调 ----------------
            Spacer(Modifier.padding(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "罗盘方位角微调 Trim",
                    color = sectionColor,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%+.1f°".format(vm.azimuthTrimDeg),
                    color = MaterialTheme.colorScheme.onSurface.redLightSafe(red),
                    fontSize = 12.sp
                )
            }
            Slider(
                value = vm.azimuthTrimDeg.toFloat(),
                onValueChange = { vm.azimuthTrimDeg = it.toDouble() },
                valueRange = -30f..30f,
                steps = 119 // 0.5° 步进
            )
            Row {
                NakedTextButton("对准太阳校准", selected = false, redLight = red) {
                    val f = vm.frame
                    if (f.sunAltDeg > 3.0) {
                        val t = vm.calibrateAzimuthTo(f.sunAzDeg)
                        Toast.makeText(
                            context, if (t != null) "已对准太阳 · Trim %+.1f°".format(t)
                            else "超出 ±30° 量程", Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "太阳低于地平线 3°，无法校准", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.width(6.dp))
                NakedTextButton("对准月亮校准", selected = false, redLight = red) {
                    val f = vm.frame
                    if (f.moonAltDeg > 3.0) {
                        val t = vm.calibrateAzimuthTo(f.moonAzDeg)
                        Toast.makeText(
                            context, if (t != null) "已对准月亮 · Trim %+.1f°".format(t)
                            else "超出 ±30° 量程", Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "月亮低于地平线 3°，无法校准", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.width(6.dp))
                NakedTextButton("归零", selected = false, redLight = red) {
                    vm.azimuthTrimDeg = 0.0
                }
            }
            Text(
                "户外将设备对准真实日月后点击校准，可消除环境磁场干扰导致的整体方位偏移。",
                fontSize = 10.sp, lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red)
            )
        }
    }
}

@Composable
private fun SiteRow(site: ObservingSite, selected: Boolean, red: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                Color(0x2694A3B8).redLightSafe(red),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            site.nameZh, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.redLightSafe(red)
        )
        Text(
            "%.2f°, %.2f° · %s".format(site.latitudeDeg, site.longitudeEastDeg, site.nameEn),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red)
        )
        Text(
            site.note, fontSize = 10.sp, lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.redLightSafe(red)
        )
    }
}

// ----------------------------------------------------------------------
// 图层设置面板
// ----------------------------------------------------------------------

@Composable
private fun LayerSettingsPanel(
    vm: SkyViewModel,
    red: Boolean,
    onCapture: () -> Unit,
    onOpenLocation: () -> Unit,
    onClose: () -> Unit
) {
    val sectionColor = Color(0xFF94A3B8).redLightSafe(red)
    GlassCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 24.dp, redLight = red,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "图层显示 LAYERS", color = sectionColor,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, "关闭", tint = sectionColor)
                }
            }
            val toggles = listOf(
                Triple("坐标网格", vm.showGrid) { v: Boolean -> vm.showGrid = v },
                Triple("黄道线", vm.showEcliptic) { v: Boolean -> vm.showEcliptic = v },
                Triple("银河光带", vm.showMilkyWay) { v: Boolean -> vm.showMilkyWay = v },
                Triple("星座骨架", vm.showConstellations) { v: Boolean -> vm.showConstellations = v },
                Triple("梅西耶天体", vm.showMessier) { v: Boolean -> vm.showMessier = v },
                Triple("选中轨迹", vm.showTrajectory) { v: Boolean -> vm.showTrajectory = v },
                Triple("空间站过境", vm.showSatellites) { v: Boolean -> vm.showSatellites = v },
                Triple("流星雨辐射点", vm.showMeteorShowers) { v: Boolean -> vm.showMeteorShowers = v }
            )
            toggles.forEach { (label, current, setter) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label, fontSize = 13.sp, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface.redLightSafe(red)
                    )
                    NakedTextButton(
                        if (current) "开" else "关",
                        selected = current, redLight = red
                    ) { setter(!current) }
                }
            }

            // ---------------- 星图文化体系 ----------------
            Spacer(Modifier.padding(6.dp))
            Text(
                "星图文化体系",
                color = sectionColor,
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Row(Modifier.padding(top = 4.dp)) {
                CultureMode.entries.forEach { mode ->
                    NakedTextButton(
                        mode.labelZh,
                        selected = vm.cultureMode == mode, redLight = red
                    ) { vm.cultureMode = mode }
                }
            }

            // ---------------- 快捷操作 ----------------
            Spacer(Modifier.padding(6.dp))
            Text(
                "快捷操作",
                color = sectionColor,
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Row(Modifier.padding(top = 4.dp)) {
                NakedTextButton("星空快门", selected = false, redLight = red) { onCapture() }
                Spacer(Modifier.width(6.dp))
                NakedTextButton("观测地点", selected = false, redLight = red) { onOpenLocation() }
                Spacer(Modifier.width(6.dp))
                NakedTextButton(
                    if (vm.soundOn) "声音·开" else "声音·关",
                    selected = vm.soundOn, redLight = red
                ) { vm.toggleSound() }
            }
        }
    }
}
