// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyAnkiActivity
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals

/** Guards against shared activity ViewModels copying unrelated intent extras into saved state. */
@RunWith(AndroidJUnit4::class)
class AnkiActivitySavedStateTest : RobolectricTest() {
    @Test
    fun `unrelated intent extras do not increase activity saved state`() {
        fun savedSize(payload: ByteArray): Int {
            val intent = Intent(targetContext, EmptyAnkiActivity::class.java).putExtra("payload", payload)
            val size = savedStateSize(EmptyAnkiActivity::class.java, intent)
            assertContentEquals(payload, intent.getByteArrayExtra("payload"))
            return size
        }

        assertThat(savedSize(ByteArray(300_000)), equalTo(savedSize(ByteArray(0))))
    }

    @Test
    fun `fragment arguments are saved exactly once`() {
        fun savedSize(payload: ByteArray): Int =
            savedStateSize(
                SingleFragmentActivity::class.java,
                SingleFragmentActivity.getIntent(targetContext, Fragment::class, bundleOf("payload" to payload)),
            )

        val payload = ByteArray(300_000)
        assertThat(
            "Saved state should grow by exactly one copy of the fragment arguments",
            savedSize(payload) - savedSize(ByteArray(0)),
            equalTo(payload.size),
        )
    }

    private fun <T : AnkiActivity> savedStateSize(
        activityClass: Class<T>,
        intent: Intent,
    ): Int {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(activityClass, intent)
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState)

        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(savedState)
            return parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }
}
