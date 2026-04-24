package com.example.lancamapp

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

// Define the data model here
data class DiscoveredDevice(val ip: String, val label: String)

class CameraDiscovery(private val context: Context) {

    // Added 'onDeviceFound' callback
    suspend fun findCameras(onDeviceFound: (DiscoveredDevice) -> Unit) = coroutineScope {
        Log.d("CameraDiscovery", "--- Starting Scan ---")

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo
        val myIpString = Formatter.formatIpAddress(connectionInfo.ipAddress)

        if (myIpString == "0.0.0.0") return@coroutineScope

        // Use a synchronized set to avoid duplicates in the UI
        val foundIps = java.util.Collections.synchronizedSet(HashSet<String>())

        // 1. UDP Scan
        launch(Dispatchers.IO) {
            runUdpDiscovery(wifiManager, myIpString) { ip ->
                if (foundIps.add(ip)) { // Only add if new
                    onDeviceFound(DiscoveredDevice(ip, "ONVIF Camera"))
                }
            }
        }

        // 2. TCP Scan
        launch(Dispatchers.IO) {
            runTcpSubnetScan(myIpString) { ip ->
                if (foundIps.add(ip)) { // Only add if new
                    onDeviceFound(DiscoveredDevice(ip, "Generic RTSP Device"))
                }
            }
        }
    }

    private suspend fun runUdpDiscovery(
        wifiManager: WifiManager,
        myIpString: String,
        onFound: (String) -> Unit
    ) {
        val lock = wifiManager.createMulticastLock("OnvifDiscovery")
        lock.setReferenceCounted(true)
        lock.acquire()
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket(null)
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(myIpString), 0))
            socket.broadcast = true
            socket.soTimeout = 2000 // Fast timeout for responsiveness

            val payload = OnvifConstants.DISCOVERY_PROBE.toByteArray()
            val targets = listOf("239.255.255.250", "255.255.255.255")

            targets.forEach {
                try { socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(it), 3702)) } catch (e: Exception) {}
            }

            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)
            val start = System.currentTimeMillis()

            while (System.currentTimeMillis() - start < 4000) {
                try {
                    socket.receive(packet)
                    val ip = packet.address.hostAddress
                    if (ip != myIpString) onFound(ip!!)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("Discovery", "UDP Error: $e")
        } finally {
            lock.release()
            socket?.close()
        }
    }

    private suspend fun runTcpSubnetScan(myIpString: String, onFound: (String) -> Unit) = coroutineScope {
        val prefix = myIpString.substringBeforeLast(".") + "."
        val jobs = (1..254).map { i ->
            async(Dispatchers.IO) {
                val targetIp = "$prefix$i"
                if (targetIp == myIpString) return@async

                // Only check Port 554 (RTSP) for speed
                if (isPortOpen(targetIp, 554)) {
                    onFound(targetIp)
                }
            }
        }
        jobs.awaitAll()
    }

    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 200)
            socket.close()
            true
        } catch (e: Exception) { false }
    }
}