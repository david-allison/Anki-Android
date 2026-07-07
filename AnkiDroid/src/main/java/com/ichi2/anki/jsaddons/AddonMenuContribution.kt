// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import kotlinx.serialization.Serializable

/**
 * A menu item an addon contributes to a **native** screen, declared in the manifest `menus`
 * list. This is the bridge for the fully-native Android screens (deck picker, etc.) that have
 * no WebView for [AddonPageHost] to inject into (see `docs/addons/design-notes.md` §4).
 *
 * The declaration is data — the host renders a real native `MenuItem` — so no addon code runs
 * to draw it. A click is dispatched to the addon's background context ([AddonBackgroundHost]),
 * where the addon handles it via `ankidroid.onMenuClick(cb)`.
 */
@Serializable
data class AddonMenuDeclaration(
    /** The native screen to add the item to; see [AddonMenus] screen ids */
    val screen: String? = null,
    /** An addon-chosen id, passed back on click */
    val id: String? = null,
    val title: String? = null,
)

/** A validated menu contribution ready to render: its owning addon and declaration */
data class AddonMenuContribution(
    val addonName: String,
    val screen: String,
    val id: String,
    val title: String,
)

/** Extracts the renderable contributions from an addon's declarations (drops incomplete ones) */
fun AddonModel.menuContributions(): List<AddonMenuContribution> =
    menus.mapNotNull { declaration ->
        val screen = declaration.screen ?: return@mapNotNull null
        val id = declaration.id ?: return@mapNotNull null
        val title = declaration.title ?: return@mapNotNull null
        AddonMenuContribution(addonName = name, screen = screen, id = id, title = title)
    }
