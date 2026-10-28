package id.ac.stmi.mobileproject

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemBookingPetugasBinding

class BookingPetugasAdapter(
    private val allowStatusUpdate: Boolean,
    private val onUpdateStatus: (BookingRecord, BookingStatus) -> Unit
) : ListAdapter<BookingRecord, BookingPetugasAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemBookingPetugasBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemBookingPetugasBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val booking = getItem(position)
        with(holder.binding) {
            tvBookingId.text = "ID ${booking.id}"
            tvCustomer.text = "Customer: ${booking.customerName.ifBlank { booking.customerId }}"
            tvBookingTitle.text = booking.serviceNames.ifBlank { "Booking servis" }
            tvWorkshop.text = booking.workshopName
            tvSchedule.text = booking.scheduleText
            tvVehicle.text = "${booking.vehicleName} - ${booking.plateNumber}"
            tvTotal.text = booking.totalPriceText
            tvPoints.text = "Estimasi ${booking.estimatedPoints} points"
            tvStatus.text = booking.status.label
            tvStatus.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    when (booking.status) {
                        BookingStatus.SEDANG_DISERVIS -> R.color.bk_accent
                        BookingStatus.DITOLAK, BookingStatus.DIBATALKAN -> R.color.bk_red_dot
                        else -> R.color.bk_primary
                    }
                )
            )
            progressStatus.progress = when (booking.status) {
                BookingStatus.MENUNGGU_KONFIRMASI -> 25
                BookingStatus.DIKONFIRMASI -> 50
                BookingStatus.SEDANG_DISERVIS -> 75
                BookingStatus.SELESAI -> 100
                BookingStatus.DITOLAK, BookingStatus.DIBATALKAN -> 100
            }

            // Status akhir (selesai/ditolak/dibatalkan): form ubah status disembunyikan
            val bisaUbah = allowStatusUpdate && !booking.status.isFinal
            statusLayout.isVisible = bisaUbah
            btnUpdateStatus.isVisible = bisaUbah
            if (bisaUbah) {
                val statuses = BookingStatus.values()
                val labels = statuses.map { it.label }
                statusInput.setAdapter(
                    ArrayAdapter(
                        root.context,
                        R.layout.item_dropdown,
                        labels
                    )
                )
                statusInput.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup)
                var selectedStatus = booking.status
                statusInput.setText(selectedStatus.label, false)
                statusInput.setOnItemClickListener { _, _, selectedPosition, _ ->
                    selectedStatus = statuses[selectedPosition]
                }
                btnUpdateStatus.setOnClickListener {
                    onUpdateStatus(booking, selectedStatus)
                }
            } else {
                statusInput.setOnItemClickListener(null)
                btnUpdateStatus.setOnClickListener(null)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<BookingRecord>() {
        override fun areItemsTheSame(oldItem: BookingRecord, newItem: BookingRecord) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BookingRecord, newItem: BookingRecord) =
            oldItem == newItem
    }
}