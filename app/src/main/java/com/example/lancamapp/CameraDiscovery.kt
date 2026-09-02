package com.example.lancamapp

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

// Data model for discovered devices
data class DiscoveredDevice(val ip: String, val label: String)

class CameraDiscovery(private val context: Context) {

    // Common ports used by IP cameras & DVRs
    companion object {
        // Ports to probe:
        // 554: Standard RTSP
        // 37777: CP Plus / Dahua Private Media Port (used on CP Plus Ezycam & DVRs)
        // 8899: ONVIF Device Service (used on CP Plus Ezycam, Xiongmai, Tuya)
        // 8000: Hikvision / CP Plus SDK Port
        // 8554: Alternative RTSP
        // 80: HTTP ONVIF / Web Server
        // 8080: Alternative HTTP ONVIF
        val CAMERA_PORTS = listOf(554, 37777, 8899, 8000, 8554, 80, 8080)
    }

    suspend fun findCameras(
        deepScan: Boolean = false,
        onDeviceFound: (DiscoveredDevice) -> Unit
    ) = coroutineScope {
        Log.d("CameraDiscovery", "--- Starting Camera Discovery (deepScan=$deepScan) ---")

        val localIps = getLocalIpAddresses()
        val broadcastAddresses = getBroadcastAddresses()
        val foundIps = Collections.synchronizedSet(HashSet<String>())

        val wifiManager = try {
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (e: Exception) {
            null
        }

        // 1. First, check the ARP cache for devices that have communicated on LAN
        val arpIps = getArpTableIps()
        Log.d("CameraDiscovery", "ARP cache IPs found: $arpIps")
        arpIps.forEach { arpIp ->
            launch(Dispatchers.IO) {
                probeSingleIp(arpIp) { detectedDevice ->
                    if (foundIps.add(detectedDevice.ip)) {
                        onDeviceFound(detectedDevice)
                    }
                }
            }
        }

        // 2. UDP ONVIF WS-Discovery with Multi-Target Broadcast (penetrates Wi-Fi repeaters & extenders)
        localIps.forEach { myIpString ->
            launch(Dispatchers.IO) {
                runUdpDiscovery(wifiManager, myIpString, broadcastAddresses) { ip, detectedLabel ->
                    if (foundIps.add(ip)) {
                        onDeviceFound(DiscoveredDevice(ip, detectedLabel))
                    }
                }
            }
        }

        // 3. Multi-Port TCP Subnet Sweep
        // Collect subnets to scan
        val subnetsToScan = mutableSetOf<String>()
        localIps.forEach { ip ->
            val prefix = ip.substringBeforeLast(".") + "."
            subnetsToScan.add(prefix)
        }

        if (deepScan) {
            // Include common home router & extender subnets if not already present
            listOf("192.168.1.", "192.168.0.", "192.168.2.", "192.168.29.", "192.168.18.").forEach { commonPrefix ->
                subnetsToScan.add(commonPrefix)
            }
        }

        subnetsToScan.forEach { subnetPrefix ->
            launch(Dispatchers.IO) {
                runTcpSubnetScan(subnetPrefix, localIps) { detectedDevice ->
                    if (foundIps.add(detectedDevice.ip)) {
                        onDeviceFound(detectedDevice)
                    }
                }
            }
        }
    }

    private fun getLocalIpAddresses(): List<String> {
        val ipList = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress
                        if (!hostAddress.isNullOrEmpty() && hostAddress != "0.0.0.0") {
                            ipList.add(hostAddress)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraDiscovery", "Error reading network interfaces: $e")
        }

        // Fallback to WifiManager
        if (ipList.isEmpty()) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                val formatted = Formatter.formatIpAddress(ipInt)
                if (formatted != "0.0.0.0") {
                    ipList.add(formatted)
                }
            } catch (e: Exception) {
                Log.e("CameraDiscovery", "WifiManager fallback error: $e")
            }
        }

        return ipList.distinct()
    }

    private fun getBroadcastAddresses(): List<String> {
        val broadcasts = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && broadcast.hostAddress != null) {
                        broadcasts.add(broadcast.hostAddress)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraDiscovery", "Error reading broadcast addresses: $e")
        }
        return broadcasts.distinct()
    }

    private fun getArpTableIps(): List<String> {
        val ips = mutableListOf<String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[0] != "IP" && parts[0].contains(".")) {
                        val ip = parts[0]
                        val flags = parts[2]
                        if (flags != "0x0" && ip != "0.0.0.0") {
                            ips.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // /proc/net/arp may be restricted on some Android 10+ devices, safe to ignore
        }
        return ips
    }

    private suspend fun runUdpDiscovery(
        wifiManager: WifiManager?,
        myIpString: String,
        broadcastAddresses: List<String>,
        onFound: (String, String) -> Unit
    ) {
        var lock: WifiManager.MulticastLock? = null
        try {
            lock = wifiManager?.createMulticastLock("OnvifDiscovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w("CameraDiscovery", "Could not acquire MulticastLock: $e")
        }

        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket(null)
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(myIpString), 0))
            socket.broadcast = true
            socket.soTimeout = 2500

            val payload = OnvifConstants.DISCOVERY_PROBE.toByteArray()

            // Target multicast, global broadcast, and all subnet directed broadcasts (for repeaters)
            val targets = mutableListOf("239.255.255.250", "255.255.255.255")
            targets.addAll(broadcastAddresses)

            targets.distinct().forEach { targetIp ->
                try {
                    socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(targetIp), 3702))
                } catch (e: Exception) {}
            }

            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)
            val start = System.currentTimeMillis()

            while (System.currentTimeMillis() - start < 4500) {
                try {
                    socket.receive(packet)
                    val ip = packet.address.hostAddress
                    if (ip != null && ip != myIpString) {
                        val responseStr = String(packet.data, 0, packet.length)
                        val label = parseOnvifResponseForBrand(responseStr)
                        onFound(ip, label)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("CameraDiscovery", "UDP Error on $myIpString: $e")
        } finally {
            try {
                if (lock?.isHeld == true) {
                    lock.release()
                }
            } catch (e: Exception) {}
            socket?.close()
        }
    }

    private fun parseOnvifResponseForBrand(xml: String): String {
        val lower = xml.lowercase()
        return when {
            lower.contains("cp plus") || lower.contains("cpplus") || lower.contains("cp-plus") || lower.contains("ezycam") -> "CP Plus Ezycam / Device"
            lower.contains("dahua") -> "Dahua ONVIF Camera"
            lower.contains("hikvision") || lower.contains("hik-vision") -> "Hikvision ONVIF Camera"
            lower.contains("prama") -> "Prama ONVIF Camera"
            lower.contains("tapo") || lower.contains("tp-link") || lower.contains("tplink") -> "Tapo / TP-Link Camera"
            lower.contains("tiandy") -> "Tiandy ONVIF Camera"
            lower.contains("uniview") || lower.contains("unv") -> "Uniview ONVIF Camera"
            lower.contains("axis") -> "Axis Network Camera"
            lower.contains("reolink") -> "Reolink IP Camera"
            lower.contains("v380") -> "V380 Wi-Fi Camera"
            else -> "ONVIF Camera"
        }
    }

    private suspend fun runTcpSubnetScan(
        subnetPrefix: String,
        localIps: List<String>,
        onFound: (DiscoveredDevice) -> Unit
    ) = coroutineScope {
        val jobs = (1..254).map { i ->
            async(Dispatchers.IO) {
                val targetIp = "$subnetPrefix$i"
                if (localIps.contains(targetIp)) return@async

                probeSingleIp(targetIp) { device ->
                    onFound(device)
                }
            }
        }
        jobs.awaitAll()
    }

    private fun probeSingleIp(ip: String, onFound: (DiscoveredDevice) -> Unit) {
        for (port in CAMERA_PORTS) {
            if (isPortOpen(ip, port, timeoutMs = 450)) {
                val label = when (port) {
                    37777 -> "CP Plus / Dahua Device"
                    8899 -> "CP Plus / ONVIF Device"
                    8000 -> "Hikvision / CP Plus Device"
                    554, 8554 -> "RTSP Camera"
                    80, 8080 -> "IP Camera (Web)"
                    else -> "Network Camera"
                }
                onFound(DiscoveredDevice(ip, label))
                return // Found on at least one camera port, no need to probe remaining ports for this IP
            }
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int = 450): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}