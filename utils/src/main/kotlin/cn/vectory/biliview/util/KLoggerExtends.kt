package cn.vectory.biliview.util

import cn.vectory.biliview.utils.BuildConfig
import io.github.oshai.kotlinlogging.KLogger

fun KLogger.fInfo(msg: () -> Any?) {
    info(msg)
}

fun KLogger.fWarn(msg: () -> Any?) {
    warn(msg)
}

fun KLogger.fDebug(msg: () -> Any?) {
    if (BuildConfig.DEBUG) {
        info(msg)
    }
}

fun KLogger.fError(msg: () -> Any?) {
    error(msg)
}

fun KLogger.fException(throwable: Throwable, msg: () -> Any?) {
    warn { "${msg()}: ${throwable.stackTraceToString()}" }
}
