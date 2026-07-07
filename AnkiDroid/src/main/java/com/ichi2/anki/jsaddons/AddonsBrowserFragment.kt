// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.utils.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Developer-only browser for the installed JS addons: list, enable/disable and delete.
 *
 * Reachable from Developer options when [com.ichi2.anki.settings.Prefs.devAddonsEnabled]
 * is set. Host with [com.ichi2.anki.SingleFragmentActivity].
 */
class AddonsBrowserFragment : Fragment(R.layout.fragment_addons_browser) {
    private val storage by lazy { AddonStorage(requireContext()) }
    private val stateStore by lazy { AddonStateStore(requireContext()) }

    private lateinit var adapter: AddonsBrowserAdapter

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        adapter =
            AddonsBrowserAdapter(
                onToggled = ::onAddonToggled,
                onDeleteRequested = ::onDeleteRequested,
                onConfigureRequested = ::onConfigureRequested,
            )
        view.findViewById<RecyclerView>(R.id.addons_list).adapter = adapter

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = TR.addonsWindowTitle()
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(
                        menu: android.view.Menu,
                        inflater: android.view.MenuInflater,
                    ) {
                        menu
                            .add(TR.addonsGetAddons())
                            .setOnMenuItemClickListener {
                                onGetAddonsRequested()
                                true
                            }.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                        menu
                            .add(TR.addonsInstallFromFile())
                            .setOnMenuItemClickListener {
                                onInstallFromFileRequested()
                                true
                            }.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                    }

                    override fun onMenuItemSelected(menuItem: MenuItem) = false
                },
            )
        }

        refreshAddonsList()
    }

    private val installFromFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                installFromUri(uri)
            }
        }

    /** Fetches the registry index, lets the user pick an addon, and installs it. */
    private fun onGetAddonsRequested() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { AddonRegistry().fetchAvailableAddons() }
            when (result) {
                is AddonRegistry.FetchResult.Failure ->
                    showThemedToast(requireContext(), result.message, false)
                is AddonRegistry.FetchResult.Success -> {
                    if (result.addons.isEmpty()) {
                        showThemedToast(requireContext(), "No addons available", true)
                        return@launch
                    }
                    val labels = result.addons.map { "${it.addonTitle} ${it.version}" }.toTypedArray()
                    AlertDialog.Builder(requireContext()).show {
                        setTitle(TR.addonsGetAddons())
                        setItems(labels) { _, index -> installFromRegistry(result.addons[index]) }
                        setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    }
                }
            }
        }
    }

    private fun installFromRegistry(addon: AddonModel) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { AddonRegistry().install(addon, storage) }
            when (result) {
                is AddonValidationResult.Valid -> {
                    showThemedToast(requireContext(), "Installed '${result.addonModel.addonTitle}'", true)
                    surfacePermissionsThenRefresh(result.addonModel)
                }
                is AddonValidationResult.Invalid ->
                    AlertDialog.Builder(requireContext()).show {
                        setTitle("Install failed")
                        setMessage(result.errors.joinToString("\n"))
                        setPositiveButton(R.string.dialog_ok) { _, _ -> }
                    }
            }
        }
    }

    private fun onInstallFromFileRequested() {
        installFromFileLauncher.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream"))
    }

    private fun installFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    val tempTarball = File.createTempFile("addon", ".tgz", requireContext().cacheDir)
                    try {
                        requireContext().contentResolver.openInputStream(uri)?.use { input ->
                            tempTarball.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@withContext AddonValidationResult.Invalid(listOf("Unable to read the selected file"))
                        storage.installFromTarball(tempTarball)
                    } finally {
                        tempTarball.delete()
                    }
                }
            when (result) {
                is AddonValidationResult.Valid -> {
                    Timber.i("Installed addon '%s'", result.addonModel.name)
                    showThemedToast(requireContext(), "Installed '${result.addonModel.addonTitle}'", true)
                    surfacePermissionsThenRefresh(result.addonModel)
                }
                is AddonValidationResult.Invalid ->
                    AlertDialog.Builder(requireContext()).show {
                        setTitle("Invalid addon")
                        setMessage(result.errors.joinToString("\n"))
                        setPositiveButton(R.string.dialog_ok) { _, _ -> }
                    }
            }
        }
    }

    /**
     * Shows the addon's declared permissions after install and grants them on accept.
     *
     * Only the recognised (non-[AddonPermission.Unknown]) permissions can be granted.
     * Declining installs the addon but grants nothing; the user can grant later from the
     * settings screen. This is the install-time half of the model; runtime request-on-use
     * is a later refinement (see `docs/addons/design-notes.md` §1).
     */
    private fun surfacePermissionsThenRefresh(addon: AddonModel) {
        val grantable = addon.permissions.filterNot { it is AddonPermission.Unknown }
        if (grantable.isEmpty()) {
            refreshAddonsList()
            return
        }
        AlertDialog.Builder(requireContext()).show {
            setTitle("'${addon.addonTitle}' requests")
            setMessage(grantable.joinToString("\n") { "• ${it.description}" })
            setPositiveButton("Grant") { _, _ ->
                stateStore.setGrantedPermissions(addon.name, grantable.map { it.id }.toSet())
                refreshAddonsList()
            }
            setNegativeButton("Not now") { _, _ -> refreshAddonsList() }
        }
    }

    private fun onAddonToggled(
        addon: AddonListItem,
        isEnabled: Boolean,
    ) {
        Timber.i("Addon '%s' toggled to %b", addon.name, isEnabled)
        stateStore.setEnabled(addon.name, isEnabled)
    }

    private fun onConfigureRequested(addon: AddonListItem) {
        startActivity(
            SingleFragmentActivity.getIntent(
                requireContext(),
                AddonSettingsFragment::class,
                AddonSettingsFragment.arguments(addon.name),
            ),
        )
    }

    private fun onDeleteRequested(addon: AddonListItem) {
        AlertDialog.Builder(requireContext()).show {
            setTitle(addon.title)
            setMessage("Remove addon '${addon.title}'?")
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                Timber.i("Deleting addon '%s'", addon.name)
                if (!storage.deleteAddon(addon.name)) {
                    showThemedToast(requireContext(), "Failed to remove addon", false)
                }
                stateStore.remove(addon.name)
                refreshAddonsList()
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        }
    }

    private fun refreshAddonsList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items =
                withContext(Dispatchers.IO) {
                    storage.getInstalledAddons().map { installed ->
                        when (val result = installed.result) {
                            is AddonValidationResult.Valid ->
                                AddonListItem(
                                    name = result.addonModel.name,
                                    title = result.addonModel.addonTitle,
                                    subtitle = "${result.addonModel.version} · ${result.addonModel.addonType}",
                                    isEnabled = stateStore.isEnabled(result.addonModel.name),
                                    isValid = true,
                                )
                            is AddonValidationResult.Invalid ->
                                AddonListItem(
                                    name = installed.directoryName,
                                    title = installed.directoryName,
                                    subtitle = result.errors.firstOrNull() ?: "Invalid addon",
                                    isEnabled = false,
                                    isValid = false,
                                )
                        }
                    }
                }
            adapter.submitList(items)
            requireView().findViewById<View>(R.id.no_addons_placeholder).isVisible = items.isEmpty()
        }
    }
}
