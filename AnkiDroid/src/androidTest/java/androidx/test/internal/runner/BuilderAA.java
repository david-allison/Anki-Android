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

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

import androidx.test.internal.runner.TestRequestBuilder;
import timber.log.Timber;

public class BuilderAA extends TestRequestBuilder {
    public BuilderAA(Instrumentation instr, Bundle arguments) {
        super(instr, arguments);
    }


    @Override
    ClassPathScanner createClassPathScanner(List<String> classPath) {
        Log.e("BuilderAA", "scanner");
        return new ScannerFix(classPath);
    }
}
