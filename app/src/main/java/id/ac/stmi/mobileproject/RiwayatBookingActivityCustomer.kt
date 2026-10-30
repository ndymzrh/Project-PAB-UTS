package id.ac.stmi.mobileproject

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityRiwayatBookingCustomerBinding

class RiwayatBookingActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityRiwayatBookingCustomerBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var listener: ListenerRegistration? = null
    private lateinit var adapter: BookingListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityRiwayatBookingCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val atasAwal = binding.header.paddingTop
        val bawahAwal = binding.main.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = atasAwal + bars.top)
            binding.main.updatePadding(bottom = bawahAwal + bars.bottom)
            insets
        }


        binding.btnBack.setOnClickListener { finish() }
        binding.btnNewBooking.setOnClickListener {
            startActivity(Intent(this, LayananActivity::class.java))
        }

        adapter = BookingListAdapter { booking ->
            startActivity(
                Intent(this, BookingSuccessActivity::class.java)
                    .putExtra(BookingSuccessActivity.EXTRA_BOOKING_ID, booking.id)
            )
        }
        binding.rvBookings.layoutManager = LinearLayoutManager(this)
        binding.rvBookings.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.progress.isVisible = true
        listener = BookingRepository.listenCustomerBookings(
            uid = uid,
            onResult = { bookings ->
                // Riwayat memuat SEMUA booking (aktif, selesai, ditolak, dibatalkan).
                // Urutan dari repository: yang aktif di atas, lalu yang terbaru.
                adapter.submitList(bookings)
                binding.progress.isVisible = false
                binding.tvEmpty.isVisible = bookings.isEmpty()
            },
            onError = {
                binding.progress.isVisible = false
                binding.tvEmpty.isVisible = true
                toast("Gagal memuat riwayat booking")
            }
        )
    }

    override fun onStop() {
        listener?.remove()
        listener = null
        super.onStop()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}