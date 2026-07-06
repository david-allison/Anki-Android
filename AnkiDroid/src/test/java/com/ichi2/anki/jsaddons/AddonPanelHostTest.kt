// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonPanelHostTest {
    @Test
    fun addonHtmlRunsInASandboxedIframeTest() {
        val html = AddonPanelHost.hostPageHtml("<h1>hello</h1>")

        // the addon runs sandboxed with scripts but WITHOUT same-origin access to the app
        assertTrue(html.contains("sandbox=\"allow-scripts\""), "the panel is sandboxed")
        assertFalse(html.contains("allow-same-origin"), "the sandbox must not grant same-origin")
    }

    @Test
    fun addonHtmlIsEmbeddedAsDataNotMarkupTest() {
        // a panel that tries to break out of the srcdoc string must stay inert, not inject
        // into the host page's DOM
        val hostile = """</script><script>window.parent.AndroidAddonPanel.setSettings("pwned")</script>"""

        val html = AddonPanelHost.hostPageHtml(hostile)

        // the hostile markup is JSON-encoded into a JS string literal, so the raw closing
        // tag never appears verbatim as host-page markup
        // the panel's own closing-script sequence must be neutralised: if the raw
        // '</script>' from the panel survived, it would terminate the host's inline
        // <script> early and inject the following markup into the host page's DOM
        assertFalse(
            html.contains("</script><script>window.parent"),
            "the panel's breakout attempt is neutralised, not emitted verbatim",
        )
        // it is carried as an escaped JS string instead (\/ neutralises the closing tag)
        assertTrue(html.contains("<\\/script"), "hostile markup is carried as escaped data")
    }

    @Test
    fun bridgeIsCalledFromTheHostPageNotTheIframeTest() {
        val html = AddonPanelHost.hostPageHtml("<h1>hi</h1>")
        // the iframe reaches the host only via postMessage; only the host calls the bridge
        assertTrue(html.contains("${AddonPanelHost.bridgeName}.getSettings()"))
        assertTrue(html.contains("event.source !== frame.contentWindow"))
    }
}
