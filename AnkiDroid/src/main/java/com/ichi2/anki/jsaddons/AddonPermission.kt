// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

/**
 * A capability an addon can request in its manifest `permissions` list.
 *
 * The set is coarse and deliberately small (see `docs/addons/design-notes.md` §1): the
 * general capabilities use `<entity>:<read|write>` per
 * [#20695](https://github.com/ankidroid/Anki-Android/issues/20695), and a few dangerous
 * capabilities are singletons. `delete` is intentionally not separate from `write`.
 *
 * Unknown permission strings are represented by [Unknown] rather than rejected: an addon
 * built for a newer AnkiDroid still installs here, it just cannot be granted a capability
 * this version does not understand.
 */
sealed interface AddonPermission {
    /** The string as it appears in the manifest, e.g. `"decks:write"` */
    val id: String

    /** A general read/write capability over a collection entity */
    data class Scoped(
        val entity: Entity,
        val access: Access,
    ) : AddonPermission {
        override val id: String get() = "${entity.id}:${access.id}"

        enum class Entity(
            val id: String,
        ) {
            DECKS("decks"),
            NOTES("notes"),
            CARDS("cards"),
            MEDIA("media"),
        }

        enum class Access(
            val id: String,
        ) {
            READ("read"),
            WRITE("write"),
        }
    }

    /** A dangerous capability granted on its own, not part of the read/write matrix */
    enum class Dangerous(
        override val id: String,
    ) : AddonPermission {
        /** Navigate the page to the app's own `ankidroid://` scheme */
        NAVIGATE("navigate"),

        /** Make outbound network requests */
        NETWORK("network"),

        /** Read or write the clipboard */
        CLIPBOARD("clipboard"),
    }

    /** A permission string this AnkiDroid does not recognise; parses but grants nothing */
    data class Unknown(
        override val id: String,
    ) : AddonPermission

    companion object {
        /** Parses a manifest permission string; never throws (unknown → [Unknown]). */
        fun parse(raw: String): AddonPermission {
            Dangerous.entries.firstOrNull { it.id == raw }?.let { return it }
            val parts = raw.split(":")
            if (parts.size == 2) {
                val entity = Scoped.Entity.entries.firstOrNull { it.id == parts[0] }
                val access = Scoped.Access.entries.firstOrNull { it.id == parts[1] }
                if (entity != null && access != null) return Scoped(entity, access)
            }
            return Unknown(raw)
        }

        fun parseAll(raw: List<String>?): List<AddonPermission> = raw.orEmpty().map(::parse)
    }
}
