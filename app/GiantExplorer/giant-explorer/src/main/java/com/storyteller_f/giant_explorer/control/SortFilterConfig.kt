package com.storyteller_f.giant_explorer.control

import androidx.lifecycle.MutableLiveData
import com.storyteller_f.file_system.instance.FileKind
import com.storyteller_f.file_system.model.FileInfo

/**
 * 排序类型
 */
enum class SortType {
    NAME, // 按名称
    SIZE, // 按大小
    TIME // 按时间
}

/**
 * 排序方向
 */
enum class SortDirection {
    ASCENDING, // 升序
    DESCENDING // 降序
}

/**
 * 排序和过滤配置
 */
data class SortFilterConfig(
    val sortType: SortType = SortType.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val filterHidden: Boolean = false
) {
    /**
     * 根据配置对文件列表进行排序
     */
    fun sort(files: List<FileInfo>): List<FileInfo> {
        val comparator = when (sortType) {
            SortType.NAME -> compareBy<FileInfo> { it.name }
            SortType.SIZE -> compareBy {
                val kind = it.kind
                if (kind is FileKind.File) kind.size else 0L
            }
            SortType.TIME -> compareBy { it.time.lastModified ?: 0L }
        }

        return if (sortDirection == SortDirection.ASCENDING) {
            files.sortedWith(comparator)
        } else {
            files.sortedWith(comparator.reversed())
        }
    }

    /**
     * 根据配置过滤文件列表
     */
    fun filter(files: List<FileInfo>): List<FileInfo> {
        return if (filterHidden) {
            files.filter { !it.name.startsWith(".") }
        } else {
            files
        }
    }

    /**
     * 应用排序和过滤
     */
    fun apply(files: List<FileInfo>): List<FileInfo> {
        return sort(filter(files))
    }
}

/**
 * 全局排序过滤配置
 */
val currentSortFilterConfig = MutableLiveData(SortFilterConfig())
