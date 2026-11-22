package com.nova.assistant.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.nova.assistant.service.NovaAccessibilityService
import com.nova.assistant.service.NovaDeviceAdminReceiver
import com.nova.assistant.service.NovaNotificationListener

enum class PermissionGrantType {
    RUNTIME,
    SPECIAL_SETTINGS,
    SERVICE_BINDING,
    DEVICE_ADMIN,
    ADB_PRIVILEGED,
    NORMAL_OR_MANIFEST
}

data class PermissionCategoryItem(
    val id: Int,
    val title: String,
    val description: String,
    val permissions: List<String>,
    val grantType: PermissionGrantType,
    val isGranted: Boolean,
    val adbCommand: String? = null
)

object NovaPermissionManager {

    fun getAllRuntimePermissionsForPrompt(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return list.distinct().toTypedArray()
    }

    fun getMissingRuntimePermissions(context: Context): Array<String> {
        return getAllRuntimePermissionsForPrompt().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    private fun areAllGranted(context: Context, perms: List<String>): Boolean {
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun evaluateAllCategories(context: Context): List<PermissionCategoryItem> {
        val pkg = context.packageName
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val mediaImagesVideo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val mediaAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val nearbyPerms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            areAllGranted(context, listOf(Manifest.permission.POST_NOTIFICATIONS))
        } else true

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            areAllGranted(context, listOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }

        val locationPerms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        val activityPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else emptyList()

        return listOf(
            PermissionCategoryItem(
                1, "Camera & Torch",
                "Take photos/selfies and toggle flashlight",
                listOf(Manifest.permission.CAMERA),
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.CAMERA))
            ),
            PermissionCategoryItem(
                2, "Microphone & Wake Word",
                "Continuous 'Hello Nova' wake-word & voice commands",
                listOf(Manifest.permission.RECORD_AUDIO),
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.RECORD_AUDIO))
            ),
            PermissionCategoryItem(
                3, "Contacts",
                "Search & create contacts by voice",
                listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            ),
            PermissionCategoryItem(
                4, "Call Logs",
                "Read missed & recent call history",
                listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.READ_CALL_LOG))
            ),
            PermissionCategoryItem(
                5, "SMS / Messages",
                "Read incoming SMS and send text messages",
                listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS))
            ),
            PermissionCategoryItem(
                6, "Calendar",
                "Read schedule and create events/reminders",
                listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            ),
            PermissionCategoryItem(
                7, "Location & Navigation",
                "GPS coordinates & Google Maps navigation",
                locationPerms,
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.ACCESS_FINE_LOCATION))
            ),
            PermissionCategoryItem(
                8, "Physical Activity",
                "Step count & motion recognition",
                activityPerms,
                PermissionGrantType.RUNTIME,
                activityPerms.isEmpty() || areAllGranted(context, activityPerms)
            ),
            PermissionCategoryItem(
                9, "Photos & Videos",
                "Access gallery photos & video media",
                mediaImagesVideo,
                PermissionGrantType.RUNTIME,
                areAllGranted(context, mediaImagesVideo)
            ),
            PermissionCategoryItem(
                10, "Music & Audio",
                "Read audio files & control media playback",
                mediaAudio,
                PermissionGrantType.RUNTIME,
                areAllGranted(context, mediaAudio)
            ),
            PermissionCategoryItem(
                11, "Files & All Storage",
                "Full file system management (MANAGE_EXTERNAL_STORAGE)",
                listOf("android.permission.MANAGE_EXTERNAL_STORAGE"),
                PermissionGrantType.SPECIAL_SETTINGS,
                storageGranted,
                "adb shell appops set $pkg MANAGE_EXTERNAL_STORAGE allow"
            ),
            PermissionCategoryItem(
                12, "Notifications & DND",
                "Post alerts, read notifications & toggle Do Not Disturb",
                listOf("POST_NOTIFICATIONS", "BIND_NOTIFICATION_LISTENER_SERVICE", "ACCESS_NOTIFICATION_POLICY"),
                PermissionGrantType.SERVICE_BINDING,
                postNotifGranted && isNotificationListenerEnabled(context) && nm.isNotificationPolicyAccessGranted
            ),
            PermissionCategoryItem(
                13, "Nearby Devices, BT & Wi-Fi",
                "Bluetooth connect/scan & Wi-Fi state controls",
                nearbyPerms + listOf(Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE),
                PermissionGrantType.RUNTIME,
                nearbyPerms.isEmpty() || areAllGranted(context, nearbyPerms)
            ),
            PermissionCategoryItem(
                14, "Phone & Calls",
                "Place calls, read SIM/network & answer calls",
                listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS),
                PermissionGrantType.RUNTIME,
                areAllGranted(context, listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            ),
            PermissionCategoryItem(
                15, "Display Over Other Apps",
                "Floating J.A.R.V.I.S. HUD Overlay",
                listOf(Manifest.permission.SYSTEM_ALERT_WINDOW),
                PermissionGrantType.SPECIAL_SETTINGS,
                Settings.canDrawOverlays(context),
                "adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow"
            ),
            PermissionCategoryItem(
                16, "Install Unknown Apps",
                "Trigger APK installation flows",
                listOf(Manifest.permission.REQUEST_INSTALL_PACKAGES),
                PermissionGrantType.SPECIAL_SETTINGS,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.packageManager.canRequestPackageInstalls() else true
            ),
            PermissionCategoryItem(
                17, "Modify System Settings",
                "Control brightness, screen timeout & auto-rotate",
                listOf(Manifest.permission.WRITE_SETTINGS),
                PermissionGrantType.SPECIAL_SETTINGS,
                Settings.System.canWrite(context),
                "adb shell appops set $pkg WRITE_SETTINGS allow"
            ),
            PermissionCategoryItem(
                18, "Biometrics",
                "Hardware fingerprint/face security gate",
                listOf(Manifest.permission.USE_BIOMETRIC),
                PermissionGrantType.NORMAL_OR_MANIFEST,
                areAllGranted(context, listOf(Manifest.permission.USE_BIOMETRIC))
            ),
            PermissionCategoryItem(
                19, "Accessibility Services",
                "Tap, scroll, back, home, read screen & WhatsApp automation",
                listOf("BIND_ACCESSIBILITY_SERVICE"),
                PermissionGrantType.SERVICE_BINDING,
                isAccessibilityEnabled(context)
            ),
            PermissionCategoryItem(
                20, "Device Admin",
                "Instant voice-triggered screen lock",
                listOf("BIND_DEVICE_ADMIN"),
                PermissionGrantType.DEVICE_ADMIN,
                isDeviceAdminActive(context)
            ),
            PermissionCategoryItem(
                21, "Privileged & Battery Exempt",
                "WRITE_SECURE_SETTINGS, Usage Stats & Unstoppable Background",
                listOf("WRITE_SECURE_SETTINGS", "PACKAGE_USAGE_STATS", "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"),
                PermissionGrantType.ADB_PRIVILEGED,
                hasWriteSecureSettings(context) && hasUsageStatsPermission(context) && isIgnoringBatteryOptimizations(context),
                "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
            )
        )
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, NovaAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val expected = ComponentName(context, NovaNotificationListener::class.java).flattenToString()
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(context, NovaDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(comp)
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openSettingsForCategory(context: Context, categoryId: Int) {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val intent = when (categoryId) {
            11 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
            }
            12 -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) {
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }
            }
            15 -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            16 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            17 -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            19 -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            20 -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(context, NovaDeviceAdminReceiver::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required for NOVA voice-triggered instant screen lock."
                )
            }
            21 -> {
                if (!isIgnoringBatteryOptimizations(context)) {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
                } else {
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                }
            }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun getFullAdbCommandBundle(packageName: String): List<Pair<String, String>> = listOf(
        "Grant Secure Settings" to "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
        "Grant Usage Stats" to "adb shell appops set $packageName GET_USAGE_STATS allow",
        "Grant Overlay (HUD)" to "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow",
        "Grant Write Settings" to "adb shell appops set $packageName WRITE_SETTINGS allow",
        "Grant All Files Access" to "adb shell appops set $packageName MANAGE_EXTERNAL_STORAGE allow",
        "Whitelist Battery Optimization" to "adb shell dumpsys deviceidle whitelist +$packageName",
        "Grant Accessibility (Optional)" to "adb shell settings put secure enabled_accessibility_services $packageName/$packageName.service.NovaAccessibilityService"
    )
}
