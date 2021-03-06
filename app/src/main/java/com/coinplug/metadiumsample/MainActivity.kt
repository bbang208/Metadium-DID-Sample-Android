package com.coinplug.metadiumsample

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.coinplug.metadiumsample.data.local.prefs.AppPreference
import com.coinplug.metadiumsample.databinding.ActivityMainBinding
import com.metadium.did.MetadiumWallet
import com.metadium.did.crypto.MetadiumKey
import com.metadium.did.exception.DidException
import com.metadium.did.protocol.MetaDelegator
import com.metadium.did.verifiable.Verifier
import com.metaidum.did.resolver.client.DIDResolverAPI
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import org.web3j.crypto.Sign.SignatureData
import org.web3j.utils.Numeric
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var viewBinding: ActivityMainBinding

    private val delegator = MetadiumModule.delegator

    private val mPrefs = AppPreference()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        val view = viewBinding.root
        setContentView(view)

        AppPreference.initPreference(this)

        viewBinding.btnCreateDID.setOnClickListener {
            createDID()
        }

        viewBinding.btnCheckDID.setOnClickListener {
            checkDID()
        }

        viewBinding.btnJwtSubmit.setOnClickListener {
            viewBinding.tokenInputText.text?.toString()?.let {
                submitVCData(it)
                viewBinding.tokenInputText.text?.clear()
            }
        }

        viewBinding.btnDeleteDID.setOnClickListener {
            deleteDID()
        }

        viewBinding.btnRemoveAllVCData.setOnClickListener {
            deleteAllVCData()
        }

        viewBinding.btnVcList.setOnClickListener {
            val intent = Intent(this, CertificationListActivity::class.java)
            startActivity(intent)
        }

        viewBinding.btnAddSampleVc.setOnClickListener {
            val wallet = mPrefs.loadWallet()

            if (wallet == null) {
                makeToast("????????? ????????????.")
                return@setOnClickListener
            }

            val firstCredential: SignedJWT = wallet.issueCredential(
                Collections.singletonList("EmailCredential"),  // types
                null,  // credential identifier. nullable
                Date(),  // issuance date. nullable
                null,  // expiration date. nullable
                "did:b-space:000000000000000000000000000000000000000000000000000000000000002e",
                mapOf("email" to "testmail@email.com") // claims
            )

            val serializedFirstCredential = firstCredential.serialize()
            viewBinding.tokenInputText.setText(serializedFirstCredential)
        }

        viewBinding.issueDriverLicenseButton.setOnClickListener {
            val wallet = mPrefs.loadWallet()

            if (wallet == null) {
                makeToast("????????? ????????????.")
                return@setOnClickListener
            }

            val date = Calendar.getInstance()
            date.add(Calendar.SECOND, 10)

            val firstCredential: SignedJWT = wallet.issueCredential(
                Collections.singletonList("DriverLicenseCredential"),  // types
                null,  // credential identifier. nullable
                Date(),  // issuance date. nullable
                date.time,  // expiration date. nullable
                wallet.did,
                mapOf("name" to "?????????", "licenseNumber" to "12-34-567890-01", "licenseType" to "1??? ??????") // claims
            )

            val serializedFirstCredential = firstCredential.serialize()

            Log.e("SerializedVC", serializedFirstCredential)
            viewBinding.verifyTokenInput.setText(serializedFirstCredential)
        }

        viewBinding.issueVPButton.setOnClickListener {
            val wallet = mPrefs.loadWallet()

            if (wallet == null) {
                makeToast("????????? ????????????.")
                return@setOnClickListener
            }

            val nameCredential: SignedJWT = wallet.issueCredential(
                Collections.singletonList("NameCredential"),  // types
                null,  // credential identifier. nullable
                Date(),  // issuance date. nullable
                null,  // expiration date. nullable
                wallet.did,
                mapOf("name" to "?????????") // claims
            )

            val serializedVC = nameCredential.serialize()

            val vp = wallet.issuePresentation(
                Collections.singletonList("TestPresentation"),
                null,
                Date(),
                null,
                listOf(serializedVC)
            )

            val serializedVP = vp.serialize()

            Log.e("SerializedVP", serializedVP)
            viewBinding.verifyTokenInput.setText(serializedVP)
        }

        viewBinding.verifyButton.setOnClickListener {
            viewBinding.verifyTokenInput.text?.toString()?.let {
                if (it.isNotEmpty())
                    verifyToken(it)
            }
        }
    }

    private fun createDID() {
        makeToast("DID ?????? ???")
        //????????? ????????? ?????? ?????????
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wallet = MetadiumWallet.createDid(delegator) //?????? ??????
                mPrefs.saveWallet(wallet.toJson()) //????????? ?????? ??????
                mPrefs.setDID(wallet.did) //DID??????
                Log.e(TAG, "DID: ${wallet.did}")
                Log.e(TAG, "DID wallet to json: ${wallet.toJson()}")
                makeToast("DID ?????? ??????")
            } catch (e: Exception) {
                makeToast("DID ?????? ??????")
                e.printStackTrace()
            }
        }
    }

    private fun checkDID() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wallet = mPrefs.loadWallet() //?????? ????????????
                if (wallet == null) {
                    makeToast("?????? ??????")
                    return@launch
                }
                val result = wallet.existsDid(delegator) //true or false ??? ????????? ??????
                makeToast("?????? ????????????: $result")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteDID() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wallet = mPrefs.loadWallet() //????????? ????????? ????????????
                wallet?.deleteDid(delegator) // ?????? ??????.
                mPrefs.removeAllData() //????????? ?????????????????? ???????????? ?????? ??????.
                makeToast("DID ?????? ??????")
            } catch (e: Exception) {
                e.printStackTrace()
                makeToast("DID ?????? ??????")
            }
        }
    }

    private fun makeToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitVCData(jwt: String) {
        //?????? JWT????????? ????????? ??? ????????????.
        if (jwt.isNotEmpty()) {
            try {
                SignedJWT.parse(jwt)
                mPrefs.saveVCData(jwt)
                makeToast("?????? ??????")
            } catch (e: Exception) {
                makeToast("????????? ?????? ????????? ????????????.")
                e.printStackTrace()
            }
        }
    }

    private fun deleteAllVCData() {
        //?????? VC???????????? ???????????????.
        Toast.makeText(this, "????????? ?????? ??????", Toast.LENGTH_LONG).show()
        mPrefs.deleteAllVCData()
    }

    private fun verifyToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val verifier = Verifier()
            DIDResolverAPI.getInstance().setResolverUrl("https://mt-resolver.blockchainbusan.kr/1.0/")
            val jwtToken = try {
                SignedJWT.parse(token)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "JWT ??????????????? ??????.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            var isSuccess: Boolean = true
            var message: String = "?????? ??????"

            try {
                if (!verifier.verify(jwtToken)) {
                    isSuccess = false
                    message = "?????? ??????"

                } else if (jwtToken.jwtClaimsSet.expirationTime != null && jwtToken.jwtClaimsSet.expirationTime.time < Date().time) {
                    isSuccess = false
                    message = "???????????? ??????"
                }
            } catch (didException: DidException) {
                didException.printStackTrace()
                isSuccess = false
                message = didException.message?: ""
            }

            if (isSuccess)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "?????? ??????", Toast.LENGTH_SHORT).show()
                }
            else
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
        }
    }
}