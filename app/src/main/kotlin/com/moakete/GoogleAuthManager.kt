package com.moakete

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

/**
 * يدير تسجيل الدخول بحساب جوجل عبر Credential Manager (الواجهة الحديثة الموصى بها من جوجل).
 *
 * أول مرة يفتح المستخدم التطبيق: تطلع نافذة اختيار حساب جوجل مرة واحدة (هذا شرط تفرضه
 * جوجل نفسها لأسباب خصوصية، ما نقدر نتجاوزه). بعدها الدخول يصير صامتًا تلقائيًا في كل
 * مرة يفتح فيها التطبيق (Credential Manager يتذكر الموافقة على مستوى نظام أندرويد).
 */
object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    // Web Client ID من google-services.json (client_type = 3) — مطلوب من Credential Manager
    // حتى لتطبيقات أندرويد البحتة، لأن توكن الهوية يُصدر من خادم جوجل المرتبط بهذا المعرف.
    private const val WEB_CLIENT_ID =
        "17729622237-7573cs8isfv1j39ehs980d1mj1k1f1uc.apps.googleusercontent.com"

    /**
     * يحاول تسجيل دخول صامت أولاً (بدون أي واجهة تظهر للمستخدم). يستخدم فقط الحسابات
     * التي سبق ووافق عليها المستخدم مع هذا التطبيق على هذا الجهاز.
     * يرجّع null إذا ما فيه حساب مصرّح مسبقًا (عندها نطلب الحساب بواجهة ظاهرة).
     */
    suspend fun trySilentSignIn(context: Context): FirebaseUser? {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true) // صامت: حسابات مصرّحة فقط
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(context, request)
            handleSignInResult(response.credential)
        } catch (e: GetCredentialException) {
            Log.i(TAG, "لا يوجد حساب مصرّح مسبقًا — يحتاج اختيار يدوي: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "خطأ غير متوقع بالدخول الصامت", e)
            null
        }
    }

    /**
     * يطلب من المستخدم اختيار حساب جوجل بواجهة ظاهرة (بطاقة اختيار حساب من جوجل).
     * يُستخدم فقط أول مرة (لما trySilentSignIn يرجّع null).
     */
    suspend fun signInWithPicker(context: Context): FirebaseUser? {
        return try {
            val nonce = UUID.randomUUID().toString().let { raw ->
                MessageDigest.getInstance("SHA-256")
                    .digest(raw.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // اسمح بأي حساب على الجهاز (أول مرة)
                .setServerClientId(WEB_CLIENT_ID)
                .setNonce(nonce)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(context, request)
            handleSignInResult(response.credential)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "المستخدم ألغى أو فشل اختيار الحساب: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "خطأ غير متوقع باختيار الحساب", e)
            null
        }
    }

    private suspend fun handleSignInResult(credential: androidx.credentials.Credential): FirebaseUser? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.e(TAG, "نوع بيانات اعتماد غير متوقع")
            return null
        }
        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(
                googleIdTokenCredential.idToken, null
            )
            val result = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
            result.user
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "فشل تحليل توكن جوجل", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "فشل تسجيل الدخول إلى Firebase", e)
            null
        }
    }

    fun currentUser(): FirebaseUser? = FirebaseAuth.getInstance().currentUser

    fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
    }
}
