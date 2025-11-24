package com.storyteller_f.giant_explorer.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.control.SortDirection
import com.storyteller_f.giant_explorer.control.SortFilterConfig
import com.storyteller_f.giant_explorer.control.SortType
import com.storyteller_f.giant_explorer.control.currentSortFilterConfig
import com.storyteller_f.giant_explorer.databinding.BottomSheetSortFilterBinding

/**
 * 排序和过滤 BottomSheet
 */
class SortFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSortFilterBinding? = null
    private val binding get() = _binding!!

    private var currentConfig = SortFilterConfig()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSortFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 从当前配置初始化UI
        currentSortFilterConfig.value?.let { config ->
            currentConfig = config
            applyConfigToUI(config)
        }

        setupListeners()
    }

    private fun applyConfigToUI(config: SortFilterConfig) {
        // 设置排序类型
        when (config.sortType) {
            SortType.NAME -> binding.sortByName.isChecked = true
            SortType.SIZE -> binding.sortBySize.isChecked = true
            SortType.TIME -> binding.sortByTime.isChecked = true
        }

        // 设置排序方向
        when (config.sortDirection) {
            SortDirection.ASCENDING -> binding.sortAscending.isChecked = true
            SortDirection.DESCENDING -> binding.sortDescending.isChecked = true
        }

        // 设置过滤隐藏文件
        binding.filterHiddenFiles.isChecked = config.filterHidden
    }

    private fun setupListeners() {
        // 排序类型改变
        binding.sortTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            currentConfig = currentConfig.copy(
                sortType = when (checkedId) {
                    R.id.sort_by_size -> SortType.SIZE
                    R.id.sort_by_time -> SortType.TIME
                    else -> SortType.NAME
                }
            )
            updateConfig()
        }

        // 排序方向改变
        binding.sortDirectionGroup.setOnCheckedChangeListener { _, checkedId ->
            currentConfig = currentConfig.copy(
                sortDirection = when (checkedId) {
                    R.id.sort_descending -> SortDirection.DESCENDING
                    else -> SortDirection.ASCENDING
                }
            )
            updateConfig()
        }

        // 过滤隐藏文件改变
        binding.filterHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            currentConfig = currentConfig.copy(filterHidden = isChecked)
            updateConfig()
        }
    }

    private fun updateConfig() {
        currentSortFilterConfig.value = currentConfig
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
