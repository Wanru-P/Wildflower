package com.floralens.ui.presentation

import java.io.IOException

/** Error display boundary: never expose HTTP, SQL, paths, credentials or platform exceptions. */
object ProductMessages {
    fun error(error: Throwable, fallback: String): String {
        if (error is SecurityException) return "需要相应权限，请前往系统设置允许后重试。"
        if (error.message.orEmpty().let { it.contains("disk full", ignoreCase = true) || it.contains("ENOSPC") })
            return "存储空间不足，请腾出空间后重试。"
        if (error is IOException && error !is java.io.FileNotFoundException)
            return "暂时无法完成操作，请检查网络或存储空间后重试。"
        val message = error.message.orEmpty()
        // Preserve the app's actionable validation messages, not arbitrary upstream text.
        return message.takeIf { it in validationMessages } ?: fallback
    }

    private val validationMessages = setOf(
        "观察已发生变化，请查看更新预览后重试", "请选择观察照片", "植物档案不存在", "观察记录不存在",
        "内容已更新，请查看更新预览后重试", "文字超出模板容量，请选择其他模板或整理观察备注；原文未截断",
        "昵称不能超过 100 字", "请填写植物昵称", "请输入有效日期", "请输入有效时间",
        "备注不能超过 500 字", "照片无法读取，请重新选择", "照片已失效，请重新选择",
        "昵称应为 1–100 个字符", "请选择同物种的植物档案", "备注最多 500 个字符",
        "封面必须来自本株植物的照片", "照片已丢失，不能设为封面", "该植物档案已删除",
        "照片为空或超过 32 MB", "照片超过 32 MB，请选择较小的图片",
        "观察不属于这株植物", "观察照片已失效，请重新选择或添加观察", "植物昵称需为 1–100 字",
        "名称过长，请先整理植物档案（名称最多 200 字，学名 300 字）",
        "此观察备注超过诗笺 600 字容量；请选另一条观察或先编辑备注，原文不会自动截断",
        "观察日期超出支持范围",
        "观察已删除，请重新选择照片", "植物档案已删除",
        "照片不可读或超过 32 MB", "照片尺寸无效", "照片无法解码，请重新选择",
    )
}
