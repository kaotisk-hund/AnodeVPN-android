package co.anode.anodium

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File

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
        //Show setup or main fragment according to wallet existing or not
        val ft = supportFragmentManager.beginTransaction()
        ft.setCustomAnimations(android.R.anim.fade_in,android.R.anim.fade_out)
        //Check if wallet exists
        val walletFile = File(baseContext.filesDir.toString() + "/pkt/wallet.db")
        if (walletFile.exists()) {
            val createFragment = supportFragmentManager.findFragmentById(R.id.wallet_fragmentCreate)
            if (createFragment != null) {
                Log.i(LOGTAG, "WalletActivity hide create fragment")
                ft.hide(createFragment)
            }
        } else {
            val mainFragment = supportFragmentManager.findFragmentById(R.id.wallet_fragmentMain)
            if (mainFragment != null) {
                Log.i(LOGTAG, "WalletActivity hide main fragment")
                ft.hide(mainFragment)
            }
        }
        ft.commit()
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun switchToMain() {
        val ft = supportFragmentManager.beginTransaction()
        ft.setCustomAnimations(android.R.anim.fade_in,android.R.anim.fade_out)
        val createFragment = supportFragmentManager.findFragmentById(R.id.wallet_fragmentCreate)
        val mainFragment = supportFragmentManager.findFragmentById(R.id.wallet_fragmentMain)
        if ((createFragment != null) && (mainFragment != null)){
            Log.i(LOGTAG, "WalletActivity switching from setup wallet to main wallet fragment")
            ft.remove(createFragment)
            ft.show(mainFragment)
            ft.commit()
            mainFragment.onResume()
        }
    }
}