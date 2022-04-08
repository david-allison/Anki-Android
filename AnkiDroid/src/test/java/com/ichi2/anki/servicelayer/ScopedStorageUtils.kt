/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import android.content.Context
import com.ichi2.libanki.Collection
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.pathString

object ScopedStorageUtils {
    fun getMigrationDestinationPath(context: Context): File {
        return File(Path(context.getExternalFilesDir(null)!!.canonicalPath, "AnkiDroid-1").pathString)
    }

    fun getMigrationSourcePath(col: Collection): String = File(col.path).parent!!
}
