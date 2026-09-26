package com.felixbrucker.torrenthttpdownloader.util

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
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

fun File.listSubDirectories(excludeRootDirectories: Boolean = false): List<File> {
    val commonIgnoredRootDirectories = setOf(
        "Adobe Acrobat",
        "Musicolet",
        "tmp",
        "update",
        "Quick Share",
    )

    return listFiles { file ->
        file.isDirectory
                && !file.name.startsWith(".")
                && (!excludeRootDirectories || !commonIgnoredRootDirectories.contains(file.name))
    }?.asList() ?: throw Exception("File is not a directory")
}

fun File.listSubdirectoriesAsRelativeStrings(excludeRootDirectories: Boolean = false, depth: Int): List<String> {
    val rootSubDirs = listSubDirectories(excludeRootDirectories = excludeRootDirectories)
    var currentDepthDirectories = rootSubDirs
    val allSubDirectories = rootSubDirs.toMutableList()
    for (currentDepth in 2..depth) {
        val currentDepthSubDirectories = currentDepthDirectories.flatMap { it.listSubDirectories() }
        allSubDirectories += currentDepthSubDirectories
        currentDepthDirectories = currentDepthSubDirectories
    }

    return allSubDirectories
        .map { it.toRelativeString(this) }
        .sorted()
}

fun File.countItemsRecursively(): Int {
    val files = listFiles() ?: return 0
    return files.sumOf {
        if (it.isDirectory) it.countItemsRecursively() + 1 else 1
    }
}

fun File.totalSizeBytesRecursively(): Long {
    if (!isDirectory) return length()
    val files = listFiles() ?: return 0
    return files.sumOf { it.totalSizeBytesRecursively() }
}

fun File.makeOpenFileIntent(): Intent {
    return Intent(Intent.ACTION_VIEW).apply {
        val uri = Uri.parse(path)
        val mimeType = if (isDirectory) {
            "resource/folder"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        setDataAndType(uri, mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}