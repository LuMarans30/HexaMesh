package com.lumarans30.hexamesh.ui

import com.lumarans30.hexamesh.R

/** Tabs in bottom-bar order. */
enum class HexaTab(val labelRes: Int, val iconRes: Int) {
    Manage(R.string.tab_manage, R.drawable.ic_hexagon),
    Mesh(R.string.tab_mesh, R.drawable.ic_mesh),
    Logs(R.string.tab_logs, R.drawable.ic_terminal),
    Settings(R.string.tab_settings, R.drawable.ic_settings),
}
