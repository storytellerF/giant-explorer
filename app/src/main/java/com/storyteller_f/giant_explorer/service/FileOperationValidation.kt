package com.storyteller_f.giant_explorer.service

import java.net.URI

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
    val sourcePath = normalizeOperationPath(source.path)
    val destinationPath = normalizeOperationPath(destination.path)
    return destinationPath != sourcePath && !destinationPath.startsWith("$sourcePath/")
}

private fun normalizeOperationPath(path: String): String {
    // Collapse leading slashes too, so a path is never parsed as a URI authority.
    val uriPath = path.replace(Regex("/{2,}"), "/")
    return URI(null, null, uriPath, null).normalize().path.trimEnd('/')
}
