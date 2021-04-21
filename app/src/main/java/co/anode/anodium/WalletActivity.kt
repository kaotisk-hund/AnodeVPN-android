package co.anode.anodium

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit

class WalletActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        //actionbar
        val actionbar = supportActionBar
        //set actionbar title
        actionbar!!.title = getString(R.string.wallet_activity_title)
        //set back button
        actionbar.setDisplayHomeAsUpEnabled(true)
        AnodeClient.eventLog(baseContext,"Activity: WalletActivity created")
        val prefs = getSharedPreferences("co.anode.anodium", Context.MODE_PRIVATE)
        val createfragment = supportFragmentManager.findFragmentById(R.id.wallet_fragmentCreate)
        val mainfragment = supportFragmentManager.findFragmentById(R.id.wallet_fragmentMain)

        //Show setup or main fragment according to wallet existing or not
        val ft = supportFragmentManager.beginTransaction()
        ft.setCustomAnimations(android.R.anim.fade_in,android.R.anim.fade_out)
        if (prefs.getBoolean("lndwallet", false)) {
            if (createfragment != null) {
                Log.i(LOGTAG, "WalletActivity hide create fragment")
                ft.hide(createfragment)
            }
            //ft.show(mainfragment)
        } else {
            if (mainfragment != null) {
                Log.i(LOGTAG, "WalletActivity hide main fragment")
                ft.hide(mainfragment)
            }
            //ft.show(createfragment)
        }
        ft.commit()
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}