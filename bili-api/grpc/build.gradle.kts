import com.google.protobuf.gradle.proto

plugins {
    //id("java-library")
    alias(gradleLibs.plugins.google.protobuf)
    alias(gradleLibs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.grpc.kotlin.stub)
    api(libs.grpc.okhttp)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.protobuf.kotlin)
    implementation(libs.kotlinx.coroutines)
}

// 通过 project dir 解析 proto 根目录，srcDir 与 exclude 过滤共用同一份解析结果，
// 不再依赖进程工作目录（protobuf-gradle-plugin 0.10.x / Gradle 9 懒配置下更稳妥）。
val protoRootDir = file("proto")

sourceSets["main"].proto {
    srcDir(protoRootDir)
    ProtobufConfiguration.excludeProtoFiles(protoRootDir).forEach(::exclude)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        create("java") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.asProvider().get()}"
        }
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.asProvider().get()}"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpc.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        // 注意：这里必须用 forEach（lambda 参数显式为 GenerateProtoTask），不能用 configureEach——
        // 0.10.0 的 GenerateProtoTaskCollection 是 Groovy 原始类型，configureEach 的 Action 泛型
        // 会让 Kotlin 把 it 推断成 Object，导致 builtins/plugins 全部 Unresolved（实测 2026-10-24）。
        all().forEach {
            it.builtins {
                named("java") {
                    // 运行时用的是完整 protobuf-kotlin / grpc-protobuf（非 lite），
                    // 若要开 lite 需同步把依赖换成 protobuf-kotlin-lite / grpc-protobuf-lite。
                    //option("lite")
                }
                create("kotlin") {
                    //option("lite")
                }
            }
            it.plugins {
                create("grpc") {
                    //option("lite")
                }
                create("grpckt") {
                    //option("lite")
                }
            }
        }
    }
}
