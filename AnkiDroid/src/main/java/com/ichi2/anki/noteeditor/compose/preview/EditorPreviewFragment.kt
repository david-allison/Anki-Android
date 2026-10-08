// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.preview

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentTemplatePreviewerBinding
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.previewer.setFrameStyle
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Keeps the existing card WebView's media, permission and renderer recovery behavior. */
class EditorPreviewFragment :
    CardViewerFragment(R.layout.fragment_template_previewer),
    BaseSnackbarBuilderProvider {
    override val viewModel: EditorPreviewViewModel by viewModels()
    private lateinit var binding: FragmentTemplatePreviewerBinding
    override val webViewLayout: SafeWebViewLayout get() = binding.webViewLayout
    override val baseSnackbarBuilder: SnackbarBuilder get() = { anchorView = binding.showAnswer }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding = FragmentTemplatePreviewerBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        binding.webViewContainer.setFrameStyle()
        binding.showAnswer.setOnClickListener { viewModel.toggleAnswer() }
        viewModel.showingAnswer
            .onEach { showingAnswer ->
                binding.showAnswer.setText(if (showingAnswer) CommonString.hide_answer else CommonString.show_answer)
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }
}
