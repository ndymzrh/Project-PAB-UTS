package id.ac.stmi.mobileproject

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBookingPetugasBinding

class BookingPetugasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingPetugasBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var listener: ListenerRegistration? = null
    private lateinit var adapter: BookingPetugasAdapter
    private val updatingIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityBookingPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val atasAwal = binding.header.paddingTop
        val bawahAwal = binding.main.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = atasAwal + bars.top)
            binding.main.updatePadding(bottom = bawahAwal + bars.bottom)
            insets
        }

        // Inset sistem ditangani sendiri, sama seperti halaman Kelola Layanan petugas
        val headerTop = binding.header.paddingTop
        val listBottom = binding.rvBookings.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.rvBookings.updatePadding(bottom = listBottom + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        adapter = BookingPetugasAdapter(
            allowStatusUpdate = true,
            onUpdateStatus = { booking, status -> updateBookingStatus(booking, status) }
        )
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
        listenBookings()
    }

    override fun onStop() {
        listener?.remove()
        listener = null
        super.onStop()
    }

    private fun listenBookings() {
        binding.progress.isVisible = true
        listener?.remove()
        listener = BookingRepository.listenAllBookings(
            onResult = { bookings ->
                adapter.submitList(bookings)
                binding.tvTotal.text = "${bookings.size} booking"
                binding.progress.isVisible = false
                binding.tvEmpty.isVisible = bookings.isEmpty()
            },
            onError = {
                binding.progress.isVisible = false
                binding.tvEmpty.isVisible = true
                toast("Gagal memuat booking")
            }
        )
    }

    private fun updateBookingStatus(booking: BookingRecord, status: BookingStatus) {
        if (booking.status == status) {
            toast("Status booking belum berubah")
            return
        }
        if (!updatingIds.add(booking.id)) return

        binding.progress.isVisible = true
        BookingRepository.updateStatus(
            bookingId = booking.id,
            status = status,
            onSuccess = {
                updatingIds.remove(booking.id)
                binding.progress.isVisible = false
                toast("Progress booking diperbarui")
            },
            onError = { error ->
                updatingIds.remove(booking.id)
                binding.progress.isVisible = false
                toast(error.message ?: "Gagal memperbarui status")
            }
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}