/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.SelectTemplateFragment.Layout.Standard
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.SelectTemplateFragment.Layout.WithWarning
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_REGULAR
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_REGULAR
import com.ichi2.anki.launchCatchingRequiringOneWaySync
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.withProgress
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.NoteTypeId
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 *
 *
 * @see ChangeNoteTypeViewModel
 */
class ChangeNoteTypeDialog : DialogFragment() {
    private val viewModel: ChangeNoteTypeViewModel by viewModels { defaultViewModelProviderFactory }

    private var initialRotation: Int = 0
    private lateinit var noteTypeSpinner: Spinner
    private val allNoteTypeIds: List<Long>
        get() = viewModel.availableNoteTypes.map { it.id }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.initialRotation = getScreenRotation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (getScreenRotation() != initialRotation) {
            Timber.d("recreating activity: orientation changed with 'Change Note Type' open")
            requireAnkiActivity().recreate()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .create {
                title(
                    text =
                        TR.browsingChangeNotetype().toSentenceCase(
                            this@ChangeNoteTypeDialog,
                            R.string.sentence_change_note_type,
                        ),
                )
                positiveButton(R.string.dialog_ok) { requireAnkiActivity().changeNoteType(viewModel) }
                negativeButton(R.string.dialog_cancel)
                setView(R.layout.change_note_type_dialog)
            }.apply {
                show()

                launchCatchingTask {
                    // TODO
                    viewModel.initStatus.collect {
                        if (it == InitStatus.COMPLETED) {
                            Timber.i("Dialog init completed")
                            findViewById<View>(R.id.change_note_type_layout)!!.isVisible = true
                            setupChangeNoteTypeDialog()
                        }
                    }
                }
            }

    private fun setupChangeNoteTypeDialog() {
        noteTypeSpinner =
            requireDialog().findViewById<Spinner>(R.id.cnt_note_type_spinner)!!.apply {
                val modelNames = viewModel.availableNoteTypes.map { it.name }
                adapter =
                    object : ArrayAdapter<String>(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        modelNames,
                    ) {
                        override fun getItemId(position: Int): Long = viewModel.availableNoteTypes[position].id

                        override fun hasStableIds() = true
                    }.apply {
                        // The resource passed to the constructor is normally used for both the spinner view
                        // and the dropdown list. This keeps the former and overrides the latter.
                        setDropDownViewResource(R.layout.spinner_dropdown_item_with_radio)
                    }

                // Set position only after we have the note type IDs
                val position = allNoteTypeIds.indexOf(viewModel.inputNoteType.id)
                setSelection(position, false)

                onItemSelectedListener =
                    BasicItemSelectedListener { position, id: NoteTypeId ->
                        viewModel.setOutputNoteTypeId(id)
                    }
            }

        // setup viewpager + tabs
        val viewPager = requireDialog().findViewById<ViewPager2>(R.id.change_note_type_pager)!!
        viewPager.adapter = ChangeNoteTypeStateAdapter(this@ChangeNoteTypeDialog)
        val tabLayout = requireDialog().findViewById<TabLayout>(R.id.change_note_type_tab_layout)!!
        TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            when (position) {
                0 -> tab.text = TR.changeNotetypeFields()
                1 -> tab.text = TR.changeNotetypeTemplates()
                else -> throw IllegalStateException("invalid position: $position")
            }
        }.attach()
        tabLayout.selectTab(tabLayout.getTabAt(0))

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    ChangeNoteTypeViewModel.Tab.entries
                        .first { it.position == position }
                        .let { selectedTab ->
                            viewModel.currentTab = selectedTab
                        }
                    super.onPageSelected(position)
                }
            },
        )
    }

    private fun getScreenRotation() = ContextCompat.getDisplayOrDefault(requireContext()).rotation

    companion object {
        const val ARG_NOTE_IDS = "ARG_NOTE_IDS"

        @CheckResult
        fun newInstance(noteIds: List<NoteId>) =
            ChangeNoteTypeDialog().apply {
                arguments =
                    bundleOf(
                        ARG_NOTE_IDS to noteIds.toLongArray(),
                    )
                Timber.i("Showing 'change note type' dialog for %d notes", noteIds.size)
            }
    }

    class ChangeNoteTypeStateAdapter(
        fragment: Fragment,
    ) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment =
            when (position) {
                0 -> SelectFieldsFragment()
                1 -> SelectTemplateFragment()
                else -> throw IllegalStateException("invalid position: $position")
            }

        override fun getItemCount() = 2
    }

    class SelectFieldsFragment : Fragment(R.layout.dialog_fields) {
        private val viewModel: ChangeNoteTypeViewModel by viewModels({ requireParentFragment() })

        val fieldsContainer: LinearLayout
            get() = requireView().findViewById(R.id.fields_container)

        val fieldTextContainer: MaterialTextView
            get() = requireView().findViewById(R.id.field_removal_text)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<MaterialTextView>(R.id.current_field_label).text =
                TR.changeNotetypeCurrent()
            view.findViewById<MaterialTextView>(R.id.new_field_label).text = TR.changeNotetypeNew()

            createFieldSpinner()

            setupFlows()
        }

        override fun onResume() {
            super.onResume()
            requireView().requestLayout()
        }

        fun setupFlows() {
            lifecycleScope.launch {
                viewModel.outputNoteTypeFlow.collect {
                    createFieldSpinner()
                }
            }

            lifecycleScope.launch {
                viewModel.discardedFieldsFlow.collect { fields ->
                    showDiscardedFieldsMessage(fields)
                }
            }
        }

        private fun showDiscardedFieldsMessage(discardedFields: List<String>) {
            fieldTextContainer.isVisible = discardedFields.isNotEmpty()
            if (discardedFields.isEmpty()) {
                return
            }

            fieldTextContainer.text =
                SpannableStringBuilder()
                    .append(TR.changeNotetypeWillDiscardContent() + " ")
                    .boldList(discardedFields, ", ")
        }

        private fun createFieldSpinner() {
            fieldsContainer.removeAllViews()

            val selectedNotetypeJson = viewModel.outputNoteTypeFlow.value
            val fieldNames = selectedNotetypeJson.fieldsNames

            fun buildFieldLayout() =
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            MATCH_PARENT,
                            WRAP_CONTENT,
                        )
                }

            fun buildFieldSpinner(i: Int) =
                Spinner(requireContext())
                    .apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    }.apply {
                        val fieldSpinnerOptions = fieldNames + "(${getString(R.string.nothing)})"
                        Timber.d("createTemplateSpinner: %d items + (nothing)", fieldNames.size)
                        this.adapter =
                            ArrayAdapter(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                fieldSpinnerOptions,
                            ).apply {
                                // The resource passed to the constructor is normally used for both the spinner view
                                // and the dropdown list. This keeps the former and overrides the latter.
                                this.setDropDownViewResource(R.layout.spinner_dropdown_item_with_radio)
                            }

                        val selectionIndex =
                            if (i < fieldSpinnerOptions.size) i else fieldSpinnerOptions.size
                        setSelection(selectionIndex, false)

                        // Add an item selection listener to update the field mapping when user changes selection
                        val oldIndex = i
                        onItemSelectedListener =
                            BasicItemSelectedListener { position, id ->
                                // The last index is '(Nothing)'
                                val newMapping =
                                    if (position == fieldSpinnerOptions.lastIndex) {
                                        SelectedIndex.NOTHING
                                    } else {
                                        SelectedIndex.from(position)
                                    }
                                viewModel.updateFieldMapping(oldIndex, newMapping)
                            }
                    }

            fun buildFieldText(initialText: String) =
                MaterialTextView(requireContext()).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    text = initialText
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }

            for (i in fieldNames.indices) {
                val fieldLayout =
                    buildFieldLayout().apply {
                        addView(buildFieldSpinner(i))
                        addView(buildFieldText(fieldNames[i]))
                    }
                fieldsContainer.addView(fieldLayout)
            }
        }
    }

    class SelectTemplateFragment : Fragment(R.layout.dialog_templates) {
        private val viewModel: ChangeNoteTypeViewModel by viewModels({ requireParentFragment() })

        val templatesCurrentHeader: MaterialTextView
            get() = requireView().findViewById(R.id.current_template_label)

        val templatesNewHeader: MaterialTextView
            get() = requireView().findViewById(R.id.new_template_label)

        val templatesDefaultLayout: LinearLayout
            get() = requireView().findViewById(R.id.templates_container)
        val templatesHeaderLayout: LinearLayout
            get() = requireView().findViewById(R.id.templates_header_layout)
        val clozeInfoLayout: LinearLayout
            get() = requireView().findViewById(R.id.cloze_info_layout)
        val clozeInfoTextView: MaterialTextView
            get() = requireView().findViewById(R.id.cloze_info_text)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            templatesCurrentHeader.text = TR.changeNotetypeCurrent()
            templatesNewHeader.text = TR.changeNotetypeNew()

            createTemplateSpinner()

            // show/hide cloze info layout based on note type
            lifecycleScope.launch {
                viewModel.outputNoteTypeFlow.collect {
                    createTemplateSpinner()
                }
            }

            lifecycleScope.launch {
                viewModel.conversionTypeFlow.collect { type ->
                    when (val layout = Layout.fromConversionType(type)) {
                        is Standard -> {
                            clozeInfoLayout.visibility = View.GONE
                            templatesDefaultLayout.visibility = View.VISIBLE
                            templatesHeaderLayout.visibility = View.VISIBLE
                        }

                        is WithWarning -> {
                            clozeInfoLayout.visibility = View.VISIBLE
                            templatesDefaultLayout.visibility = View.GONE
                            templatesHeaderLayout.visibility = View.GONE
                            clozeInfoTextView.text = getString(layout.warningRes)
                        }
                    }
                }
            }

            lifecycleScope.launch {
                viewModel.discardedTemplatesFlow.collect { discarded ->
                    Timber.w("discarded %s", discarded)
                    showDiscardedTemplatesMessage(discarded)
                }
            }
        }

        override fun onResume() {
            super.onResume()
            this.requireView().requestLayout()
        }

        sealed class Layout {
            data object Standard : Layout()

            data class WithWarning(
                @StringRes val warningRes: Int,
            ) : Layout()

            companion object {
                fun fromConversionType(conversionType: ConversionType): Layout =
                    when (conversionType) {
                        REGULAR_TO_REGULAR -> Standard
                        CLOZE_TO_CLOZE, REGULAR_TO_CLOZE ->
                            WithWarning(
                                warningRes = R.string.card_numbers_unchanged,
                            )

                        // TODO: This isn't correct:
                        // If changing to a regular note type,
                        // **AND** there are more cloze deletions than available card templates,
                        // any extra cards will be removed.
                        CLOZE_TO_REGULAR ->
                            WithWarning(
                                warningRes = R.string.extra_cloze_deletions_removed,
                            )
                    }
            }
        }

        private fun showDiscardedTemplatesMessage(discardedTemplateNames: List<String>) {
            val templateRemovalLabel = requireView().findViewById<MaterialTextView>(R.id.template_removal_text)
            templateRemovalLabel.isVisible = discardedTemplateNames.isNotEmpty()
            if (discardedTemplateNames.isEmpty()) {
                return
            }

            templateRemovalLabel.text =
                SpannableStringBuilder()
                    .append(TR.changeNotetypeWillDiscardCards() + " ")
                    .boldList(discardedTemplateNames, ", ")
        }

        private fun createTemplateSpinner() {
            val templatesContainer =
                requireView().findViewById<LinearLayout>(R.id.templates_container)
            templatesContainer.removeAllViews()

            val templateNames = viewModel.outputNoteTypeFlow.value.templatesNames

            fun buildTemplateLayout() =
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            MATCH_PARENT,
                            WRAP_CONTENT,
                        )
                }

            fun buildTemplateSpinner(spinnerIndex: Int) =
                Spinner(requireContext())
                    .apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    }.apply {
                        val templateSpinnerOptions = templateNames + "(${requireContext().getString(R.string.nothing)})"
                        Timber.d("createTemplateSpinner: %d items + (nothing)", templateNames.size)
                        adapter =
                            ArrayAdapter(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                templateSpinnerOptions,
                            ).apply {
                                // The resource passed to the constructor is normally used for both the spinner view
                                // and the dropdown list. This keeps the former and overrides the latter.
                                setDropDownViewResource(R.layout.spinner_dropdown_item_with_radio)
                            }

                        val selectionIndex =
                            if (spinnerIndex < templateNames.size) spinnerIndex else templateNames.size

                        setSelection(selectionIndex, false)

                        onItemSelectedListener =
                            BasicItemSelectedListener { position, id ->
                                // The last index is '(Nothing)'
                                val newMapping =
                                    if (position == templateSpinnerOptions.lastIndex) {
                                        SelectedIndex.NOTHING
                                    } else {
                                        SelectedIndex.from(position)
                                    }
                                viewModel.updateTemplateMapping(outputTemplateIndex = spinnerIndex, newMapping)
                            }
                    }

            fun buildTemplateText(templateName: String) =
                MaterialTextView(requireContext()).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    text = templateName
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }

            for (i in templateNames.indices) {
                val templateLayout =
                    buildTemplateLayout().apply {
                        addView(
                            buildTemplateSpinner(
                                spinnerIndex = i,
                            ),
                        )
                        addView(buildTemplateText(templateName = templateNames[i]))
                    }
                templatesContainer.addView(templateLayout)
            }
        }
    }
}

private fun AnkiActivity.changeNoteType(viewModel: ChangeNoteTypeViewModel) =
    this.launchCatchingRequiringOneWaySync {
        withProgress {
            viewModel.executeChangeNoteTypeAsync().await()
        }
        showSnackbar(resources.getString(R.string.change_note_type_complete), Snackbar.LENGTH_SHORT)
    }

/**
 * Appends [strings] to the builder, separated by [separator]. Only the strings are bolded
 */
private fun SpannableStringBuilder.boldList(
    strings: List<String>,
    separator: String,
): SpannableStringBuilder {
    var isFirst = true
    for (element in strings) {
        if (!isFirst) append(separator)
        appendBold(element)
        isFirst = false
    }
    return this
}

/**
 * Appends the provided text as a bold string
 */
private fun SpannableStringBuilder.appendBold(text: String): SpannableStringBuilder {
    val start = length
    append(text)
    setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return this
}
