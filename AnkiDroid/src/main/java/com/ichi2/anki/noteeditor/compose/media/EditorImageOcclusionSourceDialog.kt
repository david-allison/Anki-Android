// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.media

import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CommonString

/** Retain the offered clipboard image and source choice across activity recreation. */
class EditorImageOcclusionSourceDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(CommonString.image_occlusion)
            .setItems(
                arrayOf(getString(CommonString.paste_from_clipboard), getString(CommonString.multimedia_editor_image_field_editing_galery)),
            ) { _, which ->
                result(
                    if (which == 0) {
                        bundleOf(IMAGE to BundleCompat.getParcelable(requireArguments(), IMAGE, Uri::class.java))
                    } else {
                        bundleOf(GALLERY to true)
                    },
                )
            }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> result(Bundle.EMPTY) }
            .create()

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        result(Bundle.EMPTY)
    }

    private fun result(value: Bundle) = parentFragmentManager.setFragmentResult(REQUEST_KEY, value)

    companion object {
        const val REQUEST_KEY = "editorImageOcclusionSource"
        const val IMAGE = "image"
        const val GALLERY = "gallery"

        fun newInstance(image: Uri) = EditorImageOcclusionSourceDialog().apply { arguments = bundleOf(IMAGE to image) }
    }
}
