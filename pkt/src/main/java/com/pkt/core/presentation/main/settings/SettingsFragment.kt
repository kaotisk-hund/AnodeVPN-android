package com.pkt.core.presentation.main.settings

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.pkt.core.R
import com.pkt.core.databinding.FragmentSettingsBinding
import com.pkt.core.extensions.applyGradient
import com.pkt.core.extensions.getColorByAttribute
import com.pkt.core.presentation.common.adapter.AsyncListDifferAdapter
import com.pkt.core.presentation.common.state.StateFragment
import com.pkt.core.presentation.common.state.UiEvent
import com.pkt.core.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : StateFragment<SettingsState>(R.layout.fragment_settings) {

    private val viewBinding by viewBinding(FragmentSettingsBinding::bind)

    override val viewModel: SettingsViewModel by viewModels()

    private val mainViewModel: MainViewModel by activityViewModels()

    private val adapter = AsyncListDifferAdapter(
        menuAdapterDelegate {
            viewModel.onMenuItemClick(it)
        }
    ).apply {
        items = MenuItem.Type.values()
            .map { MenuItem(it, it.ordinal != MenuItem.Type.values().size - 1) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(viewBinding) {
            walletButton.setOnClickListener {
                viewModel.onWalletClick()
            }
            moreButton.setOnClickListener {
                showMorePopupMenu(addButton)
            }
            addButton.setOnClickListener {
                showAddPopupMenu(it)
            }

            recyclerView.itemAnimator = null
            recyclerView.adapter = adapter
        }
    }

    private fun showMorePopupMenu(view: View) {
        PopupMenu(requireContext(), view).apply {
            menuInflater.inflate(R.menu.settings_more_popup_menu, menu)

            menu.getItem(menu.size() - 1).apply {
                title = SpannableString(title).apply {
                    setSpan(ForegroundColorSpan(requireContext().getColorByAttribute(android.R.attr.colorError)),
                        0,
                        length,
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                }
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.rename -> viewModel.onRenameClick()
                    R.id.export -> viewModel.onExportClick()
                    R.id.delete -> viewModel.onDeleteClick()
                }
                true
            }

            gravity = Gravity.END

            show()
        }
    }

    private fun showAddPopupMenu(view: View) {
        PopupMenu(requireContext(), view).apply {
            menuInflater.inflate(R.menu.settings_add_popup_menu, menu)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.newWallet -> viewModel.onNewWalletClick()
                    R.id.walletRecovery -> viewModel.onWalletRecoveryClick()
                }
                true
            }

            gravity = Gravity.END

            show()
        }
    }

    override fun handleState(state: SettingsState) {
        with(viewBinding) {
            walletButton.apply {
                text = state.walletName
                applyGradient()
            }

            idLabel.text = "%s %s".format(getString(R.string.id), state.id)
            versionLabel.text = "%s %s".format(getString(R.string.version), state.version)
        }
    }

    override fun handleEvent(event: UiEvent) {
        super.handleEvent(event)

        when (event) {
            is SettingsEvent.OpenWalletInfo -> mainViewModel.openWalletInfo(event.address)
            is SettingsEvent.OpenCjdnsInfo -> mainViewModel.openCjdnsInfo(event.address)
        }
    }
}