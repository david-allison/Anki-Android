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
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import java.util.List;

public class MultiDexRequestBuilder extends TestRequestBuilder {
    private Context mContext;


    public MultiDexRequestBuilder(Instrumentation instr, Bundle arguments, Context classPathLoader) {
        super(instr, arguments);
        this.mContext = classPathLoader;
    }


    @Override
    ClassPathScanner createClassPathScanner(List<String> classPath) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return new MultiDexClassPathScanner(classPath, mContext);
        }
        return super.createClassPathScanner(classPath);
    }
}
