package org.wordpress.android.ui.prefs.timezone

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.databinding.SiteSettingsTimezoneBottomSheetListBinding
import org.wordpress.android.ui.prefs.timezone.TimezonesList.TimezoneItem
import org.wordpress.android.util.ActivityUtils
import org.wordpress.android.util.ToastUtils
import javax.inject.Inject

class SiteSettingsTimezoneBottomSheet : BottomSheetDialogFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var timezoneViewModel: SiteSettingsTimezoneViewModel

    private val timezoneAdapter = TimezoneAdapter { timezone ->
        setSelectedTimezone(timezone)
    }

    private var searchJob: Job? = null

    private fun search(query: CharSequence?) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            timezoneViewModel.searchTimezones(query)
        }
    }

    private var _binding: SiteSettingsTimezoneBottomSheetListBinding? = null
    private val binding get() = _binding

    private var callback: TimezoneSelectionCallback? = null

    fun setTimezoneSettingCallback(callback: TimezoneSelectionCallback) {
        this.callback = callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().applicationContext as WordPress).component().inject(this)
        timezoneViewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(SiteSettingsTimezoneViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = SiteSettingsTimezoneBottomSheetListBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupUI()
        setupLiveData()
        timezoneViewModel.getTimezones(requireActivity())
    }

    private fun setupUI() {
        binding?.apply {
            list.adapter = timezoneAdapter
            list.addItemDecoration(DividerItemDecoration(requireContext(), RecyclerView.VERTICAL))

            searchInputLayout.editText?.doOnTextChanged { inputText, _, _, _ ->
                search(inputText)
            }

            searchInputLayout.editText?.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    hideSearchKeyboard()
                }
            }

            searchInputLayout.editText?.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    updateTimezonesFromInput()
                    true
                } else {
                    false
                }
            }

            searchInputLayout.editText?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    updateTimezonesFromInput()
                    true
                } else {
                    false
                }
            }

            searchInputLayout.setEndIconOnClickListener {
                searchInputLayout.editText?.text?.clear()
                searchInputLayout.editText?.clearFocus()
                timezoneViewModel.onSearchCancelled()
                hideSearchKeyboard()
            }

            btnTimezoneSuggestion.setOnClickListener { it as MaterialButton
                callback?.onSelectTimezone(it.text.toString())
                dismiss()
            }
        }
    }

    private fun setupLiveData() {
        timezoneViewModel.showEmptyView.observe(viewLifecycleOwner, {
            showEmptyView(it)
        })

        timezoneViewModel.showProgressView.observe(viewLifecycleOwner, {
            showProgressView(it)
        })

        timezoneViewModel.timezones.observe(viewLifecycleOwner, {
            timezoneAdapter.submitList(it)
        })

        timezoneViewModel.suggestedTimezone.observe(viewLifecycleOwner, {
            binding?.btnTimezoneSuggestion?.text = it
        })

        timezoneViewModel.timezoneSearch.observe(viewLifecycleOwner, {
            timezoneAdapter.submitList(it)
        })

        timezoneViewModel.dismissWithError.observe(viewLifecycleOwner, {
            dismissWithError()
        })
    }

    private fun updateTimezonesFromInput() {
        binding?.searchInputLayout?.editText?.text?.trim().let {
            if (it?.isNotEmpty() == true) {
                search(it)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        callback = null
        super.onDestroyView()
    }

    private fun setSelectedTimezone(timezone: TimezoneItem?) {
        timezone?.let {
            callback?.onSelectTimezone(it.value)
            dismiss()
        }
    }

    private fun showEmptyView(show: Boolean) {
        if (isAdded) {
            binding?.emptyView?.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun showProgressView(show: Boolean) {
        if (isAdded) {
            binding?.progressView?.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun hideSearchKeyboard() {
        if (isAdded) {
            ActivityUtils.hideKeyboardForced(binding?.searchInputLayout)
        }
    }

    private fun dismissWithError() {
        ToastUtils.showToast(activity, R.string.site_settings_timezones_error)
        dismiss()
    }

    companion object {
        const val KEY_TIMEZONE = "timezone"

        @JvmStatic
        fun newInstance(timezone: String): SiteSettingsTimezoneBottomSheet =
                SiteSettingsTimezoneBottomSheet().apply {
                    arguments = Bundle().apply {
                        putString(KEY_TIMEZONE, timezone)
                    }
                }
    }

    interface TimezoneSelectionCallback {
        fun onSelectTimezone(timezone: String)
    }
}

