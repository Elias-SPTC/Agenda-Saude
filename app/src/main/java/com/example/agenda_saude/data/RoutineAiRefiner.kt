package com.example.agenda_saude.data

import java.util.Locale

object RoutineAiRefiner {

    /**
     * Refines and rewrites meal instructions intelligently in the background
     * when ingredients are momentarily removed, ensuring complete grammatical coherence and natural flow.
     */
    fun refineDescription(originalDescription: String, tempRemoved: Set<String>): String {
        if (tempRemoved.isEmpty()) return originalDescription

        var text = originalDescription
        for (ing in tempRemoved) {
            // Only remove the exact ingredient name safely
            val regex = Regex("(?i)\\b${Regex.escape(ing)}\\b")
            text = text.replace(regex, "")
        }

        // Clean up dangling prepositions or spaces left behind
        text = text.replace(Regex("(?i)\\b(com|de|do|da|com o|com a)\\s*,"), ",")
        text = text.replace(Regex("(?i)\\b(com|de|do|da|com o|com a)\\s*\\."), ".")
        text = text.replace(Regex("(?i)\\b(com|de|do|da|com o|com a)\\s+e\\b"), "")
        text = text.replace(Regex("\\s*,\\s*,"), ",")
        text = text.replace(Regex("\\s+\\."), ".")
        text = text.replace(Regex("\\s+e\\s+e\\s+"), " e ")
        text = text.replace(Regex("\\s+e\\s*\\."), ".")

        // Fix spaces around punctuation
        text = text.replace(Regex("\\s+,"), ",")
        text = text.replace(Regex(",(?=\\S)"), ", ")

        // Collapse multiple spaces into one
        text = text.replace(Regex("\\s+"), " ").trim()

        // Capitalize the first letter
        if (text.isNotEmpty()) {
            text = text.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }

        return if (text.isNotBlank()) text else originalDescription
    }
}
