import java.io.File

object ProtobufConfiguration {
    private val usedProtoFiles = setOf(
        "bilibili/app/archive/middleware/v1/preload.proto",
        "bilibili/app/archive/v1/archive.proto",
        "bilibili/app/card/v1/ad.proto",
        "bilibili/app/card/v1/card.proto",
        "bilibili/app/card/v1/common.proto",
        "bilibili/app/card/v1/single.proto",
        "bilibili/app/dynamic/v2/dynamic.proto",
        "bilibili/app/interfaces/v1/history.proto",
        "bilibili/app/interfaces/v1/search.proto",
        "bilibili/app/playeronline/v1/playeronline.proto",
        "bilibili/app/playerunite/v1/playerunite.proto",
        "bilibili/app/show/popular/v1/popular.proto",
        "bilibili/app/view/v1/view.proto",
        "bilibili/app/viewunite/common.proto",
        "bilibili/community/service/dm/v1/dm.proto",
        "bilibili/dagw/component/avatar/common/common.proto",
        "bilibili/dagw/component/avatar/v1/avatar.proto",
        "bilibili/dagw/component/avatar/v1/plugin.proto",
        "bilibili/main/community/reply/v1/reply.proto",
        "bilibili/metadata/device/device.proto",
        "bilibili/metadata/locale/locale.proto",
        "bilibili/metadata/metadata.proto",
        "bilibili/metadata/network/network.proto",
        "bilibili/pagination/pagination.proto",
        "bilibili/pgc/gateway/player/v2/playurl.proto",
        "bilibili/playershared/playershared.proto",
        "bilibili/polymer/app/search/v1/search.proto",
        "bilibili/rpc/status.proto",
        "common/ErrorProto.proto",
    )

    /**
     * 返回 [protoRootDir] 下未使用的 .proto（相对路径，`/` 分隔），供 protobuf 的 proto sourceSet
     * 做 exclude 过滤。
     *
     * proto 根目录由调用方（构建脚本）通过 Gradle 的 project dir 解析后传入，不再依赖进程工作目录，
     * 避免 Gradle 9 懒配置 / 从其他目录或 included build 调用时静默算出空集合（那样会把全部 122 个
     * proto 都交给 protoc 编译）。
     */
    fun excludeProtoFiles(protoRootDir: File): Set<String> {
        require(protoRootDir.isDirectory) {
            "protobuf proto 根目录不存在: ${protoRootDir.absolutePath}"
        }
        return getAllProtoFiles(protoRootDir) - usedProtoFiles
    }

    private fun getAllProtoFiles(rootDir: File): Set<String> {
        val protoFiles = mutableSetOf<String>()
        rootDir.walk().forEach {
            if (it.isFile && it.extension == "proto") {
                protoFiles.add(it.relativeTo(rootDir).invariantSeparatorsPath)
            }
        }
        return protoFiles
    }
}
