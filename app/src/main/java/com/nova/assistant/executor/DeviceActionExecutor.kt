package com.nova.assistant.executor

import android.Manifest
import android.app.NotificationManager
import android.app.SearchManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.nova.assistant.model.NovaAction
import com.nova.assistant.service.NovaAccessibilityService
import com.nova.assistant.service.NovaDeviceAdminReceiver
import com.nova.assistant.service.NovaNotificationListener
import java.net.URLEncoder

class DeviceActionExecutor(private val context: Context) {

    fun executeActions(actions: List<NovaAction>): String {
        val results = mutableListOf<String>()
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                runCatching { Thread.sleep(1000L) }
            }
            val res = executeSingle(action)
            if (res.isNotBlank()) results.add(res)
        }
        return results.joinToString(" ")
    }

    private fun executeSingle(action: NovaAction): String {
        val p = action.params
        return when (action.type.uppercase()) {
            "TOGGLE_FLASHLIGHT" -> toggleFlashlight(p["state"] == "on")
            "OPEN_CAMERA" -> openCamera(p["mode"] == "selfie")
            "MAKE_CALL" -> makeCall(p["target"].orEmpty())
            "SEND_SMS" -> sendSms(p["target"].orEmpty(), p["message"].orEmpty())
            "READ_SMS" -> readRecentSms()
            "OPEN_APP" -> openAnyInstalledApp(p["app_name"].orEmpty())
            "PLAY_YOUTUBE" -> playYouTube(p["query"].orEmpty())
            "PLAY_SPOTIFY" -> playSpotify(p["query"].orEmpty())
            "SEARCH_WEB" -> searchWeb(p["query"].orEmpty())
            "OPEN_URL" -> openUrl(p["url"].orEmpty())
            "NAVIGATE_MAPS" -> navigateMaps(p["destination"].orEmpty())
            "SET_ALARM" -> setAlarm(
                p["hour"]?.toIntOrNull() ?: 7,
                p["minute"]?.toIntOrNull() ?: 0,
                p["label"] ?: "NOVA Alarm"
            )
            "SET_TIMER" -> setTimer(
                p["seconds"]?.toIntOrNull() ?: 60,
                p["label"] ?: "NOVA Timer"
            )
            "CREATE_CALENDAR_EVENT" -> createCalendarEvent(
                p["title"] ?: "NOVA Reminder",
                p["minutes_from_now"]?.toLongOrNull() ?: 60L
            )
            "SET_BRIGHTNESS" -> setBrightness(p["level"]?.toIntOrNull() ?: 70)
            "SET_VOLUME" -> setVolume(p["level"]?.toIntOrNull() ?: 60, p["stream"] ?: "media")
            "TOGGLE_DND" -> toggleDnd(p["state"] == "on")
            "TOGGLE_WIFI" -> toggleWifi(p["state"] == "on")
            "TOGGLE_BLUETOOTH" -> toggleBluetooth(p["state"] == "on")
            "COPY_TO_CLIPBOARD" -> copyToClipboard(p["text"].orEmpty())
            "OPEN_BLUETOOTH_SETTINGS" -> {
                startActivitySafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "Opened Bluetooth settings."
            }
            "OPEN_SETTINGS_PAGE" -> openSpecificSettings(p["page"].orEmpty())
            "LOCK_SCREEN" -> {
                if (NovaDeviceAdminReceiver.lockScreenNow(context)) "Screen locked."
                else "Device Admin permission required to lock screen."
            }
            "CLICK_INDEX" -> {
                val idx = p["index"]?.toIntOrNull() ?: 1
                val ok = NovaAccessibilityService.instance?.clickNodeByIndex(idx) == true
                if (ok) "Clicked element #$idx." else "Element #$idx not found on screen."
            }
            "WAIT_SECONDS" -> {
                val sec = (p["seconds"]?.toLongOrNull() ?: 2L).coerceIn(1L, 6L)
                runCatching { Thread.sleep(sec * 1000L) }
                "Waited ${sec}s."
            }
            "CLICK_TEXT" -> {
                val target = p["text"].orEmpty()
                val ok = NovaAccessibilityService.instance?.clickNodeByTextOrDesc(target) == true
                if (ok) "Clicked '$target'." else "Element '$target' not found on current screen."
            }
            "TYPE_TEXT" -> {
                val txt = p["text"].orEmpty()
                val submit = p["submit"]?.lowercase() != "false"
                val ok = NovaAccessibilityService.instance?.typeTextInFocusedOrAnyInput(txt, submit) == true
                if (ok) "Typed '$txt'." else "No active input field found on screen."
            }
            "TAP_COORDINATES" -> {
                val x = p["x"]?.toFloatOrNull() ?: 500f
                val y = p["y"]?.toFloatOrNull() ?: 800f
                NovaAccessibilityService.instance?.performTap(x, y)
                "Tapped ($x, $y)."
            }
            "LONG_PRESS_COORDINATES" -> {
                val x = p["x"]?.toFloatOrNull() ?: 500f
                val y = p["y"]?.toFloatOrNull() ?: 800f
                NovaAccessibilityService.instance?.performLongPress(x, y)
                "Long pressed ($x, $y)."
            }
            "ACCESSIBILITY_BACK" -> {
                NovaAccessibilityService.instance?.performBack()
                ""
            }
            "ACCESSIBILITY_HOME" -> {
                NovaAccessibilityService.instance?.performHome()
                ""
            }
            "ACCESSIBILITY_RECENTS" -> {
                NovaAccessibilityService.instance?.performRecents()
                ""
            }
            "OPEN_NOTIFICATIONS_PANEL" -> {
                NovaAccessibilityService.instance?.openNotificationsShade()
                ""
            }
            "OPEN_QUICK_SETTINGS" -> {
                NovaAccessibilityService.instance?.openQuickSettings()
                ""
            }
            "ACCESSIBILITY_SCROLL" -> {
                NovaAccessibilityService.instance?.performScroll(p["direction"] ?: "down")
                ""
            }
            "ACCESSIBILITY_SWIPE" -> {
                NovaAccessibilityService.instance?.performHorizontalSwipe(p["direction"] ?: "left")
                ""
            }
            "ACCESSIBILITY_READ_SCREEN" -> {
                NovaAccessibilityService.instance?.readScreenText()
                    ?: "Accessibility service is not enabled."
            }
            "SEND_WHATSAPP" -> sendWhatsApp(p["target"].orEmpty(), p["message"].orEmpty())
            "READ_NOTIFICATIONS" -> readNotifications()
            "MEDIA_CONTROL" -> controlMedia(p["command"] ?: "play_pause")
            "GET_BATTERY_STATUS" -> getBatteryStatus()
            else -> ""
        }
    }

    private fun openAnyInstalledApp(appName: String): String {
        if (appName.isBlank()) return "App name not specified."
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)

        val exact = apps.firstOrNull {
            it.loadLabel(pm).toString().equals(appName, ignoreCase = true)
        } ?: apps.firstOrNull {
            it.loadLabel(pm).toString().contains(appName, ignoreCase = true)
        } ?: apps.firstOrNull {
            it.activityInfo.packageName.contains(appName.replace(" ", ""), ignoreCase = true)
        }

        if (exact != null) {
            val launch = pm.getLaunchIntentForPackage(exact.activityInfo.packageName)
            if (launch != null) {
                startActivitySafe(launch)
                return "Opened ${exact.loadLabel(pm)}."
            }
        }
        return "App '$appName' is not installed on this device."
    }

    private fun playYouTube(query: String): String {
        if (query.isBlank()) return openAnyInstalledApp("YouTube")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val ytIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        ).apply {
            setPackage("com.google.android.youtube")
        }
        startActivitySafe(ytIntent)
        Handler(Looper.getMainLooper()).postDelayed({
            NovaAccessibilityService.instance?.tryPlayFirstYouTubeResult()
        }, 2800L)
        return "Playing $query on YouTube."
    }

    private fun playSpotify(query: String): String {
        if (query.isBlank()) return openAnyInstalledApp("Spotify")
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage("com.spotify.music")
            putExtra(SearchManager.QUERY, query)
        }
        startActivitySafe(intent)
        return "Playing $query on Spotify."
    }

    private fun openUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return "No URL specified."
        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
        startActivitySafe(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return "Opening $url."
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        startActivitySafe(intent)
        return "Alarm set for %02d:%02d.".format(hour, minute)
    }

    private fun setTimer(seconds: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        startActivitySafe(intent)
        return "Timer started for $seconds seconds."
    }

    private fun openSpecificSettings(page: String): String {
        val action = when (page.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        startActivitySafe(Intent(action))
        return "Opened $page settings."
    }

    private fun toggleFlashlight(turnOn: Boolean): String {
        return runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cm.cameraIdList.firstOrNull() ?: return "No camera flash found."
            cm.setTorchMode(camId, turnOn)
            "Flashlight turned ${if (turnOn) "on" else "off"}."
        }.getOrElse { "Unable to toggle flashlight." }
    }

    private fun openCamera(selfie: Boolean): String {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            if (selfie) putExtra("android.intent.extras.CAMERA_FACING", 1)
        }
        startActivitySafe(intent)
        return "Camera opened."
    }

    private fun makeCall(target: String): String {
        if (target.isBlank()) return "No contact or number specified."
        val number = resolvePhoneNumber(target) ?: target
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (hasPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        startActivitySafe(intent)
        return "Calling $target."
    }

    private fun sendSms(target: String, message: String): String {
        if (target.isBlank() || message.isBlank()) return "Missing SMS recipient or message."
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "SMS permission not granted."
        }
        val number = resolvePhoneNumber(target) ?: target
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(number, null, message, null, null)
        return "SMS sent to $target."
    }

    private fun readRecentSms(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Read SMS permission not granted."
        }
        val list = mutableListOf<String>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < 3) {
                val from = cursor.getString(0)
                val body = cursor.getString(1)
                list.add("From $from: $body")
                count++
            }
        }
        return if (list.isEmpty()) "No recent SMS messages." else list.joinToString(". ")
    }

    private fun searchWeb(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        startActivitySafe(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")))
        return "Searching web for $query."
    }

    private fun navigateMaps(destination: String): String {
        val encoded = URLEncoder.encode(destination, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).apply {
            setPackage("com.google.android.apps.maps")
        }
        startActivitySafe(intent)
        return "Starting navigation to $destination."
    }

    private fun createCalendarEvent(title: String, minutesFromNow: Long): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "Calendar permission not granted."
        }
        val startMillis = System.currentTimeMillis() + minutesFromNow * 60_000L
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 3_600_000L)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, 1L)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return "Calendar event '$title' scheduled."
    }

    private fun setBrightness(percent: Int): String {
        if (!Settings.System.canWrite(context)) return "Modify System Settings permission required."
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val value = (percent.coerceIn(0, 100) * 255) / 100
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return "Brightness set to $percent percent."
    }

    private fun setVolume(percent: Int, stream: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamType = if (stream == "ring") AudioManager.STREAM_RING else AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(streamType)
        val target = (percent.coerceIn(0, 100) * max) / 100
        am.setStreamVolume(streamType, target, 0)
        return "Volume set to $percent percent."
    }

    private fun toggleDnd(enable: Boolean): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return "Notification Policy access required for DND."
        nm.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return "Do Not Disturb ${if (enable) "enabled" else "disabled"}."
    }

    @Suppress("DEPRECATION")
    private fun toggleWifi(enable: Boolean): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startActivitySafe(Intent(Settings.Panel.ACTION_WIFI))
            return "Opened Wi-Fi quick panel."
        }
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.isWifiEnabled = enable
        return "Wi-Fi ${if (enable) "enabled" else "disabled"}."
    }

    private fun sendWhatsApp(target: String, message: String): String {
        val number = (resolvePhoneNumber(target) ?: target).replace("[^0-9+]".toRegex(), "")
        val encoded = URLEncoder.encode(message, "UTF-8")
        val url = "https://api.whatsapp.com/send?phone=$number&text=$encoded"
        startActivitySafe(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp"))
        Handler(Looper.getMainLooper()).postDelayed({
            NovaAccessibilityService.instance?.tryClickWhatsAppSend()
        }, 3200L)
        return "Sending WhatsApp message to $target."
    }

    private fun readNotifications(): String {
        val listener = NovaNotificationListener.instance
            ?: return "Notification Listener service is not enabled."
        val items = listener.getNotificationSummaries(5)
        return if (items.isEmpty()) "You have no active notifications."
        else items.joinToString(". ")
    }

    private fun controlMedia(command: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (command) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return "Media command executed."
    }

    private fun getBatteryStatus(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery is at $pct percent."
    }

    private fun resolvePhoneNumber(nameOrNumber: String): String? {
        if (nameOrNumber.any { it.isDigit() } && nameOrNumber.length >= 7) return nameOrNumber
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$nameOrNumber%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }


    @Suppress("DEPRECATION")
    private fun toggleBluetooth(enable: Boolean): String {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = bm?.adapter ?: return "Bluetooth hardware not available."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            startActivitySafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return "Opened Bluetooth settings (permission needed for direct toggle)."
        }
        return runCatching {
            if (enable) adapter.enable() else adapter.disable()
            "Bluetooth turned ${if (enable) "on" else "off"}."
        }.getOrElse {
            startActivitySafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "Opened Bluetooth settings."
        }
    }

    private fun copyToClipboard(text: String): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("NOVA", text))
        return "Copied '$text' to clipboard."
    }

    private fun startActivitySafe(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
