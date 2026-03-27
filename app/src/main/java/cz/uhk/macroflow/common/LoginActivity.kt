package cz.uhk.macroflow.common

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val RC_SIGN_IN = 9001
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // ✅ OPRAVA: Pokud je uživatel už přihlášen, zkusíme stáhnout data ze serveru a až pak jdeme dál
        if (auth.currentUser != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    FirebaseRepository.syncCloudDataToLocal(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                withContext(Dispatchers.Main) {
                    goToMain()
                }
            }
            return
        }

        findViewById<View>(R.id.btnGoogleSignIn).setOnClickListener {
            startGoogleSignIn()
        }

        findViewById<View>(R.id.btnContinueOffline).setOnClickListener {
            goToMain()
        }
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("324719841390-sibsmtkcjfknjilqtq2hgi8cdtql35d8.apps.googleusercontent.com")
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, gso)
        startActivityForResult(client.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In selhal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            // ✅ OPRAVA: Zjišťujeme, jestli máme v cloudu VŮBEC nějaký profil. Pokud ne, až tehdy je to nový uživatel.
                            val cloudProfileExists = FirebaseRepository.downloadProfile() != null

                            if (cloudProfileExists) {
                                // Máme data na serveru, stáhneme je do nově nainstalovaného telefonu
                                FirebaseRepository.syncCloudDataToLocal(applicationContext)
                            } else {
                                // Na serveru nic není, nahrajeme tam lokální výchozí data z telefonu
                                FirebaseRepository.syncLocalDataToCloud(applicationContext)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    goToMain()
                }
            } else {
                Toast.makeText(this, "Přihlášení selhalo: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}