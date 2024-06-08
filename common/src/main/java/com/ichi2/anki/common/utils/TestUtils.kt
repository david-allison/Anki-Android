/*
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
package com.ichi2.anki.common.utils

import timber.log.Timber

/**
 * @return `true` if running under Robolectric or as an Instrumented test. `false` otherwise.
 */
val isRunningAsUnitTest by lazy {
    // uses technique described in:
    // https://stackoverflow.com/questions/28550370/how-to-detect-whether-android-app-is-running-ui-test-with-espresso
    try {
        Class.forName("org.junit.Test")
    } catch (ignored: ClassNotFoundException) {
        Timber.d("isRunningAsUnitTest: %b", false)
        return@lazy false
    }
    Timber.d("isRunningAsUnitTest: %b", true)
    return@lazy true
}
