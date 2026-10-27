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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.celestiaview.ar.data.catalog.BrightStarCatalog
import com.celestiaview.ar.data.catalog.ConstellationCatalog
import com.celestiaview.ar.data.catalog.CultureMode
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.data.model.Constellation
import com.celestiaview.ar.data.model.ObservingSite
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.components.CelestialDetailSheet
import com.celestiaview.ar.ui.components.DockItem
import com.celestiaview.ar.ui.components.GlassPanel
import com.celestiaview.ar.ui.components.NakedTextButton
import com.celestiaview.ar.ui.components.SearchAndFilterModal
import com.celestiaview.ar.ui.components.TargetReticleOverlay
import com.celestiaview.ar.ui.components.TimeMachineScrubber
import com.celestiaview.ar.ui.components.TonightSkyGuideSheet
import com.celestiaview.ar.ui.render.SkyCanvasRenderer
import com.celestiaview.ar.ui.theme.CelestialPalette
import com.celestiaview.ar.ui.theme.CelestialType
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
                "culture" -> vm.cultureMode = com.celestiaview.ar.data.catalog.CultureMode.CHINESE
                "culture_dual" -> vm.cultureMode = com.celestiaview.ar.data.catalog.CultureMode.DUAL
                "reset" -> {
                    vm.select(null); showDetail = false; showTonight = false
                    showSearch = false; showLayers = false; showLocation = false
                    timeMachineExpanded = false; vm.clearGuide()
                    if (vm.redLight) vm.toggleRedLight()
                }
            }
        }
    }

    // 系统返回：二级状态逐级收起，纯主星图才放行退出
    BackHandler(
        enabled = showDetail || showSearch || showTonight || showLocation || showLayers ||
            timeMachineExpanded || vm.selected != null || vm.guideTarget != null
    ) {
        when {
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
    val lstH = vm.frame.lstDeg / 15.0

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

        // ---------------- 聚焦锁定态：无框微光状态条（目标锚定 + 解除） ----------------
        if (vm.focusMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .hudSafeInsets()
                    .padding(top = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已锁定 · ${vm.selected?.displayName ?: ""}",
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    color = Color(0xCC7DD3FC).redLightSafe(red),
                    style = TextStyle(shadow = HudTextShadow)
                )
                Text(
                    " · 解除",
                    modifier = Modifier.clickable { vm.exitFocus() },
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    color = Color(0x8F9AB2D6).redLightSafe(red),
                    style = TextStyle(shadow = HudTextShadow)
                )
            }
        }

        // ---------------- 左上角：无框天文台信息流 ----------------
        Column(
            Modifier
                .hudSafeInsets()
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 8.dp)
        ) {
            // 观测地与定位状态（点击进入地点面板）
            Row(
                Modifier
                    .clickable { showLocation = true }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.LocationOn, null,
                    tint = Color(0xFF70D6FF).redLightSafe(red),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    vm.site.nameZh + if (vm.gpsState ==
                        com.celestiaview.ar.core.sensor.LocationProvider.GpsState.SEARCHING
                    ) " · 定位中" else "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xF2EDF5FF).redLightSafe(red),
                    style = TextStyle(shadow = HudTextShadow)
                )
            }
            // 天文遥测
            Text(
                "LST %02d:%02d:%02d · FOV %.0f°".format(
                    lstH.toInt(), ((lstH % 1.0) * 60).toInt(),
                    ((lstH * 60) % 60).toInt(), vm.fovYDeg
                ),
                modifier = Modifier.padding(top = 2.dp, start = 21.dp),
                fontSize = 11.sp,
                style = CelestialType.Tnum.copy(
                    color = Color(0x999AB2D6).redLightSafe(red),
                    shadow = HudTextShadow
                )
            )
            Spacer(Modifier.height(18.dp))
            // 当前视野印记：锁定天体或主星座（点击进入百科）
            val viewMark = remember(vm.frameTick / 30) { currentViewMark(vm) }
            Row(
                Modifier
                    .clickable { if (vm.selected != null) showDetail = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewMarkBadge(viewMark, red)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        viewMark.titleZh,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xF2EDF5FF).redLightSafe(red),
                        style = TextStyle(shadow = HudTextShadow)
                    )
                    Text(
                        viewMark.titleEn,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        color = Color(0x999AB2D6).redLightSafe(red),
                        style = TextStyle(shadow = HudTextShadow)
                    )
                    Text(
                        viewMark.coordLine,
                        fontSize = 10.sp,
                        style = CelestialType.Tnum.copy(
                            color = Color(0x999AB2D6).redLightSafe(red),
                            shadow = HudTextShadow
                        )
                    )
                }
            }
        }

        // ---------------- 右上角：无框工具图标列 ----------------
        Column(
            Modifier
                .hudSafeInsets()
                .align(Alignment.TopEnd)
                .padding(end = 18.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Search, "搜索寻星",
                tint = Color(0xCCDBE6F6).redLightSafe(red),
                modifier = Modifier
                    .size(19.dp)
                    .clickable { showSearch = !showSearch }
                    .padding(2.dp)
            )
            Icon(
                Icons.Outlined.NightsStay, "今夜简报",
                tint = Color(0xCCDBE6F6).redLightSafe(red),
                modifier = Modifier
                    .size(19.dp)
                    .clickable { showTonight = !showTonight }
                    .padding(2.dp)
            )
            Icon(
                Icons.Outlined.AutoAwesome, "星图文化切换",
                tint = Color(0xCCDBE6F6).redLightSafe(red),
                modifier = Modifier
                    .size(19.dp)
                    .clickable { vm.cultureMode = vm.cultureMode.next() }
                    .padding(2.dp)
            )
        }

        // ---------------- 底部：暗夜羽化 + 深空时间与视场印记 + 幽灵船坞 ----------------
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xD902050D)))
                )
        )

        if (!timeMachineExpanded) {
            // 左下角：时间机器入口（脉冲星轨图标 + LST + 日期/倍速）
            val lstText = remember((vm.simTimeMillis / 1000L)) {
                "%02d:%02d:%02d".format(
                    lstH.toInt(), ((lstH % 1.0) * 60).toInt(), ((lstH * 60) % 60).toInt()
                )
            }
            val dateText = remember(vm.simTimeMillis / 3_600_000L) {
                java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(java.util.Date(vm.simTimeMillis))
            }
            val rateText = remember(vm.isLive, vm.timeRate) {
                if (vm.isLive) "LIVE" else "%s×".format(
                    if (vm.timeRate % 1.0 == 0.0) "%d".format(vm.timeRate.toInt())
                    else "%.1f".format(vm.timeRate)
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .hudSafeInsets()
                    .padding(start = 18.dp, bottom = 104.dp)
                    .clickable { timeMachineExpanded = true }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StarTrailIcon(red)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        "LST $lstText",
                        fontSize = 14.sp,
                        style = CelestialType.Tnum.copy(
                            color = Color(0xF2EDF5FF).redLightSafe(red),
                            shadow = HudTextShadow
                        )
                    )
                    Text(
                        "$dateText · $rateText",
                        fontSize = 11.sp,
                        style = CelestialType.Tnum.copy(
                            color = Color(0x999AB2D6).redLightSafe(red),
                            shadow = HudTextShadow
                        )
                    )
                }
            }
            // 右下角：视场 / 姿态 + 聚焦切换开关
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .hudSafeInsets()
                    .padding(end = 18.dp, bottom = 112.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "FOV %.1f° · %s ›".format(vm.fovYDeg, if (vm.sensorMode) "传感器" else "手势"),
                    modifier = Modifier
                        .clickable { vm.toggleSensorMode() }
                        .padding(2.dp),
                    fontSize = 11.sp,
                    style = CelestialType.Tnum.copy(
                        color = Color(0x999AB2D6).redLightSafe(red),
                        shadow = HudTextShadow
                    )
                )
                Spacer(Modifier.width(14.dp))
                val focusOn = vm.focusMode
                Text(
                    if (focusOn) "聚焦 开" else "聚焦 关",
                    modifier = Modifier
                        .clickable {
                            if (focusOn) vm.exitFocus()
                            else (vm.selected ?: vm.snapped?.target)?.let { vm.focusOn(it) }
                        }
                        .padding(2.dp),
                    fontSize = 11.sp,
                    color = (if (focusOn) accent else Color(0x999AB2D6)).redLightSafe(red),
                    style = TextStyle(shadow = HudTextShadow)
                )
            }
        }

        // 吸附目标裸信息行（未锁定时显示，点击聚焦目标并进入百科）
        if (vm.selected == null) {
            vm.snapped?.let { snap ->
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .hudSafeInsets()
                        .padding(bottom = 148.dp)
                        .clickable {
                            vm.focusOn(snap.target)
                            showDetail = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(accent.redLightSafe(red), RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "%s · 高度 %.1f° · 方位 %.0f° · 详情 ›".format(
                            snap.target.displayName, snap.altDeg, snap.azDeg
                        ),
                        fontSize = 12.sp,
                        style = CelestialType.Tnum.copy(
                            color = Color(0xCCDBE6F6).redLightSafe(red),
                            shadow = HudTextShadow
                        )
                    )
                }
            }
        }

        // 幽灵船坞：六项无边框均分悬浮
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .hudSafeInsets()
                .fillMaxWidth()
        ) {
            if (timeMachineExpanded) {
                TimeMachineScrubber(vm, true) { timeMachineExpanded = false }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            ) {
                DockItem(
                    Icons.Outlined.CameraAlt, "实景", vm.arMode, red,
                    Modifier.weight(1f)
                ) { vm.toggleArMode() }
                DockItem(
                    Icons.Outlined.ScreenRotation, "姿态", vm.sensorMode, red,
                    Modifier.weight(1f)
                ) { vm.toggleSensorMode() }
                DockItem(
                    Icons.Outlined.AutoAwesome, "星官",
                    vm.cultureMode != CultureMode.IAU, red,
                    Modifier.weight(1f)
                ) { vm.cultureMode = vm.cultureMode.next() }
                DockItem(
                    Icons.Outlined.Layers, "图层", showLayers, red,
                    Modifier.weight(1f)
                ) { showLayers = !showLayers }
                DockItem(
                    Icons.Outlined.Schedule, "时间", timeMachineExpanded, red,
                    Modifier.weight(1f)
                ) { timeMachineExpanded = !timeMachineExpanded }
                DockItem(
                    Icons.Outlined.Nightlight, "夜视", vm.redLight, red,
                    Modifier.weight(1f)
                ) { vm.toggleRedLight() }
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
                .padding(bottom = 150.dp) // 让出底部印记与船坞
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
// 地点选择面板
// ----------------------------------------------------------------------

@Composable
private fun LocationPickerPanel(vm: SkyViewModel, red: Boolean, onClose: () -> Unit) {
    val accent = if (red) DeepSpacePalette.RedSafePrimary else DeepSpacePalette.AstralGold
    val context = LocalContext.current
    var manualLat by remember { mutableStateOf("") }
    var manualLon by remember { mutableStateOf("") }

    GlassPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 24.dp, redLight = red
    ) {
        Column(
            Modifier
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "观测地点 · 暗夜保护区",
                    color = accent.redLightSafe(red),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, "关闭", tint = accent.redLightSafe(red))
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
                    color = accent.redLightSafe(red),
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
                if (selected) DeepSpacePalette.AstralGold.copy(alpha = 0.15f) else Color.Transparent,
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
    val accent = if (red) DeepSpacePalette.RedSafePrimary else CelestialPalette.AccentGold
    GlassPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 24.dp, redLight = red
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "图层显示 LAYERS", color = accent.redLightSafe(red),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, "关闭", tint = accent.redLightSafe(red))
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
                color = accent.redLightSafe(red),
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

            // ---------------- 快捷操作（自右侧工具栏迁入） ----------------
            Spacer(Modifier.padding(6.dp))
            Text(
                "快捷操作",
                color = accent.redLightSafe(red),
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

// ----------------------------------------------------------------------
// 左上角视野印记与底部时间印记
// ----------------------------------------------------------------------

/** 当前视野印记：锁定天体优先，其次视轴主星座。 */
private data class ViewMark(
    val titleZh: String,
    val titleEn: String,
    val coordLine: String,
    val abbr: String?
)

/** 计算当前视野印记（约 2Hz 调用一次；星座中心解算 48 次属可接受成本）。 */
private fun currentViewMark(vm: SkyViewModel): ViewMark {
    vm.selected?.let { t ->
        val en = when (t) {
            is CelestialTarget.StarTarget -> t.star.nameEn
            is CelestialTarget.PlanetTarget -> t.key.takeIf { it.startsWith("CONST:") }
                ?.removePrefix("CONST:")?.let { abbr ->
                    ConstellationCatalog.byAbbr(abbr)?.nameEn
                } ?: ""
            else -> ""
        }
        val abbr = when (t) {
            is CelestialTarget.StarTarget -> t.star.constellation
            is CelestialTarget.PlanetTarget ->
                t.key.takeIf { it.startsWith("CONST:") }?.removePrefix("CONST:")
            else -> null
        }
        return ViewMark(t.displayName, en, raDecLine(t.raDeg, t.decDeg), abbr)
    }
    val active = vm.constellationActivation.maxByOrNull { it.value }
    val c = if (active != null && active.value > 0.08f) {
        ConstellationCatalog.byAbbr(active.key)
    } else {
        nearestConstellation(vm)
    } ?: ConstellationCatalog.constellations.first()
    val center = ConstellationCatalog.centerRaDec(c)
    return ViewMark(c.nameZh, c.nameEn, raDecLine(center.raDeg, center.decDeg), c.abbr)
}

private fun nearestConstellation(vm: SkyViewModel): Constellation? {
    val f = vm.frame
    val b = vm.boresight
    var best: Constellation? = null
    var bestSep = Double.MAX_VALUE
    for (c in ConstellationCatalog.constellations) {
        val center = ConstellationCatalog.centerRaDec(c)
        val hz = AstrometryEngine.equatorialToHorizontal(
            center.raDeg, center.decDeg, f.lstDeg, f.latitudeDeg
        )
        val sep = AstrometryEngine.angularSeparationDeg(
            b.azDeg, b.altDeg, hz.azDeg, hz.altDeg
        )
        if (sep < bestSep) {
            bestSep = sep
            best = c
        }
    }
    return best
}

private fun raDecLine(raDeg: Double, decDeg: Double): String {
    val h = raDeg / 15.0
    val hh = h.toInt()
    val mm = ((h - hh) * 60).toInt()
    val sign = if (decDeg < 0) "-" else "+"
    val ad = kotlin.math.abs(decDeg)
    val dd = ad.toInt()
    val dm = ((ad - dd) * 60).toInt()
    return "RA %02dh %02dm · Dec %s%02d° %02d′".format(hh, mm, sign, dd, dm)
}

/** 22dp 星座微缩连线图（静态赤道坐标归一化，remember 级缓存）。 */
private class MiniFigure(val pts: FloatArray, val edges: List<Pair<Int, Int>>)

@Composable
private fun ViewMarkBadge(mark: ViewMark, red: Boolean) {
    val lineColor = Color(0xCCDBE6F6).redLightSafe(red)
    val fig = remember(mark.abbr) {
        val abbr = mark.abbr ?: return@remember null
        val c = ConstellationCatalog.byAbbr(abbr) ?: return@remember null
        val members = c.memberStarIds.map { BrightStarCatalog.byId(it) }
        if (members.isEmpty()) return@remember null
        var minRa = Double.MAX_VALUE; var maxRa = -Double.MAX_VALUE
        var minDec = Double.MAX_VALUE; var maxDec = -Double.MAX_VALUE
        for (s in members) {
            if (s.raDeg < minRa) minRa = s.raDeg
            if (s.raDeg > maxRa) maxRa = s.raDeg
            if (s.decDeg < minDec) minDec = s.decDeg
            if (s.decDeg > maxDec) maxDec = s.decDeg
        }
        val spanRa = (maxRa - minRa).coerceAtLeast(1e-3)
        val spanDec = (maxDec - minDec).coerceAtLeast(1e-3)
        val pts = FloatArray(members.size * 2)
        members.forEachIndexed { i, s ->
            pts[i * 2] = ((s.raDeg - minRa) / spanRa).toFloat()
            pts[i * 2 + 1] = (1.0 - (s.decDeg - minDec) / spanDec).toFloat()
        }
        // 边以全局星表 ID 表示，映射为成员序号
        val idToIdx = HashMap<Int, Int>(members.size * 2)
        members.forEachIndexed { i, s -> idToIdx[s.id] = i }
        val edges = c.edges.mapNotNull { (a, b) ->
            val ia = idToIdx[a]
            val ib = idToIdx[b]
            if (ia != null && ib != null) ia to ib else null
        }
        MiniFigure(pts, edges)
    }
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val pad = 2.dp.toPx()
        val sx = w - pad * 2
        val sy = h - pad * 2
        fun px(u: Float) = pad + u * sx
        fun py(v: Float) = pad + v * sy
        if (fig == null) {
            // 非星座目标：四芒星光印记
            val c = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
            drawLine(lineColor, Offset(c.x - 7.dp.toPx(), c.y), Offset(c.x + 7.dp.toPx(), c.y), 0.8.dp.toPx())
            drawLine(lineColor, Offset(c.x, c.y - 7.dp.toPx()), Offset(c.x, c.y + 7.dp.toPx()), 0.8.dp.toPx())
            drawLine(lineColor, Offset(c.x - 4.dp.toPx(), c.y - 4.dp.toPx()), Offset(c.x + 4.dp.toPx(), c.y + 4.dp.toPx()), 0.6.dp.toPx())
            drawLine(lineColor, Offset(c.x - 4.dp.toPx(), c.y + 4.dp.toPx()), Offset(c.x + 4.dp.toPx(), c.y - 4.dp.toPx()), 0.6.dp.toPx())
            drawCircle(lineColor, 1.2.dp.toPx(), c)
            return@Canvas
        }
        for ((a, b) in fig.edges) {
            drawLine(
                lineColor,
                Offset(px(fig.pts[a * 2]), py(fig.pts[a * 2 + 1])),
                Offset(px(fig.pts[b * 2]), py(fig.pts[b * 2 + 1])),
                0.6.dp.toPx()
            )
        }
        for (i in 0 until fig.pts.size / 2) {
            drawCircle(lineColor, 0.7.dp.toPx(), Offset(px(fig.pts[i * 2]), py(fig.pts[i * 2 + 1])))
        }
    }
}

/** 18dp 脉冲星轨小图标：弧线 + 端点星。 */
@Composable
private fun StarTrailIcon(red: Boolean) {
    val color = Color(0xFF70D6FF).redLightSafe(red)
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val stroke = 1.dp.toPx()
        val r = 6.5.dp.toPx()
        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke)
        )
        // 弧端星点（200° 端）
        val a = Math.toRadians(200.0)
        drawCircle(color, 1.4.dp.toPx(), Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat()))
    }
}
