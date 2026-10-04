package com.storyteller_f.common_ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.storyteller_f.giant_explorer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

fun <T : View> T.pp(block: (T) -> Unit) = post { block(this) }

fun <T : View> T.setVisible(visible: Boolean, block: (T) -> Unit = {}) {
    isVisible = visible
    if (visible) block(this)
}

inline fun <T : View, reified V> T.setVisible(obj: Any, visible: (V) -> Boolean, block: (T, V) -> Unit) {
    val value = obj as? V
    isVisible = value != null && visible(value)
    if (isVisible && value != null) block(this, value)
}

fun List<View>.onVisible(view: View) = forEach { it.isVisible = it === view }

fun LifecycleOwner.repeatOnViewResumed(block: suspend CoroutineScope.() -> Unit) = scope.launch {
    cycle.repeatOnLifecycle(Lifecycle.State.RESUMED, block)
}

private val waitingCoordinator = Dispatchers.Default.limitedParallelism(1)

class WaitingViewModel : ViewModel() {
    internal val host = WaitingHost(waitingCoordinator)

    override fun onCleared() {
        host.close()
    }
}

class WaitingDialog : DialogFragment(R.layout.dialog_waiting) {
    override fun getTheme() = R.style.ThemeOverlay_Giant_ContentDialog

    private val model: WaitingViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.host.await(requireArguments().getString("operationId")!!)
                dismissAllowingStateLoss()
            }
        }
    }
}

suspend fun LifecycleOwner.waitingDialog(): CompletableDeferred<Unit> {
    val activity = when (this) {
        is Fragment -> requireActivity()
        is FragmentActivity -> this
        else -> error("Waiting dialog requires a Fragment or FragmentActivity")
    }
    val id = UUID.randomUUID().toString()
    val completion = ViewModelProvider(activity)[WaitingViewModel::class.java].host.begin(id)
    try {
        WaitingDialog().apply {
            arguments = Bundle().apply { putString("operationId", id) }
        }.show(fm, "waiting:$id")
    } catch (error: Exception) {
        completion.cancel()
        throw error
    }
    return completion
}

fun LifecycleOwner.waitingDialog(block: suspend () -> Unit) {
    scope.launch {
        val waiting = waitingDialog()
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Toast.makeText(ctx, error.localizedMessage ?: error.javaClass.simpleName, Toast.LENGTH_SHORT).show()
            Log.e("WaitingDialog", "Operation failed", error)
        } finally {
            waiting.end()
        }
    }
}

fun CompletableDeferred<Unit>.end() {
    complete(Unit)
}
