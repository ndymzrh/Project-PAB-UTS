package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etNama = findViewById<TextInputEditText>(R.id.etNama)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etNoHp = findViewById<TextInputEditText>(R.id.etNoHp)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etKonfirmasi = findViewById<TextInputEditText>(R.id.etKonfirmasi)
        val btnDaftar = findViewById<MaterialButton>(R.id.btnDaftar)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvKembaliLogin = findViewById<TextView>(R.id.tvKembaliLogin)

        // Tombol kembali ke halaman login
        tvKembaliLogin.setOnClickListener {
            finish() // Menutup halaman registrasi dan kembali ke halaman sebelumnya
        }

        btnDaftar.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val noHp = etNoHp.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val konfirmasi = etKonfirmasi.text.toString().trim()

            // 1. Validasi input kosong
            if (nama.isEmpty() || email.isEmpty() || noHp.isEmpty() || password.isEmpty() || konfirmasi.isEmpty()) {
                Toast.makeText(this, "Harap isi semua kolom", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Validasi kecocokan password
            if (password != konfirmasi) {
                Toast.makeText(this, "Password dan konfirmasi tidak cocok", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Tampilkan loading dan matikan tombol agar tidak diklik dua kali
            progressBar.visibility = View.VISIBLE
            btnDaftar.isEnabled = false

            // 3. Proses pembuatan akun
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid

                        // Role di-hardcode menjadi "pengguna" karena ini khusus form customer
                        val userMap = hashMapOf(
                            "nama" to nama,
                            "email" to email,
                            "no_hp" to noHp,
                            "role" to "pengguna"
                        )

                        if (uid != null) {
                            db.collection("users").document(uid)
                                .set(userMap)
                                .addOnSuccessListener {
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "Registrasi Berhasil!", Toast.LENGTH_SHORT).show()
                                    // Pindah ke halaman utama atau login
                                    // startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    progressBar.visibility = View.GONE
                                    btnDaftar.isEnabled = true
                                    Toast.makeText(this, "Gagal menyimpan data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        btnDaftar.isEnabled = true
                        Toast.makeText(this, "Registrasi Gagal: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}