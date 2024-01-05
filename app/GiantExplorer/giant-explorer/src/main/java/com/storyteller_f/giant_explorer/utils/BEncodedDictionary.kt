package com.storyteller_f.giant_explorer.utils

class BEncodedDictionary internal constructor() {
    private val list = ArrayList<Entry>()

    fun put(key: Any, value: Any) {
        list.add(Entry(key, value))
    }

    fun get(key: Any): Any? {
        for (i in list.indices) {
            val entry = list[i]
            if (entry.key == key) {
                return entry.value
            }
        }

        return null
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append('d')

        for (i in list.indices) {
            val entry = list[i]
            val key = entry.key as String
            val value = entry.value
            buffer.append(key.length).append(':').append(key)
            when (value) {
                is String -> buffer.append(value.length).append(':').append(value)

                is Long -> buffer.append('i').append(value).append('e')

                is List<*>, is BEncodedDictionary -> buffer.append(value)
            }
        }

        buffer.append('e')
        return buffer.toString()
    }

    private class Entry(override val key: Any, override var value: Any) :
        MutableMap.MutableEntry<Any?, Any> {
        override fun setValue(newValue: Any): Any {
            val tempValue = this.value
            this.value = newValue
            return tempValue
        }
    }
}

