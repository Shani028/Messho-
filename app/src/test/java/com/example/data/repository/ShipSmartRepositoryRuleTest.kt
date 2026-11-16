package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShipSmartRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveMember_renameAndDeletePersonOrAdmin_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ShipSmartRepository(firestore)

        // 1. Create an Admin/Person entry
        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createMember(
                personName = "Ramesh Verma",
                roleTitle = "Admin",
                hubLocation = "Surat Hub",
                isAdminPerson = true
            )
        }
        assertTrue("Create member should succeed", createResult.isSuccess)
        val memberId = createResult.getOrThrow()
        assertTrue(memberId.isNotEmpty())

        // 2. Observe via realtime flow
        val emittedMembers = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMembers(uid).first { list -> list.any { it.id == memberId } }
        }
        val createdMember = emittedMembers.first { it.id == memberId }
        assertEquals("Ramesh Verma", createdMember.personName)
        assertTrue(createdMember.isAdminPerson)

        // 3. Rename / update personName and roleTitle
        val renameResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateMember(
                createdMember.copy(
                    personName = "Suresh Gupta (Renamed Admin)",
                    roleTitle = "Warehouse Manager"
                )
            )
        }
        assertTrue("Rename member should succeed", renameResult.isSuccess)

        val updatedDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getMemberById(memberId).getOrThrow()
        }
        assertNotNull(updatedDoc)
        assertEquals("Suresh Gupta (Renamed Admin)", updatedDoc?.personName)

        // 4. Delete person/admin
        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteMember(memberId)
        }
        assertTrue("Delete member should succeed", deleteResult.isSuccess)
    }

    @Test
    fun createAndQuerySku_authenticatedOwner_returnsMatchingSku() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = ShipSmartRepository(firestore)

        val skuId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createSku(
                productName = "Banarasi Silk Saree",
                category = "Ethnic Wear",
                lengthCm = 30L,
                breadthCm = 24L,
                heightCm = 8L,
                deadWeightGrams = 420L,
                sellingPrice = 599L,
                productCost = 290L,
                packagingCost = 14L,
                zone = "National",
                currentShippingCharge = 92L,
                optimizedShippingCharge = 66L,
                monthlyOrders = 180L,
                recommendation = "Use flat poly-mailer to reduce height to 6cm."
            ).getOrThrow()
        }

        val skusResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getUserSkus() }
        assertTrue(skusResult.isSuccess)
        assertTrue(skusResult.getOrThrow().map { it.id }.contains(skuId))
    }

    @Test
    fun crossUserAccess_bobCannotReadOrDeleteAliceMember_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = ShipSmartRepository(firestore)
        val memberId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createMember(
                personName = "Alice Admin",
                roleTitle = "Admin",
                hubLocation = "Jaipur Hub",
                isAdminPerson = true
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ShipSmartRepository(firestore)

        val getResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getMemberById(memberId) }
        assertTrue("Cross-user get must fail", getResult.isFailure)
        val firestoreEx = getResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.deleteMember(memberId) }
        assertTrue("Cross-user delete must fail", deleteResult.isFailure)
    }

    @Test
    fun observeMembers_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ShipSmartRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeMembers("unauthenticated_uid").first()
            }
            fail("Expected exception for unauthenticated flow observation")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@shipsmart.test"
        const val BOB_EMAIL = "bob@shipsmart.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
