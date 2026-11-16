package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.MemberProfile
import com.example.data.model.SkuItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ShipSmartRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- SKU OPERATIONS ---

    fun observeSkus(userId: String = requireUserId()): Flow<List<SkuItem>> = flow {
        val path = "skus"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SkuItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserSkus(): Result<List<SkuItem>> {
        val path = "skus"
        return try {
            val uid = requireUserId()
            val snapshot = db.collection(path)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val items = snapshot.toObjects(
                SkuItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getSkuById(skuId: String): Result<SkuItem?> {
        val path = "skus/$skuId"
        return try {
            val doc = db.collection("skus").document(skuId).get().await()
            Result.success(
                doc.toObject(
                    SkuItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createSku(
        productName: String,
        category: String,
        lengthCm: Long,
        breadthCm: Long,
        heightCm: Long,
        deadWeightGrams: Long,
        sellingPrice: Long,
        productCost: Long,
        packagingCost: Long,
        zone: String,
        currentShippingCharge: Long,
        optimizedShippingCharge: Long,
        monthlyOrders: Long,
        recommendation: String
    ): Result<String> {
        val path = "skus"
        return try {
            val uid = requireUserId()
            val payload = mapOf(
                "userId" to uid,
                "productName" to productName.trim().take(120),
                "category" to category.trim().take(60),
                "lengthCm" to lengthCm.coerceIn(1L, 300L),
                "breadthCm" to breadthCm.coerceIn(1L, 300L),
                "heightCm" to heightCm.coerceIn(1L, 300L),
                "deadWeightGrams" to deadWeightGrams.coerceIn(1L, 50000L),
                "sellingPrice" to sellingPrice.coerceIn(1L, 1000000L),
                "productCost" to productCost.coerceIn(0L, 1000000L),
                "packagingCost" to packagingCost.coerceIn(0L, 100000L),
                "zone" to zone,
                "currentShippingCharge" to currentShippingCharge.coerceIn(0L, 50000L),
                "optimizedShippingCharge" to optimizedShippingCharge.coerceIn(0L, 50000L),
                "monthlyOrders" to monthlyOrders.coerceIn(1L, 1000000L),
                "recommendation" to recommendation.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val docRef = db.collection(path).add(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSku(sku: SkuItem): Result<Unit> {
        require(sku.id.isNotBlank()) { "SKU ID must not be blank for update" }
        val existingCreatedAt = requireNotNull(sku.createdAt) { "SKU createdAt must not be null for update" }
        val docRef = db.collection("skus").document(sku.id)
        return try {
            val payload = mapOf(
                "userId" to sku.userId,
                "productName" to sku.productName.trim().take(120),
                "category" to sku.category.trim().take(60),
                "lengthCm" to sku.lengthCm.coerceIn(1L, 300L),
                "breadthCm" to sku.breadthCm.coerceIn(1L, 300L),
                "heightCm" to sku.heightCm.coerceIn(1L, 300L),
                "deadWeightGrams" to sku.deadWeightGrams.coerceIn(1L, 50000L),
                "sellingPrice" to sku.sellingPrice.coerceIn(1L, 1000000L),
                "productCost" to sku.productCost.coerceIn(0L, 1000000L),
                "packagingCost" to sku.packagingCost.coerceIn(0L, 100000L),
                "zone" to sku.zone,
                "currentShippingCharge" to sku.currentShippingCharge.coerceIn(0L, 50000L),
                "optimizedShippingCharge" to sku.optimizedShippingCharge.coerceIn(0L, 50000L),
                "monthlyOrders" to sku.monthlyOrders.coerceIn(1L, 1000000L),
                "recommendation" to sku.recommendation.trim().take(500),
                "createdAt" to existingCreatedAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSku(skuId: String): Result<Unit> {
        val docRef = db.collection("skus").document(skuId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- ADMIN & PERSON DIRECTORY (MEMBERS) OPERATIONS ---

    fun observeMembers(userId: String = requireUserId()): Flow<List<MemberProfile>> = flow {
        val path = "members"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> doc.toMemberProfile() }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserMembers(): Result<List<MemberProfile>> {
        val path = "members"
        return try {
            val uid = requireUserId()
            val snapshot = db.collection(path)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val members = snapshot.documents.mapNotNull { doc -> doc.toMemberProfile() }
            Result.success(members)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getMemberById(memberId: String): Result<MemberProfile?> {
        val path = "members/$memberId"
        return try {
            val doc = db.collection("members").document(memberId).get().await()
            Result.success(if (doc.exists()) doc.toMemberProfile() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toMemberProfile(): MemberProfile {
        return MemberProfile(
            id = id,
            userId = getString("userId") ?: "",
            personName = getString("personName") ?: "",
            roleTitle = getString("roleTitle") ?: "Vendor",
            hubLocation = getString("hubLocation") ?: "Surat Hub",
            isAdminPerson = getBoolean("isAdminPerson") ?: false,
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    suspend fun createMember(
        personName: String,
        roleTitle: String,
        hubLocation: String,
        isAdminPerson: Boolean
    ): Result<String> {
        val path = "members"
        return try {
            val uid = requireUserId()
            val payload = mapOf(
                "userId" to uid,
                "personName" to personName.trim().take(100),
                "roleTitle" to roleTitle,
                "hubLocation" to hubLocation.trim().take(100),
                "isAdminPerson" to isAdminPerson,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val docRef = db.collection(path).add(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMember(member: MemberProfile): Result<Unit> {
        require(member.id.isNotBlank()) { "Member ID must not be blank for update" }
        val existingCreatedAt = requireNotNull(member.createdAt) { "Member createdAt must not be null for update" }
        val docRef = db.collection("members").document(member.id)
        return try {
            val payload = mapOf(
                "userId" to member.userId,
                "personName" to member.personName.trim().take(100),
                "roleTitle" to member.roleTitle,
                "hubLocation" to member.hubLocation.trim().take(100),
                "isAdminPerson" to member.isAdminPerson,
                "createdAt" to existingCreatedAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMember(memberId: String): Result<Unit> {
        val docRef = db.collection("members").document(memberId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
