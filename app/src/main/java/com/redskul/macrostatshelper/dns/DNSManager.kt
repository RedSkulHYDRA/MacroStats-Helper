package com.redskul.macrostatshelper.dns

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit

/**
 * Manager class for DNS functionality.
 * Handles DNS switching and settings management using Global settings.
 */
class DNSManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("dns_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DNS_ENABLED = "dns_enabled"
        private const val KEY_CURRENT_DNS = "current_dns"
        private const val KEY_DNS_1_NAME = "dns_1_name"
        private const val KEY_DNS_1_URL = "dns_1_url"
        private const val KEY_DNS_2_NAME = "dns_2_name"
        private const val KEY_DNS_2_URL = "dns_2_url"
        private const val KEY_DNS_3_NAME = "dns_3_name"
        private const val KEY_DNS_3_URL = "dns_3_url"

        // DNS modes for Global settings
        private const val DNS_MODE_OFF = "off"
        private const val DNS_MODE_OPPORTUNISTIC = "opportunistic"
        private const val DNS_MODE_STRICT = "hostname"

        private const val TAG = "DNSManager"

        // Fixed DNS options
        const val DNS_OFF_NAME = "Off"
        const val DNS_OFF_VALUE = "off"
        const val DNS_AUTO_NAME = "Automatic"
        const val DNS_AUTO_VALUE = "auto"
    }

    /**
     * Data class for DNS configuration
     */
    data class DNSOption(
        val name: String,
        val url: String  // Keep as 'url' to match existing usage
    ) {
        fun isDefault() = url.isEmpty() || url == DNS_AUTO_VALUE
        fun isOff() = url == DNS_OFF_VALUE
        fun isAuto() = url == DNS_AUTO_VALUE
        fun isCustom() = !isOff() && !isAuto() && !isDefault()
        fun isValid() = name.isNotEmpty()
    }

    /**
     * Checks if DNS tile is enabled.
     */
    fun isDNSEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DNS_ENABLED, false)
    }

    /**
     * Sets DNS tile enabled state.
     */
    fun setDNSEnabled(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_DNS_ENABLED, enabled)
        }
        Log.d(TAG, "DNS tile enabled set to: $enabled")
    }

    /**
     * Checks if WRITE_SECURE_SETTINGS permission is granted.
     */
    fun hasSecureSettingsPermission(): Boolean {
        return try {
            // Test by trying to read and write global settings
            val currentMode = getCurrentDNSMode()
            Settings.Global.putString(context.contentResolver, "private_dns_mode", currentMode)
            true
        } catch (e: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS permission not granted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking secure settings permission", e)
            false
        }
    }

    /**
     * Gets the ADB command needed to grant WRITE_SECURE_SETTINGS permission.
     */
    fun getADBCommand(): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Gets all available DNS options including fixed ones.
     */
    fun getAllDNSOptions(): List<DNSOption> {
        val options = mutableListOf<DNSOption>()

        // Always add fixed options first
        options.add(DNSOption(DNS_OFF_NAME, DNS_OFF_VALUE))
        options.add(DNSOption(DNS_AUTO_NAME, DNS_AUTO_VALUE))

        // Add configured custom DNS options
        val dns1 = getDNSOption(1)
        val dns2 = getDNSOption(2)
        val dns3 = getDNSOption(3)

        if (dns1.isValid() && dns1.url.isNotEmpty()) options.add(dns1)
        if (dns2.isValid() && dns2.url.isNotEmpty()) options.add(dns2)
        if (dns3.isValid() && dns3.url.isNotEmpty()) options.add(dns3)

        return options
    }

    /**
     * Gets a specific DNS option by number (1, 2, or 3).
     */
    fun getDNSOption(number: Int): DNSOption {
        return when (number) {
            1 -> DNSOption(
                sharedPreferences.getString(KEY_DNS_1_NAME, "") ?: "",
                sharedPreferences.getString(KEY_DNS_1_URL, "") ?: ""
            )
            2 -> DNSOption(
                sharedPreferences.getString(KEY_DNS_2_NAME, "") ?: "",
                sharedPreferences.getString(KEY_DNS_2_URL, "") ?: ""
            )
            3 -> DNSOption(
                sharedPreferences.getString(KEY_DNS_3_NAME, "") ?: "",
                sharedPreferences.getString(KEY_DNS_3_URL, "") ?: ""
            )
            else -> DNSOption("", "")
        }
    }

    /**
     * Saves a DNS option.
     */
    fun saveDNSOption(number: Int, name: String, url: String) {
        sharedPreferences.edit {
            when (number) {
                1 -> {
                    putString(KEY_DNS_1_NAME, name)
                    putString(KEY_DNS_1_URL, url)
                }
                2 -> {
                    putString(KEY_DNS_2_NAME, name)
                    putString(KEY_DNS_2_URL, url)
                }
                3 -> {
                    putString(KEY_DNS_3_NAME, name)
                    putString(KEY_DNS_3_URL, url)
                }
            }
        }
        Log.d(TAG, "Saved DNS option $number: $name -> $url")
    }

    /**
     * Gets current DNS selection.
     */
    fun getCurrentDNS(): DNSOption {
        val currentUrl = sharedPreferences.getString(KEY_CURRENT_DNS, DNS_AUTO_VALUE) ?: DNS_AUTO_VALUE
        return getAllDNSOptions().find { it.url == currentUrl } ?: DNSOption(DNS_AUTO_NAME, DNS_AUTO_VALUE)
    }

    /**
     * Sets current DNS selection and applies it to system.
     */
    fun setCurrentDNS(dnsOption: DNSOption): Boolean {
        if (!hasSecureSettingsPermission()) {
            Log.w(TAG, "Cannot set DNS without WRITE_SECURE_SETTINGS permission")
            return false
        }

        return try {
            when {
                dnsOption.isOff() -> {
                    // Turn off private DNS
                    Settings.Global.putString(context.contentResolver, "private_dns_mode", DNS_MODE_OFF)
                    Settings.Global.putString(context.contentResolver, "private_dns_specifier", "")
                    Log.d(TAG, "DNS turned off")
                }
                dnsOption.isAuto() -> {
                    // Set to automatic/opportunistic
                    Settings.Global.putString(context.contentResolver, "private_dns_mode", DNS_MODE_OPPORTUNISTIC)
                    Settings.Global.putString(context.contentResolver, "private_dns_specifier", "")
                    Log.d(TAG, "DNS set to automatic")
                }
                dnsOption.isCustom() -> {
                    // Set to specific DNS hostname
                    Settings.Global.putString(context.contentResolver, "private_dns_mode", DNS_MODE_STRICT)
                    Settings.Global.putString(context.contentResolver, "private_dns_specifier", dnsOption.url)
                    Log.d(TAG, "DNS set to custom: ${dnsOption.url}")
                }
            }

            // Save current selection
            sharedPreferences.edit {
                putString(KEY_CURRENT_DNS, dnsOption.url)
            }

            Log.d(TAG, "DNS successfully set to: ${dnsOption.name} (${dnsOption.url})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting DNS", e)
            false
        }
    }

    /**
     * Gets current DNS mode from system settings.
     */
    private fun getCurrentDNSMode(): String {
        return try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: DNS_MODE_OPPORTUNISTIC
        } catch (e: Exception) {
            Log.e(TAG, "Error getting DNS mode", e)
            DNS_MODE_OPPORTUNISTIC
        }
    }

    /**
     * Gets current DNS specifier from system settings.
     */
    private fun getCurrentDNSSpecifier(): String {
        return try {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting DNS specifier", e)
            ""
        }
    }

    /**
     * Gets current DNS status text.
     */
    fun getCurrentDNSStatusText(): String {
        return try {
            val mode = getCurrentDNSMode()
            val specifier = getCurrentDNSSpecifier()

            when (mode) {
                DNS_MODE_OFF -> "DNS: Off"
                DNS_MODE_OPPORTUNISTIC -> "DNS: Automatic"
                DNS_MODE_STRICT -> "DNS: ${if (specifier.isNotEmpty()) specifier else "Custom"}"
                else -> "DNS: Unknown mode ($mode)"
            }
        } catch (e: Exception) {
            "DNS: Unable to read status"
        }
    }

    /**
     * Syncs current DNS selection with system settings.
     */
    fun syncWithSystemSettings() {
        try {
            val currentMode = getCurrentDNSMode()
            val currentSpecifier = getCurrentDNSSpecifier()

            val actualCurrent = when (currentMode) {
                DNS_MODE_OFF -> DNSOption(DNS_OFF_NAME, DNS_OFF_VALUE)
                DNS_MODE_OPPORTUNISTIC -> DNSOption(DNS_AUTO_NAME, DNS_AUTO_VALUE)
                DNS_MODE_STRICT -> {
                    if (currentSpecifier.isNotEmpty()) {
                        // Try to find matching custom DNS
                        getAllDNSOptions().find { it.isCustom() && it.url == currentSpecifier }
                            ?: DNSOption("Custom DNS", currentSpecifier)
                    } else {
                        DNSOption(DNS_AUTO_NAME, DNS_AUTO_VALUE)
                    }
                }
                else -> DNSOption(DNS_AUTO_NAME, DNS_AUTO_VALUE)
            }

            sharedPreferences.edit {
                putString(KEY_CURRENT_DNS, actualCurrent.url)
            }

            Log.d(TAG, "Synced with system settings: ${actualCurrent.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with system settings", e)
        }
    }
}
