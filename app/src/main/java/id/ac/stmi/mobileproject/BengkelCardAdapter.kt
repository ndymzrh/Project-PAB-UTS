package id.ac.stmi.mobileproject

import android.content.Context
import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemBengkelCardBinding

class BengkelCardAdapter(
    private val modePilih: Boolean,
    private val onNavigasi: (Bengkel) -> Unit,
    private val onPilih: (Bengkel) -> Unit
) : RecyclerView.Adapter<BengkelCardAdapter.VH>() {

    companion object {
        /** Lebar kartu = 86% lebar layar; dipakai juga untuk menghitung padding RecyclerView. */
        fun lebarKartu(ctx: Context): Int =
            (ctx.resources.displayMetrics.widthPixels * 0.86f).toInt()

        fun marginKartu(ctx: Context): Int =
            (5 * ctx.resources.displayMetrics.density).toInt()
    }

    private var data: List<Bengkel> = emptyList()
    private var lokasi: Location? = null

    fun submit(list: List<Bengkel>, lokasiSaya: Location?) {
        data = list
        lokasi = lokasiSaya
        notifyDataSetChanged()
    }

    class VH(val b: ItemBengkelCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(ItemBengkelCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        val m = marginKartu(parent.context)
        vh.b.root.layoutParams = RecyclerView.LayoutParams(
            lebarKartu(parent.context), RecyclerView.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(m, 0, m, 0) }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = data[position]
        val v = holder.b

        v.tvNama.text = b.nama

        v.tvJam.text = b.jamOperasional
        v.tvJam.visibility = if (b.jamOperasional.isBlank()) View.GONE else View.VISIBLE

        v.tvAlamat.text = b.alamat.ifBlank { "Alamat belum diisi" }

        v.tvTelepon.text = b.telepon
        v.tvTelepon.visibility = if (b.telepon.isBlank()) View.GONE else View.VISIBLE

        val l = lokasi
        if (l != null) {
            v.tvJarak.text = Jarak.format(Jarak.meter(l.latitude, l.longitude, b.lat, b.lng))
            v.tvJarak.visibility = View.VISIBLE
        } else {
            v.tvJarak.visibility = View.GONE
        }

        v.btnNavigasi.setOnClickListener { onNavigasi(b) }

        v.btnPilih.visibility = if (modePilih) View.VISIBLE else View.GONE
        v.btnPilih.setOnClickListener { onPilih(b) }
    }

    override fun getItemCount() = data.size
}