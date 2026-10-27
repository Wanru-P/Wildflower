package com.floralens.domain.model

enum class VideoAvailability(val message: String?) {
    LOADING(null), READY(null),
    OFFLINE_MODE("当前为离线浏览，视频暂不可用。"),
    NO_NETWORK("当前网络不可用，暂时无法加载视频。"),
    UNAVAILABLE("当前视频服务暂不可用。仍可阅读植物笔记与文化资料。"),
    EMPTY("暂无该植物的视频资料。"),
}

fun cachedVideoAvailability(configured: Boolean, onlineMode: Boolean, connected: Boolean): VideoAvailability = when {
    !configured -> VideoAvailability.UNAVAILABLE
    !onlineMode -> VideoAvailability.OFFLINE_MODE
    !connected -> VideoAvailability.NO_NETWORK
    else -> VideoAvailability.UNAVAILABLE
}
