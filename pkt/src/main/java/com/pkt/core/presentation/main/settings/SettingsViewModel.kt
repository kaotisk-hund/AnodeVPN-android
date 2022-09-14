package com.pkt.core.presentation.main.settings

import com.pkt.core.di.qualifier.VersionName
import com.pkt.core.presentation.common.state.StateViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @VersionName private val versionName: String,
) : StateViewModel<SettingsState>() {

    override fun createInitialState() = SettingsState(
        walletName = "My Wallet 1",
        id = "another-stagbeetle",
        version = versionName
    )

    fun onWalletClick() {
        // TODO
    }

    fun onMenuItemClick(item: MenuItem) {
        when (item.type) {
            MenuItem.Type.CHANGE_PASSWORD -> {
                // TODO
            }
            MenuItem.Type.CHANGE_PIN -> {
                // TODO
            }
            MenuItem.Type.SHOW_SEED -> {
                // TODO
            }
            MenuItem.Type.CJDNS_INFO -> {
                sendEvent(SettingsEvent.OpenCjdnsInfo("pkt1q282zvfztp00nrelpw0lmy7pwz0lvz6vlmzwgzm"))
            }
            MenuItem.Type.WALLET_INFO -> {
                sendEvent(SettingsEvent.OpenWalletInfo("pkt1q282zvfztp00nrelpw0lmy7pwz0lvz6vlmzwgzm"))
            }
            MenuItem.Type.DATA_CONSENT -> {
                // TODO
            }
        }
    }

    fun onRenameClick() {
        // TODO
    }

    fun onExportClick() {
        // TODO
    }

    fun onDeleteClick() {
        // TODO
    }

    fun onNewWalletClick() {
        // TODO
    }

    fun onWalletRecoveryClick() {
        // TODO
    }
}