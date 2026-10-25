package id.ac.stmi.mobileproject

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import id.ac.stmi.mobileproject.databinding.ActivityAddVehicleCustomerBinding
import java.util.Calendar

/**
 * Halaman Edit Mobil. Memakai ulang layout form tambah mobil
 * (activity_add_vehicle_customer.xml), hanya judul/tombol dan isinya yang berbeda.
 */
class EditVehicleActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityAddVehicleCustomerBinding
    private var vehicle: Vehicle? = null

    private val brands = listOf(
        "Toyota", "Honda", "Daihatsu", "Suzuki", "Mitsubishi", "Nissan",
        "Mazda", "Hyundai", "Kia", "Wuling", "BMW", "Mercedes-Benz", "Lainnya"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddVehicleCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.setText(R.string.edit_mobil)
        binding.btnSave.setText(R.string.simpan_perubahan)
        binding.actBrand.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, brands)
        )
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val id = intent.getStringExtra(EXTRA_VEHICLE_ID)
        if (uid == null) {
            toast(R.string.belum_login)
            finish()
            return
        }
        if (id.isNullOrEmpty()) {
            toast(R.string.mobil_tidak_ditemukan)
            finish()
            return
        }

        setBusy(true)
        VehicleRepository.get(
            uid, id,
            onResult = { v ->
                setBusy(false)
                if (v == null) {
                    toast(R.string.mobil_tidak_ditemukan)
                    finish()
                } else {
                    vehicle = v
                    fillForm(v)
                }
            },
            onError = {
                setBusy(false)
                toast(R.string.gagal_memuat)
                finish()
            }
        )
    }

    private fun fillForm(v: Vehicle) = with(binding) {
        actBrand.setText(v.brand, false)   // false = jangan filter daftar dropdown
        etModel.setText(v.model)
        etYear.setText(if (v.year > 0) v.year.toString() else "")
        etColor.setText(v.color)
        etPlate.setText(v.plateNumber)
        etOdometer.setText(v.odometer.toString())
        etLastService.setText(v.lastServiceOdometer.toString())
        etInterval.setText(v.serviceIntervalKm.toString())

        chipTransmission.check(
            if (v.transmission == getString(R.string.otomatis)) R.id.chipAutomatic
            else R.id.chipManual
        )
        chipFuel.check(
            when (v.fuelType) {
                getString(R.string.diesel) -> R.id.chipDiesel
                getString(R.string.hybrid) -> R.id.chipHybrid
                getString(R.string.listrik) -> R.id.chipListrik
                else -> R.id.chipBensin
            }
        )

        // Mobil utama tidak bisa dimatikan dari sini, supaya selalu ada satu mobil utama.
        // Untuk menggantinya, jadikan mobil lain sebagai utama.
        switchPrimary.isChecked = v.primary
        if (v.primary) switchPrimary.isEnabled = false
    }

    private fun save() = with(binding) {
        val current = vehicle ?: return@with
        clearErrors()

        val brand = actBrand.text.toString().trim()
        val model = etModel.text.toString().trim()
        val year = etYear.text.toString().toIntOrNull()
        val color = etColor.text.toString().trim()
        val plate = etPlate.text.toString().trim().uppercase().replace(Regex("\\s+"), " ")
        val odometer = etOdometer.text.toString().toLongOrNull()
        val lastService = etLastService.text.toString().toLongOrNull() ?: 0L
        val interval = etInterval.text.toString().toLongOrNull()

        // ---- validasi (sama dengan form tambah) ----
        var valid = true
        fun fail(til: TextInputLayout, msg: Int) { til.error = getString(msg); valid = false }

        val maxYear = Calendar.getInstance().get(Calendar.YEAR) + 1
        if (brand.isEmpty()) fail(tilBrand, R.string.error_wajib)
        if (model.isEmpty()) fail(tilModel, R.string.error_wajib)
        if (year == null) fail(tilYear, R.string.error_wajib)
        else if (year !in 1980..maxYear) fail(tilYear, R.string.error_tahun)
        if (plate.isEmpty()) fail(tilPlate, R.string.error_wajib)
        if (odometer == null) fail(tilOdometer, R.string.error_wajib)
        else if (lastService > odometer) fail(tilLastService, R.string.error_km_servis)
        if (interval == null || interval <= 0) fail(tilInterval, R.string.error_interval)
        if (!valid) return@with

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            toast(R.string.belum_login)
            return@with
        }

        val updated = current.copy(
            brand = brand,
            model = model,
            year = year!!,
            plateNumber = plate,
            odometer = odometer!!,
            lastServiceOdometer = lastService,
            serviceIntervalKm = interval!!,
            primary = switchPrimary.isChecked,
            color = color,
            transmission = if (chipTransmission.checkedChipId == R.id.chipAutomatic)
                getString(R.string.otomatis) else getString(R.string.manual),
            fuelType = when (chipFuel.checkedChipId) {
                R.id.chipDiesel -> getString(R.string.diesel)
                R.id.chipHybrid -> getString(R.string.hybrid)
                R.id.chipListrik -> getString(R.string.listrik)
                else -> getString(R.string.bensin)
            }
        )

        setBusy(true)
        VehicleRepository.update(
            uid, updated,
            onSuccess = {
                toast(R.string.perubahan_tersimpan)
                finish()
            },
            onError = {
                setBusy(false)
                toast(R.string.gagal_menyimpan)
            }
        )
    }

    private fun clearErrors() = with(binding) {
        listOf(tilBrand, tilModel, tilYear, tilPlate, tilOdometer, tilLastService, tilInterval)
            .forEach { it.error = null }
    }

    private fun setBusy(busy: Boolean) = with(binding) {
        btnSave.isEnabled = !busy
        progressSave.isVisible = busy
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
