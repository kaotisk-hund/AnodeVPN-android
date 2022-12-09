package co.anode.anodium.ui.vpn

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.VpnService
import android.os.*
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.anode.anodium.*
import co.anode.anodium.databinding.FragmentVpnMainBinding
import co.anode.anodium.AnodeVpnService
import co.anode.anodium.VpnListActivity
import co.anode.anodium.integration.presentation.VpnActivity
import co.anode.anodium.support.AnodeClient
import co.anode.anodium.support.AnodeUtil
import co.anode.anodium.support.CjdnsSocket
import co.anode.anodium.support.CubeWifi
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class VPNFragmentMain : Fragment() {
    private val LOGTAG = BuildConfig.APPLICATION_ID
    val buttonStateDisconnected = 0
    val buttonStateConnecting = 1
    val buttonStateConnected = 2

    private var publicIpThreadSleep: Long = 10
    private var uiInForeground = true
    private var previousPublicIPv4 = ""
    private val vpnConnectionWaitingInterval = 30000L
    val h = Handler()
    private var _binding: FragmentVpnMainBinding? = null
    private lateinit var mycontext: Context
    private lateinit var root: View
    private val VPN_CONNECTED = IntentFilter("co.anode.anodium.action.VPN.Connected")
    private val VPN_DISCONNECTED = IntentFilter("co.anode.anodium.action.VPN.Disconnected")
    private val VPN_CONNECTING = IntentFilter("co.anode.anodium.action.VPN.Connecting")
    private val defaultNode = "929cwrjn11muk4cs5pwkdc5f56hu475wrlhq90pb9g38pp447640.k"
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVpnMainBinding.inflate(inflater, container, false)
        root = binding.root
        mycontext = requireContext()
        AnodeClient.statustv = root.findViewById(R.id.textview_status)

        val buttonConnectVPNs = root.findViewById<ToggleButton>(R.id.buttonconnectvpns)

        val prefs = requireActivity().getSharedPreferences(BuildConfig.APPLICATION_ID, AppCompatActivity.MODE_PRIVATE)
        val minClickInterval: Long = 1000
        var mLastClickTime: Long = 0
        buttonConnectVPNs.setOnClickListener() {
            AnodeUtil.preventTwoClick(it)
            //avoid accidental double clicks
            if (SystemClock.elapsedRealtime() - mLastClickTime > minClickInterval) {
                mLastClickTime = SystemClock.elapsedRealtime()
                if (!buttonConnectVPNs.isChecked) {
                    disconnectVPN(false)
                } else {
                    AnodeUtil.addCjdnsPeers()
                    AnodeClient.AuthorizeVPN().execute(prefs.getString("LastServerPubkey", defaultNode))
                    bigbuttonState(buttonStateConnecting)
                }
            }
        }
        val buttonVPNList = root.findViewById<Button>(R.id.buttonVPNList)
        buttonVPNList.setOnClickListener() {
            val vpnListActivity = Intent(mycontext, VpnListActivity::class.java)
            startActivityForResult(vpnListActivity, 0)
        }

        //Start background threads for checking public IP, new version, uploading errors etc
        startBackgroundThreads()

        //Starting VPN Service
        startVPNService()
        //Initialize VPN connecting waiting dialog
        VpnConnectionWaitingDialog.init(h, mycontext)

        //Pkt.cube wifi connection
        val buttonCube = root.findViewById<Button>(R.id.buttonConnectCube)
        if (CubeWifi.isConnected()) {
            buttonCube.text = getString(R.string.button_disconnect_pktcube)
        }
        CubeWifi.statusbar = root.findViewById(R.id.textview_status)
        CubeWifi.context = mycontext
        buttonCube.setOnClickListener {
            if (buttonCube.text.equals(getString(R.string.button_connect_pktcube))) {
                //AnodeClient.cjdnsConnectVPN("")
                CjdnsSocket.Core_stopTun()
                mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.DISCONNECT"))
                mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.START"))
                CubeWifi.connect()
                buttonCube.text = getString(R.string.button_disconnect_pktcube)
            } else {
                buttonCube.text = getString(R.string.button_connect_pktcube)
                CubeWifi.disconnect()
                disconnectVPN(showRatingBar = false)
            }
        }
        return root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == AppCompatActivity.RESULT_OK) {
            Log.i(LOGTAG, "onActivityResult")
            if(data != null ) {
                //Connecting to VPN Server
                if (data.getStringExtra("action") == "connect") {
                    Log.i(LOGTAG, "Connecting to " + data.getStringExtra("publickey"))
                    AnodeClient.AuthorizeVPN().execute(data.getStringExtra("publickey"))
                    bigbuttonState(buttonStateConnecting)
                }
            } else {
                //Initialize CJDNS socket
                CjdnsSocket.init(AnodeUtil.filesDirectory + "/" + AnodeUtil.CJDROUTE_SOCK)
            }
        }
    }

    private fun startBackgroundThreads() {
        //Check internet connectivity & public IP
        Thread({
            while (this.isVisible) {
                if (!AnodeUtil.internetConnection() && uiInForeground) {
                    activity?.runOnUiThread {
                        Toast.makeText(mycontext, getString(R.string.toast_no_internet), Toast.LENGTH_LONG).show()
                    }
                }
                Thread.sleep(3000)
            }
        }, "VPNFragmentMain.CheckInternetConnectivity").start()

        //Get v4 public IP
        Thread({
            while (true) {
                if (AnodeUtil.internetConnection() && uiInForeground) {
                    val textPublicIP = activity?.findViewById<TextView>(R.id.v4publicip)
                    val publicIP = getPublicIPv4()
                    if (!publicIP.contains("Error")) {
                        publicIpThreadSleep = 10000
                    }
                    activity?.runOnUiThread {
                        textPublicIP?.text = Html.fromHtml("<b>" + this.resources.getString(R.string.text_publicipv4) + "</b>&nbsp;" + publicIP)
                        if ((AnodeClient.vpnConnected) &&
                            (previousPublicIPv4 != publicIP) &&
                            ((publicIP != "None") || (!publicIP.contains("Error")))) {
                            bigbuttonState(buttonStateConnected)
                        }
                    }
                    previousPublicIPv4 = publicIP
                }
                Thread.sleep(publicIpThreadSleep)
            }
        }, "VPNFragmentMain.GetPublicIPv4").start()

        //Get v6 public IP
        Thread({
            while (true) {
                if (AnodeUtil.internetConnection() && uiInForeground) {
                    val textPublicIP = activity?.findViewById<TextView>(R.id.v6publicip)
                    val publicIP = getPublicIPv6()
                    if (!publicIP.contains("Error")) {
                        publicIpThreadSleep = 10000
                    }
                    activity?.runOnUiThread {
                        textPublicIP?.text = Html.fromHtml("<b>" + this.resources.getString(R.string.text_publicipv6) + "</b>&nbsp;" + publicIP)
                        if (AnodeClient.vpnConnected) {
                            bigbuttonState(buttonStateConnected)
                        }
                    }
                }
                Thread.sleep(publicIpThreadSleep)
            }
        }, "VPNFragmentMain.GetPublicIPv4").start()
    }

    private fun startVPNService() {
        val intent = VpnService.prepare(mycontext)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            onActivityResult(0, AppCompatActivity.RESULT_OK, null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun disconnectVPN(showRatingBar: Boolean) {
        AnodeClient.AuthorizeVPN().cancel(true)
        AnodeClient.stopThreads()
        CjdnsSocket.IpTunnel_removeAllConnections()
        CjdnsSocket.Core_stopTun()
        CjdnsSocket.clearRoutes()
        mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.STOP"))
        bigbuttonState(buttonStateDisconnected)
        //Rating bar
        /*Removed rating*/
        /*if (showRatingBar) {
            val ratingFragment: BottomSheetDialogFragment = RatingFragment()
            activity?.let { ratingFragment.show(it.supportFragmentManager, "") }
        }*/
    }

    private fun getPublicIPv4(): String {
        val getURL = "http://v4.vpn.anode.co/api/0.3/vpn/clients/ipaddress/"
        val url = URL(getURL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.requestMethod = "GET"
        try {
            conn.connect()
        } catch (e: java.lang.Exception) {
            return "None"
        }
        return try {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            json.getString("ipAddress")
        } catch (e: Exception) {
            "None"
        }
    }

    private fun getPublicIPv6(): String {
        val getURL = "http://v6.vpn.anode.co/api/0.3/vpn/clients/ipaddress/"
        val url = URL(getURL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.requestMethod = "GET"
        try {
            conn.connect()
        } catch (e: java.lang.Exception) {
            return "None"
        }
        return try {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            json.getString("ipAddress")
        } catch (e: Exception) {
            "None"
        }
    }

    fun bigbuttonState(state: Int) {
        val status = root.findViewById<TextView>(R.id.textview_status)
        val buttonconnectvpns = root.findViewById<ToggleButton>(R.id.buttonconnectvpns)
        when(state) {
            buttonStateDisconnected -> {
                buttonconnectvpns.isChecked = false
                AnodeClient.eventLog("Main button status DISCONNECTING")
                h.removeCallbacks(VpnConnectionWaitingDialog)
                //Status bar
                status.text = ""
                //Button
                buttonconnectvpns.alpha = 1.0f
            }
            buttonStateConnecting -> {
                AnodeClient.eventLog("Main button status for CONNECTING")
                //Start 30sec timer
                h.postDelayed(VpnConnectionWaitingDialog, vpnConnectionWaitingInterval)
                status.text = resources.getString(R.string.status_connecting)
                buttonconnectvpns.text = getString(R.string.button_cancel)
                buttonconnectvpns.alpha = 0.5f
            }
            buttonStateConnected -> {
                AnodeClient.eventLog("Main button status for CONNECTED")
                h.removeCallbacks(VpnConnectionWaitingDialog)
                status.text = ""
                buttonconnectvpns.alpha = 1.0f
                buttonconnectvpns.isChecked = true
                status.text = resources.getString(R.string.status_connected)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(vpnStatusReceiver, IntentFilter(VPN_CONNECTED))
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(vpnStatusReceiver, IntentFilter(VPN_CONNECTING))
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(vpnStatusReceiver, IntentFilter(VPN_DISCONNECTED))

        internetSharingStatus()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(vpnStatusReceiver)
    }

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                VPN_CONNECTED.getAction(0) -> bigbuttonState(buttonStateConnected)
                VPN_DISCONNECTED.getAction(0) -> bigbuttonState(buttonStateDisconnected)
                VPN_CONNECTING.getAction(0) -> bigbuttonState(buttonStateConnecting)
            }
        }
    }

    private fun internetSharingStatus() {
        val cubeButton = root.findViewById<Button>(R.id.buttonConnectCube)
        if (AnodeUtil.isInternetSharingAvailable) {
            cubeButton.visibility = View.VISIBLE
        } else {
            cubeButton.visibility = View.GONE
        }
    }

    @SuppressLint("StaticFieldLeak")
    object VpnConnectionWaitingDialog: Runnable {
        private lateinit var h: Handler
        private var c: Context? = null

        fun init(handler: Handler, context: Context)  {
            h = handler
            c = context
        }

        override fun run() {
            h.removeCallbacks(VpnConnectionWaitingDialog)
            if (!AnodeClient.vpnConnected) {
                if (c !=null) {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(c!!)
                    builder.setTitle(c?.getString(R.string.toast_no_internet))
                    builder.setMessage(c?.getString(R.string.msg_vpn_timeout))
                    builder.setPositiveButton(c?.getString(R.string.button_keep_waiting)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    builder.setNegativeButton(c?.getString(R.string.button_vpn_disconnect)) { dialog, _ ->
                        AnodeClient.AuthorizeVPN().cancel(true)
                        AnodeClient.stopThreads()
                        CjdnsSocket.IpTunnel_removeAllConnections()
                        CjdnsSocket.Core_stopTun()
                        CjdnsSocket.clearRoutes()
                        c!!.startService(Intent(c!!, AnodeVpnService::class.java).setAction("co.anode.anodium.STOP"))
                        dialog.dismiss()
                    }
                    val alert: AlertDialog = builder.create()
                    alert.show()
                }
            }
        }
    }
}