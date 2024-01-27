package com.storyteller_f.giant_explorer.utils

import com.storyteller_f.file_system.instance.FileInstance
import java.io.*
import java.nio.charset.StandardCharsets

/**
 * 获得种子的名字，耗时操作
 *
 * @param fileInstance 用来获取输入流
 * @return 种子名
 * @throws IOException io 异常
 */
@Throws(Exception::class)
suspend fun getTorrentName(fileInstance: FileInstance): String {
    val inputStream = fileInstance.getFileInputStream()
    return getTorrentName(inputStream)
}

suspend fun getTorrentName(inputStream: InputStream): String {
    val bEncodedDictionary = Decode.bDecode(inputStream)
    val encodedDictionary =
        bEncodedDictionary.get("info") as BEncodedDictionary
    return String(
        encodedDictionary.get("name").toString().toByteArray(StandardCharsets.ISO_8859_1),
        StandardCharsets.UTF_8
    )
}
