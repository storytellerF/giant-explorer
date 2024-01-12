package com.storyteller_f.giant_explorer.dialog

import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory
import com.storyteller_f.common_ui.CommonDialogFragment
import com.storyteller_f.config_core.ConfigItem
import com.storyteller_f.config_core.DefaultDialog
import com.storyteller_f.file_system.model.FileInfo
import com.storyteller_f.filter_core.Filter
import com.storyteller_f.filter_core.config.FilterConfigItem
import com.storyteller_f.filter_ui.FilterDialog
import com.storyteller_f.giant_explorer.filter.FilterFactory
import com.storyteller_f.giant_explorer.filter.NameFilter
import java.io.IOException

val activeFilters = MutableLiveData<List<Filter<FileInfo>>>()

class FilterDialogFragment : CommonDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        setupFilterFunction().selfDialog

    @Throws(IOException::class)
    private fun setupFilterFunction() = FilterDialog(
        requireContext(),
        SUFFIX,
        filters,
        listener,
        FilterFactory(),
        factory
    )

    private val listener =
        object : DefaultDialog.Listener<Filter<FileInfo>, FilterConfigItem> {
            override fun onSaveState(oList: List<Filter<FileInfo>>): List<FilterConfigItem> =
                oList.map {
                    (it as NameFilter).item
                }.toMutableList()

            override fun onActiveChanged(activeList: List<Filter<FileInfo>>) {
                activeFilters.value = activeList
            }

            override fun onEditingChanged(editing: List<Filter<FileInfo>>) {
            }

            override fun onRestoreState(configItems: List<FilterConfigItem>): List<Filter<FileInfo>> {
                return configItems.buildFilters()
            }
        }

    private val filters: List<Filter<FileInfo>>
        get() = buildList {
            add(NameFilter(NameFilter.Config("^$")))
        }

    companion object {
        const val SUFFIX = "filter"
        val factory: RuntimeTypeAdapterFactory<ConfigItem> = RuntimeTypeAdapterFactory.of(
            ConfigItem::class.java, "config-item-key"
        ).registerSubtype(NameFilter.Config::class.java, "name")!!
    }
}

fun List<FilterConfigItem>.buildFilters() =
    map {
        NameFilter(it as NameFilter.Config)
    }
