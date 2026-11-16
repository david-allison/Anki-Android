// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import android.view.Menu
import androidx.appcompat.widget.Toolbar
import com.ichi2.anki.AnkiActivity
import kotlin.test.assertNotNull
import com.ichi2.anki.browse.R as BrowseR

fun AnkiActivity.menu(): Menu = assertNotNull(findViewById<Toolbar>(BrowseR.id.toolbar)?.menu)
