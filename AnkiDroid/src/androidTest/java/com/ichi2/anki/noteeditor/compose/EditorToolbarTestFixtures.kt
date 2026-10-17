// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

/** Configure a field through the same shared-preset and quick-action APIs as the UI. */
internal suspend fun EditorViewModel.setFieldToolbar(
    target: EditorFieldTarget,
    toolbar: EditorFieldToolbar,
) {
    val current = checkNotNull(state.value)
    check(target.matches(current))
    val name = "Test toolbar ${target.field + 1}"
    val preset = current.toolbarPresets.firstOrNull { it.name == name } ?: EditorToolbarPreset(name = name)
    saveToolbarPreset(preset.withToolbar(toolbar))
    setFieldToolbarPreset(target, preset.id)
    setFieldQuickActions(target, toolbar.quickActions)
    setFieldToolbarVisibility(target, toolbar.showToolbar)
}
