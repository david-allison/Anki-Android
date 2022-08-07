/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroPageTransformerType
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.introduction.SetupCollectionFragment
import com.ichi2.anki.introduction.SetupCollectionFragment.*
import com.ichi2.anki.introduction.SetupCollectionFragment.Companion.handleCollectionSetupOption
import com.ichi2.themes.Themes
import timber.log.Timber

/**
 * App introduction for new users.
 */
class IntroductionActivity : AppIntro() {

    private val onLoginResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            finish()
        } else {
            Timber.i("login was not successful")
        }
        invalidateOptionsMenu() // maybe the availability of undo changed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)

        // Check for WebView related error
        val startupFailure = InitialActivity.getStartupFailureType(this)
        startupFailure?.let {
            handleStartupFailure(it)
        }
        Themes.setTheme(this)

        setTransformer(AppIntroPageTransformerType.Zoom)

        addSlide(SetupCollectionFragment())

        handleCollectionSetupOption { option ->
            when (option) {
                CollectionSetupOption.DeckPickerWithNewCollection -> startDeckPicker()
                CollectionSetupOption.SyncFromExistingAccount -> openLoginDialog()
            }
        }

        this.setColorDoneText(R.color.black)
    }

    private fun openLoginDialog() {
        onLoginResult.launch(Intent(this, LoginActivity::class.java))
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        startDeckPicker()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        startDeckPicker()
    }

    private fun startDeckPicker() {
        AnkiDroidApp.getSharedPrefs(this).edit().putBoolean(IntentHandler.INTRODUCTION_SLIDES_SHOWN, true).apply()
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(deckPicker)
        finish()
    }

    /**
     * When WebView is not available on a device, then error message indicating
     * the same needs to be shown.
     * @param startupFailure Type of error on startup
     */
    private fun handleStartupFailure(startupFailure: StartupFailure) {
        if (startupFailure == StartupFailure.WEBVIEW_FAILED) {
            MaterialDialog(this).show {
                title(R.string.ankidroid_init_failed_webview_title)
                message(R.string.ankidroid_init_failed_webview, AnkiDroidApp.getWebViewErrorMessage())
                positiveButton(R.string.close) { finish() }
                cancelable(false)
            }
        }
    }

    /**
     * Information required to show a slide during the initial app introduction
     */
    data class IntroductionResources(
        val title: String,
    )

    // This method has been taken from AnkiActivity.
    // Duplication is required since IntroductionActivity doesn't inherit from AnkiActivity.
    private fun showedActivityFailedScreen(savedInstanceState: Bundle?): Boolean {
        if (AnkiDroidApp.isInitialized()) {
            return false
        }

        // #7630: Can be triggered with `adb shell bmgr restore com.ichi2.anki` after AnkiDroid settings are changed.
        // Application.onCreate() is not called if:
        // * The App was open
        // * A restore took place
        // * The app is reopened (until it exits: finish() does not do this - and removes it from the app list)
        Timber.w("Activity started with no application instance")
        UIUtils.showThemedToast(this, getString(R.string.ankidroid_cannot_open_after_backup_try_again), false)

        // Avoids a SuperNotCalledException
        super.onCreate(savedInstanceState)
        AnkiActivity.finishActivityWithFade(this)

        // If we don't kill the process, the backup is not "done" and reopening the app show the same message.
        Thread {

            // 3.5 seconds sleep, as the toast is killed on process death.
            // Same as the default value of LENGTH_LONG
            try {
                Thread.sleep(3500)
            } catch (e: InterruptedException) {
                Timber.w(e)
            }
            Process.killProcess(Process.myPid())
        }.start()
        return true
    }
}
