package com.brian.dialbuddy.telecom

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallLine(
    val key: String,
    val number: String,
    val state: Int,
) {
    fun isIncoming(): Boolean =
        state == Call.STATE_RINGING || state == Call.STATE_SIMULATED_RINGING
}

data class InCallState(
    val calls: List<CallLine> = emptyList(),
    val muted: Boolean = false,
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val bluetoothAvailable: Boolean = false,
) {
    val speakerOn: Boolean get() = route == CallAudioState.ROUTE_SPEAKER
    val bluetoothOn: Boolean get() = route == CallAudioState.ROUTE_BLUETOOTH
}

object Calls {
    private val main = Handler(Looper.getMainLooper())
    private val current = LinkedHashMap<Call, Unit>()
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) promoteHeld()
            notifyChanged()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = notifyChanged()
    }

    private val _state = MutableStateFlow(InCallState())
    val state: StateFlow<InCallState> = _state.asStateFlow()

    var service: CallService? = null
    var mainVisible: Boolean = false
    private var audioRoute: Int = CallAudioState.ROUTE_EARPIECE
    private var supportedRoutes: Int = 0
    private var muted: Boolean = false

    fun add(call: Call) {
        if (current.containsKey(call)) return
        current[call] = Unit
        call.registerCallback(callback)
        notifyChanged()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        current.remove(call)
        promoteHeld()
        notifyChanged()
    }

    fun updateAudio(audio: CallAudioState) {
        audioRoute = audio.route
        supportedRoutes = audio.supportedRouteMask
        muted = audio.isMuted
        notifyChanged()
    }

    fun answer(key: String) {
        val target = find(key) ?: return
        current.keys.forEach { call ->
            if (call !== target && call.details.state == Call.STATE_ACTIVE) call.hold()
        }
        target.answer(0)
    }

    fun silenceRing(key: String, stopAudible: Boolean) {
        val number = currentNumber(key) ?: return
        val noneLeft = UnknownRinger.silenceNumber(number)
        if (!stopAudible || !noneLeft) return
        val telecom = service?.getSystemService(TelecomManager::class.java) ?: return
        runCatching { telecom.silenceRinger() }
    }

    fun decline(key: String) {
        find(key)?.reject(false, null)
    }

    fun hangup(key: String) {
        find(key)?.disconnect()
    }

    fun toggleHold(key: String) {
        val call = find(key) ?: return
        if (call.details.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun toggleMute() {
        service?.setMuted(!muted)
    }

    fun activateSpeaker() {
        val route = if (audioRoute == CallAudioState.ROUTE_SPEAKER) {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service?.setAudioRoute(route)
    }

    fun switchToBluetooth() {
        val route = if (audioRoute == CallAudioState.ROUTE_BLUETOOTH) {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        } else {
            CallAudioState.ROUTE_BLUETOOTH
        }
        service?.setAudioRoute(route)
    }

    fun startTone(key: String, symbol: String): Boolean {
        val digit = symbol.singleOrNull()?.takeIf { it in DTMF } ?: return false
        val call = find(key) ?: return false
        if (call.details.state != Call.STATE_ACTIVE) return false
        return runCatching { call.playDtmfTone(digit) }.isSuccess
    }

    fun stopTone(key: String) {
        val call = find(key) ?: return
        runCatching { call.stopDtmfTone() }
    }

    private fun notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            publish()
        } else {
            main.post { publish() }
        }
    }

    private fun publish() {
        val lines = current.keys.map { call ->
            val handle = call.details.handle
            CallLine(
                key = System.identityHashCode(call).toString(),
                number = handle?.schemeSpecificPart.orEmpty(),
                state = call.details.state,
            )
        }
        _state.value = InCallState(
            calls = lines,
            muted = muted,
            route = audioRoute,
            bluetoothAvailable = (supportedRoutes and CallAudioState.ROUTE_BLUETOOTH) != 0,
        )
        service?.let { UnknownRinger.sync(it, lines) }
    }

    private fun find(key: String): Call? =
        current.keys.firstOrNull { System.identityHashCode(it).toString() == key }

    private fun currentNumber(key: String): String? {
        find(key)?.details?.handle?.schemeSpecificPart?.let { return it }
        return _state.value.calls.firstOrNull { it.key == key }?.number
    }

    private fun promoteHeld() {
        val live = current.keys.filter { call ->
            val state = call.details.state
            state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING
        }
        val busy = live.any { call ->
            val state = call.details.state
            state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
        }
        if (busy) return
        val held = live.filter { it.details.state == Call.STATE_HOLDING }
        if (held.size == 1) held.first().unhold()
    }
}

private const val DTMF = "0123456789*#"
