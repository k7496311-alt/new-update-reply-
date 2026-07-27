package com.example.rule.selection

/**
 * Customer type categories for targeted rule selection.
 */
enum class CustomerType {
    ALL,
    NEW_CUSTOMER,
    EXISTING_CUSTOMER,
    VIP
}

/**
 * Supported target languages for rule selection constraints.
 */
enum class RuleLanguage {
    ALL,
    BANGLA,
    ENGLISH,
    MIXED
}

/**
 * Contextual criteria passed to the Rule Selection Engine.
 */
data class RuleSelectionCriteria(
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val customerType: CustomerType = CustomerType.ALL,
    val language: RuleLanguage = RuleLanguage.ALL,
    val isBusinessHoursActive: Boolean = true,
    val currentHourOfDay: Int = 12 // 0..23
)
