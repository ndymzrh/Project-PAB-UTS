package id.ac.stmi.mobileproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemBookingCustomerBinding

class BookingListAdapter(
    private val onClick: (BookingRecord) -> Unit
) : ListAdapter<BookingRecord, BookingListAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemBookingCustomerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemBookingCustomerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val booking = getItem(position)
        with(holder.binding) {
            tvBookingId.text = "ID ${booking.id}"
            tvBookingTitle.text = booking.serviceNames.ifBlank { "Booking servis" }
            tvWorkshop.text = booking.workshopName
            tvSchedule.text = booking.scheduleText
            tvVehicle.text = "${booking.vehicleName} - ${booking.plateNumber}"
            tvTotal.text = booking.totalPriceText
            tvPoints.text = when (booking.status) {
                BookingStatus.SELESAI -> when (booking.pointsStatus) {
                    PointsStatus.AWARDED -> "Diperoleh ${booking.awardedPoints} points"
                    PointsStatus.NOT_ELIGIBLE -> "Points tidak tersedia"
                    PointsStatus.REVERSED -> "Points dikoreksi"
                    PointsStatus.PENDING -> "Menunggu pencatatan points"
                }
                else -> "Estimasi ${booking.estimatedPoints} points"
            }
            tvStatus.text = booking.status.label
            tvStatus.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    when (booking.status) {
                        BookingStatus.SEDANG_DISERVIS -> R.color.bk_accent
                        else -> R.color.bk_primary
                    }
                )
            )
            progressStatus.progress = when (booking.status) {
                BookingStatus.MENUNGGU_KONFIRMASI -> 25
                BookingStatus.DIKONFIRMASI -> 50
                BookingStatus.SEDANG_DISERVIS -> 75
                BookingStatus.SELESAI -> 100
                else -> 0
            }
            root.setOnClickListener { onClick(booking) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<BookingRecord>() {
        override fun areItemsTheSame(oldItem: BookingRecord, newItem: BookingRecord) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BookingRecord, newItem: BookingRecord) =
            oldItem == newItem
    }
}