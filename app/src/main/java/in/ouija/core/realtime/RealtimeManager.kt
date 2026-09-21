package ouija.app.core.realtime

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ouija.app.core.commands.Command
import ouija.app.core.commands.CommandPacket
import ouija.app.core.telemetry.Telemetry

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class RealtimeManager(
    private val supabaseClient: SupabaseClient
) {
    companion object {
        private const val TAG = "OuijaRealtime"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var channel: RealtimeChannel? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingCommand = MutableSharedFlow<Command>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingCommand: SharedFlow<Command> = _incomingCommand.asSharedFlow()

    private val _incomingTelemetry = MutableStateFlow<Telemetry?>(null)
    val incomingTelemetry: StateFlow<Telemetry?> = _incomingTelemetry.asStateFlow()

    private val _isPeerConnected = MutableStateFlow(false)
    val isPeerConnected: StateFlow<Boolean> = _isPeerConnected.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private var currentChannelName: String? = null

    fun joinRoom(roomCode: String, isController: Boolean) {
        val targetChannel = "room:${roomCode.uppercase()}"
        if (_connectionState.value == ConnectionState.CONNECTED && currentChannelName == targetChannel) {
            Log.d(TAG, "Already connected to $targetChannel")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "Joining channel: $targetChannel (isController = $isController)")
                channel?.unsubscribe()
                _connectionState.value = ConnectionState.CONNECTING
                supabaseClient.realtime.connect()

                currentChannelName = targetChannel
                val ch = supabaseClient.realtime.channel(targetChannel)
                channel = ch

                // Subscribe to commands
                val commandFlow = ch.broadcastFlow<CommandPacket>(event = "command")
                scope.launch {
                    commandFlow.collect { packet ->
                        if (!isController) {
                            val cmd = Command.fromPacket(packet)
                            Log.d(TAG, "Received command packet: $packet -> $cmd")
                            _incomingCommand.emit(cmd)
                        }
                    }
                }

                // Subscribe to telemetry
                val telemetryFlow = ch.broadcastFlow<Telemetry>(event = "telemetry")
                scope.launch {
                    telemetryFlow.collect { telemetry ->
                        if (isController) {
                            Log.d(TAG, "Received telemetry: $telemetry")
                            _incomingTelemetry.value = telemetry
                            _isPeerConnected.value = true
                        }
                    }
                }

                ch.subscribe()
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "Successfully subscribed to $targetChannel")
            } catch (e: Exception) {
                Log.e(TAG, "Error joining channel $targetChannel: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    fun sendCommand(command: Command) {
        scope.launch {
            channel?.let { ch ->
                try {
                    val packet = command.toPacket()
                    Log.d(TAG, "Sending command: $command (packet: $packet)")
                    ch.broadcast(event = "command", message = packet)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send command $command: ${e.message}", e)
                }
            } ?: Log.w(TAG, "Cannot send command $command: Channel is null")
        }
    }

    fun sendTelemetry(telemetry: Telemetry) {
        scope.launch {
            channel?.let { ch ->
                try {
                    Log.d(TAG, "Sending telemetry: $telemetry")
                    ch.broadcast(event = "telemetry", message = telemetry)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send telemetry: ${e.message}", e)
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                Log.d(TAG, "Disconnecting from channel $currentChannelName")
                channel?.unsubscribe()
                supabaseClient.realtime.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting: ${e.message}", e)
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
                _isPeerConnected.value = false
                currentChannelName = null
            }
        }
    }
}
