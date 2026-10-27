const { setGlobalOptions } = require("firebase-functions/v2");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ maxInstances: 10 });

const db = admin.firestore();

exports.syncLoyaltyPoints = onDocumentUpdated("bookings/{bookingId}", async (event) => {
  const before = event.data?.before?.data();
  const after = event.data?.after?.data();
  if (!before || !after) return;

  const bookingId = event.params.bookingId;
  const customerId = after.customerId;
  const points = Math.max(
    0,
    Math.round(Number(after.totalPrice || 0) * Number(after.pointsRate || 0.10))
  );
  if (!customerId) return;

  if (after.status === "SELESAI" && before.status !== "SELESAI") {
    if (points === 0) {
      await markNotEligible(event.data.after.ref);
    } else {
      await awardPoints(bookingId, customerId, points, event.data.after.ref);
    }
  }

  if (["DITOLAK", "DIBATALKAN"].includes(after.status) &&
      before.status !== after.status && after.pointsStatus === "PENDING") {
    await markNotEligible(event.data.after.ref);
  }

  if (after.status === "DIBATALKAN" && before.status === "SELESAI") {
    const awarded = Math.max(0, Number(before.awardedPoints || points));
    if (awarded > 0 && before.pointsStatus === "AWARDED") {
      await reversePoints(bookingId, customerId, awarded, event.data.after.ref);
    }
  }
});

async function markNotEligible(bookingRef) {
  const now = admin.firestore.FieldValue.serverTimestamp();
  await bookingRef.update({
    awardedPoints: 0,
    pointsStatus: "NOT_ELIGIBLE",
    completedAt: now,
    updatedAt: now
  });
}

async function awardPoints(bookingId, customerId, points, bookingRef) {
  const transactionRef = db.collection("points_transactions").doc(`${bookingId}_EARN`);
  const customerRef = db.collection("users").doc(customerId);

  await db.runTransaction(async (transaction) => {
    const [bookingSnapshot, transactionSnapshot, customerSnapshot] = await Promise.all([
      transaction.get(bookingRef),
      transaction.get(transactionRef),
      transaction.get(customerRef)
    ]);
    if (!bookingSnapshot.exists || transactionSnapshot.exists) return;
    const currentBalance = Number(customerSnapshot.get("points") || 0);
    const now = admin.firestore.FieldValue.serverTimestamp();

    transaction.set(transactionRef, {
      transactionId: transactionRef.id,
      customerId,
      bookingId,
      type: "EARN",
      points,
      description: `Points servis booking ${bookingId}`,
      createdAt: now
    });
    transaction.update(customerRef, {
      points: currentBalance + points,
      updatedAt: now
    });
    transaction.update(bookingRef, {
      awardedPoints: points,
      pointsStatus: "AWARDED",
      completedAt: now,
      pointsAwardedAt: now,
      updatedAt: now
    });
  });
}

async function reversePoints(bookingId, customerId, points, bookingRef) {
  const transactionRef = db.collection("points_transactions").doc(`${bookingId}_REVERSE`);
  const customerRef = db.collection("users").doc(customerId);

  await db.runTransaction(async (transaction) => {
    const [bookingSnapshot, transactionSnapshot, customerSnapshot] = await Promise.all([
      transaction.get(bookingRef),
      transaction.get(transactionRef),
      transaction.get(customerRef)
    ]);
    if (!bookingSnapshot.exists || transactionSnapshot.exists) return;
    const currentBalance = Number(customerSnapshot.get("points") || 0);
    const nextBalance = Math.max(0, currentBalance - points);
    const now = admin.firestore.FieldValue.serverTimestamp();

    transaction.set(transactionRef, {
      transactionId: transactionRef.id,
      customerId,
      bookingId,
      type: "REVERSE",
      points: -points,
      description: `Koreksi points booking ${bookingId}`,
      createdAt: now
    });
    transaction.update(customerRef, {
      points: nextBalance,
      updatedAt: now
    });
    transaction.update(bookingRef, {
      pointsStatus: "REVERSED",
      updatedAt: now
    });
  });
}
