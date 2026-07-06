// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import com.ichi2.anki.jsaddons.AddonPermission.Scoped.Access
import com.ichi2.anki.jsaddons.AddonPermission.Scoped.Entity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AddonPermissionTest {
    @Test
    fun parsesScopedCapabilities() {
        assertEquals(AddonPermission.Scoped(Entity.DECKS, Access.WRITE), AddonPermission.parse("decks:write"))
        assertEquals(AddonPermission.Scoped(Entity.CARDS, Access.READ), AddonPermission.parse("cards:read"))
    }

    @Test
    fun parsesDangerousCapabilities() {
        assertEquals(AddonPermission.Dangerous.NAVIGATE, AddonPermission.parse("navigate"))
        assertEquals(AddonPermission.Dangerous.NETWORK, AddonPermission.parse("network"))
    }

    @Test
    fun unknownStringsBecomeUnknownNotAnError() {
        assertEquals(AddonPermission.Unknown("teleport"), AddonPermission.parse("teleport"))
        assertEquals(AddonPermission.Unknown("decks:teleport"), AddonPermission.parse("decks:teleport"))
        assertIs<AddonPermission.Unknown>(AddonPermission.parse("a:b:c"))
    }

    @Test
    fun idRoundTripsForKnownCapabilities() {
        for (raw in listOf("decks:read", "notes:write", "media:read", "navigate", "network", "clipboard")) {
            assertEquals(raw, AddonPermission.parse(raw).id)
        }
    }
}
