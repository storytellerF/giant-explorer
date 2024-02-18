package com.storyteller_f.giant_explorer.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.R
import com.storyteller_f.common_ui.setOnClick
import com.storyteller_f.giant_explorer.databinding.LayoutPathBinding

class PathMan @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    private val pathBuilder = StringBuilder()

    var pathChangeListener: PathChangeListener? = null

    private val linearLayout: LinearLayout = LinearLayout(context)

    private var scrollToEnd = false

    /**
     * 通过点击PathMan来进行跳转时，只能跳转到上级目录
     */
    private val clickListener = OnClickListener { v -> //跳转路径
        val index = linearLayout.indexOfChild(v)
        val path = getPath(index)
        drawPath(path)
        if (pathChangeListener != null) {
            pathChangeListener!!.onSkipOnPathMan(path)
        }
    }

    private fun getPath(index: Int): String {
        if (index == 0) return "/"
        val split = pathBuilder.toString().split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val path = StringBuilder()
        for (i in 1..index) {
            val name = split[i]
            path.append("/").append(name)
        }
        return path.toString()
    }

    init {
        addView(
            linearLayout,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        if (isInEditMode) {
            drawPath("/TEST")
        } else {
            drawPath("")
        }
        linearLayout.viewTreeObserver.addOnDrawListener {
            if (!scrollToEnd) return@addOnDrawListener
            scrollToEnd = false
            val contentWidth = linearLayout.width
            val containerWidth = width
            val result = contentWidth - containerWidth
            if (result > 0) {
                scrollTo(result, 0)
            }
        }
    }

    /**
     * 展示当前路径的按钮
     *
     * @param path 全路径
     */
    fun drawPath(path: String) {
        pathBuilder.delete(0, pathBuilder.length)
        pathBuilder.append(path)
        val layoutParams = layoutParam
        linearLayout.removeAllViews()
        if (path.length > 1) {
            val strings = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (s in strings) {
                val textView = producePathCell(s)
                linearLayout.addView(textView, layoutParams)
            }
        }
        scroll()
    }

    private val layoutParam: LinearLayout.LayoutParams
        get() {
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutParams.marginEnd = 20
            return layoutParams
        }

    private fun producePathCell(s: String): TextView {
        val textView = TextView(context)
        if (s.isEmpty()) textView.text = "/" else textView.text = s
        textView.textSize = 20f
        textView.gravity = Gravity.CENTER
        textView.updatePadding(left = 10, right = 10)
        val secondaryIndex = 0
        val onSecondaryIndex = 1
        context.theme.obtainStyledAttributes(
            intArrayOf(
                R.attr.colorSecondary,
                R.attr.colorOnSecondary
            )
        ).use {
            val color = it.getColor(secondaryIndex, 0)
            val textColor = it.getColor(onSecondaryIndex, 0)
            textView.setBackgroundColor(color)
            textView.setTextColor(textColor)
        }
        textView.setOnClickListener(clickListener)
        return textView
    }

    /**
     * 添加监听事件，当可以获取到宽度时进行跳转
     */
    private fun scroll() {
        scrollToEnd = true
    }

    fun interface PathChangeListener {
        fun onSkipOnPathMan(pathString: String)
    }
}

fun LayoutPathBinding.flash(path: String) {
    pathEdit.setText(path)
    pathMan.drawPath(path)
}

fun LayoutPathBinding.setup() {
    val resetEdit = { b: Boolean ->
        pathMan.isVisible = b
        pathEdit.isVisible = !b
        editMode.isActivated = !b
    }
    resetEdit(true)
    editMode.setOnClick {
        resetEdit(!pathMan.isVisible)
    }
    pathEdit.setOnKeyListener { _: View?, keyCode: Int, _: KeyEvent? ->
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            val input = pathEdit.text.toString().trim { it <= ' ' }
            pathMan.redirect(input)
        }
        false
    }
}

private fun PathMan.redirect(input: String) {
    //如果用户输入了错误的路径，进行裁切
    val length = input.length
    val path = if (input.endsWith("/") && length != 1) input.substring(0, length - 1)
    else input
    pathChangeListener?.let {
        it.onSkipOnPathMan(path)
        drawPath(path)
    }
}