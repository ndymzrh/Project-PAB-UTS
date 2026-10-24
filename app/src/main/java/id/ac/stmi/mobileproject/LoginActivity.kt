package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Inisialisasi View berdasarkan ID dari activity_login.xml
        val rgRole = findViewById<RadioGroup>(R.id.rgRole)
        val layoutDaftar = findViewById<LinearLayout>(R.id.layoutDaftar)

        // 2. Pasang Listener saat opsi Role berubah
        rgRole.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbPetugas) {
                // Sembunyikan tulisan daftar saat opsi Petugas dipilih
                layoutDaftar.visibility = View.GONE
            } else {
                // Tampilkan kembali tulisan daftar saat opsi Pelanggan dipilih
                layoutDaftar.visibility = View.VISIBLE
            }
        }

        // Tombol "Daftar Akun" -> halaman register
        findViewById<TextView>(R.id.tvDaftar).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

    }
}