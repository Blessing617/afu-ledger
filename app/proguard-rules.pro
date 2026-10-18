# 第一版不申请 INTERNET 权限，保持本地优先、可审计
-keep class com.afu.ledger.data.local.** { *; }

# ONNX Runtime 通过 JNI 反射查找 Java 类，混淆后必须保留。
# AAR 自带 consumer rules，这里再加一层保险：模型加载失败在 release 上很难排查。
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
