package ouija.app.core.realtime

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val scope = CoroutineScope(Dispatchers.IO)
    private var channel: RealtimeChannel? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingCommand = MutableStateFlow<Command?>(null)
    val incomingCommand: StateFlow<Command?> = _incomingCommand.asStateFlow()

    private val _incomingTelemetry = MutableStateFlow<Telemetry?>(null)
    val incomingTelemetry: StateFlow<Telemetry?> = _incomingTelemetry.asStateFlow()

    private val _isPeerConnected = MutableStateFlow(false)
    val isPeerConnected: StateFlow<Boolean> = _isPeerConnected.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun joinRoom(roomCode: String, isController: Boolean) {
        scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                supabaseClient.realtime.connect()

                val channelName = "room:${roomCode.uppercase()}"
                channel = supabaseClient.realtime.channel(channelName)

                // Subscribe to commands
                val commandFlow = channel!!.broadcastFlow<CommandPacket>(event = "command")
                scope.launch {
                    commandFlow.collect { packet ->
                        if (!isController) {
                            _incomingCommand.value = Command.fromPacket(packet)
                        }
                    }
                }

                // Subscribe to telemetry
                val telemetryFlow = channel!!.broadcastFlow<Telemetry>(event = "telemetry")
                scope.launch {
                    telemetryFlow.collect { telemetry ->
                        if (isController) {
                            _incomingTelemetry.value = telemetry
                            _isPeerConnected.value = true
                        }
                    }
                }

                channel!!.subscribe()
                _connectionState.value = ConnectionState.CONNECTED
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    fun sendCommand(command: Command) {
        scope.launch {
            channel?.let { ch ->
                try {
                    val packet = command.toPacket()
                    ch.broadcast(event = "command", message = packet)
                } catch (e: Exception) {
                    // Ignore broadcast errors
                }
            }
        }
    }

    fun sendTelemetry(telemetry: Telemetry) {
        scope.launch {
            channel?.let { ch ->
                try {
                    ch.broadcast(event = "telemetry", message = telemetry)
                } catch (e: Exception) {
                    // Ignore telemetry errors
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                channel?.unsubscribe()
                supabaseClient.realtime.disconnect()
            } catch (e: Exception) {
                // Ignore disconnect errors
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
                _isPeerConnected.value = false
            }
        }
    }
}
