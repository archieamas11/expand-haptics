package com.hapticks.app.data.model


data class LatestRelease(
    val title: String,
    val body: String,
    val tagName: String,
    val url: String,
    val apkDownloadUrl: String?,
)
