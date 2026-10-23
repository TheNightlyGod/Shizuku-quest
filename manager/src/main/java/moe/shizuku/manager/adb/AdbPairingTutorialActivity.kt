package moe.shizuku.manager.adb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AdbPairingTutorialActivityBinding
import java.net.ConnectException

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppBarActivity() {

    private lateinit var binding: AdbPairingTutorialActivityBinding
    private var adbMdns: AdbMdns? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = AdbPairingTutorialActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.apply {
            updateDevOptionsState()

            pairingCode.editText?.doAfterTextChanged {
                pairingCode.error = null
            }
            port.editText?.doAfterTextChanged {
                port.error = null
            }

            buttonPair.setOnClickListener {
                onPairClicked()
            }
        }
    }

    private fun isDevOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }

    private fun updateDevOptionsState() {
        val enabled = isDevOptionsEnabled(this)
        binding.apply {
            if (enabled) {
                developerOptions.text = getString(R.string.development_settings)
                devOptionsDisabledCard.isGone = true
                step1ClickableNotice.isVisible = true
                developerOptions.setOnClickListener {
                    val intent = Intent().apply {
                        component = ComponentName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Throwable) {
                        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                        }
                        try {
                            startActivity(fallbackIntent)
                        } catch (e2: Throwable) {
                        }
                    }
                }
            } else {
                developerOptions.text = getString(R.string.settings_button_text)
                devOptionsDisabledCard.isVisible = true
                step1ClickableNotice.isGone = true
                developerOptions.setOnClickListener {
                    openAndroidSettings(this@AdbPairingTutorialActivity)
                }
            }
        }
    }

    private fun openAndroidSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:com.android.settings")
            setPackage("com.android.settings")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallback)
            } catch (e2: Exception) {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateDevOptionsState()
    }

    override fun onStart() {
        super.onStart()
        startMdns()
    }

    override fun onStop() {
        super.onStop()
        stopMdns()
    }

    private fun startMdns() {
        if (adbMdns == null) {
            adbMdns = AdbMdns(this, AdbMdns.TLS_PAIRING) { port ->
                runOnUiThread {
                    if (port > 0) {
                        binding.pairingStatus.text =
                            getString(R.string.notification_adb_pairing_service_found_title) + " ($port)"
                        val currentPortText = binding.port.editText?.text?.toString()
                        if (currentPortText.isNullOrEmpty()) {
                            binding.port.editText?.setText(port.toString())
                        }
                    } else {
                        binding.pairingStatus.text = getString(R.string.dialog_adb_pairing_discovery)
                    }
                }
            }.apply { start() }
        }
    }

    private fun stopMdns() {
        adbMdns?.stop()
        adbMdns = null
    }

    private fun onPairClicked() {
        val port = try {
            binding.port.editText?.text?.toString()?.toInt() ?: -1
        } catch (e: Exception) {
            -1
        }
        if (port !in 1..65535) {
            binding.port.error = getString(R.string.dialog_adb_invalid_port)
            return
        }
        binding.port.error = null

        val code = binding.pairingCode.editText?.text?.toString()?.trim() ?: ""
        if (code.isEmpty()) {
            binding.pairingCode.error = getString(R.string.dialog_adb_pairing_paring_code)
            return
        }
        binding.pairingCode.error = null

        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

        binding.pairingProgress.isVisible = true
        binding.buttonPair.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val host = "127.0.0.1"
            val key = try {
                AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.pairingProgress.isVisible = false
                    binding.buttonPair.isEnabled = true
                    Toast.makeText(
                        this@AdbPairingTutorialActivity,
                        getString(R.string.adb_error_key_store),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            val result = runCatching {
                AdbPairingClient(host, port, code, key).use { client ->
                    client.start()
                }
            }

            withContext(Dispatchers.Main) {
                binding.pairingProgress.isVisible = false
                binding.buttonPair.isEnabled = true

                result.onSuccess { success ->
                    if (success) {
                        binding.pairingInputContainer.isGone = true
                        binding.pairingSuccess.isVisible = true
                        binding.pairingStatus.text = getString(R.string.notification_adb_pairing_succeed_title)
                    } else {
                        binding.pairingCode.error = getString(R.string.paring_code_is_wrong)
                    }
                }.onFailure { ex ->
                    when (ex) {
                        is ConnectException -> {
                            binding.port.error = getString(R.string.cannot_connect_port)
                        }
                        is AdbInvalidPairingCodeException -> {
                            binding.pairingCode.error = getString(R.string.paring_code_is_wrong)
                        }
                        is AdbKeyException -> {
                            Toast.makeText(
                                this@AdbPairingTutorialActivity,
                                getString(R.string.adb_error_key_store),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            binding.pairingCode.error = ex.localizedMessage ?: getString(R.string.paring_code_is_wrong)
                        }
                    }
                }
            }
        }
    }
}
