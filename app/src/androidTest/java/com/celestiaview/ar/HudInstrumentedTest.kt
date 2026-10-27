package com.celestiaview.ar

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.celestiaview.ar.data.model.CelestialTarget
import com.celestiaview.ar.ui.SkyViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 实机集成测试：深度链接导航、时间机器状态机、传感器切换、帧循环活性。
 *
 * 采用 ActivityScenario + 状态断言而非 ComposeTestRule：星图应用以
 * withFrameNanos 60fps 持续重组，ComposeIdlingResource 永不空闲，
 * Espresso 交互注入在这类应用上无法就绪。
 */
@RunWith(AndroidJUnit4::class)
class HudInstrumentedTest {

    @Before
    fun parkFrameLoop() {
        // 挂起 60fps 渲染循环：状态断言无需帧推进，而全速软渲染会占满主线程，
        // onActivity 回调可能排队数分钟；前 30 帧仍放行以保证首次组合完成。
        SkyViewModel.framesSuspended = true
    }

    @Test
    fun deepLinkDetailSelectsTarget() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { act ->
                act.navBus.tryEmit(
                    Intent(act, MainActivity::class.java)
                        .putExtra("navigate", "detail").putExtra("target", "Kochab")
                )
            }
            awaitState(sc, "selected Kochab", 5000) { vm ->
                (vm.selected as? CelestialTarget.StarTarget)?.star?.nameEn == "Kochab"
            }
        }
    }

    @Test
    fun deepLinkGuideSetsTarget() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { act ->
                act.navBus.tryEmit(
                    Intent(act, MainActivity::class.java)
                        .putExtra("navigate", "guide").putExtra("target", "Vega")
                )
            }
            awaitState(sc, "guide Vega", 5000) { vm ->
                vm.guideTarget?.displayName?.contains("织女") == true
            }
        }
    }

    @Test
    fun deepLinkRedLightToggles() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { act ->
                act.navBus.tryEmit(
                    Intent(act, MainActivity::class.java).putExtra("navigate", "redlight")
                )
            }
            awaitState(sc, "redLight on", 5000) { it.redLight }
            sc.onActivity { act ->
                act.navBus.tryEmit(
                    Intent(act, MainActivity::class.java).putExtra("navigate", "reset")
                )
            }
            awaitState(sc, "redLight off", 5000) { !it.redLight }
        }
    }

    @Test
    fun timeMachineRateStateMachine() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { act ->
                val vm = act.vmForTest!!
                vm.applyTimeRate(3600.0)
                assertEquals(3600.0, vm.timeRate, 1e-9)
                assertFalse(vm.isLive)
                vm.nudgeTime(3_600_000L, 2)
                vm.backToLive()
                assertEquals(1.0, vm.timeRate, 1e-9)
                assertTrue(vm.isLive)
            }
        }
    }

    @Test
    fun sensorModeToggleFlipsState() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { act ->
                val vm = act.vmForTest!!
                val before = vm.sensorMode
                vm.toggleSensorMode()
                assertEquals(!before, vm.sensorMode)
                vm.toggleSensorMode()
                assertEquals(before, vm.sensorMode)
            }
        }
    }

    @Test
    fun frameLoopAdvancesTick() {
        SkyViewModel.framesSuspended = false // 本用例需要真实帧推进
        // 单实例内测量：跨两次 launch 是不同 ViewModel 实例，frameTick 不可比
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            var t0 = -1L
            sc.onActivity { act -> t0 = act.vmForTest!!.frameTick }
            Thread.sleep(1500)
            sc.onActivity { act ->
                assertTrue(
                    "帧循环应持续推进 frameTick (t0=$t0)",
                    act.vmForTest!!.frameTick > t0 + 10
                )
            }
        }
    }

    @Test
    fun tonightHighlightsAvailable() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { act ->
                assertTrue("今夜榜单不应为空", act.vmForTest!!.tonightHighlights().isNotEmpty())
            }
        }
    }

    /** 轮询等待 VM 进入目标状态（navBus 经主线程协程异步投递）。 */
    private fun awaitState(
        sc: ActivityScenario<MainActivity>,
        what: String,
        timeoutMs: Long,
        predicate: (SkyViewModel) -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = false
        while (System.currentTimeMillis() < deadline) {
            sc.onActivity { act -> last = predicate(act.vmForTest!!) }
            if (last) return
            Thread.sleep(150)
        }
        throw AssertionError("等待状态超时: $what")
    }
}
