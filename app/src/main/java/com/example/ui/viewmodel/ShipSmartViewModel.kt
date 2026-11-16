package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MemberProfile
import com.example.data.model.OptimizationResult
import com.example.data.model.ShippingOptimizerEngine
import com.example.data.model.SkuItem
import com.example.data.repository.ShipSmartRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class CalculatorFormState(
    val editingSkuId: String? = null,
    val productName: String = "Embroidered Kurti Pant Dupatta Set",
    val category: String = "Ethnic Wear",
    val lengthCm: String = "28",
    val breadthCm: String = "22",
    val heightCm: String = "9",
    val deadWeightGrams: String = "380",
    val sellingPrice: String = "449",
    val productCost: String = "210",
    val packagingCost: String = "12",
    val zone: String = "National",
    val monthlyOrders: String = "250"
)

data class SkuPreset(
    val label: String,
    val productName: String,
    val category: String,
    val lengthCm: Long,
    val breadthCm: Long,
    val heightCm: Long,
    val deadWeightGrams: Long,
    val sellingPrice: Long,
    val productCost: Long,
    val packagingCost: Long,
    val zone: String,
    val monthlyOrders: Long
)

class ShipSmartViewModel(
    private val repository: ShipSmartRepository,
    private val currentUserId: String,
    private val currentUserEmail: String?,
    private val currentUserDisplayName: String?
) : ViewModel() {

    val skusState: StateFlow<UiState<List<SkuItem>>> = repository.observeSkus(currentUserId)
        .map<List<SkuItem>, UiState<List<SkuItem>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            Log.w(TAG, "Error observing SKUs", error)
            emit(UiState.Error(error.message ?: "Failed to load saved SKUs"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val membersState: StateFlow<UiState<List<MemberProfile>>> = repository.observeMembers(currentUserId)
        .map<List<MemberProfile>, UiState<List<MemberProfile>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            Log.w(TAG, "Error observing members", error)
            emit(UiState.Error(error.message ?: "Failed to load people & admins"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _formState = MutableStateFlow(CalculatorFormState())
    val formState: StateFlow<CalculatorFormState> = _formState.asStateFlow()

    private val _optimizationResult = MutableStateFlow(computeOptimization(_formState.value))
    val optimizationResult: StateFlow<OptimizationResult> = _optimizationResult.asStateFlow()

    // Admin Login Session State
    private val _isAdminLoggedIn = MutableStateFlow(
        currentUserEmail?.equals("bhartisani933@gmail.com", ignoreCase = true) == true
    )
    val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn.asStateFlow()

    private val _adminPinCode = MutableStateFlow("1234")
    val adminPinCode: StateFlow<String> = _adminPinCode.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var hasCheckedInitialSeed = false

    val presets = listOf(
        SkuPreset(
            label = "Kurti Box (Volumetric Trap)",
            productName = "Embroidered Kurti Pant Set",
            category = "Ethnic Wear",
            lengthCm = 28L,
            breadthCm = 22L,
            heightCm = 9L,
            deadWeightGrams = 380L,
            sellingPrice = 449L,
            productCost = 210L,
            packagingCost = 12L,
            zone = "National",
            monthlyOrders = 250L
        ),
        SkuPreset(
            label = "Kitchen Jar Set (520g Borderline)",
            productName = "Airtight Masala Box 6-Grid",
            category = "Home & Kitchen",
            lengthCm = 22L,
            breadthCm = 18L,
            heightCm = 6L,
            deadWeightGrams = 520L,
            sellingPrice = 299L,
            productCost = 115L,
            packagingCost = 14L,
            zone = "Zonal",
            monthlyOrders = 400L
        ),
        SkuPreset(
            label = "Kids Footwear Carton",
            productName = "LED Kids Sports Sneakers",
            category = "Footwear",
            lengthCm = 25L,
            breadthCm = 18L,
            heightCm = 12L,
            deadWeightGrams = 340L,
            sellingPrice = 379L,
            productCost = 155L,
            packagingCost = 15L,
            zone = "National",
            monthlyOrders = 180L
        ),
        SkuPreset(
            label = "Jewellery Combo (<150g Mini)",
            productName = "Oxidised Jhumka Bangle Combo",
            category = "Fashion Jewellery",
            lengthCm = 16L,
            breadthCm = 12L,
            heightCm = 5L,
            deadWeightGrams = 125L,
            sellingPrice = 199L,
            productCost = 68L,
            packagingCost = 8L,
            zone = "Local",
            monthlyOrders = 500L
        )
    )

    init {
        seedStarterWorkspaceIfEmpty()
    }

    private fun seedStarterWorkspaceIfEmpty() {
        if (hasCheckedInitialSeed) return
        hasCheckedInitialSeed = true
        viewModelScope.launch {
            val existingMembers = repository.getUserMembers().getOrNull()
            if (existingMembers != null && existingMembers.isEmpty()) {
                val defaultAdminName = currentUserDisplayName?.takeIf { it.isNotBlank() } ?: "Rajesh Sharma (Primary Admin)"
                repository.createMember(
                    personName = defaultAdminName,
                    roleTitle = "Admin",
                    hubLocation = "Surat Hub",
                    isAdminPerson = true
                )
                repository.createMember(
                    personName = "Priya Verma",
                    roleTitle = "Warehouse Manager",
                    hubLocation = "Delhi NCR Hub",
                    isAdminPerson = false
                )
                repository.createMember(
                    personName = "Amitabh Patel",
                    roleTitle = "Packaging Lead",
                    hubLocation = "Jaipur Hub",
                    isAdminPerson = false
                )
            }

            val existingSkus = repository.getUserSkus().getOrNull()
            if (existingSkus != null && existingSkus.isEmpty()) {
                val p1 = presets[0]
                val res1 = ShippingOptimizerEngine.analyzePackage(
                    p1.lengthCm, p1.breadthCm, p1.heightCm, p1.deadWeightGrams,
                    p1.sellingPrice, p1.productCost, p1.packagingCost, p1.zone, p1.monthlyOrders
                )
                repository.createSku(
                    productName = p1.productName,
                    category = p1.category,
                    lengthCm = p1.lengthCm,
                    breadthCm = p1.breadthCm,
                    heightCm = p1.heightCm,
                    deadWeightGrams = p1.deadWeightGrams,
                    sellingPrice = p1.sellingPrice,
                    productCost = p1.productCost,
                    packagingCost = p1.packagingCost,
                    zone = p1.zone,
                    currentShippingCharge = res1.currentShippingCharge,
                    optimizedShippingCharge = res1.optimizedShippingCharge,
                    monthlyOrders = p1.monthlyOrders,
                    recommendation = res1.primaryRecommendation
                )

                val p2 = presets[1]
                val res2 = ShippingOptimizerEngine.analyzePackage(
                    p2.lengthCm, p2.breadthCm, p2.heightCm, p2.deadWeightGrams,
                    p2.sellingPrice, p2.productCost, p2.packagingCost, p2.zone, p2.monthlyOrders
                )
                repository.createSku(
                    productName = p2.productName,
                    category = p2.category,
                    lengthCm = p2.lengthCm,
                    breadthCm = p2.breadthCm,
                    heightCm = p2.heightCm,
                    deadWeightGrams = p2.deadWeightGrams,
                    sellingPrice = p2.sellingPrice,
                    productCost = p2.productCost,
                    packagingCost = p2.packagingCost,
                    zone = p2.zone,
                    currentShippingCharge = res2.currentShippingCharge,
                    optimizedShippingCharge = res2.optimizedShippingCharge,
                    monthlyOrders = p2.monthlyOrders,
                    recommendation = res2.primaryRecommendation
                )
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun updateForm(transform: (CalculatorFormState) -> CalculatorFormState) {
        val updated = transform(_formState.value)
        _formState.value = updated
        _optimizationResult.value = computeOptimization(updated)
    }

    fun applyPreset(preset: SkuPreset) {
        updateForm {
            CalculatorFormState(
                editingSkuId = null,
                productName = preset.productName,
                category = preset.category,
                lengthCm = preset.lengthCm.toString(),
                breadthCm = preset.breadthCm.toString(),
                heightCm = preset.heightCm.toString(),
                deadWeightGrams = preset.deadWeightGrams.toString(),
                sellingPrice = preset.sellingPrice.toString(),
                productCost = preset.productCost.toString(),
                packagingCost = preset.packagingCost.toString(),
                zone = preset.zone,
                monthlyOrders = preset.monthlyOrders.toString()
            )
        }
        _statusBanner.value = "Loaded preset: ${preset.label}"
    }

    fun applySmartDimensionsToCalculator() {
        val opt = _optimizationResult.value
        val currentDead = _formState.value.deadWeightGrams.toLongOrNull() ?: 380L
        val targetDead = if (currentDead in 501L..535L) 495L else if (currentDead in 151L..165L) 145L else currentDead
        updateForm { state ->
            state.copy(
                lengthCm = opt.targetLengthCm.toString(),
                breadthCm = opt.targetBreadthCm.toString(),
                heightCm = opt.targetHeightCm.toString(),
                deadWeightGrams = targetDead.toString()
            )
        }
        _statusBanner.value = "Smart dimensions (${opt.targetLengthCm}×${opt.targetBreadthCm}×${opt.targetHeightCm} cm) applied!"
    }

    fun loadSkuForEditing(sku: SkuItem) {
        updateForm {
            CalculatorFormState(
                editingSkuId = sku.id,
                productName = sku.productName,
                category = sku.category,
                lengthCm = sku.lengthCm.toString(),
                breadthCm = sku.breadthCm.toString(),
                heightCm = sku.heightCm.toString(),
                deadWeightGrams = sku.deadWeightGrams.toString(),
                sellingPrice = sku.sellingPrice.toString(),
                productCost = sku.productCost.toString(),
                packagingCost = sku.packagingCost.toString(),
                zone = sku.zone,
                monthlyOrders = sku.monthlyOrders.toString()
            )
        }
        _statusBanner.value = "Editing SKU: ${sku.productName}"
    }

    fun saveCurrentSkuToCatalog() {
        val state = _formState.value
        val opt = _optimizationResult.value
        val name = state.productName.trim().ifBlank { "Meesho Vendor SKU" }
        val l = (state.lengthCm.toLongOrNull() ?: 25L).coerceIn(1L, 300L)
        val b = (state.breadthCm.toLongOrNull() ?: 20L).coerceIn(1L, 300L)
        val h = (state.heightCm.toLongOrNull() ?: 8L).coerceIn(1L, 300L)
        val dead = (state.deadWeightGrams.toLongOrNull() ?: 350L).coerceIn(1L, 50000L)
        val price = (state.sellingPrice.toLongOrNull() ?: 399L).coerceIn(1L, 1000000L)
        val cost = (state.productCost.toLongOrNull() ?: 180L).coerceIn(0L, 1000000L)
        val pack = (state.packagingCost.toLongOrNull() ?: 10L).coerceIn(0L, 100000L)
        val orders = (state.monthlyOrders.toLongOrNull() ?: 100L).coerceIn(1L, 1000000L)

        viewModelScope.launch {
            val editingId = state.editingSkuId
            val existingList = (skusState.value as? UiState.Success)?.data.orEmpty()
            val existingSku = editingId?.let { id -> existingList.find { it.id == id } }

            if (existingSku != null && existingSku.createdAt != null) {
                val updatedSku = existingSku.copy(
                    productName = name,
                    category = state.category,
                    lengthCm = l,
                    breadthCm = b,
                    heightCm = h,
                    deadWeightGrams = dead,
                    sellingPrice = price,
                    productCost = cost,
                    packagingCost = pack,
                    zone = state.zone,
                    currentShippingCharge = opt.currentShippingCharge,
                    optimizedShippingCharge = opt.optimizedShippingCharge,
                    monthlyOrders = orders,
                    recommendation = opt.primaryRecommendation
                )
                val res = repository.updateSku(updatedSku)
                if (res.isSuccess) {
                    _formState.value = state.copy(editingSkuId = null)
                    _statusBanner.value = "Updated '$name' in SKU Catalog!"
                } else {
                    _statusBanner.value = "Failed to update SKU: ${res.exceptionOrNull()?.localizedMessage}"
                }
            } else {
                val res = repository.createSku(
                    productName = name,
                    category = state.category,
                    lengthCm = l,
                    breadthCm = b,
                    heightCm = h,
                    deadWeightGrams = dead,
                    sellingPrice = price,
                    productCost = cost,
                    packagingCost = pack,
                    zone = state.zone,
                    currentShippingCharge = opt.currentShippingCharge,
                    optimizedShippingCharge = opt.optimizedShippingCharge,
                    monthlyOrders = orders,
                    recommendation = opt.primaryRecommendation
                )
                if (res.isSuccess) {
                    _statusBanner.value = "Saved '$name' (saves ₹${opt.savingsPerOrder}/order)!"
                } else {
                    _statusBanner.value = "Failed to save SKU: ${res.exceptionOrNull()?.localizedMessage}"
                }
            }
        }
    }

    fun renameSku(sku: SkuItem, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank() || sku.createdAt == null) return
        viewModelScope.launch {
            val res = repository.updateSku(sku.copy(productName = cleanName))
            if (res.isSuccess) {
                _statusBanner.value = "Renamed SKU to '$cleanName'"
            }
        }
    }

    fun deleteSku(skuId: String, skuName: String) {
        viewModelScope.launch {
            val res = repository.deleteSku(skuId)
            if (res.isSuccess) {
                _statusBanner.value = "Deleted SKU '$skuName'"
            } else {
                _statusBanner.value = "Could not delete SKU: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // --- ADMIN LOGIN & PERSON MANAGEMENT ---

    fun loginAsAdmin(enteredPin: String, adminDisplayName: String): Boolean {
        val expectedPin = _adminPinCode.value
        return if (enteredPin.trim() == expectedPin || enteredPin.trim() == "1234") {
            _isAdminLoggedIn.value = true
            val cleanAdminName = adminDisplayName.trim()
            if (cleanAdminName.isNotEmpty()) {
                viewModelScope.launch {
                    val currentMembers = (membersState.value as? UiState.Success)?.data.orEmpty()
                    val firstAdmin = currentMembers.firstOrNull { it.isAdminPerson }
                    if (firstAdmin != null && firstAdmin.createdAt != null) {
                        repository.updateMember(
                            firstAdmin.copy(
                                personName = cleanAdminName,
                                roleTitle = "Admin",
                                isAdminPerson = true
                            )
                        )
                    } else {
                        repository.createMember(
                            personName = cleanAdminName,
                            roleTitle = "Admin",
                            hubLocation = "Surat Hub",
                            isAdminPerson = true
                        )
                    }
                }
            }
            _statusBanner.value = "Admin Login successful! Full Admin controls unlocked."
            true
        } else {
            _statusBanner.value = "Invalid Admin PIN (Default PIN is 1234)."
            false
        }
    }

    fun logoutAdmin() {
        _isAdminLoggedIn.value = false
        _statusBanner.value = "Switched to Standard Vendor Mode."
    }

    fun addPersonOrAdmin(
        personName: String,
        roleTitle: String,
        hubLocation: String,
        isAdminPerson: Boolean
    ) {
        val cleanName = personName.trim()
        if (cleanName.isBlank()) {
            _statusBanner.value = "Please enter a valid name."
            return
        }
        val effectiveRole = if (isAdminPerson) "Admin" else roleTitle
        viewModelScope.launch {
            val res = repository.createMember(
                personName = cleanName,
                roleTitle = effectiveRole,
                hubLocation = hubLocation.ifBlank { "Surat Hub" },
                isAdminPerson = isAdminPerson || effectiveRole == "Admin"
            )
            if (res.isSuccess) {
                _statusBanner.value = "Added ${if (isAdminPerson) "Admin" else "Person"}: '$cleanName'"
            } else {
                _statusBanner.value = "Error adding person: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun updatePersonOrAdmin(
        member: MemberProfile,
        newName: String,
        newRoleTitle: String,
        newHubLocation: String,
        newIsAdminPerson: Boolean
    ) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            _statusBanner.value = "Name cannot be empty."
            return
        }
        if (member.createdAt == null) {
            _statusBanner.value = "Please wait a moment for sync before updating."
            return
        }
        val effectiveRole = if (newIsAdminPerson) "Admin" else newRoleTitle
        viewModelScope.launch {
            val res = repository.updateMember(
                member.copy(
                    personName = cleanName,
                    roleTitle = effectiveRole,
                    hubLocation = newHubLocation.trim().ifBlank { "Surat Hub" },
                    isAdminPerson = newIsAdminPerson || effectiveRole == "Admin"
                )
            )
            if (res.isSuccess) {
                _statusBanner.value = "Updated name to '$cleanName' (${effectiveRole})"
            } else {
                _statusBanner.value = "Failed to update name: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deletePersonOrAdmin(member: MemberProfile) {
        viewModelScope.launch {
            val res = repository.deleteMember(member.id)
            if (res.isSuccess) {
                val label = if (member.isAdminPerson) "Admin" else "Person"
                _statusBanner.value = "Deleted $label '${member.personName}'"
            } else {
                _statusBanner.value = "Failed to delete: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun buildCsvReportContent(): String {
        val skus = (skusState.value as? UiState.Success)?.data.orEmpty()
        val members = (membersState.value as? UiState.Success)?.data.orEmpty()
        val opt = _optimizationResult.value
        val form = _formState.value

        return buildString {
            appendLine("Meesho ShipSmart Optimization Report - Shani028/Messho")
            appendLine("Section,Name/SKU,Category/Role,Dimensions/Hub,DeadWeight(g),VolumetricWeight(g),Zone,CurrentCharge(INR),OptimizedCharge(INR),SavingsPerOrder(INR),MonthlyOrders,MonthlySavings(INR)")
            // Active Calculator Row
            val l = form.lengthCm.toLongOrNull() ?: 28L
            val b = form.breadthCm.toLongOrNull() ?: 22L
            val h = form.heightCm.toLongOrNull() ?: 9L
            val orders = form.monthlyOrders.toLongOrNull() ?: 250L
            appendLine(
                "Active Calculator,\"${form.productName.replace("\"", "'")}\",\"${form.category}\",${l}x${b}x${h}cm,${opt.deadWeightGrams},${opt.volumetricWeightGrams},${form.zone},${opt.currentShippingCharge},${opt.optimizedShippingCharge},${opt.savingsPerOrder},$orders,${opt.monthlySavings}"
            )
            // Saved SKUs
            skus.forEach { sku ->
                appendLine(
                    "Saved SKU,\"${sku.productName.replace("\"", "'")}\",\"${sku.category}\",${sku.lengthCm}x${sku.breadthCm}x${sku.heightCm}cm,${sku.deadWeightGrams},${sku.volumetricWeightGrams},${sku.zone},${sku.currentShippingCharge},${sku.optimizedShippingCharge},${sku.savingsPerOrder},${sku.monthlyOrders},${sku.monthlySavings}"
                )
            }
            // Directory Members
            members.forEach { member ->
                val adminTag = if (member.isAdminPerson) "Admin" else "Person"
                appendLine(
                    "Directory ($adminTag),\"${member.personName.replace("\"", "'")}\",\"${member.roleTitle}\",\"${member.hubLocation}\",-,-,-,-,-,-,-,-"
                )
            }
        }
    }

    fun buildShareableSummary(): String {
        val skus = (skusState.value as? UiState.Success)?.data.orEmpty()
        val members = (membersState.value as? UiState.Success)?.data.orEmpty()
        val opt = _optimizationResult.value
        val totalMonthly = skus.sumOf { it.monthlySavings }
        return buildString {
            appendLine("📦 Meesho ShipSmart Report (Shani028/Messho)")
            appendLine("• Total Monthly Catalog Savings: ₹$totalMonthly/mo across ${skus.size} SKUs")
            appendLine("• Current Calculator: ₹${opt.currentShippingCharge} -> ₹${opt.optimizedShippingCharge} (Saves ₹${opt.savingsPerOrder}/order)")
            appendLine("• Recommended Box: ${opt.targetLengthCm}×${opt.targetBreadthCm}×${opt.targetHeightCm} cm")
            appendLine("• Team & Admins (${members.size}): ${members.joinToString { "${it.personName} (${it.roleTitle})" }}")
        }
    }

    fun notifyReportDownloaded(message: String) {
        _statusBanner.value = message
    }

    private fun computeOptimization(state: CalculatorFormState): OptimizationResult {
        return ShippingOptimizerEngine.analyzePackage(
            lengthCm = state.lengthCm.toLongOrNull() ?: 25L,
            breadthCm = state.breadthCm.toLongOrNull() ?: 20L,
            heightCm = state.heightCm.toLongOrNull() ?: 8L,
            deadWeightGrams = state.deadWeightGrams.toLongOrNull() ?: 350L,
            sellingPrice = state.sellingPrice.toLongOrNull() ?: 399L,
            productCost = state.productCost.toLongOrNull() ?: 180L,
            packagingCost = state.packagingCost.toLongOrNull() ?: 10L,
            zone = state.zone,
            monthlyOrders = state.monthlyOrders.toLongOrNull() ?: 200L
        )
    }

    private companion object {
        const val TAG = "ShipSmartVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
