package com.storyteller_f.giant_explorer.utils

class BEncodedList : ArrayList<Any?>() {

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append('l')

        for (i in this.indices) {
            when (val obj = this[i]) {
                is String -> buffer.append(obj.length).append(':').append(obj)

                is Long -> buffer.append('i').append(obj).append('e')

                is List<*>, is BEncodedDictionary -> buffer.append(obj)
            }
        }

        buffer.append('e')
        return buffer.toString()
    }
}