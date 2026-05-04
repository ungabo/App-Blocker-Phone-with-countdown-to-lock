package com.gabe.focusblocker.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.gabe.focusblocker.FocusBlockerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DomainBlockVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        vpnInterface = Builder()
            .setSession("Focus Blocker Website Filter")
            .addAddress("10.111.0.2", 32)
            .addDnsServer(DNS_SERVER)
            .addRoute(DNS_SERVER, 32)
            .setBlocking(true)
            .establish()

        isRunning = vpnInterface != null
        val descriptor = vpnInterface ?: return
        serviceScope.launch {
            runDnsLoop(descriptor)
        }
    }

    private fun stopVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        serviceScope.cancel()
        stopSelf()
    }

    private fun runDnsLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(4096)

        while (isRunning) {
            val length = try {
                input.read(buffer)
            } catch (_: Exception) {
                break
            }
            if (length <= 0) continue

            val query = DnsPacketCodec.parseIpv4UdpDns(buffer, length) ?: continue
            val domain = query.queryDomain ?: continue
            val blocked = runBlocking {
                val app = application as FocusBlockerApplication
                app.container.blockingRepository.evaluateDomain(domain).blocked
            }

            val response = if (blocked) {
                DnsPacketCodec.blockedResponse(query)
            } else {
                forwardDns(query)?.let { DnsPacketCodec.allowedResponse(query, it) }
            }

            if (response != null) {
                output.write(response)
            }
        }
    }

    private fun forwardDns(query: DnsPacket): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 2_500
                val address = InetAddress.getByName(DNS_SERVER)
                socket.send(DatagramPacket(query.payload, query.payload.size, address, 53))

                val response = ByteArray(1500)
                val packet = DatagramPacket(response, response.size)
                socket.receive(packet)
                response.copyOf(packet.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val DNS_SERVER = "8.8.8.8"
        private const val ACTION_STOP = "com.gabe.focusblocker.vpn.STOP"
        @Volatile var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            context.startService(Intent(context, DomainBlockVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DomainBlockVpnService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
