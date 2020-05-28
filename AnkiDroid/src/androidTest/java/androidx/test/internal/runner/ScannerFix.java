/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package androidx.test.internal.runner;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexFile;

class ScannerFix extends ClassPathScanner {
    public ScannerFix(List<String> classPath) {
        super(classPath);
    }

    @Override
    Enumeration<String> getDexEntries(DexFile dexFile) {
        return Collections.enumeration(Collections.singleton("com.ichi2.anki.tests.ACRATest"));
    }
}
