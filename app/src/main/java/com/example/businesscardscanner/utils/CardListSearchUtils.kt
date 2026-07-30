package com.example.businesscardscanner.utils

import com.example.businesscardscanner.models.BusinessCard

object CardListSearchUtils {
    fun filter(cards: List<BusinessCard>, query: String): List<BusinessCard> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return cards

        return cards.filter { card ->
            listOf(
                card.name,
                card.company,
                card.jobTitle,
                card.phone,
                card.email,
                card.address,
                card.group
            ).any { value ->
                value.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
}
