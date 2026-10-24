package id.ac.stmi.mobileproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tanpa setContentView: yang tampil hanya windowBackground dari tema

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            routeByRole(user.uid)
        } else {
            val seen = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("welcome_seen", false)
            go(if (seen) LoginActivity::class.java else WelcomeActivity::class.java)
        }
    }

    private fun routeByRole(uid: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val target = when (doc.getString("role")?.trim()?.lowercase()) {
                    "pengguna" -> HomeActivityCustomer::class.java
                    "petugas", "pemilik" -> HomeActivityPetugas::class.java
                    else -> {
                        // Data akun tidak ada / role tidak dikenal
                        FirebaseAuth.getInstance().signOut()
                        LoginActivity::class.java
                    }
                }
                go(target)
            }
            .addOnFailureListener {
                // Mungkin hanya koneksi: LoginActivity akan mencoba lagi
                // karena user masih login (ia tidak signOut saat gagal koneksi)
                go(LoginActivity::class.java)
            }
    }

    private fun go(target: Class<*>) {
        startActivity(Intent(this, target))
        finish()
    }
}