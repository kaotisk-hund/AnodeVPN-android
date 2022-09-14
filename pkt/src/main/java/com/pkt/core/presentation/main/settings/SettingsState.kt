package com.pkt.core.presentation.main.settings

import com.pkt.core.presentation.common.state.UiEvent
import com.pkt.core.presentation.common.state.UiState

data class SettingsState(
    val walletName: String,
    val id: String,
    val version: String,
) : UiState

sealed class SettingsEvent : UiEvent {

    data class OpenWalletInfo(val address: String) : SettingsEvent()

    data class OpenCjdnsInfo(val address: String) : SettingsEvent()
}