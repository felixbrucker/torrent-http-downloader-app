package com.felixbrucker.torrenthttpdownloader

import com.github.junrar.Junrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

// Moves up single directories without other files in the directory tree, flatting the tree from
// nested single directories to a single directory.
fun File.flatten() {
    if (!isDirectory) {
        throw Exception("File is not a directory")
    }
    var filesInRoot = listFiles() ?: arrayOf()
    while (filesInRoot.size == 1 && filesInRoot[0].isDirectory) {
        filesInRoot[0].mergeIntoDirectory(this)
        filesInRoot = listFiles() ?: arrayOf()
    }
}

fun File.mergeIntoDirectory(destinationDirectory: File) {
    if (!destinationDirectory.isDirectory) {
        throw Exception("Destination is not a directory")
    }
    listFiles()?.forEach { file ->
        if (!file.exists()) {
            return@forEach
        }
        val destFile = File(destinationDirectory, file.name)
        if (destFile.exists()) {
            if (file.isDirectory && destFile.isDirectory) {
                return@forEach file.mergeIntoDirectory(destFile)
            } else {
                destFile.delete()
            }
        }
        file.renameTo(destFile)
    }
    if (listFiles()?.size == 0) {
        delete()
    }
}

suspend fun File.extractArchiveInPlace(
    scope: CoroutineScope,
    deleteArchiveAfterExtraction: Boolean = false
) {
    val file = this
    val destination = parentFile
    scope.async(Dispatchers.IO) {
        Junrar.extract(file, destination)
    }.await()
    if (deleteArchiveAfterExtraction) {
        delete()
    }
}

suspend fun File.tryToExtractArchiveInPlace(
    scope: CoroutineScope,
    deleteArchiveAfterExtraction: Boolean = false
): Boolean {
    try {
        extractArchiveInPlace(scope, deleteArchiveAfterExtraction = deleteArchiveAfterExtraction)
        return true
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return false
}