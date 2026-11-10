package com.ushareit.core.p2p

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log

class WifiDirectTransferEngine(private val context: Context) {

    private val p2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null

    init {
        channel = p2pManager?.initialize(context, Looper.getMainLooper(), null)
    }

    fun discoverPeers(onPeersFound: (List<WifiP2pDevice>) -> Unit) {
        channel?.let { ch ->
            p2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i("WifiDirect", "P2P discovery started successfully")
                }

                override fun onFailure(reasonCode: Int) {
                    Log.w("WifiDirect", "Discovery failed code: $reasonCode")
                }
            })
        }
    }

    fun connectToDevice(device: WifiP2pDevice, onSuccess: () -> Unit, onError: (Int) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        channel?.let { ch ->
            p2pManager?.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i("WifiDirect", "P2P handshake successful with ${device.deviceName}")
                    onSuccess()
                }

                override fun onFailure(reason: Int) {
                    Log.e("WifiDirect", "Connection failed code: $reason")
                    onError(reason)
                }
            })
        }
    }
}

