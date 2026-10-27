package com.celestiaview.ar

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.celestiaview.ar.widget.TonightSkyWidgetProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 桌面小组件实机测试：月相位图几何与 RemoteViews 构建。 */
@RunWith(AndroidJUnit4::class)
class WidgetInstrumentedTest {

    @Test
    fun fullMoonBitmapIsBrightAtCenter() {
        val bmp: Bitmap = TonightSkyWidgetProvider.moonBitmap(180.0, 96)
        val px = bmp.getPixel(48, 48)
        // 满月中心应为亮色（R/G/B 均高于暗面底色 0x2A2E38）
        assertTrue("满月中心应为亮色: $px",
            (px shr 16) and 0xFF > 0xC0 && (px shr 8) and 0xFF > 0xC0)
    }

    @Test
    fun newMoonBitmapIsDarkAtCenter() {
        val bmp = TonightSkyWidgetProvider.moonBitmap(2.0, 96)
        val px = bmp.getPixel(48, 48)
        assertTrue("新月中心应为暗色: $px", ((px shr 16) and 0xFF) < 0x60)
    }

    @Test
    fun buildViewsResolvesWidgetLayout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // RemoteViews 构建即完成布局与资源解析
        TonightSkyWidgetProvider().buildViews(context)
    }
}
