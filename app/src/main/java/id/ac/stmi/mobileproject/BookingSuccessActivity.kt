package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBookingSuccessBinding

/**
 * Detail booking realtime. Judul, ikon, dan tombol mengikuti status booking:
 * baru dibuat -> "Booking berhasil dibuat", selesai -> "Servis selesai" + tombol invoice, dst.
 */
class BookingSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingSuccessBinding
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

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
        binding.btnInvoice.setOnClickListener {
            startActivity(
                Intent(this, InvoiceBookingActivity::class.java)
                    .putExtra(InvoiceBookingActivity.EXTRA_BOOKING_ID, bookingId)
            )
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
        renderHeading(booking.status)

        tvBookingId.text = booking.id
        tvVehicle.text = "${booking.vehicleName} • ${booking.plateNumber}"
        tvServices.text = booking.serviceNames
        tvWorkshop.text = booking.workshopName
        tvSchedule.text = booking.scheduleText
        tvTotal.text = booking.totalPriceText

        tvStatus.text = booking.status.label
        tvStatus.setTextColor(
            getColor(if (booking.status.isFailed()) R.color.bk_red_dot else R.color.bk_accent)
        )

        // Points: hilang bila booking ditolak/dibatalkan, "diperoleh" bila selesai
        val adaPoints = !booking.status.isFailed()
        tvEstimatedPoints.isVisible = adaPoints
        tvPointsStatus.isVisible = adaPoints
        tvEstimatedPoints.text = if (booking.status == BookingStatus.SELESAI &&
            booking.pointsStatus == PointsStatus.AWARDED
        ) "${booking.awardedPoints} points" else "${booking.estimatedPoints} points"
        tvPointsStatus.text = when (booking.pointsStatus) {
            PointsStatus.PENDING -> "Pending — masuk setelah servis selesai"
            PointsStatus.AWARDED -> "Sudah diberikan"
            PointsStatus.REVERSED -> "Dikoreksi"
            PointsStatus.NOT_ELIGIBLE -> "Tidak memenuhi syarat"
        }
        tvPointsStatus.setTextColor(
            getColor(if (booking.pointsStatus == PointsStatus.AWARDED) R.color.bk_primary else R.color.bk_text_secondary)
        )

        // Invoice hanya setelah servis selesai
        val selesai = booking.status == BookingStatus.SELESAI
        btnInvoice.isVisible = selesai
        btnViewPoints.isVisible = !booking.status.isFailed()
    }

    private fun renderHeading(status: BookingStatus) = with(binding) {
        val (judul, sub) = when (status) {
            BookingStatus.MENUNGGU_KONFIRMASI ->
                "Booking berhasil dibuat" to "Simpan ID booking ini untuk ditunjukkan ke petugas."
            BookingStatus.DIKONFIRMASI ->
                "Booking dikonfirmasi" to "Datang ke bengkel sesuai jadwal dan tunjukkan ID booking."
            BookingStatus.SEDANG_DISERVIS ->
                "Kendaraan sedang diservis" to "Status akan diperbarui otomatis saat servis selesai."
            BookingStatus.SELESAI ->
                "Servis selesai" to "Terima kasih! Invoice dan points Anda sudah tersedia."
            BookingStatus.DITOLAK ->
                "Booking ditolak" to "Maaf, booking ini tidak dapat diproses oleh bengkel."
            BookingStatus.DIBATALKAN ->
                "Booking dibatalkan" to "Booking ini telah dibatalkan."
        }
        tvHeading.text = judul
        tvSubheading.text = sub

        val gagal = status.isFailed()
        if (gagal) {
            // Ikon silang sudah berwarna sendiri (lingkaran merah + silang putih): tanpa tint/filter
            ivStatusIcon.setImageResource(R.drawable.ic_status_cross)
            ImageViewCompat.setImageTintList(ivStatusIcon, null)
            ivStatusIcon.clearColorFilter()
        } else {
            // Ikon centang diwarnai lewat tint
            ivStatusIcon.setImageResource(R.drawable.ic_svc_check_circle)
            ImageViewCompat.setImageTintList(
                ivStatusIcon,
                ColorStateList.valueOf(getColor(R.color.bk_primary))
            )
        }
    }

    private fun BookingStatus.isFailed() = this == BookingStatus.DITOLAK || this == BookingStatus.DIBATALKAN

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_BOOKING_ID = "booking_id"
    }
}