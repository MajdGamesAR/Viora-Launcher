package com.viora.launcher

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.viora.launcher.core.auth.AuthManager
import com.viora.launcher.core.auth.model.Account
import com.viora.launcher.core.storage.AccountRepository
import com.viora.launcher.core.storage.SecureStorage
import com.viora.launcher.ui.components.NavItem
import com.viora.launcher.ui.screens.AccountSwitcherScreen
import com.viora.launcher.ui.screens.HomeScreen
import com.viora.launcher.ui.screens.LaunchScreen
import com.viora.launcher.ui.screens.VersionsScreen
import com.viora.launcher.ui.screens.LoginScreen
import com.viora.launcher.ui.screens.MicrosoftLoginScreen
import com.viora.launcher.ui.screens.ModsScreen
import com.viora.launcher.ui.screens.SkinsScreen
import com.viora.launcher.ui.screens.VioraLoginScreen
import com.viora.launcher.ui.theme.VioraTheme

sealed class Screen {
    object Login : Screen()
    object MicrosoftLogin : Screen()
    object VioraLogin : Screen()
    object AccountSwitcher : Screen()
    data class Home(val account: Account) : Screen()
    data class Versions(val account: Account) : Screen()
    data class Skins(val account: Account) : Screen()
    data class Mods(val account: Account) : Screen()
    data class Launch(val account: Account, val versionId: String) : Screen()
}

fun main() = application {
    val secureStorage = remember { SecureStorage() }
    val repo = remember { AccountRepository(secureStorage) }
    val authManager = remember { AuthManager(repo) }

    var isDarkMode by remember { mutableStateOf(true) }

    var screen by remember {
        mutableStateOf<Screen>(
            if (authManager.getAllAccounts().isNotEmpty()) Screen.AccountSwitcher
            else Screen.Login
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Viora Launcher",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        VioraTheme(darkTheme = isDarkMode) {
            when (val s = screen) {
                is Screen.Login -> LoginScreen(
                    isDark = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onMicrosoftClick = { screen = Screen.MicrosoftLogin },
                    onVioraClick = { screen = Screen.VioraLogin }
                )

                is Screen.MicrosoftLogin -> MicrosoftLoginScreen(
                    authManager = authManager,
                    onSuccess = { screen = Screen.Home(it) },
                    onError = { screen = Screen.Login },
                    onBack = { screen = Screen.Login }
                )

                is Screen.VioraLogin -> VioraLoginScreen(
                    authManager = authManager,
                    onSuccess = { screen = Screen.Home(it) },
                    onError = { },
                    onBack = { screen = Screen.Login }
                )

                is Screen.AccountSwitcher -> AccountSwitcherScreen(
                    authManager = authManager,
                    isDark = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onAccountSelected = { screen = Screen.Home(it) },
                    onAddAccount = { screen = Screen.Login }
                )

                is Screen.Home -> HomeScreen(
                    account = s.account,
                    isDark = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onNavigate = { item: NavItem ->
                        when (item) {
                            NavItem.VERSIONS -> screen = Screen.Versions(s.account)
                            NavItem.SKINS -> screen = Screen.Skins(s.account)
                            NavItem.MODS -> screen = Screen.Mods(s.account)
                            else -> println("Navigate: ${item.label}")
                        }
                    },
                    onAccountClick = { screen = Screen.AccountSwitcher },
                    onPlay = { screen = Screen.Versions(s.account) }
                )

                is Screen.Versions -> VersionsScreen(
                    account = s.account,
                    isDark = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onNavigate = { item: NavItem ->
                        when (item) {
                            NavItem.HOME -> screen = Screen.Home(s.account)
                            NavItem.SKINS -> screen = Screen.Skins(s.account)
                            NavItem.MODS -> screen = Screen.Mods(s.account)
                            else -> println("Navigate: ${item.label}")
                        }
                    },
                    onAccountClick = { screen = Screen.AccountSwitcher },
                    onBackToHome = { screen = Screen.Home(s.account) },
                    onPlayVersion = { versionId ->
                        screen = Screen.Launch(s.account, versionId)
                    }
                )

                is Screen.Skins -> SkinsScreen(
                    account = s.account,
                    isDark = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onNavigate = { item: NavItem ->
                        when (item) {
                            NavItem.HOME -> screen = Screen.Home(s.account)
                            NavItem.VERSIONS -> screen = Screen.Versions(s.account)
                            NavItem.MODS -> screen = Screen.Mods(s.account)
                            else -> println("Navigate: ${item.label}")
                        }
                    },
                    onAccountClick = { screen = Screen.AccountSwitcher },
                    onBackToHome = { screen = Screen.Home(s.account) }
                )

                is Screen.Mods -> ModsScreen(
                    account = s.account,
                    isDark = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onNavigate = { item: NavItem ->
                        when (item) {
                            NavItem.HOME -> screen = Screen.Home(s.account)
                            NavItem.VERSIONS -> screen = Screen.Versions(s.account)
                            NavItem.SKINS -> screen = Screen.Skins(s.account)
                            else -> println("Navigate: ${item.label}")
                        }
                    },
                    onAccountClick = { screen = Screen.AccountSwitcher },
                    onBackToHome = { screen = Screen.Home(s.account) }
                )

                is Screen.Launch -> LaunchScreen(
                    account = s.account,
                    versionId = s.versionId,
                    isDark = isDarkMode,
                    onBack = { screen = Screen.Versions(s.account) }
                )
            }
        }
    }
}