package com.storyteller_f.giant_explorer.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
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
import com.storyteller_f.common_ui.setOnClick
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.LayoutPathBinding

class PathMan @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    private val cellVerticalMargin =
        resources.getDimension(R.dimen.path_man_cell_vertical_margin)
            .toInt()
    private val cellEndMargin =
        resources.getDimension(R.dimen.path_man_cell_end_margin)
            .toInt()

    private val cellTextSize = resources.getDimension(R.dimen.path_man_cell_text_size)
    private val cellHorizontalPadding =
        resources.getDimension(R.dimen.path_man_cell_horizontal_padding)
            .toInt()

    private val cellCorner = resources.getDimension(R.dimen.path_man_cell_corner)

    private val pathBuilder = StringBuilder()

    var pathChangeListener: PathChangeListener? = null

    private val linearLayout: LinearLayout = LinearLayout(context)

    private var scrollToEnd = false

    /**
     * 通过点击PathMan来进行跳转时，只能跳转到上级目录
     */
    private val clickListener = OnClickListener { v -> // 跳转路径
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
        val layoutParams = cellLayoutParam
        linearLayout.removeAllViews()
        if (path.length > 1) {
            val strings = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (s in strings) {
                linearLayout.addView(createPathCell(s), layoutParams)
            }
        } else {
            linearLayout.addView(createPathCell("/"), layoutParams)
        }
        scroll()
    }

    private val cellLayoutParam: LinearLayout.LayoutParams
        get() {
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            layoutParams.topMargin = cellVerticalMargin
            layoutParams.bottomMargin = cellVerticalMargin
            layoutParams.marginEnd = cellEndMargin
            return layoutParams
        }

    private fun createPathCell(s: String): TextView {
        return TextView(context).apply {
            bindPathCell(s, secondaryIndex, onSecondaryIndex)
            setOnClickListener(clickListener)
        }
    }

    private fun TextView.bindPathCell(
        s: String,
        secondaryIndex: Int,
        onSecondaryIndex: Int
    ) {
        text = s.ifEmpty { "/" }
        gravity = Gravity.CENTER
        setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            cellTextSize
        )
        updatePadding(left = cellHorizontalPadding, right = cellHorizontalPadding)

        context.theme.obtainStyledAttributes(
            intArrayOf(
                com.google.android.material.R.attr.colorSecondary,
                com.google.android.material.R.attr.colorOnSecondary
            )
        ).useCompat({ recycle() }) {
            val color = it.getColor(secondaryIndex, 0)
            val textColor = it.getColor(onSecondaryIndex, 0)
            background = GradientDrawable().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cornerRadius = cellCorner
                }
                setColor(color)
            }
            setTextColor(textColor)
        }
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

    companion object {
        const val secondaryIndex = 0
        const val onSecondaryIndex = 1
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
    // 如果用户输入了错误的路径，进行裁切
    val length = input.length
    val path = if (input.endsWith("/") && length != 1) {
        input.substring(0, length - 1)
    } else {
        input
    }
    pathChangeListener?.let {
        it.onSkipOnPathMan(path)
        drawPath(path)
    }
}

fun <T, R> T.useCompat(close: T.() -> Unit, block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}
