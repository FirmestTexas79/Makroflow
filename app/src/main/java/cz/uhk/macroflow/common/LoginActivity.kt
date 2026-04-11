package cz.uhk.macroflow.common

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
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val RC_SIGN_IN = 9001
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. OKAMŽITÁ KONTROLA PŘIHLÁŠENÍ (Před nastavením layoutu)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            // Uživatel už je v systému, nebudeme mu kreslit Login, rovnou synchronizujeme a jdeme do Main
            startAutoLogin()
            return
        }

        // 2. NASTAVENÍ UI (Tento kód proběhne, jen když uživatel NENÍ přihlášen)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_login)

        findViewById<View>(R.id.btnGoogleSignIn).setOnClickListener {
            startGoogleSignIn()
        }

        findViewById<View>(R.id.btnContinueOffline).setOnClickListener {
            goToMain()
        }
    }

    private fun startAutoLogin() {
        // 1. Okamžitě do Main (žádné čekání)
        goToMain()

        // 2. Sync ať si běží na pozadí, klidně ať si přemazává DB,
        // MainActivity si s tím teď díky Handleru poradí (počká si).
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FirebaseRepository.syncCloudDataToLocal(applicationContext)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startGoogleSignIn() {
        // ID token je tvůj Client ID pro Web Application z Google Console
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
                firebaseAuthWithGoogle(account?.idToken ?: return)
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
                            val cloudProfileExists = FirebaseRepository.downloadProfile() != null

                            if (cloudProfileExists) {
                                FirebaseRepository.syncCloudDataToLocal(applicationContext)
                            } else {
                                // Nový uživatel — zajistíme výchozí profil v lokální DB před uploadem
                                val db = cz.uhk.macroflow.data.AppDatabase.getDatabase(applicationContext)
                                if (db.userProfileDao().getProfileSync() == null) {
                                    db.userProfileDao().saveProfile(
                                        cz.uhk.macroflow.data.UserProfileEntity(id = 1)
                                    )
                                }
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
        val intent = Intent(this, MainActivity::class.java)
        // Tyto flagy jsou KLÍČOVÉ: vyčistí zásobník aktivit, takže "Zpět" aplikaci zavře a nevrátí na Login
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}