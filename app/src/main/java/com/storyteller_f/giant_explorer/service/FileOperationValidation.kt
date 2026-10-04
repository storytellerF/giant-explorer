package com.storyteller_f.giant_explorer.service

/** Only directories on the same filesystem can contain the destination. */
internal fun isCopyDestinationValid(
    source: FileOperationLocation,
    destination: FileOperationLocation,
    isDirectory: Boolean
): Boolean {
    if (!isDirectory) return true
    if (source.scheme != destination.scheme || source.authority != destination.authority ||
        source.root != destination.root
    ) {
        return true
    }
    val sourcePath = java.io.File(source.path).normalize().path.trimEnd('/')
    val destinationPath = java.io.File(destination.path).normalize().path.trimEnd('/')
    return destinationPath != sourcePath && !destinationPath.startsWith("$sourcePath/")
}
