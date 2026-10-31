// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>

package com.ichi2.anki.ui.windows.managespace

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.progress.HasProgress
import com.ichi2.anki.common.progress.ProgressManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

sealed interface Size {
    data object Calculating : Size

    class Bytes(
        val totalSize: Long,
    ) : Size

    class FilesAndBytes(
        val files: Collection<File>,
        val totalSize: Long,
    ) : Size

    class Error(
        val exception: Exception,
        @StringRes val widgetTextId: Int = CommonString.pref__widget_text__error,
    ) : Size
}

/**************************************************************************************************
 ********************************************* Model **********************************************
 **************************************************************************************************/

class ManageSpaceViewModel(
    private val applicationStorage: ApplicationStorage = AndroidApplicationStorage(appContext),
) : ViewModel(),
    CollectionDirectoryProvider,
    HasProgress<ManageSpaceProgress> {
    override val collectionDirectory = CollectionManager.getCollectionDirectory()
    override val progressManager = ProgressManager<ManageSpaceProgress>()

    val flowOfDeleteUnusedMediaSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteBackupsSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteCollectionSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteEverythingSize = MutableStateFlow<Size>(Size.Calculating)

    init {
        launchCalculationOfBackupsSize()
        launchCalculationOfSizeOfEverything()
        launchCalculationOfCollectionSize()
        launchSearchForUnusedMedia()
    }

    /************************************* Unused media files *************************************/

    private fun launchSearchForUnusedMedia() =
        viewModelScope.launch {
            flowOfDeleteUnusedMediaSize.ifCollectionDirectoryExistsEmit {
                withCol {
                    val unusedFiles = media.findUnusedMediaFiles()
                    val unusedFilesSize = unusedFiles.sumOf(::calculateSize)
                    Size.FilesAndBytes(unusedFiles, unusedFilesSize)
                }
            }
        }

    suspend fun deleteMediaFiles(filesNamesToDelete: List<String>) {
        progressManager.withProgress(message = ManageSpaceProgress.DELETING_MEDIA) {
            try {
                withCol {
                    media.trashFiles(filesNamesToDelete)
                    media.emptyTrash()
                }
            } finally {
                launchCalculationOfSizeOfEverything()
                launchCalculationOfCollectionSize()
                launchSearchForUnusedMedia()
            }
        }
    }

    /***************************************** Backups ********************************************/

    private fun launchCalculationOfBackupsSize() =
        viewModelScope.launch {
            flowOfDeleteBackupsSize.ifCollectionDirectoryExistsEmit {
                withCol {
                    val backupFiles = BackupManager.getBackups(colDb).toList()
                    val backupFilesSize = backupFiles.sumOf(::calculateSize)
                    Size.FilesAndBytes(backupFiles, backupFilesSize)
                }
            }
        }

    suspend fun deleteBackups(backupsToDelete: List<File>) {
        progressManager.withProgress(message = ManageSpaceProgress.DELETING_BACKUPS) {
            try {
                withCol { BackupManager.deleteBackups(this@withCol, backupsToDelete) }
            } finally {
                launchCalculationOfBackupsSize()
                launchCalculationOfCollectionSize()
                launchCalculationOfSizeOfEverything()
            }
        }
    }

    /*************************************** Collection *******************************************/

    private fun launchCalculationOfCollectionSize() =
        viewModelScope.launch {
            flowOfDeleteCollectionSize.ifCollectionDirectoryExistsEmit {
                withContext(Dispatchers.IO) {
                    Size.Bytes(calculateSize(collectionDirectory))
                }
            }
        }

    suspend fun deleteCollection() {
        progressManager.withProgress(message = ManageSpaceProgress.DELETING_COLLECTION) {
            try {
                CollectionManager.deleteCollectionDirectory() // Executed in withQueue
            } finally {
                launchCalculationOfBackupsSize()
                launchCalculationOfSizeOfEverything()
                launchCalculationOfCollectionSize()
                launchSearchForUnusedMedia()
            }
        }
    }

    /*************************************** Everything *******************************************/

    private fun launchCalculationOfSizeOfEverything() =
        viewModelScope.launch {
            flowOfDeleteEverythingSize.emit(Size.Calculating)
            flowOfDeleteEverythingSize.emit(
                withContext(Dispatchers.IO) {
                    try {
                        Size.Bytes(applicationStorage.sizeInBytes())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Size.Error(e)
                    }
                },
            )
        }

    // This kills the process afterwards, so no need to recalculate sizes
    fun deleteEverything() {
        applicationStorage.clear()
    }

    /**********************************************************************************************/

    private suspend fun MutableStateFlow<Size>.ifCollectionDirectoryExistsEmit(block: suspend () -> Size) {
        try {
            ensureCanWriteToOrCreateCollectionDirectory()
            if (!collectionDirectoryExists()) {
                emit(Size.FilesAndBytes(emptyList(), 0L))
            } else {
                emit(Size.Calculating)
                emit(block())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Size.Error(e))
        }
    }
}

enum class ManageSpaceProgress {
    DELETING_MEDIA,
    DELETING_BACKUPS,
    DELETING_COLLECTION,
}
