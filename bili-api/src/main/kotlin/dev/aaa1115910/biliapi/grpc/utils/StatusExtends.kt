package dev.aaa1115910.biliapi.grpc.utils

import bilibili.rpc.Status
import com.google.protobuf.Message
import io.grpc.Metadata
import io.grpc.StatusException

// 如果没有设置 java_multiple_files = true; 那么就需要使用下面的代码
// 这时候生成的代码会将很多 class 放在同一个 class 里面
// 例如 bilibili.rpc.Status 会被放在 bilibili.rpc.StatusOuterClass$Status 中
// 这时候直接从 typeUrl 中获取 class 名称是不对的
/*
@Suppress("UNCHECKED_CAST")
fun Status.getTypeClass(): Class<Message> {
    val protoClassName = this.detailsList.first().typeUrl.split("/").last()
    val splitProtoClassNames = protoClassName.split(".")
    val nameClass = splitProtoClassNames
        .subList(0, splitProtoClassNames.size - 1)
        .joinToString(".") +
            ".${splitProtoClassNames.last()}OuterClass$${splitProtoClassNames.last()}"
    return Class.forName(nameClass) as Class<Message>
}
*/

/**
 * 取第一条 detail 的类型。
 *
 * 注意：这条链依赖 R8 保留生成消息类的**原始类名**与 `getDefaultInstance()`，
 * 见 app/proguard-rules.pro 的 `-keep class * extends com.google.protobuf.GeneratedMessage`。
 */
@Suppress("UNCHECKED_CAST")
fun Status.getTypeClass(): Class<Message> {
    val detail = detailsList.firstOrNull()
        ?: throw IllegalStateException("gRPC status 不含 detail，无法解析错误类型: $message")
    val nameClass = detail.typeUrl.split("/").last()
    return Class.forName(nameClass) as Class<Message>
}

fun Status.getDetail(): Any {
    val clazz = this.getTypeClass()
    return detailsList.first().unpack(clazz)
}

fun handleGrpcException(it: Throwable) {
    when (it) {
        is StatusException -> {
            val statusDetailsKey = Metadata.Key.of(
                "grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER
            )
            // 没有 status-details 时保留原始 StatusException，不要用 NPE 覆盖掉它
            val data = it.trailers[statusDetailsKey] ?: throw it
            val status = Status.parseFrom(data).getDetail()
            when (status) {
                is bilibili.rpc.Status -> {
                    throw IllegalStateException(status.message)
                }

                is common.ErrorProto -> {
                    throw IllegalStateException(status.message)
                }
            }
        }

        else -> throw it
    }
}