package com.felixbrucker.torrenthttpdownloader

fun String.cleanedForUseAsPath(): String {
    return this.replace(":", " ")
}
