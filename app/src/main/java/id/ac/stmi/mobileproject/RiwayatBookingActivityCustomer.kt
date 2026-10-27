package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
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
        binding = ActivityRiwayatBookingCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
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
                val completedBookings = bookings.filter { it.status == BookingStatus.SELESAI }
                adapter.submitList(completedBookings)
                binding.progress.isVisible = false
                binding.tvEmpty.isVisible = completedBookings.isEmpty()
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