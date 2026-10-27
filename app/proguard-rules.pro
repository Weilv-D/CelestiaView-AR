# Aetheris · 苍穹之境 — R8 release 规则
# Compose / CameraX 由 AGP 与 AAR 内置 consumer 规则覆盖，这里只补应用特有项。

# 保留行号便于线上崩溃归因（映射文件同时输出）
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod

# 小组件 RemoteViews 引用的布局/Bitmap 工厂
-keep class com.celestiaview.ar.widget.TonightSkyWidgetProvider { *; }
