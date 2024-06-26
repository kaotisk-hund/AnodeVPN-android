package co.anode.anodium.support

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.anode.anodium.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.*
import java.net.*
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLProtocolException


object AnodeClient {
    private val LOGTAG = BuildConfig.APPLICATION_ID
    lateinit var mycontext: Context
    lateinit var statustv: TextView
    lateinit var mainActivity: AppCompatActivity
    var vpnConnected: Boolean = false
    private const val apiVersion = "0.3"
    private const val FILE_BASE_PATH = "file://"
    private const val PROVIDER_PATH = ".provider"
    private const val API_ERROR_URL = "https://vpn.anode.co/api/$apiVersion/vpn/clients/events/"
    private const val API_REGISTRATION_URL = "https://vpn.anode.co/api/$apiVersion/vpn/client/accounts/"
    private const val API_LOGOUT_URL = "https://vpn.anode.co/api/$apiVersion/vpn/accounts/authorize/"
    private const val API_PUBLICKEY_REGISTRATION = "https://vpn.anode.co/api/$apiVersion/vpn/clients/publickeys/"
    private const val API_DELETE_ACCOUNT = "https://vpn.anode.co/api/$apiVersion/vpn/accounts/<email_or_username>/delete/"
    private const val API_PEERING_LINES = "https://vpn.anode.co/api/$apiVersion/vpn/cjdns/peeringlines/"
    private const val API_AUTH_VPN = "https://vpn.anode.co/api/$apiVersion/vpn/servers/"
    private const val API_RATINGS_URL = "https://vpn.anode.co/api/$apiVersion/vpn/servers/ratings/"
    private const val API_GET_LATEST_RELEASE = "https://api.github.com/repos/anode-co/AnodeVPN-android/releases/latest"
    private const val API_GET_ALL_RELEASES = "https://api.github.com/repos/anode-co/AnodeVPN-android/releases"
    private var apiUsernameGenerate = "https://vpn.anode.co/api/$apiVersion/vpn/accounts/username/"
    private const val buttonStateConnected = 0
    private const val buttonStateConnecting = 1
    const val Auth_TIMEOUT = 1000*60*60 //1 hour in millis
    var downloadFails = 0
    var downloadingUpdate = false
    private const val PostMessageInterval = 60000
    var lastpostmessage: Long = 0
    val h = Handler()
    private val VPN_CONNECTED = Intent("co.anode.anodium.action.VPN.Connected")
    private val VPN_DISCONNECTED = Intent("co.anode.anodium.action.VPN.Disconnected")
    private val VPN_CONNECTING = Intent("co.anode.anodium.action.VPN.Connecting")
    private val defaultNode = "929cwrjn11muk4cs5pwkdc5f56hu475wrlhq90pb9g38pp447640.k"

    fun init(context: Context, activity: AppCompatActivity)  {
        mycontext = context
        mainActivity = activity
    }

    fun showToast(message: String) {
        if (message == "") return
        Log.i(LOGTAG, message)
        if (mainActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mainActivity.runOnUiThread {
                Toast.makeText(mycontext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun stackString(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    fun httpPostError(dir: File): String {
        //Do not post when in x86 or x86_64 (emulator) for development use
        val arch = System.getProperty("os.arch")
        if (arch != null) {
            if (arch.contains("x86") || arch.contains("i686")) { return ""}
        }
        //Do not post without consent
        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
        if (!prefs.getBoolean("DataConsent", false)) { return "No user consent to submit data" }
        try {
            if (!dir.exists()) { return "No log files to be submitted" }
            val files = dir.listFiles { file -> file.name.startsWith("error-uploadme-") }
            if ((files != null) && (files.isEmpty())) { return "No log files to be submitted" }
            val file = files.random()
            val resp = APIHttpReq(API_ERROR_URL,file.readText(), "POST", false, false)
            try {
                val json = JSONObject(resp)
                if (json.has("status") and (json.getString("status") == "success")) {
                    // ok, it looks like everything worked, we can delete the file now
                    Log.e(LOGTAG, "Log submitted successfully ${file.name}")
                    file.delete()
                    return "Log submitted successfully"
                }
                else if (json.has("status") and (json.getString("status") != "success")) {
                    showToast("Error invalid status posting ${file.name}: $resp")
                    return "Error invalid status posting ${file.name}: $resp"
                } else {
                    showToast("Error posting ${file.name} to server: $resp")
                    return "Error posting ${file.name} to server: $resp"
                }
            } catch (e:Exception) {
                showToast("Error posting ${file.name}: $resp")
                return "Error posting ${file.name}: $resp"
            }
        } catch (e: Exception) {
            showToast("Error reporting error: ${e.message}")
            return "Error reporting error: ${e.message}\n${stackString(e)}"
        }
    }

    fun httpPostMessage(type:String, message: String, force: String): String {
        //Do not post when on x86 or x86_64 (emulator) for development use only
        val arch = System.getProperty("os.arch")
        if (arch != null) {
            if (arch.contains("x86") || arch.contains("i686")) { return ""}
        }
        //Do not post without consent
        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
        val bforce = force.toBoolean()
        if (!bforce && !prefs.getBoolean("DataConsent", false)) { return "No user consent to submit data" }

        //Allow a single post message every minute
        if (!bforce && (((System.currentTimeMillis() - lastpostmessage) < PostMessageInterval) || (message!!.contains("io.grpc.StatusRuntimeException: UNAVAILABLE")))) {
            return ""
        }
        lastpostmessage = System.currentTimeMillis()
        Log.i(LOGTAG, "Posting error at $lastpostmessage")
        try {
            val msg = messageJsonObj(type, message).toString(1)
            val resp = APIHttpReq(API_ERROR_URL,msg, "POST", false, false)

            try {
                val json = JSONObject(resp)
                if (json.has("status") and (json.getString("status") == "success")) {
                    Log.e(LOGTAG, "Message submitted successfully")
                    return "Log submitted successfully"
                }
                else if (json.has("status") and (json.getString("status") != "success")) {
                    showToast("Error invalid status posting: $resp")
                    return "Error invalid status posting: $resp"
                } else {
                    showToast("Error posting to server: $resp")
                    return "Error posting to server: $resp"
                }
            } catch (e:Exception) {
                showToast("Error posting: $resp")
                return "Error posting: $resp"
            }
        } catch (e: Exception) {
            showToast("Error reporting error: ${e.message}")
            return "Error reporting error: ${e.message}\n${stackString(e)}"
        }
    }

    fun httpPostEvent(dir: File): String {
        //Do not post without consent
        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
        if (!prefs.getBoolean("DataConsent", false)) { return "No user consent to submit data" }
        try {
            if (!dir.exists()) { return "No event log files to be submitted" }
            val files = dir.listFiles { file -> file.name.startsWith("anodium-events") }
            if ((files != null) && (files.isEmpty())) { return "No event log files to be submitted" }
            val file = files.random()
            val eventLog = eventJsonObj().toString(1)
            val resp = APIHttpReq(API_ERROR_URL,eventLog, "POST", false, false)

            try {
                val json = JSONObject(resp)
                if (json.has("status") and (json.getString("status") == "success")) {
                    // ok, it looks like everything worked, we can delete the file now
                    Log.e(LOGTAG, "Log submitted successfully ${file.name}")
                    file.delete()
                    with (prefs.edit()) {
                        putLong("LastEventLogFileSubmitted", System.currentTimeMillis())
                        commit()
                    }
                    return "Log submitted successfully"
                }
                else if (json.has("status") and (json.getString("status") != "success")) {
                    Log.e(LOGTAG, "Error invalid status posting ${file.name}: $resp")
                    return "Error invalid status posting ${file.name}: $resp"
                } else {
                    return "Error posting ${file.name} to server: $resp"
                }
            } catch (e:Exception) {
                Log.e(LOGTAG, "Error posting ${file.name}: $resp")
                return "Error posting ${file.name}: $resp"
            }
        } catch (e: Exception) {
            Log.e(LOGTAG, "Error reporting events: ${e.message}\n${stackString(e)}")
            return "Error reporting events: ${e.message}\n${stackString(e)}"
        }
    }

    fun httpPostRating(): String {
        val ratings = File(AnodeUtil.filesDirectory +"/anodium-rating.json").readText()
        val resp = APIHttpReq(API_RATINGS_URL, ratings, "POST", true, false)
        try {
            val json = JSONObject(resp)
            if (json.has("status") and (json.getString("status") == "success")) {
                Log.e(LOGTAG, "Rating submitted successfully")
                //Delete file
                File(AnodeUtil.filesDirectory +"/anodium-rating.json").delete()
            } else {
                Log.e(LOGTAG, "Error posting rating to server: $resp")
                return "Error posting rating"
            }
        } catch (e:Exception) {
            Log.e(LOGTAG, "Error posting rating: $resp")
            return "Error posting rating"
        }
        return ""
    }

    fun checkNetworkConnection() =
        with(mycontext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager) {
            getNetworkCapabilities(activeNetwork)?.run {
                hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } ?: false
        }

    fun storeFileAsError(ctx: Context, type: String, filename:String) {
        val fname = "error-uploadme-" + Instant.now().toEpochMilli().toString() + ".json"
        var e = Throwable()
        //rename filename to anodium.log so it will be posted as current log file
        File(AnodeUtil.filesDirectory +"/anodium.log").renameTo(File(AnodeUtil.filesDirectory +"/tempanodium.log"))
        File(filename).renameTo(File(AnodeUtil.filesDirectory +"/anodium.log"))
        val err = errorJsonObj(type, e).toString(1)
        //rename it back
        //anodium.log back to filename
        File(AnodeUtil.filesDirectory +"/anodium.log").renameTo(File(filename))
        //tempanodium back to anodium.log
        File(AnodeUtil.filesDirectory +"/tempanodium.log").renameTo(File(AnodeUtil.filesDirectory +"/anodium.log"))
        File(ctx.filesDir,fname).appendText(err)
    }

    fun storeError(ctx: Context?, type: String, e: Throwable) {
        var c = ctx
        if (ctx == null) {
            c = mycontext
        }
        val fname = "error-uploadme-" + Instant.now().toEpochMilli().toString() + ".json"
        val err = errorJsonObj(type, e).toString(1)
        Log.e(LOGTAG, "Logged error [${e.message}] to file $fname")
        File(c?.filesDir,fname).appendText(err)
    }

    fun storeRating(pubkey: String, rating: Float, comment: String) {
        var jsonRatings: JSONArray = JSONArray()
        if (pubkey.isEmpty()) { return }
        if (File(AnodeUtil.filesDirectory +"/anodium-rating.json").exists())
        {
            jsonRatings = JSONArray(File(AnodeUtil.filesDirectory +"/anodium-rating.json").readText())
        }
        val jsonObject = JSONObject()
        jsonObject.accumulate("publicKey", pubkey)
        jsonObject.accumulate("rating", rating)
        jsonObject.accumulate("comment", comment)
        jsonObject.accumulate("created_at", System.currentTimeMillis())
        jsonRatings.put(jsonObject)
        val ratingfile = File(AnodeUtil.filesDirectory +"/anodium-rating.json")
        ratingfile.writeText(jsonRatings.toString())
    }

    fun errorCount(ctx: Context): Int {
        return ctx.filesDir.listFiles { file -> file.name.startsWith("error-uploadme-") }.size
    }

    fun ignoreErr(l: ()->Unit) = try { l() } catch (t: Throwable) { }

    @Throws(JSONException::class)
    private fun errorJsonObj(type: String, err: Throwable): JSONObject {
        val jsonObject = JSONObject()
        var pubkey = ""
        ignoreErr{ pubkey = AnodeUtil.getPubKey() }
        if (pubkey == "") pubkey = "unknown"
        jsonObject.accumulate("publicKey", pubkey)
        jsonObject.accumulate("error", type)
        jsonObject.accumulate("clientSoftwareVersion", BuildConfig.VERSION_NAME)
        jsonObject.accumulate("clientOs", "Android")
        jsonObject.accumulate("clientOsVersion", android.os.Build.VERSION.RELEASE)
        ignoreErr{ jsonObject.accumulate("localTimestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now())) }
        ignoreErr{ jsonObject.accumulate("ip4Address", CjdnsSocket.ipv4Address) }
        ignoreErr{ jsonObject.accumulate("ip6Address", CjdnsSocket.ipv6Route) }
        ignoreErr{ jsonObject.accumulate("cpuUtilizationPercent", "0") }
        ignoreErr{ jsonObject.accumulate("availableMemoryBytes", AnodeUtil.readMemUsage()) }
        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
        val username = prefs!!.getString("username","")
        ignoreErr{ jsonObject.accumulate("username", username) }

        if ((!err.message.isNullOrEmpty()) && (err.message?.length!! < 254)) {
            jsonObject.accumulate("message", err.message)
        } else if (!err.message.isNullOrEmpty()){
            jsonObject.accumulate("message", err.message?.substring(0, 254))
        } else {
            jsonObject.accumulate("message", "")
        }
        AnodeUtil.logFile()
        val cjdroutelogfile = File(AnodeUtil.filesDirectory +"/"+ AnodeUtil.CJDROUTE_LOG)
        val lastlogfile = File(AnodeUtil.filesDirectory +"/anodium.log")
        val currlogfile = File(AnodeUtil.filesDirectory +"/anodeTimber.txt")
        var debugmsg = "";
        ignoreErr {
            debugmsg += "Error stack: " + stackString(err) + "\n";
        }
        ignoreErr {
            debugmsg += "peerStats: " + CjdnsSocket.logpeerStats + "\nshowConnections: " + CjdnsSocket.logshowConnections
        }
        if ((!err.message.isNullOrEmpty()) && (err.message!! == "Submit logs"))
        {
            ignoreErr {
                if (currlogfile.exists()) jsonObject.accumulate("newAndroidLog", currlogfile.readText(Charsets.UTF_8))
            }
            ignoreErr {
                if (lastlogfile.exists()) jsonObject.accumulate("previousAndroidLog", lastlogfile.readText(Charsets.UTF_8))
            }
            ignoreErr {
                if (cjdroutelogfile.exists()) debugmsg += "\n\nCDJROUTE LOG:" + cjdroutelogfile.readText(Charsets.UTF_8)
            }
        } else {
            ignoreErr {
                if (currlogfile.exists()) jsonObject.accumulate("newAndroidLog", currlogfile.readText(Charsets.UTF_8))
            }
            ignoreErr {
                if (cjdroutelogfile.exists()) debugmsg += "\n\nCDJROUTE LOG:" + cjdroutelogfile.readText(Charsets.UTF_8)
            }
        }
        jsonObject.accumulate("debuggingMessages", debugmsg)
        return jsonObject
    }

    @Throws(JSONException::class)
    private fun messageJsonObj(type: String, message:String): JSONObject {
        val jsonObject = JSONObject()
        var pubkey = ""
        ignoreErr{ pubkey = AnodeUtil.getPubKey() }
        if (pubkey == "") pubkey = "unknown"
        jsonObject.accumulate("publicKey", pubkey)
        jsonObject.accumulate("error", type)
        jsonObject.accumulate("clientSoftwareVersion", BuildConfig.VERSION_NAME)
        jsonObject.accumulate("clientOs", "Android")
        jsonObject.accumulate("clientOsVersion", android.os.Build.VERSION.RELEASE)
        ignoreErr{ jsonObject.accumulate("localTimestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now())) }
        ignoreErr{ jsonObject.accumulate("ip4Address", CjdnsSocket.ipv4Address) }
        ignoreErr{ jsonObject.accumulate("ip6Address", CjdnsSocket.ipv6Route) }
        ignoreErr{ jsonObject.accumulate("cpuUtilizationPercent", "0") }
        ignoreErr{ jsonObject.accumulate("availableMemoryBytes", AnodeUtil.readMemUsage()) }
        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
        val username = prefs!!.getString("username","")
        ignoreErr{ jsonObject.accumulate("username", username) }
        jsonObject.accumulate("message", "postmessage")
        jsonObject.accumulate("debuggingMessages", message)
        return jsonObject
    }

    @Throws(JSONException::class)
    private fun eventJsonObj(): JSONObject {
        val jsonObject = JSONObject()
        var pubkey = ""
        ignoreErr { pubkey = AnodeUtil.getPubKey() }
        if (pubkey == "") pubkey = "unknown"
        jsonObject.accumulate("publicKey", pubkey)
        jsonObject.accumulate("error", "appUsage")
        jsonObject.accumulate("clientSoftwareVersion", BuildConfig.VERSION_CODE)
        jsonObject.accumulate("clientOs", "Android")
        jsonObject.accumulate("clientOsVersion", android.os.Build.VERSION.RELEASE)
        jsonObject.accumulate("localTimestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        ignoreErr { jsonObject.accumulate("ip4Address", CjdnsSocket.ipv4Address) }
        ignoreErr { jsonObject.accumulate("ip6Address", CjdnsSocket.ipv6Route) }
        ignoreErr { jsonObject.accumulate("cpuUtilizationPercent", "0") }
        ignoreErr { jsonObject.accumulate("availableMemoryBytes", AnodeUtil.readMemUsage()) }
        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
        val username = prefs!!.getString("username","")
        ignoreErr { jsonObject.accumulate("username", username) }
        jsonObject.accumulate("message", "Events log")

        val eventlogfile = File(AnodeUtil.filesDirectory +"/anodium-events.log")
        jsonObject.accumulate("debuggingMessages", eventlogfile.readText(Charsets.UTF_8))
        return jsonObject
    }


    fun deleteFiles(folder: String, ext: String) {
        val dir = File(folder)
        if (!dir.exists()) return
        val files: Array<File> = dir.listFiles()
        for (file in files) {
            if ((!file.isDirectory) and (file.endsWith(ext))) {
                file.delete()
            }
        }
    }

    fun getLatestRelease(userInitiated: Boolean) {
        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
        AnodeUtil.apiController.getArray(API_GET_ALL_RELEASES) {
            response ->
            if (response != null) {
                for (i in 0 until response.length()) {
                    val release = response.getJSONObject(i)
                    if ((release != null) && release.has("name") && !release.isNull("name")) {
                        //Check for release/pre-release
                        if (release.getBoolean("prerelease") && !prefs.getBoolean("preRelease",false))
                        {
                            continue
                        }
                        //Check against current version
                        val versionName = BuildConfig.VERSION_NAME
                        val curMajorNumber = versionName.split(".")[0].toInt()
                        val curMinorNumber = versionName.split(".")[1].toInt()
                        val curRevisionNumber = versionName.split(".")[2].toInt()
                        val gitVersion = release.getString("name").split("-").get(1)
                        val latestMajorNumber = gitVersion.split(".")[0].toInt()
                        val latestMinorNumber = gitVersion.split(".")[1].toInt()
                        val latestRevisionNumber = gitVersion.split(".")[2].toInt()
                        if ((latestMajorNumber > curMajorNumber) ||
                            (latestMinorNumber> curMinorNumber) ||
                            (latestRevisionNumber> curRevisionNumber)){
                            //Checking for update
                            //Get assets
                            val assets = release.getJSONArray("assets")
                            var url: String
                            var filesize: Long
                            //Get apk from assets
                            for (i in 0 until assets.length()) {
                                if (assets.getJSONObject(i).getString("name").endsWith(".apk")) {
                                    url = assets.getJSONObject(i).getString("browser_download_url")
                                    filesize = assets.getJSONObject(i).getLong("size")
                                    downloadFile(Uri.parse(url), gitVersion, filesize)
                                    return@getArray
                                }
                            }
                        } else {
                            if (userInitiated) {
                                showToast("Application already at latest version")
                            }
                            return@getArray
                        }
                    }
                }
            }
        }
    }

    fun checkNewVersion(notify: Boolean): Boolean {
        Log.i(LOGTAG, "Checking for latest APK")
        downloadingUpdate = true
        getLatestRelease(notify)
        return false
    }

    fun downloadFile(uri: Uri, version: String, filesize: Long): Long {
        Log.i(LOGTAG, "download file from $uri")
        val filename = "anodium-$version.apk"
        var downloadReference: Long = 0
        val downloadManager = mycontext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var destination = mycontext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.toString() + "/"
        destination += filename
        val destinationuri = Uri.parse("$FILE_BASE_PATH$destination")
        var flag = true
        try {
            val file = File(destination)
            if (!file.exists() or (file.length() < filesize)) {
                mainActivity.runOnUiThread {
                    Toast.makeText(mycontext, R.string.downloading_update, Toast.LENGTH_LONG).show()
                }
                val request = DownloadManager.Request(uri)
                //Setting title of request
                request.setTitle(filename)
                request.setMimeType("application/vnd.android.package-archive")
                //Setting description of request
                request.setDescription("Your file is downloading")
                //set notification when download completed
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                //Set the local destination for the downloaded file to a path within the application's external files directory
                //request.setDestinationInExternalPublicDir(destination, FILE_NAME) //WORKS
                request.setDestinationUri(destinationuri)
                //Enqueue download and save the referenceId
                downloadReference = downloadManager.enqueue(request)

                var query = DownloadManager.Query()

                query.setFilterByStatus(DownloadManager.STATUS_FAILED or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING)
                downloadingUpdate = true
                Thread(Runnable {
                    while (downloadingUpdate) {
                        val c = downloadManager.query(query)
                        if (c.moveToFirst()) {
                            var status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_FAILED) {
                                flag = false
                                downloadingUpdate = false
                                break
                            } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                downloadingUpdate = false
                                flag = true
                                break
                            }
                        }
                    }
                }, "AnodeClient.downloadfile").start()

                if (flag) {
                    downloadFails = 0
                    //Try to install apk when download completes
                    showInstallOptionOnDownload(destination)
                } else {
                    downloadFails++
                    showToast("ERROR downloading")
                }
            } else {
                //Apk already exists
                //Try to install existing APK
                val contentUri = FileProvider.getUriForFile(mycontext, mycontext.applicationContext.packageName + PROVIDER_PATH, File(destination))
                installAPK(mycontext, contentUri)
            }

        } catch (e: IllegalArgumentException) {
            showToast("ERROR downloading file ${e.message}")
        }
        return downloadReference
    }

    fun showInstallOptionOnDownload(destination: String) {
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(
                    context: Context,
                    intent: Intent
            ) {
                val contentUri = FileProvider.getUriForFile(
                        context,
                        context.applicationContext.packageName + PROVIDER_PATH,
                        File(destination)
                )
                installAPK(context, contentUri)
                context.unregisterReceiver(this)
            }
        }
        mycontext.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    fun installAPK(context: Context, contentUri: Uri) {
        Log.i(LOGTAG, "Installing new apk")
        val install = Intent(Intent.ACTION_VIEW)
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        install.data = contentUri
        context.startActivity(install)
    }

    fun generateKeys(): KeyPair? {
        Log.i(LOGTAG,"Generating key pair")
        try {
            // creating the object of KeyPairGenerator
            val kpg = KeyPairGenerator.getInstance("RSA")
            // initializing with 1024
            kpg.initialize(1024)
            // getting key pairs
            return kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            Log.e(LOGTAG,"ERROR - Failed to generate key pair: $e")
            return null
        }
    }

    class AuthorizeVPN() : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String?): String? {
            var pubkey = params[0]
            val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
            if (pubkey.isNullOrEmpty()) {
                pubkey = prefs.getString("LastServerPubkey", defaultNode)
            }
            with (prefs.edit()) {
                putString("LastServerPubkey", pubkey)
                commit()
            }
            val jsonObject = JSONObject()
            jsonObject.accumulate("date", System.currentTimeMillis())
            val resp = APIHttpReq( "$API_AUTH_VPN$pubkey/authorize/",jsonObject.toString(), "POST", true , false)
            if(AnodeClient::statustv.isInitialized) {
                statustv.post(Runnable {
                    statustv.text = "VPN connecting..."
                })
            }
            return resp
        }

        override fun onCancelled() {
            super.onCancelled()
            vpnConnected = false
            LocalBroadcastManager.getInstance(mycontext).sendBroadcast(VPN_DISCONNECTED)
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            Log.i(LOGTAG,"Received from $API_AUTH_VPN: $result")
            //if 200 or 201 then connect to VPN
            if (result.isNullOrBlank() || result.contains("ERROR: ")) {
                LocalBroadcastManager.getInstance(mycontext).sendBroadcast(VPN_DISCONNECTED)
                if(AnodeClient::statustv.isInitialized) {
                    statustv.post(Runnable {
                        statustv.text = mycontext.resources.getString(R.string.status_authorization_failed)
                    })
                }
                //Sign user out
                val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
                with (prefs.edit()) {
                    putBoolean("SignedIn", false)
                    commit()
                }
            } else {
                try {
                    val jsonObj = JSONObject(result)
                    if (jsonObj.has("status")) {
                        if (jsonObj.getString("status") == "success") {
                            if(AnodeClient::statustv.isInitialized) {
                                statustv.post(Runnable {
                                    statustv.text = mycontext.resources.getString(R.string.status_authorized)
                                })
                            }
                            //do not try to reconnect while re-authorization
                            val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
                            val node = prefs.getString("LastServerPubkey", defaultNode)
                            val connectedNode = prefs.getString("ServerPublicKey","")
                            if ((!node.isNullOrEmpty()) && ((!isVpnActive()) || (node != connectedNode))) {
                                cjdnsConnectVPN(node)
                            }
                            with(prefs.edit()) {
                                putLong("LastAuthorized", System.currentTimeMillis())
                                putString("ServerPublicKey", node)
                                commit()
                            }
                        } else {
                            if(AnodeClient::statustv.isInitialized) {
                                statustv.post(Runnable {
                                    statustv.text = "VPN Authorization failed: ${jsonObj.getString("message")}"
                                })
                            }
                            val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
                            with(prefs.edit()) {
                                putLong("LastAuthorized", 0)
                                commit()
                            }
                            LocalBroadcastManager.getInstance(mycontext).sendBroadcast(VPN_CONNECTED)
                            CjdnsSocket.IpTunnel_removeAllConnections()
                            CjdnsSocket.Core_stopTun()
                            CjdnsSocket.clearRoutes()
                            mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.DISCONNECT"))
                        }
                    }
                } catch (e: JSONException) {
                    LocalBroadcastManager.getInstance(mycontext).sendBroadcast(VPN_DISCONNECTED)
                    if(AnodeClient::statustv.isInitialized) {
                        statustv.post(Runnable {
                            statustv.text = mycontext.resources.getString(R.string.status_authorization_failed)
                        })
                    }
                }
            }
        }
    }

    fun cjdnsConnectVPN(node: String) {
        var iconnected: Boolean = false
        runnableConnection.init(h)
        CjdnsSocket.IpTunnel_removeAllConnections()
        if (node.isNotEmpty()) {
            //Connect to Internet
            CjdnsSocket.ipTunnelConnectTo(node)
        }
        var tries = 0
        //Check for ip address given by cjdns try for 20 times, 10secs
        Thread(Runnable {
            while ((node.isNotEmpty()) && (!iconnected && (tries < 10))) {
                if(this::statustv.isInitialized) {
                    statustv.post(Runnable {
                        statustv.text = "Getting routes..."
                    })
                }
                vpnConnected = false
                iconnected = CjdnsSocket.getCjdnsRoutes()
                tries++
                Thread.sleep(2000)
            }
            if (iconnected || node.isEmpty()) {
                if(this::statustv.isInitialized) {
                    statustv.post(Runnable {
                        statustv.text = "Got routes..."
                    })
                }
                //Restart Service
                CjdnsSocket.Core_stopTun()
                mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.DISCONNECT"))
                mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.START"))
                //mainButtonState(BUTTON_STATE_CONNECTED)
                vpnConnected = true
                //Start Thread for checking connection
                h.postDelayed(runnableConnection, 10000)
            } else {
                Log.i(LOGTAG,"VPN connection failed")
                vpnConnected = false
                LocalBroadcastManager.getInstance(mycontext).sendBroadcast(VPN_DISCONNECTED)
                CjdnsSocket.IpTunnel_removeAllConnections()
                //Stop UI thread
                h.removeCallbacks(runnableConnection)
            }
        }, "AnodeClient.cjdnsConnectVPN").start()
    }

    fun APIHttpReq(address: String, body: String, method: String, needsAuth: Boolean,isRetry: Boolean): String {
        var useHttps = true
        Log.i(LOGTAG,"HttpReq at $address with $body and Auth:$needsAuth")
        val cjdnsServerAddress = "[fca8:420d:c940:d70b:55b2:44c0:c01b:3f31]"
        var result:String
        var url: URL

        if(this::statustv.isInitialized) {
            statustv.post(Runnable { statustv.text = "Waiting for network..." })
        }
        url = URL(address)
        //url = URL("https://vpn.anode.co/api/0.3/tests/500error/")
        //if connection failed and we are connected to cjdns try with ipv6 address
        if((isRetry && isVpnActive()) || (CubeWifi.isConnected())) {
            var cjdnsurl = address.replace("vpn.anode.co",cjdnsServerAddress)
            cjdnsurl = cjdnsurl.replace("https:","http:")
            useHttps = false
            url = URL(cjdnsurl)
        }
        val conn: HttpURLConnection
        if (useHttps)
            conn = url.openConnection() as HttpsURLConnection
        else
            conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.requestMethod = method

        val bytes = body.toByteArray()
        if (needsAuth) {
            val md = MessageDigest.getInstance("SHA-256")
            val digest: ByteArray = md.digest(bytes)
            val digestStr = Base64.getEncoder().encodeToString(digest)
            val res = CjdnsSocket.Sign_sign(digestStr)
            val sig = res["signature"].str()
            conn.setRequestProperty("Authorization", "cjdns $sig")
        }
        try {
            conn.connect()
        } catch (e: SocketTimeoutException) {
            if (url.toString().contains(cjdnsServerAddress)) {
                APIHttpReq(address, body, method, needsAuth, true)
            } else {
                Log.w(LOGTAG,"SocketTimeoutException: "+e.printStackTrace())
            }
            return ""
        } catch (e: IOException) {
            Log.w(LOGTAG,"IOException: "+e.printStackTrace())
            return ""
        } catch (e: SocketException) {
            Log.w(LOGTAG,"socketException: "+e.printStackTrace())
            return ""
        } catch (e: Exception) {
            Log.w(LOGTAG,"Exception: "+ e.printStackTrace())
            return ""
        } catch (e: SSLProtocolException) {
            Log.w(LOGTAG,"SSLProtocolException: "+ e.printStackTrace())
            return ""
        }
        //Send body
        if (conn.requestMethod == "POST") conn.outputStream.write(bytes)

        try {
            result = conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            if ((conn.responseCode == 400) or (conn.responseCode == 403)){
                result = "ERROR: "+conn.responseCode.toString()+" - "+ conn.errorStream.reader().readText()
            } else {
                result = "ERROR: "+conn.responseCode.toString() + " - " + conn.responseMessage
            }
        }
        if(this::statustv.isInitialized) {
            statustv.post(Runnable {
                statustv.text = ""
            })
        }
        return result
    }


    object runnableConnection: Runnable {
        private var h: Handler? = null

        fun init(handler: Handler)  {
            h = handler
        }

        override fun run() {
            if (!CjdnsSocket.getCjdnsRoutes()) {
                //Disconnect
                stopThreads()
                CjdnsSocket.IpTunnel_removeAllConnections()
                CjdnsSocket.Core_stopTun()
                CjdnsSocket.clearRoutes()
                mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.DISCONNECT"))
                LocalBroadcastManager.getInstance(mycontext).sendBroadcast(VPN_CONNECTED)
            }
            val newip4address = CjdnsSocket.ipv4Address
            val newip6address = CjdnsSocket.ipv6Address
            //Reset VPN with new address
            if ((CjdnsSocket.VPNipv4Address != newip4address) || (CjdnsSocket.VPNipv6Address != newip6address)){
                if(AnodeClient::statustv.isInitialized) {
                    statustv.post(Runnable {
                        statustv.text = mycontext.resources.getString(R.string.status_connecting)
                    })
                }
                LocalBroadcastManager.getInstance(mycontext).sendBroadcast(VPN_CONNECTING)
                //Restart Service
                CjdnsSocket.Core_stopTun()
                mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.DISCONNECT"))
                mycontext.startService(Intent(mycontext, AnodeVpnService::class.java).setAction("co.anode.anodium.START"))
            } else if (CjdnsSocket.VPNipv6Address != "") {
                //mainButtonState(BUTTON_STATE_CONNECTED)
            }
            //Check for needed authorization call
            val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
            val Authtimestamp = prefs.getLong("LastAuthorized",0)
            if ((System.currentTimeMillis() - Authtimestamp) > Auth_TIMEOUT) {
                AuthorizeVPN().execute(prefs.getString("LastServerPubkey", defaultNode))
            }
            //GetPublicIP().execute(status)
            h!!.postDelayed(this, 10000) //ms
        }
    }

    class GetPublicIP(): AsyncTask<TextView, Void, String>() {
        var text:TextView? = null
        override fun doInBackground(vararg params: TextView?): String {
            text = params[0]
            return try {
                URL("https://api.ipify.org").readText(Charsets.UTF_8)
            } catch (e: Exception) {
                "error in getting public ip"
            }
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            text?.post(Runnable { text?.text  = "Public IP: $result" } )
        }
    }

    fun stopThreads() {
        h.removeCallbacks(runnableConnection)
    }

    class PostLogs() : AsyncTask<Any?, Any?, String>() {
        override fun doInBackground(objects: Array<Any?>): String? {
            if (checkNetworkConnection())
                return httpPostError( mycontext.filesDir)
            return "no connection"
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if ((result != null) && (result.isNotEmpty()) && (result.contains("No user consent to submit data"))) {
                showToast(result)
            }
        }
    }

    class PostMessage() : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String): String? {
            if (checkNetworkConnection())
                return httpPostMessage(params[0], params[1], params[2])
            return "no connection"
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (result != null) {
                showToast(result)
            }
        }
    }

    fun isVpnActive(): Boolean {
        val networkList:ArrayList<String> = ArrayList()
        try {
            for (networkInterface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp()) networkList.add(networkInterface.getName())
            }
        } catch (ex: java.lang.Exception) {
            return false
        }

        return networkList.contains("tun0")
    }

    fun logoutUser():String{
        val resp = APIHttpReq( API_LOGOUT_URL, "","DELETE", true , false)
        Log.i(LOGTAG, resp)
        return resp
    }

    /*Removed account functionality */
    /*fun logoutUserHandler(result:String){
        Log.i(LOGTAG,"Received from $API_LOGOUT_URL: $result")
        if ((!result.isNullOrBlank()) && (!result.contains("ERROR: "))) {
            try {
                val jsonObj = JSONObject(result)
                if (jsonObj.has("status")) {
                    if (jsonObj.getString("status") == "success") {
                        showToast("User logged out")
                        //Sign user out
                        val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
                        with(prefs.edit()) {
                            putBoolean("SignedIn", false)
                            putBoolean("Registered", false)
                            commit()
                        }
                        //On Log out start sign in activity
                        val signinActivity = Intent(mycontext, SignInActivity::class.java)
                        mainActivity.startActivity(signinActivity)
                    }
                }
            } catch (e: JSONException) {
                showToast(result)
            }
        } else {
            showToast(result)
        }
    }*/

    fun getPeeringLines(): String {
        val url = API_PEERING_LINES
        //if (checkNetworkConnection()) {
            val resp = APIHttpReq(url, "", "GET", false, false)
            Log.i(LOGTAG, resp)
            return resp
        //}
    }

    fun getPeeringLinesHandler(result: String){
        Log.i(LOGTAG,"Received from $API_PEERING_LINES: $result")
        if ((!result.isNullOrBlank()) && (!result.contains("ERROR: "))) {
            try {
                val peers = JSONArray(result)
                for (i in 0 until peers.length()) {
                    val peer = peers.getJSONObject(i)
                    CjdnsSocket.UDPInterface_beginConnection(peer.getString("publicKey"), peer.getString("ip"), peer.getInt("port"), peer.getString("password"), peer.getString("login"))
                }
            } catch (e: JSONException) {
                showToast("Error, invalid JSON")
            } catch (e: java.lang.Exception) {
                showToast("Error: "+e.message)
            }
        } else if (result.isNullOrEmpty()) {
            //TODO:???
        }else {
            showToast(result)
        }
    }

    fun removeCjdnsPeers() {
        for (i in 0 until CjdnsSocket.peers.size) {
            var splitPubKey = CjdnsSocket.peers[i]["addr"].toString().split(".")
            val pubKey = splitPubKey[splitPubKey.size-2]+"."+splitPubKey[splitPubKey.size-1]
            CjdnsSocket.InterfaceController_disconnectPeer(pubKey)
        }
    }

    /*Removed account functionality */
    /*
    class DeleteAccount() : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String?): String? {
            val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
            val username = prefs.getString("username", "")
            val url = API_DELETE_ACCOUNT.replace("<email_or_username>", username!!)
            val resp = APIHttpReq( url, "","DELETE", true , false)
            Log.i(LOGTAG, resp)
            return resp
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            Log.i(LOGTAG,"Received from $API_DELETE_ACCOUNT: $result")
            if ((!result.isNullOrBlank()) && (!result.contains("ERROR: "))) {
                try {
                    val jsonObj = JSONObject(result)
                    if (jsonObj.has("status")) {
                        if (jsonObj.getString("status") == "success") {
                            showToast(jsonObj.getString("message"))
                            val prefs = mycontext.getSharedPreferences(BuildConfig.APPLICATION_ID,Context.MODE_PRIVATE)
                            with(prefs.edit()) {
                                putBoolean("SignedIn", false)
                                putBoolean("Registered", false)
                                putString("username", "")
                                commit()
                            }
                            //On delete account start nickname activity
                            val nicknameActivity = Intent(mycontext, AccountNicknameActivity::class.java)
                            mainActivity.startActivity(nicknameActivity)
                        }
                    }
                } catch (e: JSONException) {
                    showToast("Error, invalid JSON")
                }
            } else if (result != null){
                showToast(result)
            }
        }
    }*/

    fun generateUsername():String {
        val resp = APIHttpReq(apiUsernameGenerate, "", "GET", needsAuth = true, isRetry = false)
        Log.i(LOGTAG, resp)
        return resp
    }

    fun eventLog(message: String) {
        val logFile = File(AnodeUtil.filesDirectory +"/anodium-events.log")
        //Do not log if file is bigger than 1MB
        if (logFile.length() > 1000000) return

        if (!logFile.exists()) {
            try {
                logFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        try {
            val buf = BufferedWriter(FileWriter(logFile, true))
            buf.append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())+": "+message)
            buf.newLine()
            buf.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}