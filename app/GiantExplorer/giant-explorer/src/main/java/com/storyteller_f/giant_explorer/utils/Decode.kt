package com.storyteller_f.giant_explorer.utils

import kotlinx.coroutines.yield
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object Decode {

    private fun getInteger(input: String): String {
        return input.substring(1, input.indexOf('e'))
    }

    /**
     * 读取一个字符串，有长度和内容组成，中间用冒号分割
     */
    private fun getString(input: String): Pair<Int, String> {
        assert(input.first().isDigit())
        val splitIndex = input.indexOf(':')
        val length = input.substring(0, splitIndex).toInt()
        val total = splitIndex + 1 + length
        return Pair(total, input.substring(splitIndex + 1, total))
    }

    /**
     * @return 返回消耗的字符个数
     */
    private suspend fun parseEntry(
        string: String,
        dictionary: BEncodedDictionary,
    ): Int {
        val (consume, key) = getString(string)

        val (i1, value1) = parseValue(string.substring(consume).first(), string.substring(consume))
        dictionary.put(key, value1)
        return i1 + consume
    }

    private suspend fun parseValue(
        firstChar: Char,
        value: String
    ) = when {
        Character.isDigit(firstChar) -> {
            getString(value)
        }

        firstChar == 'd' -> {
            val (l, aDictionary) = parseDictionary(value.substring(1))
            l + 1 to aDictionary
        }

        firstChar == 'l' -> {
            val (l, list) = parseList(value.substring(1))
            l + 1 to list
        }

        firstChar == 'i' -> {
            val i = getInteger(value)
            i.length + 2 to i.toLong()
        }

        else -> {
            error("unrecognized $firstChar")
        }
    }

    private suspend fun parseList(string: String): Pair<Int, BEncodedList> {
        val list = BEncodedList()

        val rest = loop(string) {
            yield()
            if (it.isEmpty() || it.first() == 'e') {
                null
            } else {
                val (l, v) = parseValue(it.first(), it)
                list.add(v)
                it.substring(l)
            }
        }

        return (string.length - rest.length + 1) to list
    }

    /**
     * 解析dictionary 的内容，直到遇到一个e，传入的内容不包含前面的d
     */
    private suspend fun parseDictionary(string: String): Pair<Int, BEncodedDictionary> {
        val dictionary = BEncodedDictionary()

        val rest = loop(string) {
            yield()
            if (it.isEmpty() || it.first() == 'e') {
                null
            } else {
                it.substring(parseEntry(it, dictionary))
            }
        }
        return ((string.length - rest.length) + 1) to dictionary
    }

    private suspend fun bDecode(string: String): BEncodedDictionary {
        require(string[0] == 'd') { "The string must begin with a dictionary" }
        return parseDictionary(string.substring(1)).second
    }

    @Throws(IOException::class)
    suspend fun bDecode(inputStream: InputStream): BEncodedDictionary {
        val read = InputStreamReader(
            inputStream,
            StandardCharsets.ISO_8859_1
        ).buffered().readText()
        return bDecode(read)
    }
}

private inline fun <T> loop(init: T, block: (T) -> T?): T {
    var temp = init
    while (true) {
        val block1 = block(temp) ?: break
        temp = block1
    }
    return temp
}
