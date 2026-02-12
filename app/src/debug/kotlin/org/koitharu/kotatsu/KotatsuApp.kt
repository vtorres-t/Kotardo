package org.koitharu.kotatsu

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.StrictMode
import androidx.core.content.edit
import androidx.fragment.app.strictmode.FragmentStrictMode
import leakcanary.LeakCanary
import org.koitharu.kotatsu.core.BaseApp

class KotatsuApp : BaseApp() {

	var isLeakCanaryEnabled: Boolean
		get() = getDebugPreferences(this).getBoolean(KEY_LEAK_CANARY, true)
		set(value) {
			getDebugPreferences(this).edit { putBoolean(KEY_LEAK_CANARY, value) }
			configureLeakCanary()
		}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		enableStrictMode()
		configureLeakCanary()
	}

	private fun configureLeakCanary() {
		LeakCanary.config = LeakCanary.config.copy(
			dumpHeap = isLeakCanaryEnabled,
		)
	}

	private fun enableStrictMode() {
        val notifier = StrictModeNotifier(this)

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().apply {
                detectNetwork()
                detectDiskWrites()
                detectCustomSlowCalls()
                detectResourceMismatches()
                detectUnbufferedIo()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) detectExplicitGc()
                penaltyLog()
                if (notifier != null) {
                    penaltyListener(notifier.executor, notifier)
                }
            }.build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().apply {
                detectActivityLeaks()
                detectLeakedSqlLiteObjects()
                detectLeakedClosableObjects()
                detectLeakedRegistrationObjects()
                detectContentUriWithoutPermission()
                detectFileUriExposure()
                penaltyLog()
                if (notifier != null) {
                    penaltyListener(notifier.executor, notifier)
                }
            }.build(),
        )
        FragmentStrictMode.defaultPolicy = FragmentStrictMode.Policy.Builder().apply {
            detectWrongFragmentContainer()
            detectFragmentTagUsage()
            detectRetainInstanceUsage()
            detectSetUserVisibleHint()
            detectWrongNestedHierarchy()
            detectFragmentReuse()
            penaltyLog()
            if (notifier != null) {
                penaltyListener(notifier)
            }
        }.build()
    }

	private companion object {

		const val PREFS_DEBUG = "_debug"
		const val KEY_LEAK_CANARY = "leak_canary"

		fun getDebugPreferences(context: Context): SharedPreferences =
			context.getSharedPreferences(PREFS_DEBUG, MODE_PRIVATE)
	}
}
