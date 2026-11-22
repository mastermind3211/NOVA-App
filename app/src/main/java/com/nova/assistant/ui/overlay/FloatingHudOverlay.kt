package com.nova.assistant.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nova.assistant.service.NovaRuntimeBus
import com.nova.assistant.ui.components.NovaHolographicOrb
import com.nova.assistant.ui.theme.NovaColors

class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class FloatingHudOverlay(
    private val context: Context,
    private val onTriggerListen: () -> Unit,
    private val onCloseOverlay: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var isExpanded by mutableStateOf(false)

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (!Settings.canDrawOverlays(context) || composeView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val orbState by NovaRuntimeBus.orbState.collectAsState()
                val audioLevel by NovaRuntimeBus.audioLevel.collectAsState()
                val lastReply by NovaRuntimeBus.lastReply.collectAsState()

                Row(
                    modifier = Modifier
                        .background(NovaColors.ObsidianBg.copy(alpha = 0.88f), RoundedCornerShape(32.dp))
                        .border(1.5.dp, NovaColors.NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(32.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovaHolographicOrb(
                        state = orbState,
                        audioLevel = audioLevel,
                        size = 54.dp,
                        onClick = { isExpanded = !isExpanded }
                    )
                    if (isExpanded) {
                        Spacer(Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.widthIn(max = 210.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "NOVA HUD • ${orbState.label}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = NovaColors.NeonCyan
                            )
                            Text(
                                text = lastReply.ifBlank { "Say 'Hello Nova'..." },
                                fontSize = 11.sp,
                                color = NovaColors.TextPrimary,
                                maxLines = 2
                            )
                        }
                        IconButton(onClick = onTriggerListen, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Mic, contentDescription = "Listen", tint = NovaColors.EmeraldActive)
                        }
                        IconButton(onClick = onCloseOverlay, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Hide HUD", tint = NovaColors.CrimsonError)
                        }
                    }
                }
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(this, params)
                        false
                    }
                    else -> false
                }
            }
        }

        composeView = view
        windowManager.addView(view, params)
        NovaRuntimeBus.setOverlayActive(true)
    }

    fun hide() {
        composeView?.let {
            runCatching { windowManager.removeView(it) }
        }
        composeView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        NovaRuntimeBus.setOverlayActive(false)
    }
}
