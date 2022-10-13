package com.pkt.core.presentation.navigation

import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.pkt.core.presentation.common.state.UiNavigation
import com.pkt.core.presentation.common.state.navigation.NavigationHandler

abstract class AppNavigationHandler : NavigationHandler {

    override fun handleNavigation(fragment: Fragment, navigation: UiNavigation) {
        when (navigation) {
            is AppNavigation -> {
                when (navigation) {
                    AppNavigation.NavigateBack -> navigateBack(fragment)
                    is AppNavigation.OpenCjdnsInfo -> openCjdnsInfo(fragment, navigation.address)
                    is AppNavigation.OpenWalletInfo -> openWalletInfo(fragment, navigation.address)
                    AppNavigation.OpenCreateWallet -> openCreateWallet(fragment)
                    AppNavigation.OpenRecoverWallet -> openRecoverWallet(fragment)
                    AppNavigation.OpenMain -> openMain(fragment)
                }
            }

            is InternalNavigation -> {
                navigate(fragment, navigation.navDirections)
            }

            else -> {
                throw IllegalArgumentException("Unknown UiNavigation: $navigation")
            }
        }
    }

    private fun navigate(fragment: Fragment, navDirections: NavDirections) {
        fragment.findNavController()
            .takeIf { it.currentDestination?.getAction(navDirections.actionId) != null }
            ?.navigate(navDirections)
    }

    abstract fun navigateBack(fragment: Fragment)
    abstract fun openCjdnsInfo(fragment: Fragment, address: String)
    abstract fun openWalletInfo(fragment: Fragment, address: String)
    abstract fun openCreateWallet(fragment: Fragment)
    abstract fun openRecoverWallet(fragment: Fragment)
    abstract fun openMain(fragment: Fragment)
}
