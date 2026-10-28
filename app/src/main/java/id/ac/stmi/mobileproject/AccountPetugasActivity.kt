
package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AccountPetugasActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var tvNama: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvId: TextView
    private lateinit var btnLogout: android.view.View
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_petugas)

        initViews()
        setupBottomNavigation()
        loadProfilPetugas()
        setupLogout()
    }

    private fun initViews() {
        tvNama = findViewById(R.id.tvNamaPetugas)
        tvEmail = findViewById(R.id.tvEmailPetugas)
        tvPhone = findViewById(R.id.tvPhonePetugas)
        tvId = findViewById(R.id.tvIdPetugas)
        btnLogout = findViewById(R.id.btnLogout)
        bottomNav = findViewById(R.id.bottomNav)
    }

    private fun setupBottomNavigation() {
        // Tandai menu akun sebagai halaman aktif.
        bottomNav.selectedItemId = R.id.nav_account

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(this, HomeActivityPetugas::class.java)
                    )
                    finish()
                    true
                }

                R.id.nav_booking -> {
                    startActivity(
                        Intent(this, BookingPetugasActivity::class.java)
                    )
                    finish()
                    true
                }

                R.id.nav_account -> {
                    true
                }

                else -> false
            }
        }
    }

    private fun loadProfilPetugas() {
        val user = auth.currentUser

        if (user == null) {
            goToLogin()
            return
        }

        tvNama.text = user.displayName ?: "Petugas"
        tvEmail.text = user.email ?: "-"
        tvPhone.text = "-"
        tvId.text = user.uid

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (doc.exists()) {
                    tvNama.text = doc.getString("nama")
                        ?: doc.getString("name")
                                ?: user.displayName
                                ?: "Petugas"

                    tvEmail.text = doc.getString("email")
                        ?: user.email
                                ?: "-"

                    tvPhone.text = doc.getString("phone")
                        ?: doc.getString("noHp")
                                ?: "-"

                    tvId.text = doc.getString("idPetugas")
                        ?: user.uid
                }
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener

                Toast.makeText(
                    this,
                    "Gagal memuat profil: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun setupLogout() {
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Akun")
                .setMessage("Yakin ingin keluar dari akun ini?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Keluar") { _, _ ->
                    auth.signOut()
                    goToLogin()
                }
                .show()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }
}
