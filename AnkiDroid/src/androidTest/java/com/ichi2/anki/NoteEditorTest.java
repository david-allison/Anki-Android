/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import com.ichi2.anki.tests.libanki.RetryRule;
import com.ichi2.anki.testutil.ThreadUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class NoteEditorTest {

    @Rule public ActivityScenarioRule<NoteEditor> activityRule = new ActivityScenarioRule<>(getStartActivityIntent());
    @Rule public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(WRITE_EXTERNAL_STORAGE);

    @NonNull
    private Intent getStartActivityIntent() {
        Intent intent = new Intent(getTargetContext(), NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
        return intent;
    }

    @Test
    public void testTabOrder() {
        ensureCollectionLoaded();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        ActivityScenario<NoteEditor> scenario = activityRule.getScenario();
        scenario.moveToState(Lifecycle.State.RESUMED);

        // These needed to be sent before the .onActivity. During causes a deadlock, after causes an activity restart.
        runOnInstrumentationThread(() -> {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_A);
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_B);
        }, 10000);

        scenario.onActivity(editor -> {
            String[] currentFieldStrings = editor.getCurrentFieldStrings();
            assertThat(currentFieldStrings[0], is("a"));
            assertThat(currentFieldStrings[1], is("b"));
        });
    }


    protected void runOnInstrumentationThread(Runnable runnable, int timeout) {
        AtomicBoolean ended = new AtomicBoolean(false);
        ThreadUtils.runAndJoin(() ->{
            runnable.run();
            ended.set(true);
        }, timeout);
        if (!ended.get()) {
            throw new IllegalStateException(String.format("Thread did not finish within %dms", timeout));
        }
    }


    private void ensureCollectionLoaded() {
        CollectionHelper.getInstance().getCol(getTargetContext());
    }


    private Context getTargetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
