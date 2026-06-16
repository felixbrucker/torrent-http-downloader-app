package com.felixbrucker.torrenthttpdownloader

import java.io.File

fun File.deleteIfExists() {
    if (exists()) {
        delete()
    }
}

fun File.createDirectoryRecursivelyIfNotExists() {
    if (!exists()) {
        mkdirs()
    }
}