package co.anode.anodium

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import java.io.File

class DebugWalletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_wallet)
        //actionbar
        val actionbar = supportActionBar
        //set actionbar title
        actionbar!!.title = "PLTD Log"
        //set back button
        actionbar.setDisplayHomeAsUpEnabled(true)
        //Open pltd log file and display it
        val logtext = findViewById<TextView>(R.id.debugwalletlogtext)
        val anodeUtil = AnodeUtil(application)
        val logfile = File(anodeUtil.CJDNS_PATH+"/"+anodeUtil.PLTD_LOG).readText()
        logtext.text = logfile
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}