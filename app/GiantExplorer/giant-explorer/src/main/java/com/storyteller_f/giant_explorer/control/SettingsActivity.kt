package com.storyteller_f.giant_explorer.control

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.storyteller_f.giant_explorer.R

class SettingsActivity : AppCompatActivity() {

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            preferenceManager.findPreference<ListPreference>(getString(R.string.setting_key_open_window_mode))
                ?.let {
                    it.title = getString(R.string.open_window_mode, it.value)
                    it.setOnPreferenceChangeListener { preference, newValue ->
                        preference.title = getString(R.string.open_window_mode, newValue)
                        if (newValue == getString(R.string.freeform_open_window_mode)) {
                            val context = preference.context
                            val packageManager = context.packageManager
                            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
                                Toast.makeText(context, "may be unsupported", Toast.LENGTH_SHORT).show()
                            }
                        }
                        true
                    }
                }
        }
    }
}

val Fragment.defaultSettings
    get() = activity?.defaultSettings

val Activity.defaultSettings: SharedPreferences
    get() = getSharedPreferences(
        "${packageName}_preferences",
        Activity.MODE_PRIVATE
    )
