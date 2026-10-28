package id.ac.stmi.mobileproject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import id.ac.stmi.mobileproject.databinding.ActivityVoucherSayaBinding
import kotlin.jvm.java

class VoucherSayaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoucherSayaBinding
    private lateinit var voucherAdapter: VoucherAdapter
    private var voucherListener: PromoListener? = null
    private var semuaVoucher: List<VoucherSaya> = emptyList()
    private var selectedTab = 0 // 0: Tersedia, 1: Terpakai, 2: Kedaluwarsa

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoucherSayaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        voucherAdapter = VoucherAdapter(
            onSalin = { v ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Kode Voucher", v.kode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Kode ${v.kode} disalin", Toast.LENGTH_SHORT).show()
            },
            onPakai = { v ->
                // Pilih layanan dulu; voucher dibawa sebagai extra
                val intent = Intent(this, LayananActivity::class.java).apply {
                    putExtra(EXTRA_VOUCHER_KODE, v.kode)
                    putExtra(EXTRA_VOUCHER_JUDUL, v.judul)
                }
                startActivity(intent)
            }
        )

        binding.rvVouchers.layoutManager = LinearLayoutManager(this)
        binding.rvVouchers.adapter = voucherAdapter

        binding.tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTab = tab.position
                render()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun onStart() {
        super.onStart()
        voucherListener = VoucherStore.listen(
            this,
            onError = {
                Toast.makeText(this, "Gagal memuat voucher", Toast.LENGTH_SHORT).show()
            },
            onChange = { list ->
                semuaVoucher = list
                render()
            }
        )
    }

    override fun onStop() {
        super.onStop()
        voucherListener?.remove()
        voucherListener = null
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val filtered = when (selectedTab) {
            0 -> semuaVoucher.filter { it.status(now) == StatusVoucher.TERSEDIA }
            1 -> semuaVoucher.filter { it.status(now) == StatusVoucher.DIPAKAI }
            2 -> semuaVoucher.filter { it.status(now) == StatusVoucher.KEDALUWARSA }
            else -> semuaVoucher
        }

        val totalTersedia = semuaVoucher.count { it.status(now) == StatusVoucher.TERSEDIA }
        binding.tvTotalVoucher.text = "$totalTersedia voucher"

        voucherAdapter.submitList(filtered)
        binding.rvVouchers.isVisible = filtered.isNotEmpty()
        binding.tvKosong.isVisible = filtered.isEmpty()
    }

    companion object {
        const val EXTRA_VOUCHER_KODE = "EXTRA_VOUCHER_KODE"
        const val EXTRA_VOUCHER_JUDUL = "EXTRA_VOUCHER_JUDUL"
    }
}