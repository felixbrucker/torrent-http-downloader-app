package com.felixbrucker.torrenthttpdownloader

fun String.cleanedForUseAsPath(): String {
    return this.replace(":", " ")
}

fun String.asStateText(): String {
    return this.replace("_", " ").lowercase()
}