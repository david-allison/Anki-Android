/*
 *  Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.storage

import android.os.Environment

/**
 * Utilities for accessing an SD Card (if the user's device supports one)
 *
 * The AnkiDroid collection folder can be stored on an external SD card.
 * This was common in older versions of Android, and is still supported.
 */
object SdCard {
    val isMounted: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
}
