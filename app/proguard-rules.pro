# 第一版不申请 INTERNET 权限，保持本地优先、可审计
-keep class com.afu.ledger.data.local.** { *; }

# ONNX Runtime 通过 JNI 反射查找 Java 类，混淆后必须保留。
# AAR 自带 consumer rules，这里再加一层保险：模型加载失败在 release 上很难排查。
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ML Kit 文字识别（bundled 中文模型）。
#
# R8 会把 ML Kit 内部实现类与字段一起重命名，而它的一部分代码通过反射/运行时字段访问
# 拿到这些成员。实测在 release 构建里直接抛：
#   Attempt to read from field 'pn4 rm4.a' on a null object reference in method 'jz0.a()'
# 表现就是「OCR 状态：不可用」，自动记账在 release 上完全失效 —— 而 debug 一切正常，
# 这是最容易漏掉的一类问题。
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
