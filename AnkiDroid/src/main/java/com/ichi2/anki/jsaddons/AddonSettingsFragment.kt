// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.utils.show
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import timber.log.Timber

/**
 * Host-rendered settings screen for a single addon.
 *
 * Two views of the same stored values are provided, switchable from the toolbar menu:
 *
 * 1. **Generated native UI** (the default): each [AddonSettingDefinition] becomes a native
 *    widget (switch, dropdown, number/text field...). Works without executing any addon
 *    code, so it is safe even before the addon is trusted. Unknown setting types are
 *    skipped rather than failing the screen.
 * 2. **Raw JSON editor** (the fallback, and the only option for a schema-less addon): edits
 *    the stored settings object directly, mirroring Anki desktop's config editor. Nothing a
 *    schema cannot express is ever unreachable.
 */
class AddonSettingsFragment : Fragment(R.layout.fragment_addon_settings) {
    private val addonName: String by lazy { requireArguments().getString(ARG_ADDON_NAME)!! }
    private val stateStore by lazy { AddonStateStore(requireContext()) }
    private val addon: AddonModel? by lazy {
        val installed = AddonStorage(requireContext()).getInstalledAddons().firstOrNull { it.directoryName == addonName }
        (installed?.result as? AddonValidationResult.Valid)?.addonModel
    }
    private val prettyJson = Json { prettyPrint = true }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = addon?.addonTitle ?: addonName
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            menu.add("Edit JSON").setOnMenuItemClickListener {
                showJsonEditor()
                true
            }
        }
        renderNativeSettings()
    }

    /** Implementation 1: native widgets generated from the schema */
    private fun renderNativeSettings() {
        val container = requireView().findViewById<LinearLayout>(R.id.settings_container)
        container.removeAllViews()
        val schema = addon?.settings.orEmpty()
        val values = stateStore.getSettingsValues(addonName)

        requireView().findViewById<View>(R.id.no_settings_placeholder).isVisible =
            schema.none { it.type in AddonSettingDefinition.VALUE_TYPES }

        for (setting in schema) {
            buildRow(setting, values)?.let(container::addView)
        }
    }

    private fun buildRow(
        setting: AddonSettingDefinition,
        values: JsonObject,
    ): View? {
        if (setting.type == AddonSettingDefinition.TYPE_HEADING) return headingView(setting.title.orEmpty())
        // 'action' fires addon JS, which is out of scope for the host-rendered screen
        if (setting.type == AddonSettingDefinition.TYPE_ACTION) return null
        val key = setting.key ?: return null
        val current = values[key] ?: setting.default

        val row = verticalRow()
        row.addView(labelView(setting))
        val widget: View? =
            when (setting.type) {
                AddonSettingDefinition.TYPE_TOGGLE ->
                    MaterialSwitch(requireContext()).apply {
                        isChecked = (current as? JsonPrimitive)?.booleanOrNull ?: false
                        setOnCheckedChangeListener { _, checked -> save(key, JsonPrimitive(checked)) }
                    }
                AddonSettingDefinition.TYPE_ENUM ->
                    enumSpinner(setting, (current as? JsonPrimitive)?.contentOrNull)
                AddonSettingDefinition.TYPE_NUMBER ->
                    numberField((current as? JsonPrimitive)?.doubleOrNull) { save(key, JsonPrimitive(it)) }
                AddonSettingDefinition.TYPE_TEXT, AddonSettingDefinition.TYPE_TEXTAREA ->
                    textField(
                        (current as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        multiline = setting.type == AddonSettingDefinition.TYPE_TEXTAREA,
                    ) { save(key, JsonPrimitive(it)) }
                else -> {
                    // an unknown (e.g. future) setting type: show the label only
                    Timber.d("Skipping unsupported setting type '%s'", setting.type)
                    null
                }
            }
        widget?.let(row::addView)
        return row
    }

    private fun save(
        key: String,
        value: JsonPrimitive,
    ) = stateStore.setSettingValue(addonName, key, value)

    /** Implementation 2: raw JSON editor over the stored settings values */
    private fun showJsonEditor() {
        val editText =
            EditText(requireContext()).apply {
                setText(prettyJson.encodeToString(JsonObject.serializer(), stateStore.getSettingsValues(addonName)))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                val pad = 16.toPx(context)
                setPadding(pad, pad, pad, 0)
            }
        AlertDialog.Builder(requireContext()).show {
            setTitle("Edit JSON")
            setView(editText)
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                try {
                    val parsed =
                        Json.parseToJsonElement(editText.text.toString()) as? JsonObject
                            ?: throw IllegalArgumentException("The top-level value must be a JSON object")
                    stateStore.replaceSettingsValues(addonName, parsed)
                    renderNativeSettings()
                } catch (e: Exception) {
                    showThemedToast(requireContext(), "Invalid JSON: ${e.message}", false)
                }
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        }
    }

    // ---- view builders ----

    private fun headingView(text: String): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 24.toPx(context), 0, 4.toPx(context))
        }

    private fun verticalRow(): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8.toPx(requireContext())
                }
        }

    private fun labelView(setting: AddonSettingDefinition): View =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(requireContext()).apply {
                    text = setting.title.orEmpty()
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                },
            )
            setting.description?.takeIf { it.isNotBlank() }?.let { desc ->
                addView(
                    TextView(requireContext()).apply {
                        text = desc
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    },
                )
            }
        }

    private fun enumSpinner(
        setting: AddonSettingDefinition,
        currentValue: String?,
    ): Spinner {
        val choices = setting.choices.orEmpty()
        return Spinner(requireContext()).apply {
            adapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    choices.map { it.label ?: it.value.orEmpty() },
                )
            choices.indexOfFirst { it.value == currentValue }.takeIf { it >= 0 }?.let(::setSelection)
            onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        choices.getOrNull(position)?.value?.let { save(setting.key!!, JsonPrimitive(it)) }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
        }
    }

    private fun numberField(
        current: Double?,
        onChange: (Double) -> Unit,
    ): EditText =
        TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(current?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "")
            doAfterTextChanged { text -> text?.toString()?.toDoubleOrNull()?.let(onChange) }
        }

    private fun textField(
        current: String,
        multiline: Boolean,
        onChange: (String) -> Unit,
    ): EditText =
        TextInputEditText(requireContext()).apply {
            inputType =
                if (multiline) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                } else {
                    InputType.TYPE_CLASS_TEXT
                }
            setText(current)
            doAfterTextChanged { text -> onChange(text?.toString().orEmpty()) }
        }

    private fun Int.toPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        const val ARG_ADDON_NAME = "addonName"

        fun arguments(addonName: String) = Bundle().apply { putString(ARG_ADDON_NAME, addonName) }
    }
}
