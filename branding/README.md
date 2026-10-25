# 等风 · 图标

图标为风中的茶叶与两缕轻风，采用珍珠白、浅茶色及雾蓝边缘，延续“白茶柔雾”的玻璃质感。图标不含文字，名称由 Android 桌面显示为“等风”。

- 原始透明 PNG：dengfeng-icon-master.png。
- Android 资源：app/src/main/res/drawable-nodpi/dengfeng_foreground.png，仅从原图等比缩小为 512×512，保留透明通道。
- 自适应图标：分离的浅色渐变背景与透明前景，保留额外安全边距，适配桌面圆形或圆角方形裁切。
- Android 13 及以上提供同一意象的原生单色主题图标；通知使用简化的叶片与风线，保持小尺寸清晰。

原图使用内置 image_gen 工具生成；自适应背景、单色图标和资源组装为项目内原生代码。没有外部图标包或额外运行时依赖。

前景边距按图标尺寸的 14% 设置，避免固定 dp 边距在不同桌面尺寸下造成裁切。比例边距由 [Android InsetDrawable](https://android.googlesource.com/platform/frameworks/base/+/master/graphics/java/android/graphics/drawable/InsetDrawable.java) 提供；项目最低 Android 8.0 支持该方式。

## 最终生成提示词

```text
Use case: logo-brand. Asset type: production Android adaptive launcher icon foreground for a Chinese personal finance app renamed 等风 (Dengfeng, waiting for the wind). Generate ONE square 1024x1024 icon asset with a genuinely transparent background. No text or letters anywhere. Subject: one elegant curved tea leaf caught in a gentle breeze, accompanied by two short flowing wind ribbons, composed into a compact cohesive emblem. Style: refined frosted liquid glass, translucent pearl-white highlights, very pale white-tea sage and a legible soft slate-blue rim; calm, airy, fresh, minimal, softly dimensional, premium but restrained. Keep recognisable at 48px: broad clear leaf silhouette, only one subtle center vein, two clean wind strokes, no fine decorative details. Composition: centered symbol occupying only the central 58% of canvas in both dimensions, with generous transparent safety margin for Android adaptive icon circle/squircle masks. No tile, no rounded-square container, no frame, no scenery, no coins, no currency symbol, no text, no watermark. The app UI already uses pale mist-white, muted tea sage, and slate-blue; the icon should feel like a gentle breeze and a tea leaf, quiet good money habits. Deliver a clean isolated glass emblem with actual alpha transparency, not a checkerboard painted into the image.
```

实际生成尺寸为 1254×1254 RGBA，已检查透明通道；原文件完整保留。
