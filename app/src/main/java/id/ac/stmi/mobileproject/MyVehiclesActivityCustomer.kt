package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityMyVehiclesCustomerBinding

class MyVehiclesActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityMyVehiclesCustomerBinding
    private val adapter = VehicleAdapter { confirmDelete(it) }
    private var registration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyVehiclesCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvVehicles.layoutManager = LinearLayoutManager(this)
        binding.rvVehicles.adapter = adapter

        val goAdd = {
            startActivity(Intent(this, AddVehicleActivityCustomer::class.java))
        }
        binding.btnBack.setOnClickListener { finish() }
        binding.fabAddVehicle.setOnClickListener { goAdd() }
        binding.btnAddFirstVehicle.setOnClickListener { goAdd() }
    }

    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            toast(R.string.belum_login)
            finish()
            return
        }
        showLoading()
        registration = VehicleRepository.listen(
            uid,
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

    private fun showLoading() = with(binding) {
        progressLoading.isVisible = true
        rvVehicles.isVisible = false
        layoutEmpty.isVisible = false
    }

    private fun render(list: List<Vehicle>) = with(binding) {
        progressLoading.isVisible = false
        tvVehicleCount.text = getString(R.string.jumlah_mobil, list.size)
        val empty = list.isEmpty()
        layoutEmpty.isVisible = empty
        rvVehicles.isVisible = !empty
        fabAddVehicle.isVisible = !empty   // saat kosong, tombol ada di tengah layar
        adapter.submitList(list)
    }

    private fun confirmDelete(v: Vehicle) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hapus_mobil_judul)
            .setMessage(getString(R.string.hapus_mobil_pesan, v.displayName))
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.hapus) { _, _ ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                VehicleRepository.delete(
                    uid, v,
                    onSuccess = { toast(R.string.mobil_terhapus) },
                    onError = { toast(R.string.gagal_menyimpan) }
                )
            }
            .show()
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
