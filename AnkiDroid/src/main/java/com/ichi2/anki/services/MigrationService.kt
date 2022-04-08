/*
 *  Copyright (c) 2021 Farjad Ilyas <ilyasfarjad@gmail.com>
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

package com.ichi2.anki.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.NotificationChannels
import com.ichi2.anki.R
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData
import com.ichi2.anki.servicelayer.scopedstorage.NumberOfBytes
import com.ichi2.async.CoroutineTask
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.utils.FileUtil
import timber.log.Timber

class MigrationService : Service() {

    companion object {
        /** The id of the notification for in-progress user data migration.  */
        private const val MIGRATION_NOTIFY_ID = 2
    }

    private var task: MigrateUserData? = null
    lateinit var notificationBuilder: NotificationCompat.Builder
    lateinit var notificationManager: NotificationManagerCompat
    lateinit var listener: TaskListenerWithContext<Context, Int, Boolean>
    var cancelled = false

    private inner class MigrateUserDataListener(sourceSize: NumberOfBytes?) :
        TaskListenerWithContext<Context, NumberOfBytes?, Boolean>(this) {
        private var mSourceSize: NumberOfBytes = 0L
        private var mCurrentProgress: NumberOfBytes
        private val mUpdateInterval = 2000
        private var mIncreaseSinceLastUpdate: NumberOfBytes
        private var mMostRecentUpdateTime = 0L

        override fun actualOnPreExecute(context: Context) {
            notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) {
                Timber.v("MigrateUserDataListener - notifications disabled, returning")
                return
            }

            val channel = NotificationChannels.Channel.GENERAL

            notificationBuilder = NotificationCompat.Builder(
                context,
                NotificationChannels.getId(channel)
            )
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(context.resources.getString(R.string.migrating_data_message))
                .setContentText(context.resources.getString(R.string.migration_transferred_size, 0f, mSourceSize / 1024f))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .setProgress(100, 0, false)

            startForeground(MIGRATION_NOTIFY_ID, notificationBuilder.build())
        }

        override fun actualOnProgressUpdate(context: Context, value: NumberOfBytes?) {
            super.actualOnProgressUpdate(context, value)

            if (value == null) {
                return
            }

            // Convert progress in bytes to kilobytes
            mIncreaseSinceLastUpdate += value

            // Update Progress Bar progress if progress > 1% of max
            val currentTime = CollectionHelper.getInstance().getTimeSafe(context).intTimeMS()
            if (currentTime - mMostRecentUpdateTime > mUpdateInterval) {
                mMostRecentUpdateTime = currentTime
                mCurrentProgress += mIncreaseSinceLastUpdate
                mIncreaseSinceLastUpdate = 0
                notificationBuilder.setProgress(mSourceSize.toKB(), mCurrentProgress.toKB(), false)
                notificationBuilder.setContentText(
                    context.resources.getString(
                        R.string.migration_transferred_size,
                        mCurrentProgress.toMB(), mSourceSize.toMB()
                    )
                )
                notificationManager.notify(MIGRATION_NOTIFY_ID, notificationBuilder.build())
            }
        }

        override fun actualOnPostExecute(context: Context, result: Boolean) {
            if (result) {
                notificationBuilder.setContentTitle(context.resources.getString(R.string.migration_successful_message))
            } else {
                notificationBuilder.setContentTitle(context.resources.getString(R.string.migration_failed_message))
            }

            notificationBuilder.setProgress(0, 0, false).setOngoing(false)
            notificationManager.notify(MIGRATION_NOTIFY_ID, notificationBuilder.build())

            stopSelf()
        }

        override fun onCancelled() {
            cancelled = true
            stopSelf()
        }

        init {
            mSourceSize = sourceSize ?: 0
            mCurrentProgress = 0
            mIncreaseSinceLastUpdate = 0
        }
    }

    private fun getRestartBehavior() = START_STICKY

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val task = try {
            MigrateUserData.createInstance(AnkiDroidApp.getSharedPrefs(this))
        } catch (e: MigrateUserData.MissingDirectoryException) {
            // TODO: Log and handle - likely SD card removal
            throw e
        } catch (e: Exception) {
            stopSelf()
            return getRestartBehavior()
        }

        // a migration is not taking place
        if (task == null) {
            Timber.w("MigrationService started when a migration was not taking place")
            stopSelf()
            return getRestartBehavior()
        }

        this.task = task

        val sourceSize = safeGetDirectorySize(task, default = 0)

        CoroutineTask(
            task,
            MigrateUserDataListener(sourceSize),
            exceptionListener = null
        ).execute()

        return getRestartBehavior()
    }

    private fun safeGetDirectorySize(
        task: MigrateUserData,
        @Suppress("SameParameterValue") default: NumberOfBytes
    ): NumberOfBytes {
        return try {
            FileUtil.getDirectorySize(task.source.directory)
        } catch (e: Exception) {
            Timber.w(e, "Failed to get directory size")
            default
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     *
     * See: https://developer.android.com/guide/components/bound-services#Binder
     */
    @Suppress("unused") // getService
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        // TODO: Write a usable method for the Binder
        fun getService(): MigrationService = this@MigrationService
    }

    override fun onBind(intent: Intent): IBinder {
        return LocalBinder()
    }
}

private fun NumberOfBytes.toKB(): Int {
    return ((this / 1024).toInt())
}

private fun NumberOfBytes.toMB(): Int {
    return this.toKB() / 1024
}
