/*
 *  Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import androidx.core.content.ContextCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.compat.CompatHelper.Companion.registerReceiverCompat
import com.ichi2.anki.receiver.SdCardReceiver

/**
 * Utilities for accessing an SD Card (if the user's device supports one)
 *
 * The AnkiDroid collection folder can be stored on an external SD card.
 * This was common in older versions of Android, and is still supported.
 */
object SdCard {
    val isMounted: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    enum class Event {
        Mount,
        Eject,
    }

    /**
     * Listens for SD card [mount][Event.Mount] and [eject][Event.Eject] events
     * via [SdCardReceiver] broadcasts.
     *
     * Call [register] to start listening, [unregister] to stop.
     */
    abstract class MountListener {
        private val context: Context = AnkiDroidApp.instance
        private var receiver: BroadcastReceiver? = null

        abstract fun onEvent(
            event: Event,
            context: Context,
        )

        /** Registers this listener if not already registered. */
        fun register() {
            if (receiver != null) return
            receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        doNotUseCtx: Context,
                        intent: Intent,
                    ) {
                        // ctx.baseContext may be null, so ctx.applicationContext() throws a NPE,
                        // context may not have the locale override from AnkiDroidApp
                        val event =
                            when (intent.action) {
                                SdCardReceiver.MEDIA_MOUNT -> Event.Mount
                                SdCardReceiver.MEDIA_EJECT -> Event.Eject
                                else -> return
                            }
                        onEvent(event, context)
                    }
                }
            val filter =
                IntentFilter().apply {
                    addAction(SdCardReceiver.MEDIA_MOUNT)
                    addAction(SdCardReceiver.MEDIA_EJECT)
                }
            context.registerReceiverCompat(receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }

        fun unregister() {
            receiver?.let {
                context.unregisterReceiver(it)
                receiver = null
            }
        }
    }
}
