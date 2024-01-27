package com.storyteller_f.giant_explorer.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TorrentFileTest {
    @Test
    fun testTorrentName() {
        runBlocking {
            assertEquals("temp", getTorrentName(javaClass.classLoader!!.getResourceAsStream("base.torrent")))
        }
    }
}
