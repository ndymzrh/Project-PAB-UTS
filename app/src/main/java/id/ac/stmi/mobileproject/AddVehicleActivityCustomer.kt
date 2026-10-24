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

class AddVehicleActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityAddVehicleCustomerBinding

    private val brands = listOf(
        "Toyota", "Honda", "Daihatsu", "Suzuki", "Mitsubishi", "Nissan",
        "Mazda", "Hyundai", "Kia", "Wuling", "BMW", "Mercedes-Benz", "Lainnya"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddVehicleCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.actBrand.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, brands)
        )
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() = with(binding) {
        clearErrors()

        val brand = actBrand.text.toString().trim()
        val model = etModel.text.toString().trim()
        val year = etYear.text.toString().toIntOrNull()
        val color = etColor.text.toString().trim()
        val plate = etPlate.text.toString().trim().uppercase().replace(Regex("\\s+"), " ")
        val odometer = etOdometer.text.toString().toLongOrNull()
        val lastService = etLastService.text.toString().toLongOrNull() ?: 0L
        val interval = etInterval.text.toString().toLongOrNull()

        // ---- validasi ----
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

        val vehicle = Vehicle(
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

        setSaving(true)
        VehicleRepository.add(
            uid, vehicle,
            onSuccess = {
                toast(R.string.mobil_tersimpan)
                finish()
            },
            onError = {
                setSaving(false)
                toast(R.string.gagal_menyimpan)
            }
        )
    }

    private fun clearErrors() = with(binding) {
        listOf(tilBrand, tilModel, tilYear, tilPlate, tilOdometer, tilLastService, tilInterval)
            .forEach { it.error = null }
    }

    private fun setSaving(saving: Boolean) = with(binding) {
        btnSave.isEnabled = !saving
        progressSave.isVisible = saving
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
