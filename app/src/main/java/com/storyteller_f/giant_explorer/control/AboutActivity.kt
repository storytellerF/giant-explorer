package com.storyteller_f.giant_explorer.control

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.storyteller_f.common_ui.CommonActivity
import com.storyteller_f.giant_explorer.BuildConfig
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.ActivityAboutBinding
import com.storyteller_f.giant_explorer.view.applyScreenInsets

class AboutActivity : CommonActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyScreenInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
        binding.repository.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val PROJECT_URL = "https://github.com/storytellerF/giant-explorer"
    }
}
