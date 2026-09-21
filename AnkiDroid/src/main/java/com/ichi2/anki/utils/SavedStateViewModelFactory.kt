// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Creates a ViewModel with a [ViewModelSavedStateHandle] independent of its owner's launch arguments.
 * AndroidX otherwise copies every argument of the owning activity or fragment into each handle.
 *
 * Pass launch inputs directly to the ViewModel constructor. Any state written to its handle is
 * saved and restored normally, without declaring its keys here.
 */
inline fun <reified VM : ViewModel> savedStateViewModelFactory(
    crossinline create: (ViewModelSavedStateHandle) -> VM,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val handle =
                MutableCreationExtras(this)
                    .apply { this[DEFAULT_ARGS_KEY] = Bundle.EMPTY }
                    .createSavedStateHandle()
            create(handle)
        }
    }
