package id.ac.stmi.mobileproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemBengkelPetugasBinding
import java.util.Locale

/** Daftar bengkel untuk petugas: menampilkan SEMUA bengkel, termasuk yang nonaktif. */
class BengkelPetugasAdapter(
    private val onEdit: (Bengkel) -> Unit,
    private val onToggleAktif: (Bengkel, Boolean) -> Unit
) : RecyclerView.Adapter<BengkelPetugasAdapter.VH>() {

    private var data: List<Bengkel> = emptyList()

    fun submit(list: List<Bengkel>) {
        data = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemBengkelPetugasBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBengkelPetugasBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = data[position]
        val v = holder.b
        val ctx = v.root.context

        v.tvNama.text = b.nama
        v.tvAlamat.text = b.alamat.ifBlank { "Alamat belum diisi" }

        val info = listOf(b.telepon, b.jamOperasional).filter { it.isNotBlank() }.joinToString(" • ")
        v.tvInfo.text = info
        v.tvInfo.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE

        // Dokumen tanpa lokasi (mis. dibuat manual di console) ditandai merah
        val punyaLokasi = !(b.lat == 0.0 && b.lng == 0.0)
        v.tvLokasi.text = if (punyaLokasi)
            String.format(Locale.US, "Lat %.5f, Lng %.5f", b.lat, b.lng)
        else "Lokasi belum diatur"
        v.tvLokasi.setTextColor(
            ContextCompat.getColor(ctx, if (punyaLokasi) R.color.bk_text_secondary else R.color.bk_red_dot)
        )

        // Lepas listener dulu agar setChecked tidak memicu pembaruan ke Firestore
        v.swAktif.setOnCheckedChangeListener(null)
        v.swAktif.isChecked = b.aktif
        v.swAktif.setOnCheckedChangeListener { _, checked -> onToggleAktif(b, checked) }

        v.btnEdit.setOnClickListener { onEdit(b) }
    }

    override fun getItemCount() = data.size
}