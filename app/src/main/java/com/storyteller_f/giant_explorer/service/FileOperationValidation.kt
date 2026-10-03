package com.storyteller_f.giant_explorer.service

/** Only directories can contain the destination; compare whole path segments. */
internal fun isCopyDestinationValid(source: String, destination: String, isDirectory: Boolean): Boolean {
    if (!isDirectory) return true
    val sourcePath = java.io.File(source).normalize().path.trimEnd('/')
    val destinationPath = java.io.File(destination).normalize().path.trimEnd('/')
    return destinationPath != sourcePath && !destinationPath.startsWith("$sourcePath/")
}
