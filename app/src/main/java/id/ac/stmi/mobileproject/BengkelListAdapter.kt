package id.ac.stmi.mobileproject

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemBengkelListBinding

/** Daftar bengkel vertikal untuk pelanggan (jarak, navigasi, dan mode pilih untuk booking). */
class BengkelListAdapter(
    private val modePilih: Boolean,
    private val onNavigasi: (Bengkel) -> Unit,
    private val onPilih: (Bengkel) -> Unit
) : RecyclerView.Adapter<BengkelListAdapter.VH>() {

    private var data: List<Bengkel> = emptyList()
    private var lokasi: Location? = null

    fun submit(list: List<Bengkel>, lokasiSaya: Location?) {
        data = list
        lokasi = lokasiSaya
        notifyDataSetChanged()
    }

    class VH(val b: ItemBengkelListBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBengkelListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = data[position]
        val v = holder.b

        v.tvNama.text = b.nama

        v.tvJam.text = b.jamOperasional
        v.tvJam.visibility = if (b.jamOperasional.isBlank()) View.GONE else View.VISIBLE

        v.tvAlamat.text = b.alamat.ifBlank { "Alamat belum diisi" }

        v.panelTelepon.visibility = if (b.telepon.isBlank()) View.GONE else View.VISIBLE
        v.tvTelepon.text = "Telepon: ${b.telepon}"

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