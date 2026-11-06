package com.moakete

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * معلومات تسجيل هذا الحساب في القاعدة المشتركة.
 */
data class RegistrationInfo(
    val uid: String,
    val sequenceNumber: Long,
    val isPrimarySource: Boolean,
    val totalUsers: Long
)

/**
 * القاعدة المصدرية المشتركة عبر الإنترنت (Firestore) لكل نسخ التطبيق على كل الأجهزة.
 *
 * آلية العمل:
 *  - وثيقة meta/counter تحمل العداد الكلي (totalUsers) ومعرّف صاحب المصدر الأساسي.
 *  - كل مستخدم له وثيقة في users/{uid} (uid ثابت لحساب جوجل، لا يتغير حتى لو حُذف
 *    التطبيق وأُعيد تثبيته وسُجّل الدخول بنفس الحساب).
 *  - أول حساب يتصل بالقاعدة (meta/counter غير موجودة بعد) يصبح تلقائيًا "المصدر الأساسي"
 *    برقم تسلسلي 1. أي حساب يتصل لاحقًا يأخذ الرقم التالي بالترتيب.
 *  - كل هذا يتم داخل معاملة Firestore ذرية (transaction) لضمان عدم تكرار الأرقام حتى
 *    لو اتصل عدة أجهزة بنفس اللحظة تمامًا.
 *  - الطابع الزمني (registeredAt) يُؤخذ من خادم جوجل (FieldValue.serverTimestamp)
 *    وليس من ساعة الجهاز، حتى لا يقدر أي جهاز يزوّر ترتيبه.
 */
object UserRegistry {

    private val db get() = FirebaseFirestore.getInstance()
    private const val USERS = "users"
    private const val META = "meta"
    private const val COUNTER_DOC = "counter"

    /**
     * يسجّل المستخدم في القاعدة إذا لم يكن مسجّلاً من قبل (بحسب uid)، ويرجّع معلومات
     * تسجيله (رقمه التسلسلي، هل هو المصدر الأساسي، العدد الكلي الحالي).
     * آمن للاستدعاء في كل مرة يفتح فيها التطبيق — إذا كان مسجّلاً مسبقًا يرجّع بياناته
     * الموجودة فقط بدون أي كتابة جديدة.
     */
    suspend fun registerOrFetch(user: FirebaseUser): RegistrationInfo {
        val userRef = db.collection(USERS).document(user.uid)
        val counterRef = db.collection(META).document(COUNTER_DOC)

        return db.runTransaction { txn ->
            val existingUserSnap = txn.get(userRef)
            val counterSnap = txn.get(counterRef)

            if (existingUserSnap.exists()) {
                // مسجّل من قبل — نرجّع بياناته الحالية بدون أي تعديل
                val seq = existingUserSnap.getLong("sequenceNumber") ?: 0L
                val isPrimary = existingUserSnap.getBoolean("isPrimarySource") ?: false
                val total = counterSnap.getLong("totalUsers") ?: seq
                return@runTransaction RegistrationInfo(user.uid, seq, isPrimary, total)
            }

            val counterExists = counterSnap.exists()
            val newSeq = if (counterExists) (counterSnap.getLong("totalUsers") ?: 0L) + 1 else 1L
            val isPrimary = !counterExists // أول من يسجل بالقاعدة الفاضية = المصدر الأساسي

            txn.set(
                userRef, mapOf(
                    "sequenceNumber" to newSeq,
                    "isPrimarySource" to isPrimary,
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: ""),
                    "registeredAt" to FieldValue.serverTimestamp()
                )
            )

            if (counterExists) {
                txn.update(counterRef, "totalUsers", newSeq)
            } else {
                txn.set(
                    counterRef, mapOf(
                        "totalUsers" to newSeq,
                        "primarySourceUid" to user.uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            RegistrationInfo(user.uid, newSeq, isPrimary, newSeq)
        }.await()
    }

    /** يقرأ العدد الكلي الحالي للمستخدمين المسجلين بالقاعدة (بدون تسجيل أي شيء). */
    suspend fun fetchTotalUsers(): Long {
        val snap = db.collection(META).document(COUNTER_DOC).get().await()
        return snap.getLong("totalUsers") ?: 0L
    }
}
