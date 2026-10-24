package id.ac.stmi.mobileproject

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.button).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<Button>(R.id.button2).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Teks WhatsApp -> buka chat WA
        findViewById<TextView>(R.id.textView17).setOnClickListener {
            bukaWhatsApp()
        }
    }

    private fun bukaWhatsApp() {
        val nomorWa = "6285156210062"
        val pesan = "Halo Annova AutoCare, saya ingin konsultasi servis mobil."
        val url = "https://wa.me/$nomorWa?text=${Uri.encode(pesan)}"

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp tidak ditemukan di perangkat ini", Toast.LENGTH_SHORT).show()
        }
    }
}