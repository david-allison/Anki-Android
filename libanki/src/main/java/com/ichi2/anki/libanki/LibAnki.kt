/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.libanki

import net.ankiweb.rsdroid.Backend

object LibAnki {
    /**
     * The currently active backend, which is created on demand via [ensureBackend], and
     * implicitly via [ensureOpen] and routines like [withCol].
     * The backend is long-lived, and will generally only be closed when switching interface
     * languages or changing schema versions. A closed backend cannot be reused, and a new one
     * must be created.
     */
    var backend: Backend? = null

    /**
     * The current collection, which is opened on demand via [withCol]. If you need to
     * close and reopen the collection in an atomic operation, add a new method that
     * calls [withQueue], and then executes [ensureClosedInner] and [ensureOpenInner] inside it.
     * A closed collection can be detected via [withOpenColOrNull] or by checking [Collection.dbClosed].
     */
    var collection: Collection? = null
}
