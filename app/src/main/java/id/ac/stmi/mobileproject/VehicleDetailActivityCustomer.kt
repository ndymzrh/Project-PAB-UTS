package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityVehicleDetailCustomerBinding
import java.text.NumberFormat
import java.util.Locale

class VehicleDetailActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityVehicleDetailCustomerBinding
    private val nf = NumberFormat.getIntegerInstance(Locale("id", "ID"))

    private var vehicleId: String = ""
    private var vehicle: Vehicle? = null
    private var registration: ListenerRegistration? = null
    private var deleting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVehicleDetailCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID).orEmpty()
        if (vehicleId.isEmpty()) {
            toast(R.string.mobil_tidak_ditemukan)
            finish()
            return
        }

        // Tampilkan data yang sudah ada di memori (dari daftar) langsung, tanpa spinner.
        VehicleRepository.cached(vehicleId)?.let { render(it) }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEdit.setOnClickListener { openEdit() }
        binding.btnEditVehicle.setOnClickListener { openEdit() }
        binding.btnDeleteVehicle.setOnClickListener { confirmDelete() }
    }

    // Listener realtime: setelah kembali dari halaman Edit, data langsung segar.
    override fun onStart() {
        super.onStart()
        if (vehicleId.isEmpty()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            toast(R.string.belum_login)
            finish()
            return
        }
        registration = VehicleRepository.listenOne(
            uid, vehicleId,
            onResult = ::render,
            onError = {
                binding.progressLoading.isVisible = false
                toast(R.string.gagal_memuat)
            }
        )
    }

    override fun onStop() {
        super.onStop()
        registration?.remove()
        registration = null
    }

    private fun render(v: Vehicle?) = with(binding) {
        progressLoading.isVisible = false
        if (v == null) {
            // dokumen sudah tidak ada (mis. baru dihapus)
            if (!deleting) toast(R.string.mobil_tidak_ditemukan)
            finish()
            return@with
        }
        vehicle = v
        scrollContent.isVisible = true

        // ---- header ----
        tvDetailName.text = v.displayName
        val spec = listOf(v.transmission, v.fuelType, v.color)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        tvDetailSpec.text = spec
        tvDetailSpec.isVisible = spec.isNotEmpty()
        tvPlate.text = v.plateNumber
        tvPrimaryBadge.isVisible = v.primary

        // ---- hitungan servis ----
        val interval = v.serviceIntervalKm.coerceAtLeast(1L)
        val used = (v.odometer - v.lastServiceOdometer).coerceAtLeast(0L)
        val nextAt = v.lastServiceOdometer + interval
        val remaining = nextAt - v.odometer

        tvOdometerValue.text = "${nf.format(v.odometer)} km"
        tvLastService.text = "${nf.format(v.lastServiceOdometer)} km"
        tvInterval.text = "${nf.format(interval)} km"
        tvNextService.text = "${nf.format(nextAt)} km"
        tvRemaining.text = if (remaining > 0)
            getString(R.string.sisa_km_lagi, nf.format(remaining))
        else
            getString(R.string.lewat_km, nf.format(-remaining))

        progressService.max = interval.toInt()
        progressService.progress = used.coerceAtMost(interval).toInt()
        tvServiceUsed.text =
            "${nf.format(used.coerceAtMost(interval))}/${nf.format(interval)} km sejak servis terakhir"

        // ---- status: aman / segera / terlambat ----
        val (label, colorRes) = when {
            remaining <= 0 -> R.string.status_terlambat to R.color.bk_red_dot
            remaining <= interval / 10 -> R.string.status_segera to R.color.bk_accent
            else -> R.string.status_aman to R.color.bk_primary
        }
        val color = ContextCompat.getColor(this@VehicleDetailActivityCustomer, colorRes)
        tvServiceStatus.setText(label)
        tvServiceStatus.setTextColor(color)
        progressService.setIndicatorColor(color)
        tvRemaining.setTextColor(color)
    }

    private fun openEdit() {
        startActivity(
            Intent(this, EditVehicleActivityCustomer::class.java)
                .putExtra(EXTRA_VEHICLE_ID, vehicleId)
        )
    }

    private fun confirmDelete() {
        val v = vehicle ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hapus_mobil_judul)
            .setMessage(getString(R.string.hapus_mobil_pesan, v.displayName))
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.hapus) { _, _ ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@setPositiveButton
                deleting = true
                VehicleRepository.delete(
                    uid, v,
                    onSuccess = {
                        toast(R.string.mobil_terhapus)
                        finish()
                    },
                    onError = {
                        deleting = false
                        toast(R.string.gagal_menyimpan)
                    }
                )
            }
            .show()
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}