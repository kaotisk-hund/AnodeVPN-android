package co.anode.anodium.wallet

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import co.anode.anodium.R
import co.anode.anodium.support.AnodeClient
import co.anode.anodium.support.AnodeUtil
import co.anode.anodium.support.LOGTAG
import co.anode.anodium.volley.APIController
import co.anode.anodium.volley.ServiceVolley
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject

class RecoverySeed : AppCompatActivity() {
    private lateinit var statusbar: TextView
    private lateinit var apiController: APIController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery_seed)
        val actionbar = supportActionBar
        actionbar!!.title = getString(R.string.pin_prompt_title)
        actionbar.setDisplayHomeAsUpEnabled(true)
        AnodeClient.eventLog("Activity: PinPrompt created")
        val param = intent.extras
        val passwordString = param?.get("password").toString()

        statusbar = findViewById(R.id.textview_status)

        //Initialize Volley Service
        val service = ServiceVolley()
        apiController = APIController(service)
        //Prompt user for existing seed
        recoveryPrompt()
        val createButton = findViewById<Button>(R.id.button_wallet_create)
        createButton.setOnClickListener {
            val seedString = findViewById<TextView>(R.id.seed_column).text.toString()
            val seedArray = JSONArray(seedString.split(" "))
            createWallet(passwordString,seedArray)
        }

        val recoverButton = findViewById<Button>(R.id.button_wallet_recover_from_seed)

        recoverButton.setOnClickListener {
            //Hide soft keyboard
            val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(window.decorView.rootView.windowToken, 0)
            val seedInputText = findViewById<EditText>(R.id.input_seed)
            //Check for seed length
            val seedWords = seedInputText.text.split(" ")
            if (seedWords.size != 15) {
                seedInputText.error = getString(R.string.wallet_seed_length_msg)
            } else {
                val seedArray = JSONArray()
                for (i in seedWords.indices) {
                    seedArray.put(seedWords[i])
                }
                showLoading()
                recoverButton.visibility = View.GONE
                val seedPassText = findViewById<EditText>(R.id.editTextWalletSeedPass)
                recoverWallet(passwordString, seedPassText.text.toString(), seedArray)
            }
        }
        val closeButton = findViewById<Button>(R.id.button_wallet_close)
        closeButton.setOnClickListener {
            //TODO: close pinprompt and passwordprompt activities

            //Launch wallet activity
            startActivity(Intent(applicationContext, WalletActivity::class.java))
            finish()
        }
    }

    private fun confirmWalletLayout(isRecovery: Boolean) {
        hideLoading()
        val recoverButton = findViewById<Button>(R.id.button_wallet_recover_from_seed)
        val createButton = findViewById<Button>(R.id.button_wallet_create)
        val closeButton = findViewById<Button>(R.id.button_wallet_close)
        recoverButton.visibility = View.GONE
        createButton.visibility = View.GONE
        closeButton.visibility = View.VISIBLE
        val recoveryLayout = findViewById<LinearLayout>(R.id.recover_seed_layout)
        val topLabel = findViewById<TextView>(R.id.text_wallet_recovery_label)
        val newWalletLayout = findViewById<LinearLayout>(R.id.create_wallet_show_seed)
        newWalletLayout.visibility = View.GONE
        recoveryLayout.visibility = View.GONE
        if (isRecovery) {
            topLabel.text = getString(R.string.wallet_recovery_success)
        } else {
            topLabel.text = getString(R.string.wallet_creation_success)
        }
    }

    private fun initLayout(recovery: Boolean) {
        val newWalletLayout = findViewById<LinearLayout>(R.id.create_wallet_show_seed)
        val recoveryLayout = findViewById<LinearLayout>(R.id.recover_seed_layout)
        val recoverButton = findViewById<Button>(R.id.button_wallet_recover_from_seed)
        val createButton = findViewById<Button>(R.id.button_wallet_create)
        val closeButton = findViewById<Button>(R.id.button_wallet_close)
        closeButton.visibility = View.GONE
        if (recovery) {
            recoveryLayout.visibility = View.VISIBLE
            recoverButton.visibility = View.VISIBLE
            newWalletLayout.visibility = View.GONE
            createButton.visibility = View.GONE
        } else {
            recoveryLayout.visibility = View.GONE
            recoverButton.visibility = View.GONE
            newWalletLayout.visibility = View.VISIBLE
            createButton.visibility = View.VISIBLE
        }
    }

    private fun recoveryPrompt() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Creating PKT wallet")
        builder.setMessage("Do you have an existing PKTwallet seed you want to use?")
        builder.setNegativeButton("No") { _, _ ->
            initLayout(false)
            generateSeed()
        }
        builder.setPositiveButton("Yes") { _, _ ->
            initLayout(true)
        }
        val alert: AlertDialog = builder.create()
        alert.show()
    }

    private fun generateSeed() {
        AnodeClient.eventLog("Button: Create PKT wallet clicked")
        Log.i(LOGTAG, "RecoverySeed Activity creating wallet")
        //Set up UI
        statusbar.text = getString(R.string.generating_wallet_seed)
        showLoading()
        val jsonData = JSONObject()
        Log.i(LOGTAG, "generating wallet seed...")
        apiController.post(apiController.createSeedURL, jsonData)
        { response ->
            hideLoading()
            if ((response != null) && (response.has("seed") && !response.isNull("seed"))) {
                Log.i(LOGTAG, "wallet seed created")
                //Get seed
                val seedText = findViewById<TextView>(R.id.seed_column)
                val seedArray = response.getJSONArray("seed")
                var seedString = ""
                for (i in 0 until seedArray.length()) {
                    seedString += seedArray.getString(i) + " "
                }
                seedText.text = seedString.trim()
                //Update UI
                statusbar.text = ""
                val createButton = findViewById<Button>(R.id.button_wallet_create)
                createButton.visibility = View.VISIBLE
            } else {
                Log.e(LOGTAG, "Error in generating wallet seed")
                Toast.makeText(this, "Failed to generate wallet seed.", Toast.LENGTH_LONG).show()
                //TODO: update UI, do we need to change layout?
            }
        }
    }

    private fun recoverWallet( password: String, seedPass:String, seedArray: JSONArray) {
        Log.i(LOGTAG, "recovering PKT wallet...")
        statusbar.text = getString(R.string.recovering_wallet)
        val jsonData = JSONObject()
        if (seedPass.isNotEmpty()) {
            jsonData.put("seed_passphrase", seedPass)
        }
        jsonData.put("wallet_passphrase", password)
        jsonData.put("wallet_seed", seedArray)
        initWallet( jsonData, true)
    }

    private fun createWallet( password: String, seedArray: JSONArray) {
        Log.i(LOGTAG, "creating PKT wallet...")
        statusbar.text = getString(R.string.creating_wallet)
        val jsonData = JSONObject()
        jsonData.put("wallet_passphrase", password)
        jsonData.put("wallet_seed", seedArray)
        initWallet( jsonData, false)
    }

    private fun initWallet( jsonData: JSONObject, isRecovery:Boolean) {
        //Reset any previously daved address
        val prefs = getSharedPreferences("co.anode.anodium", MODE_PRIVATE)
        prefs.edit().remove("lndwalletaddress").apply()
        showLoading()
        apiController.post(apiController.walletCreateURL, jsonData)
        { response ->
            hideLoading()
            if ((response != null) &&
                (!response.has("message")) &&
                (!response.has("error"))) {
                //Update UI
                statusbar.text = ""
                confirmWalletLayout(isRecovery)
            } else {
                Log.i(LOGTAG, "PKT create wallet failed")
                statusbar.text = "Error in creating wallet..."
                //Wallet creation failed, parse error, log and notify user
                var errorString = response?.getString("error")
                if ((isRecovery) && (errorString!!.contains("The birthday of this seed appears to be"))) {
                    val seedPassLayout = findViewById<TextInputLayout>(R.id.walletseedpassLayout)
                    seedPassLayout.error = getString(R.string.seed_wrong_password)
                } else  if (errorString != null) {
                    Log.e(LOGTAG, errorString)
                    //Get user friendly message
                    errorString = errorString.substring(errorString.indexOf(" "), errorString.indexOf("\n\n"))
                    Toast.makeText(this, "Error: $errorString", Toast.LENGTH_LONG).show()
                }
                //Reset UI
                initLayout(isRecovery)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun makeBackgroundWhite(){
        val layout = findViewById<ConstraintLayout>(R.id.RecoverySeedLayout)
        layout.setBackgroundColor(getColor(android.R.color.white))
    }

    private fun makeBackgroundGrey() {
        val layout = findViewById<ConstraintLayout>(R.id.RecoverySeedLayout)
        layout.setBackgroundColor(getColor(android.R.color.darker_gray))
    }

    private fun showLoading() {
        makeBackgroundGrey()
        val loading = findViewById<ProgressBar>(R.id.loadingAnimation)
        loading.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        makeBackgroundWhite()
        val loading = findViewById<ProgressBar>(R.id.loadingAnimation)
        loading.visibility = View.GONE
    }
}