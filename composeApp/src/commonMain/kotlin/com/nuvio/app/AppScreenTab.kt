package com.nuvio.app

import com.nuvio.app.core.ui.NativeNavigationTab

enum class AppScreenTab {
    Home,
    Search,
    QuickWatch,
    Music,
    Library,
    Settings,
    Videos,
    Photos,
    ;

    companion object {
        fun fromName(name: String): AppScreenTab =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Videos
    }
}

internal fun AppScreenTab.toNativeNavigationTab(): NativeNavigationTab = when (this) {
    AppScreenTab.Home -> NativeNavigationTab.Home
    AppScreenTab.Search -> NativeNavigationTab.Search
    AppScreenTab.QuickWatch -> NativeNavigationTab.Home
    AppScreenTab.Music -> NativeNavigationTab.Home
    AppScreenTab.Library -> NativeNavigationTab.Library
    AppScreenTab.Settings -> NativeNavigationTab.Settings
    AppScreenTab.Videos -> NativeNavigationTab.Home
    AppScreenTab.Photos -> NativeNavigationTab.Home
}

internal fun NativeNavigationTab.toAppScreenTab(): AppScreenTab = when (this) {
    NativeNavigationTab.Home -> AppScreenTab.Home
    NativeNavigationTab.Search -> AppScreenTab.Search
    NativeNavigationTab.Library -> AppScreenTab.Library
    NativeNavigationTab.Settings -> AppScreenTab.Settings
}
