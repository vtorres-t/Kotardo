package org.koitharu.kotatsu.settings

import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentResultListener
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.viewLifecycleScope
import org.koitharu.kotatsu.sync.data.SyncSettings
import org.koitharu.kotatsu.sync.domain.SyncController
import org.koitharu.kotatsu.sync.ui.SyncHostDialogFragment
import javax.inject.Inject

@AndroidEntryPoint
class SyncSettingsFragment : BasePreferenceFragment(R.string.sync_settings), FragmentResultListener {

	@Inject
	lateinit var syncSettings: SyncSettings

	@Inject
	lateinit var syncController: SyncController

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		Log.d("AMOGUS", requireActivity().intent.data.toString())
		val intent = activity?.intent
		when(intent?.action) {
			Intent.ACTION_VIEW -> {
				when (intent.data?.host) {
					SettingsActivity.HOST_RESET_PASSWORD -> {
						Log.d("AMOGUS", intent.data?.query.toString())
					}
					else -> null
				}
			}
		}

		addPreferencesFromResource(R.xml.pref_sync)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		childFragmentManager.setFragmentResultListener(SyncHostDialogFragment.REQUEST_KEY, viewLifecycleOwner, this)
		bindSummaries()
	}

	override fun onResume() {
		super.onResume()
		bindSummaries()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			SyncSettings.KEY_SYNC_URL -> {
				SyncHostDialogFragment.show(childFragmentManager, null)
				true
			}

			SyncSettings.KEY_SYNC -> {
				val am = AccountManager.get(requireContext())
				val accountType = getString(R.string.account_type_sync)
				val account = am.getAccountsByType(accountType).firstOrNull()
				if (account == null) {
					am.addAccount(accountType, accountType, null, null, requireActivity(), ::onAccountUpdated, null)
				} else {
					if (!router.openSystemSyncSettings(account)) {
						Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
					}
				}
				true
			}

			SyncSettings.KEY_LOGOUT -> {
				val am = AccountManager.get(requireContext())
				val accountType = getString(R.string.account_type_sync)
				val account = am.getAccountsByType(accountType).firstOrNull()
				if(account != null) {
					am.removeAccount(account, requireActivity(), ::onAccountUpdated, null)
				}
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onFragmentResult(requestKey: String, result: Bundle) {
		bindSummaries()
	}

	private fun bindHostSummary() {
		val preference = findPreference<Preference>(SyncSettings.KEY_SYNC_URL) ?: return
		preference.summary = syncSettings.syncUrl
	}

	private fun onAccountUpdated(future: AccountManagerFuture<Bundle>) {
		val am = AccountManager.get(requireContext())
		val accountType = getString(R.string.account_type_sync)
		val account = am.getAccountsByType(accountType).firstOrNull()

		if(account != null) {
			syncController.setEnabled(account, syncFavorites = true, syncHistory = true)
			viewLifecycleScope.launch {
				syncController.requestFullSync()
			}
		}

		bindSummaries()
	}

	private fun bindSummaries() {
		bindHostSummary()
		bindSyncSummary()
	}

	private fun bindSyncSummary() {
		viewLifecycleScope.launch {
			val account = withContext(Dispatchers.Default) {
				val type = getString(R.string.account_type_sync)
				AccountManager.get(requireContext()).getAccountsByType(type).firstOrNull()
			}
			findPreference<Preference>(SyncSettings.KEY_SYNC)?.run {
				summary = when {
					account == null -> getString(R.string.sync_login)
					syncController.isEnabled(account) -> {
						val enabledSync = ArrayList<String>()
						if(syncController.isFavouritesEnabled(account)) enabledSync.add(getString(R.string.favourites))
						if(syncController.isHistoryEnabled(account)) enabledSync.add(getString(R.string.history))

						account.name + enabledSync.joinToString(", ", " (", ")")
					}
					else -> getString(R.string.disabled)
				}
			}
			findPreference<Preference>(SyncSettings.KEY_SYNC_URL)?.isEnabled = account != null
			findPreference<Preference>(SyncSettings.KEY_LOGOUT)?.isEnabled = account != null
		}
	}
}
