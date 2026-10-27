package com.floralens.data.culture

import java.net.URI

/** A service base can include a CloudBase gateway mount; old /v1/nature debug URLs remain supported. */
object NatureServiceEndpoints {
    fun base(configured: String): String {
        val uri = URI(configured)
        require(uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.fragment == null)
        require(uri.scheme in setOf("https", "http"))
        val raw = uri.rawPath.orEmpty()
        require(Regex("[A-Za-z0-9_./~-]*").matches(raw) && !raw.contains("//"))
        require(raw.split('/').none { it == "." || it == ".." })
        val path = raw.trimEnd('/').replace(Regex("/v1/(chat|nature|session)$"), "")
        return URI(uri.scheme, null, uri.host, uri.port, path, null, null).toString().trimEnd('/')
    }

    fun endpoint(configured: String, operation: String): String {
        require(operation in setOf("session", "chat", "nature"))
        return "${base(configured)}/v1/$operation"
    }
}
