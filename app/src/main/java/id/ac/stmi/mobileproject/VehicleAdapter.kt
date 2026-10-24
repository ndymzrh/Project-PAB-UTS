package id.ac.stmi.mobileproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemVehicleBinding
import java.text.NumberFormat
import java.util.Locale

class VehicleAdapter(
    private val onDelete: (Vehicle) -> Unit
) : ListAdapter<Vehicle, VehicleAdapter.VH>(Diff) {

    class VH(val b: ItemVehicleBinding) : RecyclerView.ViewHolder(b.root)

    private val nf = NumberFormat.getInstance(Locale("id", "ID"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVehicleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = getItem(position)
        with(holder.b) {
            tvVehicleName.text = v.displayName

            val spec = listOf(v.transmission, v.fuelType, v.color)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            tvVehicleSpec.text = spec
            tvVehicleSpec.isVisible = spec.isNotEmpty()

            tvPlate.text = v.plateNumber
            tvPrimaryBadge.isVisible = v.primary

            // progress servis berikutnya: jarak tempuh sejak servis terakhir
            val interval = v.serviceIntervalKm.coerceAtLeast(1L)
            val used = (v.odometer - v.lastServiceOdometer).coerceIn(0L, interval)
            progressService.max = interval.toInt()
            progressService.progress = used.toInt()
            tvServiceProgress.text = "${nf.format(used)}/${nf.format(interval)} km"

            tvOdometer.text = "Odometer: ${nf.format(v.odometer)} km"

            btnDelete.setOnClickListener { onDelete(v) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Vehicle>() {
        override fun areItemsTheSame(a: Vehicle, b: Vehicle) = a.id == b.id
        override fun areContentsTheSame(a: Vehicle, b: Vehicle) = a == b
    }
}
