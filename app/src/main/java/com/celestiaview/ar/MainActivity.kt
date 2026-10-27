package com.celestiaview.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celestiaview.ar.core.media.SkySnapshotExporter
import com.celestiaview.ar.data.catalog.CultureMode
import com.celestiaview.ar.data.model.ObservingSite
import com.celestiaview.ar.ui.SkyViewModel
import com.celestiaview.ar.ui.components.CelestialDetailSheet
import com.celestiaview.ar.ui.components.GlassCard
import com.celestiaview.ar.ui.components.GlassCapsuleButton
import com.celestiaview.ar.ui.components.GlassCapsuleLabelButton
import com.celestiaview.ar.ui.components.GlassPanel
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
        enableEdgeToEdge()
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
                    vm.starByName(intent.getStringExtra("target") ?: "")?.let {
                        vm.select(com.celestiaview.ar.data.model.CelestialTarget.StarTarget(it))
                        showDetail = true
                    }
                }
                "guide" -> {
                    vm.starByName(intent.getStringExtra("target") ?: "")?.let {
                        vm.startGuide(com.celestiaview.ar.data.model.CelestialTarget.StarTarget(it))
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
                    var lastTimeMs = 0L
                    var velX = 0f
                    var velY = 0f
                    detectDragGestures(
                        onDragStart = { lastTimeMs = 0L; velX = 0f; velY = 0f },
                        onDrag = { change, drag ->
                            val dtSec = if (lastTimeMs == 0L) 0.016f
                            else ((change.uptimeMillis - lastTimeMs).coerceAtLeast(1L)) / 1000f
                            lastTimeMs = change.uptimeMillis
                            velX = drag.x / dtSec * 0.65f + velX * 0.35f
                            velY = drag.y / dtSec * 0.65f + velY * 0.35f
                            vm.orbit.onDrag(drag.x, drag.y, degPerPxY, degPerPxY)
                        },
                        onDragEnd = {
                            vm.orbit.onFling(velX, velY, degPerPxY, degPerPxY)
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

        // ---------------- 硬件降级提示胶囊（无相机授权 / 相机不可用 / 无陀螺仪） ----------------
        val degradeHint = when {
            vm.arMode && !cameraGranted -> "相机未授权 · 已切换纯深空大气模式"
            vm.arMode && vm.cameraBindFailed -> "相机不可用 · 已切换纯深空大气模式"
            !vm.sensorAvailable -> "未检测到陀螺仪 · 触摸拖动巡天"
            else -> null
        }
        if (degradeHint != null) {
            GlassCard(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
                cornerRadius = 18.dp,
                redLight = red,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    degradeHint,
                    fontSize = 11.sp,
                    color = accent.redLightSafe(red)
                )
            }
        }

        // ---------------- 顶部极简悬浮顶栏 ----------------
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCard(
                Modifier.weight(1f),
                cornerRadius = 20.dp,
                redLight = red,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place, null,
                        tint = CelestialPalette.AccentCyan.redLightSafe(red),
                        modifier = Modifier.width(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            vm.site.nameZh + if (vm.gpsState ==
                                com.celestiaview.ar.core.sensor.LocationProvider.GpsState.SEARCHING
                            ) " · 定位中" else "",
                            color = CelestialPalette.TextPrimary.redLightSafe(red),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "LST %02d:%02d:%02d · FOV %.0f°".format(
                                lstH.toInt(), ((lstH % 1.0) * 60).toInt(),
                                ((lstH * 60) % 60).toInt(), vm.fovYDeg
                            ),
                            color = CelestialPalette.TextMuted.redLightSafe(red),
                            fontSize = 11.sp,
                            style = CelestialType.Tnum
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            GlassCapsuleButton(Icons.Default.Search, "搜索寻星", redLight = red) {
                showSearch = !showSearch
            }
            Spacer(Modifier.width(10.dp))
            GlassCapsuleButton(Icons.Default.Nightlife, "今夜简报", redLight = red) {
                showTonight = !showTonight
            }
        }

        // ---------------- 底部：锁定卡 + 时间机器 + 流光船坞 ----------------
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (vm.selected == null && !showDetail) {
                vm.snapped?.let { snap ->
                    // 88dp 紧凑预览胶囊卡：发光图标 + 双语名 + 实时高度方位 + 详情入口
                    GlassCard(
                        Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clickable { vm.select(snap.target); showDetail = true },
                        cornerRadius = 24.dp,
                        redLight = red,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(44.dp)
                                    .height(44.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                CelestialPalette.AccentCyan.copy(alpha = 0.45f),
                                                Color.Transparent
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Explore, null,
                                    tint = CelestialPalette.TextPrimary.redLightSafe(red),
                                    modifier = Modifier.width(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    snap.target.displayName,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    color = CelestialPalette.TextPrimary.redLightSafe(red)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "高度 %.1f° · 方位 %.0f°".format(snap.altDeg, snap.azDeg),
                                    fontSize = 11.sp,
                                    style = CelestialType.Tnum,
                                    color = CelestialPalette.TextMuted.redLightSafe(red)
                                )
                            }
                            Text(
                                "详情 ›",
                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                color = CelestialPalette.AccentCyan.redLightSafe(red)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            TimeMachineScrubber(vm, timeMachineExpanded) { timeMachineExpanded = !timeMachineExpanded }

            Spacer(Modifier.height(10.dp))

            // 流光磨砂玻璃船坞栏
            GlassCard(
                cornerRadius = 28.dp,
                redLight = red,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    GlassCapsuleButton(
                        Icons.Default.CameraAlt, "AR 实景",
                        selected = vm.arMode, redLight = red
                    ) { vm.toggleArMode() }
                    GlassCapsuleButton(
                        Icons.Default.ScreenRotation, "陀螺仪",
                        selected = vm.sensorMode, redLight = red
                    ) { vm.toggleSensorMode() }
                    GlassCapsuleButton(
                        Icons.Default.AutoAwesome, "星座线",
                        selected = vm.showConstellations, redLight = red
                    ) { vm.showConstellations = !vm.showConstellations }
                    GlassCapsuleButton(
                        Icons.Default.GridOn, "网格",
                        selected = vm.showGrid, redLight = red
                    ) { vm.showGrid = !vm.showGrid }
                    GlassCapsuleButton(
                        Icons.Default.Schedule, "时间机器",
                        selected = timeMachineExpanded, redLight = red
                    ) { timeMachineExpanded = !timeMachineExpanded }
                    GlassCapsuleButton(
                        Icons.Default.Nightlight, "红光模式",
                        selected = vm.redLight, redLight = red
                    ) { vm.toggleRedLight() }
                }
            }
        }

        // ---------------- 抽屉层：独立覆盖，不挤占底部控件 ----------------
        if (showDetail && vm.selected != null) {
            ModalBottomSheet(
                onDismissRequest = { vm.select(null); showDetail = false },
                containerColor = Color.Transparent
            ) {
                CelestialDetailSheet(vm) { vm.select(null); showDetail = false }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 168.dp) // 让出时间机器条 + 船坞高度
        ) {
            AnimatedVisibility(
                visible = showTonight,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                TonightSkyGuideSheet(vm, { showTonight = false }) { t ->
                    vm.startGuide(t); showTonight = false
                }
            }
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                SearchAndFilterModal(vm, { showSearch = false }) { t ->
                    vm.startGuide(t); showSearch = false
                }
            }
            AnimatedVisibility(visible = showLocation, enter = fadeIn(), exit = fadeOut()) {
                LocationPickerPanel(vm, red) { showLocation = false }
            }
            AnimatedVisibility(visible = showLayers, enter = fadeIn(), exit = fadeOut()) {
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
                    Icon(Icons.Default.Close, "关闭", tint = accent.redLightSafe(red))
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
                GlassCapsuleLabelButton(Icons.Default.LocationOn, "应用坐标", redLight = red) {
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
                GlassCapsuleLabelButton(Icons.Default.Star, "对准太阳校准", redLight = red) {
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
                GlassCapsuleLabelButton(Icons.Default.Star, "对准月亮校准", redLight = red) {
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
                GlassCapsuleLabelButton(Icons.Default.Close, "归零", redLight = red) {
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
                    Icon(Icons.Default.Close, "关闭", tint = accent.redLightSafe(red))
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
                    GlassCapsuleLabelButton(
                        Icons.Default.Layers, if (current) "开" else "关",
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
                    GlassCapsuleLabelButton(
                        Icons.Default.Star, mode.labelZh,
                        selected = vm.cultureMode == mode, redLight = red
                    ) { vm.cultureMode = mode }
                    Spacer(Modifier.width(5.dp))
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
                GlassCapsuleLabelButton(
                    Icons.Default.PhotoCamera, "星空快门", redLight = red
                ) { onCapture() }
                Spacer(Modifier.width(6.dp))
                GlassCapsuleLabelButton(
                    Icons.Default.Place, "观测地点", redLight = red
                ) { onOpenLocation() }
                Spacer(Modifier.width(6.dp))
                GlassCapsuleLabelButton(
                    if (vm.soundOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    if (vm.soundOn) "声音·开" else "声音·关",
                    selected = vm.soundOn, redLight = red
                ) { vm.toggleSound() }
            }
        }
    }
}
