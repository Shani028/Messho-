package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.max
import kotlin.math.roundToLong

@IgnoreExtraProperties
data class SkuItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productName: String = "",
    val category: String = "Ethnic Wear",
    val lengthCm: Long = 25L,
    val breadthCm: Long = 20L,
    val heightCm: Long = 8L,
    val deadWeightGrams: Long = 350L,
    val sellingPrice: Long = 399L,
    val productCost: Long = 180L,
    val packagingCost: Long = 10L,
    val zone: String = "National",
    val currentShippingCharge: Long = 88L,
    val optimizedShippingCharge: Long = 65L,
    val monthlyOrders: Long = 200L,
    val recommendation: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val volumetricWeightGrams: Long
        get() = (lengthCm * breadthCm * heightCm) / 5L

    val billableWeightGrams: Long
        get() = max(deadWeightGrams, volumetricWeightGrams)

    val savingsPerOrder: Long
        get() = max(0L, currentShippingCharge - optimizedShippingCharge)

    val monthlySavings: Long
        get() = savingsPerOrder * monthlyOrders

    val currentNetProfitPerOrder: Long
        get() = sellingPrice - productCost - packagingCost - currentShippingCharge

    val optimizedNetProfitPerOrder: Long
        get() = sellingPrice - productCost - packagingCost - optimizedShippingCharge
}

@IgnoreExtraProperties
data class MemberProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personName: String = "",
    val roleTitle: String = "Vendor",
    val hubLocation: String = "Surat Hub",
    @get:PropertyName("isAdminPerson")
    val isAdminPerson: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class OptimizationResult(
    val deadWeightGrams: Long,
    val volumetricWeightGrams: Long,
    val billableWeightGrams: Long,
    val currentSlabLabel: String,
    val optimizedSlabLabel: String,
    val currentShippingCharge: Long,
    val optimizedShippingCharge: Long,
    val targetLengthCm: Long,
    val targetBreadthCm: Long,
    val targetHeightCm: Long,
    val targetVolumetricGrams: Long,
    val savingsPerOrder: Long,
    val monthlySavings: Long,
    val currentProfitPerOrder: Long,
    val optimizedProfitPerOrder: Long,
    val currentMarginPercent: Int,
    val optimizedMarginPercent: Int,
    val isVolumetricPenaltyActive: Boolean,
    val primaryRecommendation: String,
    val actionChecklist: List<String>,
    val courierComparison: List<CourierQuote>
)

data class CourierQuote(
    val partnerName: String,
    val currentRate: Long,
    val optimizedRate: Long,
    val rtoProtectionNote: String
)

object ShippingOptimizerEngine {

    val categories = listOf(
        "Ethnic Wear",
        "Western Apparel",
        "Footwear",
        "Home & Kitchen",
        "Beauty & Personal Care",
        "Fashion Jewellery",
        "Electronics & Accessories",
        "Kids & Toys"
    )

    val zones = listOf("Local", "Zonal", "National")

    val roleOptions = listOf(
        "Admin",
        "Vendor",
        "Warehouse Manager",
        "Packaging Lead",
        "Logistics Staff"
    )

    val hubOptions = listOf(
        "Surat Hub",
        "Delhi NCR Hub",
        "Jaipur Hub",
        "Mumbai Bhiwandi Hub",
        "Bengaluru Hub",
        "Tiruppur Hub",
        "Kolkata Hub"
    )

    fun slabLabelForWeight(weightGrams: Long): String = when {
        weightGrams <= 150L -> "0–150g (Mini Slab)"
        weightGrams <= 500L -> "151–500g (Standard Base)"
        weightGrams <= 1000L -> "501g–1.0kg (Medium)"
        weightGrams <= 1500L -> "1.0kg–1.5kg (Heavy)"
        weightGrams <= 2000L -> "1.5kg–2.0kg (Bulk)"
        else -> "2.0kg+ (Oversize)"
    }

    fun calculateMeeshoCharge(weightGrams: Long, zone: String): Long {
        val base500 = when (zone) {
            "Local" -> 42L
            "Zonal" -> 54L
            else -> 66L // National
        }
        return when {
            weightGrams <= 150L -> base500 - 11L
            weightGrams <= 500L -> base500
            weightGrams <= 1000L -> base500 + 26L
            weightGrams <= 1500L -> base500 + 52L
            weightGrams <= 2000L -> base500 + 78L
            else -> {
                val extraHalfKgs = ((weightGrams - 2000L + 499L) / 500L).coerceAtLeast(1L)
                base500 + 78L + (extraHalfKgs * 24L)
            }
        }
    }

    fun analyzePackage(
        lengthCm: Long,
        breadthCm: Long,
        heightCm: Long,
        deadWeightGrams: Long,
        sellingPrice: Long,
        productCost: Long,
        packagingCost: Long,
        zone: String,
        monthlyOrders: Long
    ): OptimizationResult {
        val safeL = lengthCm.coerceIn(1L, 300L)
        val safeB = breadthCm.coerceIn(1L, 300L)
        val safeH = heightCm.coerceIn(1L, 300L)
        val safeDead = deadWeightGrams.coerceIn(1L, 50000L)
        val safeOrders = monthlyOrders.coerceAtLeast(1L)
        val safePrice = sellingPrice.coerceAtLeast(1L)

        val volWeight = (safeL * safeB * safeH) / 5L
        val billable = max(safeDead, volWeight)
        val currentCharge = calculateMeeshoCharge(billable, zone)
        val currentSlab = slabLabelForWeight(billable)

        // Determine lowest slab achievable based on deadWeightGrams or slight dead-weight trim
        val targetWeightCeiling = when {
            safeDead <= 150L -> 150L
            safeDead <= 165L -> 150L // Light polybag switch can shed 15g packaging weight
            safeDead <= 500L -> 500L
            safeDead <= 535L -> 500L // Can shed 35g box weight by switching to bubble flyer
            safeDead <= 1000L -> 1000L
            safeDead <= 1045L -> 1000L
            safeDead <= 1500L -> 1500L
            else -> ((safeDead / 500L) * 500L).coerceAtLeast(500L)
        }

        // Max allowed L*B*H = targetWeightCeiling * 5
        val maxVolumeCm3 = targetWeightCeiling * 5L
        var optL = safeL
        var optB = safeB
        var optH = safeH

        // First reduce height (folding / tighter box / poly-mailer), then length/breadth if needed
        while ((optL * optB * optH) > maxVolumeCm3 && optH > 2L) {
            optH -= 1L
        }
        while ((optL * optB * optH) > maxVolumeCm3 && optL > 8L) {
            optL -= 1L
        }
        while ((optL * optB * optH) > maxVolumeCm3 && optB > 8L) {
            optB -= 1L
        }

        val optVolWeight = (optL * optB * optH) / 5L
        val optDeadWeight = if (safeDead > targetWeightCeiling && (safeDead - targetWeightCeiling) <= 45L) {
            targetWeightCeiling
        } else {
            safeDead
        }
        val optBillable = max(optDeadWeight, optVolWeight)

        // Even if already in slab, check if Valmo/Polybag packaging consolidation yields ₹6–₹12 savings
        val rawOptCharge = calculateMeeshoCharge(optBillable, zone)
        val optimizedCharge = if (rawOptCharge < currentCharge) {
            rawOptCharge
        } else {
            // Suggest Valmo / smart bag consolidation discount (₹7 to ₹11 per order)
            (currentCharge - 8L).coerceAtLeast(29L)
        }

        val optimizedSlab = slabLabelForWeight(optBillable)
        val savingsPerOrder = max(0L, currentCharge - optimizedCharge)
        val monthlySavings = savingsPerOrder * safeOrders

        val currentProfit = safePrice - productCost - packagingCost - currentCharge
        val optimizedProfit = safePrice - productCost - packagingCost - optimizedCharge

        val currentMarginPct = ((currentProfit.toDouble() / safePrice.toDouble()) * 100.0).roundToLong().toInt()
        val optimizedMarginPct = ((optimizedProfit.toDouble() / safePrice.toDouble()) * 100.0).roundToLong().toInt()

        val isVolPenalty = volWeight > safeDead
        val primaryRec = when {
            isVolPenalty && rawOptCharge < currentCharge ->
                "Volumetric trap detected (${volWeight}g vs ${safeDead}g dead weight). Trim package from ${safeL}×${safeB}×${safeH}cm to ${optL}×${optB}×${optH}cm to drop into $optimizedSlab and save ₹$savingsPerOrder/order!"
            safeDead > targetWeightCeiling ->
                "Borderline dead weight (${safeDead}g). Replace corrugated box with 60-micron tamper-evident polybag to get under ${targetWeightCeiling}g slab and save ₹$savingsPerOrder/order!"
            else ->
                "Dimensions are slab-aligned ($currentSlab). Use tight-fold poly-mailer & prioritize Valmo/Shadowfax pickup slot to save ₹$savingsPerOrder/order on forward + RTO handling."
        }

        val checklist = buildList {
            if (optH < safeH || optL < safeL || optB < safeB) {
                add("Resize outer packaging from ${safeL}×${safeB}×${safeH} cm to ${optL}×${optB}×${optH} cm (reduces volumetric weight from ${volWeight}g to ${optVolWeight}g).")
            }
            if (safeDead > targetWeightCeiling) {
                add("Shed ${safeDead - targetWeightCeiling}g packaging weight by switching from heavy 3-ply carton to lightweight bubble-lined polybag.")
            }
            add("Print thermal 4×6 label flat on the largest face (${optL}×${optB} cm) so automated DWS scanners do not add +1.5 cm wrinkle height.")
            add("Expected monthly recovery: ₹$monthlySavings across $safeOrders orders (${optimizedMarginPct - currentMarginPct}% net margin boost).")
        }

        val couriers = listOf(
            CourierQuote(
                partnerName = "Valmo (Meesho Logistics)",
                currentRate = currentCharge,
                optimizedRate = optimizedCharge,
                rtoProtectionNote = "Zero RTO penalty on verified dimension scan"
            ),
            CourierQuote(
                partnerName = "Shadowfax Surface",
                currentRate = currentCharge + 3L,
                optimizedRate = optimizedCharge + 2L,
                rtoProtectionNote = "Fastest local & zonal hub turnaround"
            ),
            CourierQuote(
                partnerName = "Delhivery E-Com",
                currentRate = currentCharge + 5L,
                optimizedRate = optimizedCharge + 4L,
                rtoProtectionNote = "Strict DWS laser scanner — keep height <= ${optH}cm"
            ),
            CourierQuote(
                partnerName = "Xpressbees Surface",
                currentRate = currentCharge + 4L,
                optimizedRate = optimizedCharge + 3L,
                rtoProtectionNote = "Best for Apparel & <500g poly-mailers"
            )
        )

        return OptimizationResult(
            deadWeightGrams = safeDead,
            volumetricWeightGrams = volWeight,
            billableWeightGrams = billable,
            currentSlabLabel = currentSlab,
            optimizedSlabLabel = optimizedSlab,
            currentShippingCharge = currentCharge,
            optimizedShippingCharge = optimizedCharge,
            targetLengthCm = optL,
            targetBreadthCm = optB,
            targetHeightCm = optH,
            targetVolumetricGrams = optVolWeight,
            savingsPerOrder = savingsPerOrder,
            monthlySavings = monthlySavings,
            currentProfitPerOrder = currentProfit,
            optimizedProfitPerOrder = optimizedProfit,
            currentMarginPercent = currentMarginPct,
            optimizedMarginPercent = optimizedMarginPct,
            isVolumetricPenaltyActive = isVolPenalty,
            primaryRecommendation = primaryRec,
            actionChecklist = checklist,
            courierComparison = couriers
        )
    }
}
