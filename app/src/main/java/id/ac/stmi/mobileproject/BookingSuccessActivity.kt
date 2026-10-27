package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBookingSuccessBinding

class BookingSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingSuccessBinding
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookingId = intent.getStringExtra(EXTRA_BOOKING_ID)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (bookingId.isNullOrBlank() || uid.isNullOrBlank()) {
            toast("Data booking tidak ditemukan")
            finish()
            return
        }

        binding.btnBackHome.setOnClickListener {
            startActivity(
                Intent(this, HomeActivityCustomer::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
        binding.btnViewPoints.setOnClickListener {
            startActivity(Intent(this, RiwayatPoinCustActivity::class.java))
        }

        listener = BookingRepository.listenOne(
            uid = uid,
            bookingId = bookingId,
            onResult = { booking ->
                binding.progress.isVisible = false
                if (booking == null) {
                    toast("Booking tidak ditemukan")
                    return@listenOne
                }
                render(booking)
            },
            onError = {
                binding.progress.isVisible = false
                toast("Gagal memuat detail booking")
            }
        )
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun render(booking: BookingRecord) = with(binding) {
        tvBookingId.text = booking.id
        tvVehicle.text = "${booking.vehicleName} • ${booking.plateNumber}"
        tvServices.text = booking.serviceNames
        tvWorkshop.text = booking.workshopName
        tvSchedule.text = booking.scheduleText
        tvTotal.text = booking.totalPriceText
        tvEstimatedPoints.text = "${booking.estimatedPoints} points"
        tvStatus.text = booking.status.label
        tvPointsStatus.text = when (booking.pointsStatus) {
            PointsStatus.PENDING -> "Pending — masuk setelah servis selesai"
            PointsStatus.AWARDED -> "Sudah diberikan"
            PointsStatus.REVERSED -> "Dikoreksi"
            PointsStatus.NOT_ELIGIBLE -> "Tidak memenuhi syarat"
        }
        tvPointsStatus.setTextColor(
            getColor(if (booking.pointsStatus == PointsStatus.AWARDED) R.color.bk_primary else R.color.bk_text_secondary)
        )
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_BOOKING_ID = "booking_id"
    }
}
