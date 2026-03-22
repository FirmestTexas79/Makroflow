package cz.uhk.macroflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat // 👈 Ujisti se, že máš tento import
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val RC_SIGN_IN = 9001
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Pokud je uživatel už přihlášen, přejdi rovnou do aplikace
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        findViewById<View>(R.id.btnGoogleSignIn).setOnClickListener {
            startGoogleSignIn()
        }

        // Tlačítko "Pokračovat bez účtu"
        findViewById<View>(R.id.btnContinueOffline).setOnClickListener {
            goToMain()
        }
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // Tento web client ID najdeš ve firebase console nebo google-services.json
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
                            val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                            if (isNewUser) {
                                FirebaseRepository.syncLocalDataToCloud(applicationContext)
                            } else {
                                FirebaseRepository.syncCloudDataToLocal(applicationContext)
                            }
                        }
                    } catch (e: Exception) {
                        // I když sync selže, uživatele chceme pustit do aplikace
                    }
                    // Přesunuto mimo try-catch nebo hned za něj
                    goToMain()
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}