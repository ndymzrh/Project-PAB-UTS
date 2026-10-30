package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Halaman akun petugas / pemilik (Firebase Auth + Firestore realtime).
 */
class AccountPetugasActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AccountPetugas"
        private const val PACKAGE = "id.ac.stmi.mobileproject"

        private const val COL_USERS = "users"
        private const val COL_PETUGAS = "petugas"
        private const val COL_BOOKING = "bookings"

        private const val FIELD_PETUGAS = "petugasId"
        private val STATUS_SELESAI = listOf("SELESAI", "DISERAHKAN")

        private const val HALAMAN_ANTREAN = "BookingPetugasActivity"
        private const val HALAMAN_BANTUAN = "BantuanPetugasActivity"
        private const val HALAMAN_LAPORAN_PEMILIK = "LaporanPendapatanActivity"
        private const val HALAMAN_EDIT_PROFIL = "EditProfilPetugasActivity"
        private const val HALAMAN_HOME = "HomeActivityPetugas"
        private const val HALAMAN_LOGIN = "LoginActivity"
    }

    private data class MenuItem(
        val icon: Int,
        val judul: String,
        val sub: String,
        val aksi: () -> Unit
    )

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val listeners = mutableListOf<ListenerRegistration>()
    private var regServis: ListenerRegistration? = null
    private var regKonfirmasi: ListenerRegistration? = null

    private var isPemilik = false
    private var namaPetugas: String? = null // petugas/{uid}.nama (diubah lewat Edit Profil)
    private var namaUsers: String? = null   // users/{uid}.nama (data awal saat daftar)
    private var roleDimuat = false

    private lateinit var tvAvatarPetugas: TextView
    private lateinit var tvStaffName: TextView
    private lateinit var tvNip: TextView
    private lateinit var tvBranch: TextView
    private lateinit var tvUid: TextView
    private lateinit var tvStatusAktif: TextView
    private lateinit var tvRoleChip: TextView
    private lateinit var tvServisCount: TextView
    private lateinit var llStatServis: View
    private lateinit var tvTerakhirKonfirmasi: TextView
    private lateinit var tvTerakhirSelesai: TextView
    private lateinit var tvTelepon: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvBergabung: TextView
    private lateinit var btnEditProfilTop: View
    private lateinit var llMenuContainer: LinearLayout
    private lateinit var menuLogout: View
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_petugas)

        bindViews()
        setupTopBar(showNotificationDot = false) // Mengikuti HomeActivityPetugas
        buildMenu()
        setupClicks()
        setupBottomNav()
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            keLogin()
            return
        }
        listenProfil(uid)
    }

    override fun onStop() {
        regServis?.remove()
        regServis = null
        regKonfirmasi?.remove()
        regKonfirmasi = null
        listeners.forEach { it.remove() }
        listeners.clear()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (bottomNav.selectedItemId != R.id.nav_account) {
            bottomNav.selectedItemId = R.id.nav_account
        }
    }

    // ---------------------------------------------------------------- UI setup

    private fun bindViews() {
        tvAvatarPetugas = findViewById(R.id.tvAvatarPetugas)
        tvStaffName = findViewById(R.id.tvStaffName)
        tvNip = findViewById(R.id.tvNip)
        tvBranch = findViewById(R.id.tvBranch)
        tvUid = findViewById(R.id.tvUid)
        tvStatusAktif = findViewById(R.id.tvStatusAktif)
        tvRoleChip = findViewById(R.id.tvRoleChip)
        tvServisCount = findViewById(R.id.tvServisCount)
        tvTerakhirKonfirmasi = findViewById(R.id.tvTerakhirKonfirmasi)
        tvTerakhirSelesai = findViewById(R.id.tvTerakhirSelesai)
        tvTelepon = findViewById(R.id.tvTelepon)
        tvEmail = findViewById(R.id.tvEmail)
        tvBergabung = findViewById(R.id.tvBergabung)
        llStatServis = findViewById(R.id.llStatServis)
        llStatServis.visibility = View.GONE // tampil hanya untuk petugas, setelah role terbaca dari Firebase
        btnEditProfilTop = findViewById(R.id.btnEditProfilTop)
        llMenuContainer = findViewById(R.id.llMenuContainer)
        menuLogout = findViewById(R.id.menuLogout)
        bottomNav = findViewById(R.id.bottomNav)
    }

    private fun buildMenu() {
        val items = mutableListOf<MenuItem>()

        if (isPemilik) {
            items += MenuItem(R.drawable.ic_history, "Laporan Pendapatan",
                "Pilih rentang tanggal, unduh CSV & PDF") { bukaHalaman(HALAMAN_LAPORAN_PEMILIK) }
        }
        items += MenuItem(R.drawable.ic_help_box, "Bantuan Operasional",
            "Hubungi kepala bengkel & laporkan kendala") { bukaHalaman(HALAMAN_BANTUAN) }

        val inflater = LayoutInflater.from(this)
        llMenuContainer.removeAllViews()
        items.forEachIndexed { i, m ->
            val row = inflater.inflate(R.layout.item_menu_petugas, llMenuContainer, false)
            row.findViewById<ImageView>(R.id.ivIcon).setImageResource(m.icon)
            row.findViewById<TextView>(R.id.tvTitle).text = m.judul
            row.findViewById<TextView>(R.id.tvSubtitle).text = m.sub
            row.setOnClickListener { m.aksi() }
            llMenuContainer.addView(row)

            if (i < items.lastIndex) {
                llMenuContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1).apply {
                        marginStart = (62 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundColor(ContextCompat.getColor(this@AccountPetugasActivity, R.color.bk_stroke_soft))
                })
            }
        }
    }

    private fun setupClicks() {
        btnEditProfilTop.setOnClickListener { bukaHalaman(HALAMAN_EDIT_PROFIL) }
        menuLogout.setOnClickListener { konfirmasiLogout() }
    }

    private fun setupBottomNav() {
        bottomNav.selectedItemId = R.id.nav_account
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == bottomNav.selectedItemId) {
                return@setOnItemSelectedListener true
            }

            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, Class.forName("$PACKAGE.$HALAMAN_HOME")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    false
                }
                R.id.nav_booking -> {
                    val intent = Intent(this, Class.forName("$PACKAGE.$HALAMAN_ANTREAN")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    false
                }
                R.id.nav_servis -> {
                    toast("Kelola servis")
                    bottomNav.post { bottomNav.selectedItemId = R.id.nav_account }
                    false
                }
                R.id.nav_account -> true
                else -> false
            }
        }
    }

    private fun bukaHalaman(namaClass: String) {
        try {
            startActivity(Intent(this, Class.forName("$PACKAGE.$namaClass")))
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Activity $namaClass tidak ditemukan", e)
            toast("Halaman $namaClass belum tersedia")
        }
    }

    private fun keLogin() {
        val intent = Intent(this, Class.forName("$PACKAGE.$HALAMAN_LOGIN")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun konfirmasiLogout() {
        AlertDialog.Builder(this)
            .setTitle("Keluar Akun")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                auth.signOut()
                keLogin()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------------------------------------------------------------- Firebase

    private fun listenProfil(uid: String) {
        val user = auth.currentUser
        tvUid.text = "${uid.take(4)}…${uid.takeLast(4)}"
        tvEmail.text = user?.email?.takeIf { it.isNotBlank() } ?: "-"
        tvBergabung.text = user?.metadata?.creationTimestamp?.let {
            SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date(it))
        } ?: "-"
        isiProfil(null, null, null, null, true, user?.displayName ?: user?.email)

        listeners += db.collection(COL_USERS).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Gagal membaca role", err)
                    return@addSnapshotListener
                }
                namaUsers = snap?.getString("nama") ?: snap?.getString("name")
                if (namaPetugas.isNullOrBlank()) tampilkanNama(namaUsers ?: user?.displayName ?: user?.email)
                terapkanRole(snap?.getString("role"), uid)
            }

        listeners += db.collection(COL_PETUGAS).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Gagal membaca profil petugas", err)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                namaPetugas = snap.getString("nama")
                isiProfil(
                    snap.getString("nama"), snap.getString("nip"),
                    snap.getString("cabang"),
                    snap.getString("telepon"),
                    snap.getBoolean("aktif") ?: true,
                    namaUsers ?: user?.displayName ?: user?.email
                )
            }
    }

    private fun tampilkanNama(nama: String?) {
        val n = nama?.takeIf { it.isNotBlank() } ?: "Petugas"
        tvStaffName.text = n
        tvAvatarPetugas.text = n.trim().take(1).uppercase().ifEmpty { "P" }
    }

    private val formatWaktu by lazy { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")) }

    private fun waktuText(d: Date?): String = d?.let { formatWaktu.format(it) } ?: "-"

    /**
     * Statistik petugas:
     * - Total servis selesai yang ditangani + waktu servis terakhir diselesaikan (petugasId, completedAt)
     * - Waktu terakhir mengonfirmasi booking (confirmedBy, confirmedAt)
     * Hanya filter 1 field di server (tanpa composite index); status disaring di aplikasi.
     */
    private fun listenServisSelesai(uid: String) {
        regServis?.remove()
        regServis = db.collection(COL_BOOKING)
            .whereEqualTo(FIELD_PETUGAS, uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Gagal membaca servis selesai", err)
                    toast("Gagal memuat total servis")
                    return@addSnapshotListener
                }
                val selesai = snap?.documents?.filter { d ->
                    d.getString("status")?.trim()?.uppercase() in STATUS_SELESAI
                }.orEmpty()
                tvServisCount.text = selesai.size.toString()
                tvTerakhirSelesai.text = waktuText(
                    selesai.mapNotNull { it.getTimestamp("completedAt")?.toDate() }.maxOrNull()
                )
            }

        regKonfirmasi?.remove()
        regKonfirmasi = db.collection(COL_BOOKING)
            .whereEqualTo("confirmedBy", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Gagal membaca konfirmasi", err)
                    return@addSnapshotListener
                }
                tvTerakhirKonfirmasi.text = waktuText(
                    snap?.documents?.mapNotNull { it.getTimestamp("confirmedAt")?.toDate() }?.maxOrNull()
                )
            }
    }

    private fun terapkanRole(roleRaw: String?, uid: String) {
        val role = roleRaw?.trim()?.lowercase()
        if (role != "pemilik" && role != "admin" && role != "petugas") {
            toast("Akun ini bukan akun staf")
            auth.signOut()
            keLogin()
            return
        }
        // Sama seperti HomeActivityPetugas: hanya pemilik yang tanpa statistik servis & punya menu laporan.
        // Admin/petugas = staf yang menangani servis.
        val pemilik = role == "pemilik"
        val labelRole = when (role) {
            "pemilik" -> "Pemilik"
            "admin" -> "Admin"
            else -> "Petugas"
        }
        tvRoleChip.text = labelRole
        if (roleDimuat && pemilik == isPemilik) return
        roleDimuat = true
        isPemilik = pemilik

        buildMenu()

        if (pemilik) {
            // Pemilik tidak menampilkan statistik servis
            regServis?.remove()
            regServis = null
            regKonfirmasi?.remove()
            regKonfirmasi = null
            llStatServis.visibility = View.GONE
        } else {
            llStatServis.visibility = View.VISIBLE
            listenServisSelesai(uid)
        }
    }

    private fun isiProfil(
        nama: String?, nip: String?, cabang: String?,
        telepon: String?, aktif: Boolean, fallbackNama: String?
    ) {
        val namaTaniya = nama?.takeIf { it.isNotBlank() }
            ?: fallbackNama?.takeIf { it.isNotBlank() } ?: "Petugas"

        tvStaffName.text = namaTaniya
        tvNip.text = nip?.takeIf { it.isNotBlank() } ?: "-"
        tvBranch.text = cabang?.takeIf { it.isNotBlank() } ?: "-"
        tvTelepon.text = telepon?.takeIf { it.isNotBlank() } ?: "-"

        tvStatusAktif.text = if (aktif) "Aktif" else "Nonaktif"
        tvStatusAktif.setTextColor(
            ContextCompat.getColor(this, if (aktif) R.color.bk_primary else R.color.bk_red_dot)
        )

        val inisial = namaTaniya.trim().take(1).uppercase()
        tvAvatarPetugas.text = inisial.ifEmpty { "P" }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}