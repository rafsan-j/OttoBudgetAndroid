package com.rafsan.ottobudget.domain

import com.rafsan.ottobudget.data.local.KeywordRuleEntity

object RuleEngine {
    fun matchCategory(
        description: String,
        txKind: String,
        rules: List<KeywordRuleEntity>
    ): KeywordRuleEntity? {
        val clean = description.lowercase().replace("\n", " ").trim()
        if (clean.isEmpty()) return null

        val tokens = clean.split(Regex("[^a-z0-9-]+")).filter { it.isNotEmpty() }.toSet()
        val sortedRules = rules
            .filter { it.kind == txKind }
            .sortedWith(compareByDescending<KeywordRuleEntity> { it.priority }.thenByDescending { it.keyword.length })

        for (rule in sortedRules) {
            val kw = rule.keyword.lowercase().trim()
            if (kw.isEmpty()) continue
            if (kw.contains(" ")) {
                if (clean.contains(kw)) return rule
            } else {
                if (tokens.contains(kw) || tokens.any { it.length >= 4 && kw.length >= 4 && it.startsWith(kw) }) {
                    return rule
                }
            }
        }
        return null
    }

    fun evaluateMathExpression(raw: String): Double {
        val sanitized = raw.replace(Regex("[^0-9.+-]"), "")
        if (sanitized.isBlank()) return 0.0
        val tokens = Regex("[+-]?[0-9.]+").findAll(sanitized).mapNotNull { it.value.toDoubleOrNull() }
        return tokens.sum().coerceAtLeast(0.0)
    }
}
