// SPDX-License-Identifier: GPL-3.0-or-later

package local.webviewkeyboardprobe;

import android.app.Activity;
import android.os.Bundle;
import android.os.LocaleList;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

/** Standalone platform reproduction: no Anki, Compose, JavaScript, or network. */
public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        final boolean skipFocusLoss = getIntent().getBooleanExtra("skipFocusLoss", false);
        final boolean hint = getIntent().getBooleanExtra("hint", false);
        WebView web = new WebView(this) {
            @Override
            public void onWindowFocusChanged(boolean focused) {
                Log.i("KeyboardProbe", "windowFocus=" + focused + " skipFocusLoss=" + skipFocusLoss);
                // Diagnostic only: deliberately bypass the provider's focus-loss handling.
                if (!focused && skipFocusLoss) return;
                super.onWindowFocusChanged(focused);
            }

            @Override
            public InputConnection onCreateInputConnection(EditorInfo attrs) {
                InputConnection connection = super.onCreateInputConnection(attrs);
                if (hint) attrs.hintLocales = LocaleList.forLanguageTags("zh");
                Log.i("KeyboardProbe", "connection=" + (connection != null) + " type=" + attrs.inputType);
                return connection;
            }
        };
        Log.i("KeyboardProbe", "WebView=" + WebView.getCurrentWebViewPackage().versionName);
        setContentView(web);
        web.loadDataWithBaseURL(null,
                "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<p>Tap the field, then long-press Gboard's spacebar.</p>"
                + "<textarea style='width:95%;height:180px;font-size:24px'></textarea>",
                "text/html", "UTF-8", null);
    }
}
