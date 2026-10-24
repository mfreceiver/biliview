# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
# 崩溃栈可读性：本 App 有 LogCatcherUtil（logcat 落盘）+ LogsActivity 供导出日志，
# 行号对定位问题价值高，代价是 dex 体积略增。已启用。
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# akdanmaku
# AGP 9 起 R8 默认 strict full mode：keep 规则不再隐式保留构造器/子包。
# ECS 组件与 DanmakuContext 构造类均为运行时反射实例化，静态分析不可见，
# 故整库保守 keep（库体积小，代价可接受）。旧规则仅覆盖 component.* 单层包，子包会漏。
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.jnigen.BuildTarget*
-dontwarn com.badlogic.gdx.graphics.g2d.freetype.FreetypeBuild
-keep class com.badlogic.gdx.controllers.android.AndroidControllers
-keep class com.kuaishou.akdanmaku.** { *; }

# okhttp
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# kotlin serialization

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
# 泛型实参只存在于 Signature 属性里：kotlinx-serialization 库自带的规则文件不保留
# Signature，而本项目有 body<BiliResponse<Int>>() / decodeFromJsonElement<List<X>>()
# 这类运行时 serializer 查找，缺了它会在反序列化时报 Serializer for class not found。
-keepattributes Signature,InnerClasses,EnclosingMethod

# Serializer for classes with named companion objects are retrieved using `getDeclaredClasses`.
# If you have any, uncomment and replace classes with those containing named companion objects.
#-keepattributes InnerClasses # Needed for `getDeclaredClasses`.
#-if @kotlinx.serialization.Serializable class
#com.example.myapplication.HasNamedCompanion, # <-- List serializable classes with named companions.
#com.example.myapplication.HasNamedCompanion2
#{
#    static **$* *;
#}
#-keepnames class <1>$$serializer { # -keepnames suffices; class is kept when serializer() is kept.
#    static <1>$$serializer INSTANCE;
#}

# ktor 混淆后，请求参数会莫名其妙消失
-keep class io.ktor.**
# 这部分是加上不混淆 ktor 后冒出来的 missing rules
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# LibVLC
# 原来这里是 `-keep class org.videolan.libvlc.** { *; }`，但当前没有任何模块依赖
# :libs:libVLC（settings 里有 include，全仓无 project(":libs:libVLC") 也无可执行引用），
# 发货 APK 的 mapping 里 videolan 命中 0、APK 里也没有 vlc 的 so —— 规则是空转的，故删除。
# 将来真正接上 VLC 解码时，需要把这条 keep 与 libVLCVersion 下载链路一起恢复。

# gRPC
# 生成的 proto 消息类共 24 个包：23 个在 bilibili.* 下（bilibili.app.*、bilibili.metadata.*、
# bilibili.pgc.gateway.player.v2、bilibili.community.service.dm.v1 等），另有顶层包 common
# （common/ErrorProto.proto 无 java_package，落在顶层 common）。只有 status.proto 生成到
# bilibili.rpc —— 单靠下一行只保住 1 个消息类。
-keep class bilibili.rpc.** { *; }
# 运行时反射链（bili-api/.../grpc/utils/StatusExtends.kt）：
#   Status.getTypeClass() -> Class.forName(服务端下发的 typeUrl)
#   Status.getDetail()    -> Any.unpack(Class) -> protobuf Internal.getDefaultInstance(Class)
#                                            -> clazz.getMethod("getDefaultInstance").invoke(...)
# R8 静态分析看不到这两条字符串/反射链，而 `-keep class com.google.protobuf.**` 只覆盖
# protobuf 运行时自身，不含 bilibili.* / common 下的生成消息类。AGP 9 起 R8 默认 strict
# full mode，这类生成类会被改名、成员被裁 —— gRPC 错误路径（登录失败/-352/播放报错）会抛
# ClassNotFoundException 或 "Failed to get default instance"。故按基类整库 keep。
# 注意：protobuf 4.x 生成基类是 GeneratedMessage（GeneratedMessageV3 已退化为 deprecated stub）。
-keep class * extends com.google.protobuf.GeneratedMessage { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.GeneratedMessageV3$Builder
-dontwarn com.google.protobuf.GeneratedMessageV3$BuilderParent
-dontwarn com.google.protobuf.GeneratedMessageV3$FieldAccessorTable
-dontwarn com.google.protobuf.GeneratedMessageV3
-dontwarn com.google.protobuf.RepeatedFieldBuilderV3

# gRPC provider 经 java.util.ServiceLoader 按 META-INF/services 查找
# （触发点 bili-api/.../grpc/utils/Channel.kt 的 ManagedChannelBuilder.forAddress）。
# R8 对 ServiceLoader 有内建重写，理论上不需要显式规则；但这条链一旦断掉整个 API 层全挂，
# 而 keep 成本几乎为零，故显式兜底（只保类名与无参构造，不阻止方法优化）。
-keep class * implements io.grpc.ManagedChannelProvider { <init>(); }
-keep class * implements io.grpc.NameResolverProvider { <init>(); }
-keep class * implements io.grpc.LoadBalancerProvider { <init>(); }

# kotlin-logging
-dontwarn ch.qos.logback.classic.Level
-dontwarn ch.qos.logback.classic.Logger
-dontwarn ch.qos.logback.classic.LoggerContext
-dontwarn ch.qos.logback.classic.spi.ILoggingEvent
-dontwarn ch.qos.logback.classic.spi.LogbackServiceProvider
-dontwarn ch.qos.logback.classic.spi.LoggingEvent

# geetest
-keep class com.geetest.sdk.** {*;}
