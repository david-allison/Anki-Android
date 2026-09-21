// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.AnkiActivity

/**
 * Saved UI state for a ViewModel, without automatically copying its owner's launch arguments.
 *
 * Pass launch inputs explicitly to the ViewModel constructor. A fresh handle starts empty;
 * restored state is retained. Values written to the handle, including through its state flows,
 * are saved and restored normally after process death. No separate list of saved keys is needed.
 *
 * This behavior is supplied by:
 *
 * - [AnkiActivity.defaultViewModelCreationExtras] for activity-scoped ViewModels, including
 *   those obtained through `activityViewModels()`.
 * - [savedStateViewModelFactory] for fragment-scoped ViewModels.
 *
 * This alias documents that creation contract; it does not filter arguments itself. A fragment's
 * default factory still copies its arguments, so use [savedStateViewModelFactory] for those models.
 * Keep state small: explicitly writing large launch inputs into the handle will still duplicate them.
 *
 * An alias preserves the [SavedStateHandle] constructor signature expected by AndroidX's default
 * ViewModel factory, allowing activity-scoped models to use it without a custom factory.
 */
typealias ViewModelSavedStateHandle = SavedStateHandle
