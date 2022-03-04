package co.anode.anodium.wallet

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import co.anode.anodium.support.AnodeUtil
import co.anode.anodium.support.LOGTAG
import co.anode.anodium.R
import java.io.File

class DebugWalletActivity : AppCompatActivity() {
    private var scrollposition = 0
    private var toBottom = false
    private var logfile = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_wallet)
        //actionbar
        val actionbar = supportActionBar
        //set actionbar title
        actionbar!!.title = "PLD Log"
        //set back button
        actionbar.setDisplayHomeAsUpEnabled(true)
        //Open pld log file and display it
        val logtext = findViewById<TextView>(R.id.debugwalletlogtext)

        logfile = File(AnodeUtil.CJDNS_PATH +"/"+ AnodeUtil.PLD_LOG).readText()
        logtext.text = logfile
        val scroll = findViewById<ScrollView>(R.id.wallet_debug_scroll)
        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
        }

        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            scrollposition = scrollY
            val diff = scroll.getChildAt(0).bottom - (scrollY + scroll.height)
            toBottom = diff < 50
        }

        Thread({
            Log.i(LOGTAG, "DebugActivity.RefreshValues startup")
            var sleep: Long = 500
            var oldlog = logfile
            while (true) {
                this.runOnUiThread {
                    if (toBottom) {
                        val newlog = File(AnodeUtil.CJDNS_PATH + "/" + AnodeUtil.PLD_LOG).readText()
                        if (newlog.length > oldlog.length) {
                            oldlog = newlog
                            logtext.text = newlog
                            //position is in characters?
                            //scroll.fullScroll(View.FOCUS_DOWN)
                            scroll.post {
                                scroll.fullScroll(View.FOCUS_DOWN)
                            }
                            sleep = 1000
                        } else {
                            sleep = 300
                        }
                    } else {
                        sleep = 1000
                    }
                }
                Thread.sleep(sleep)
            }
        }, "DebugWalletActivity.RefreshValues").start()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}