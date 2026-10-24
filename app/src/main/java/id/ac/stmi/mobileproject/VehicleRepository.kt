package id.ac.stmi.mobileproject

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Satu-satunya tempat yang bicara ke Firestore untuk data kendaraan.
 * Dipakai bersama oleh customer (dan nanti petugas), jadi namanya tanpa akhiran peran.
 */
object VehicleRepository {

    private val db get() = FirebaseFirestore.getInstance()

    private fun col(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("vehicles")

    /** Realtime. Ingat panggil registration.remove() di onStop(). */
    fun listen(
        uid: String,
        onResult: (List<Vehicle>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { onError(e); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Vehicle::class.java)?.copy(id = doc.id)
                }.orEmpty()
                onResult(list)
            }

    /**
     * Tambah mobil. Mobil pertama otomatis jadi utama.
     * Kalau jadi utama, mobil utama lama diturunkan dalam satu batch (atomik).
     */
    fun add(
        uid: String,
        vehicle: Vehicle,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val c = col(uid)
        c.get().addOnSuccessListener { snap ->
            val makePrimary = vehicle.primary || snap.isEmpty
            val batch = db.batch()
            if (makePrimary) {
                snap.documents
                    .filter { it.getBoolean("primary") == true }
                    .forEach { batch.update(it.reference, "primary", false) }
            }
            batch.set(c.document(), toMap(vehicle, makePrimary))
            batch.commit()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener(onError)
        }.addOnFailureListener(onError)
    }

    /** Hapus mobil. Kalau yang dihapus mobil utama, mobil lain dijadikan utama. */
    fun delete(
        uid: String,
        vehicle: Vehicle,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val c = col(uid)
        c.get().addOnSuccessListener { snap ->
            val batch = db.batch()
            batch.delete(c.document(vehicle.id))
            if (vehicle.primary) {
                snap.documents.firstOrNull { it.id != vehicle.id }
                    ?.let { batch.update(it.reference, "primary", true) }
            }
            batch.commit()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener(onError)
        }.addOnFailureListener(onError)
    }

    // id tidak disimpan sebagai field (sudah ada sebagai document ID)
    private fun toMap(v: Vehicle, primary: Boolean) = hashMapOf(
        "brand" to v.brand,
        "model" to v.model,
        "year" to v.year,
        "plateNumber" to v.plateNumber,
        "odometer" to v.odometer,
        "lastServiceOdometer" to v.lastServiceOdometer,
        "serviceIntervalKm" to v.serviceIntervalKm,
        "primary" to primary,
        "color" to v.color,
        "transmission" to v.transmission,
        "fuelType" to v.fuelType,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
