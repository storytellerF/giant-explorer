package com.storyteller_f.giant_explorer.dialog

import android.net.Uri
import androidx.fragment.app.FragmentFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenFileDialogTest {
    @Test
    fun navigationCanInstantiateDialogBeforeAssigningArguments() {
        val dialog = FragmentFactory().instantiate(
            OpenFileDialog::class.java.classLoader!!,
            OpenFileDialog::class.java.name
        ) as OpenFileDialog
        val uri = Uri.parse("file:///storage/emulated/0/notes.txt")
        dialog.arguments = OpenFileDialogArgs(uri).toBundle()
        assertEquals(uri, dialog.uri)
    }
}
