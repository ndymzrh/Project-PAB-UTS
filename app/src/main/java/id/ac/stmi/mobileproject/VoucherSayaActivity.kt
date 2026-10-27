
package id.ac.stmi.mobileproject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout

class VoucherSayaActivity : AppCompatActivity() {

    private var filter = StatusVoucher.TERSEDIA
    private var semua: List<VoucherSaya> = emptyList()
    private var listener: PromoListener? = null

    private lateinit var adapter: VoucherAdapter
    private lateinit var tvKosong: LinearLayout
    private lateinit var tvPesanKosong: TextView
    private lateinit var tvTotalVoucher: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voucher_saya)

        adapter = VoucherAdapter(
            onSalin = { voucher ->
                salin(voucher.kode)
                toast("Kode ${voucher.kode} disalin")
            },
            onPakai = { voucher ->
                salin(voucher.kode)
                toast("Kode disalin, tempel di halaman Booking")
            }
        )

        // Toolbar
        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener {
                finish()
            }

        tvTotalVoucher = findViewById(R.id.tvTotalVoucher)

        // Daftar voucher
        findViewById<RecyclerView>(R.id.rvVouchers).apply {
            layoutManager = LinearLayoutManager(this@VoucherSayaActivity)
            adapter = this@VoucherSayaActivity.adapter
        }

        // Tampilan ketika daftar voucher kosong
        tvKosong = findViewById(R.id.tvKosong)
        tvPesanKosong = tvKosong.getChildAt(1) as TextView

        // Tab filter: Tersedia, Terpakai, Kedaluwarsa
        val tabFilter = findViewById<TabLayout>(R.id.tabFilter)

        tabFilter.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab) {
                    filter = StatusVoucher.values().getOrElse(tab.position) {
                        StatusVoucher.TERSEDIA
                    }
                    render()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            }
        )
    }

    // Memuat voucher secara realtime dari Firestore
    override fun onStart() {
        super.onStart()

        listener = VoucherStore.listen(
            this,
            onError = {
                toast("Gagal memuat voucher")
            }
        ) { daftar ->
            semua = daftar
            render()
        }
    }

    override fun onStop() {
        listener?.remove()
        listener = null
        super.onStop()
    }

    private fun render() {
        val daftar = semua.filter { it.status() == filter }

        adapter.submitList(daftar)

        tvTotalVoucher.text =
            "${VoucherStore.jumlahTersedia(semua)} voucher"

        tvPesanKosong.text = when (filter) {
            StatusVoucher.TERSEDIA ->
                "Belum ada voucher tersedia.\nKlaim promo di halaman Promo."

            StatusVoucher.DIPAKAI ->
                "Belum ada voucher yang terpakai."

            StatusVoucher.KEDALUWARSA ->
                "Tidak ada voucher kedaluwarsa."
        }

        tvKosong.visibility =
            if (daftar.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun salin(teks: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("Kode voucher", teks)
        )
    }

    private fun toast(pesan: String) {
        Toast.makeText(
            this,
            pesan,
            Toast.LENGTH_SHORT
        ).show()
    }
}
