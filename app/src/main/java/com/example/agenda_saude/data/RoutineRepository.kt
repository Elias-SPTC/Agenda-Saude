package com.example.agenda_saude.data

import android.content.Context
import com.example.agenda_saude.model.Recommendation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.Locale

object RoutineRepository {

    private const val SAVED_PLAN_FILENAME = "saved_routine_plan.json"

    private val knownKeywords = listOf(
        "vinagre de maçã", "cominho negro", "açafrão-da-terra",
        "pimenta-do-reino", "oleaginosas", "sopa de legumes", "iogurte natural",
        "linhaça", "pistache", "nozes", "omelete",
        "cominho", "açafrão", "azeite", "chia", "magnésio", "melatonina",
        "triptofano", "vitamina c", "whey", "creatina", "canela", "gengibre",
        "cromo", "aveia"
    ).sortedByDescending { it.length }

    fun extractIngredientsFromText(text: String, explicitComponents: List<String> = emptyList()): List<String> {
        val found = mutableSetOf<String>()
        found.addAll(explicitComponents)
        var remainingText = text.lowercase(Locale.getDefault())

        for (keyword in knownKeywords) {
            if (remainingText.contains(keyword)) {
                val formatted = keyword.split(" ").joinToString(" ") {
                    it.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
                }
                found.add(formatted)
                remainingText = remainingText.replace(keyword, " ")
            }
        }
        return found.toList()
    }

    fun sanitizeLoadedDescription(description: String): String {
        var desc = description
        desc = desc.replace(Regex("(?i)\\bInclui(\\s+[A-Za-zÀ-ú0-9_\\-]+)*(\\s+para otimização metabólica\\.?)?"), "").trim()
        desc = desc.replace(Regex("(?i)para otimização metabólica\\.?"), "").trim()
        desc = desc.replace(Regex("(?i)\\bCloreto\\s+de(\\s+magnésio)?\\b"), "").trim()

        desc = desc.replace(Regex("\\s*,\\s*$"), ".")
        desc = desc.replace(Regex("\\s+\\."), ".")
        desc = desc.replace(Regex("\\s+"), " ").trim()

        if (desc.isNotEmpty() && !desc.endsWith(".") && !desc.endsWith("?") && !desc.endsWith("!")) {
            desc += "."
        }
        return desc
    }

    fun getDefaultRecommendations(): List<Recommendation> {
        val rawList = listOf(
            Recommendation(
                id = "1",
                title = "Em Jejum / Pré-café",
                timeSlot = "06:00 - 08:00",
                description = "",
                category = "Jejum",
                startHour = 6,
                endHour = 8,
                components = listOf("Vinagre de Maçã")
            ),
            Recommendation(
                id = "2",
                title = "Café da Manhã",
                timeSlot = "08:00 - 10:30",
                description = "",
                category = "Café da Manhã",
                startHour = 8,
                endHour = 11,
                components = listOf("Linhaça", "Açafrão-da-terra", "Pimenta-do-reino")
            ),
            Recommendation(
                id = "3",
                title = "Pré-Almoço",
                timeSlot = "11:30 - 12:00",
                description = "",
                category = "Pré-Almoço",
                startHour = 11,
                endHour = 12,
                components = listOf("Vinagre de Maçã")
            ),
            Recommendation(
                id = "4",
                title = "Almoço",
                timeSlot = "12:00 - 14:00",
                description = "",
                category = "Almoço",
                startHour = 12,
                endHour = 15,
                components = listOf("Cominho Negro", "Açafrão-da-terra", "Azeite")
            ),
            Recommendation(
                id = "5",
                title = "Lanche da Tarde",
                timeSlot = "15:00 - 17:30",
                description = "",
                category = "Lanche",
                startHour = 15,
                endHour = 18,
                components = listOf("Oleaginosas")
            ),
            Recommendation(
                id = "6",
                title = "Jantar",
                timeSlot = "19:00 - 21:30",
                description = "",
                category = "Jantar",
                startHour = 19,
                endHour = 23,
                components = listOf("Linhaça", "Cominho")
            )
        )

        return rawList.map { rec ->
            val extracted = extractIngredientsFromText(rec.description, rec.components)
            val synthesizedDesc = RoutineAiSynthesizer.synthesizeDescription(rec.title, extracted)
            rec.copy(description = synthesizedDesc, components = extracted)
        }
    }

    fun loadRecommendations(context: Context): List<Recommendation> {
        val localFile = File(context.filesDir, SAVED_PLAN_FILENAME)
        if (localFile.exists()) {
            try {
                val jsonString = localFile.readText()
                val parsed = parseJsonRecommendations(jsonString)
                if (parsed.isNotEmpty()) return checkAndResetOutdatedCompletions(context, parsed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return loadRecommendationsFromAssets(context)
    }

    private fun checkAndResetOutdatedCompletions(context: Context, list: List<Recommendation>): List<Recommendation> {
        val todayStr = LocalDate.now().toString()
        var modified = false
        val resetList = list.map { rec ->
            if (rec.isCompleted && rec.completedDate != null && rec.completedDate != todayStr) {
                modified = true
                rec.copy(isCompleted = false, completedDate = null)
            } else rec
        }
        if (modified) {
            saveRecommendations(context, resetList)
        }
        return resetList
    }

    fun saveRecommendations(context: Context, recommendations: List<Recommendation>) {
        try {
            val jsonArray = JSONArray()
            for (rec in recommendations) {
                val obj = JSONObject().apply {
                    put("id", rec.id)
                    put("title", rec.title)
                    put("timeSlot", rec.timeSlot)
                    put("description", rec.description)
                    put("category", rec.category)
                    put("startHour", rec.startHour)
                    put("endHour", rec.endHour)
                    put("isCompleted", rec.isCompleted)
                    if (rec.completedDate != null) {
                        put("completedDate", rec.completedDate)
                    }
                    val compArr = JSONArray()
                    rec.components.forEach { compArr.put(it) }
                    put("components", compArr)
                }
                jsonArray.put(obj)
            }
            val localFile = File(context.filesDir, SAVED_PLAN_FILENAME)
            localFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearSavedRecommendations(context: Context) {
        try {
            val localFile = File(context.filesDir, SAVED_PLAN_FILENAME)
            if (localFile.exists()) localFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadRecommendationsFromAssets(context: Context, fileName: String = "routine_plan.json"): List<Recommendation> {
        return try {
            val inputStream = context.assets.open(fileName)
            val jsonString = InputStreamReader(inputStream).use { it.readText() }
            parseJsonRecommendations(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultRecommendations()
        }
    }

    fun parseJsonRecommendations(jsonString: String): List<Recommendation> {
        val list = mutableListOf<Recommendation>()
        try {
            val jsonArray = JSONArray(jsonString.trim())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val explicit = mutableListOf<String>()
                val compArray = obj.optJSONArray("components")
                val isSavedUserPlan = compArray != null

                if (compArray != null) {
                    for (j in 0 until compArray.length()) {
                        explicit.add(compArray.getString(j))
                    }
                }

                val finalComponents = if (isSavedUserPlan) {
                    explicit
                } else {
                    val desc = obj.optString("description", "")
                    extractIngredientsFromText(desc, explicit)
                }

                val synthesizedDesc = RoutineAiSynthesizer.synthesizeDescription(title, finalComponents)

                list.add(
                    Recommendation(
                        id = obj.getString("id"),
                        title = title,
                        timeSlot = obj.getString("timeSlot"),
                        description = synthesizedDesc,
                        category = obj.getString("category"),
                        startHour = obj.getInt("startHour"),
                        endHour = obj.getInt("endHour"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedDate = if (obj.has("completedDate") && !obj.isNull("completedDate")) obj.getString("completedDate") else null,
                        components = finalComponents
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return if (list.isNotEmpty()) list else getDefaultRecommendations()
    }

    fun parsePlainTextRecommendations(text: String): List<Recommendation> {
        val list = mutableListOf<Recommendation>()
        val lines = text.split(Regex("\\r?\\n"))
        var idCounter = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val colonIndex = trimmed.indexOf(':')
            val title: String
            val description: String

            if (colonIndex != -1) {
                title = trimmed.substring(0, colonIndex).trim()
                description = trimmed.substring(colonIndex + 1).trim()
            } else {
                title = "Recomendação $idCounter"
                description = trimmed
            }

            val lowerTitle = title.lowercase(Locale.getDefault())
            val (startHour, endHour, timeSlot) = when {
                lowerTitle.contains("jejum") || lowerTitle.contains("antes do café") -> Triple(6, 8, "06:00 - 08:00")
                lowerTitle.contains("café") -> Triple(8, 11, "08:00 - 11:00")
                lowerTitle.contains("antes do almoço") || lowerTitle.contains("pré-almoço") -> Triple(11, 12, "11:30 - 12:00")
                lowerTitle.contains("almoço") -> Triple(12, 15, "12:00 - 15:00")
                lowerTitle.contains("lanche") -> Triple(15, 18, "15:00 - 18:00")
                lowerTitle.contains("jantar") -> Triple(19, 23, "19:00 - 23:00")
                else -> {
                    val start = 6 + (idCounter * 2) % 16
                    Triple(start, start + 2, "%02d:00 - %02d:00".format(start, start + 2))
                }
            }

            val extractedComponents = extractIngredientsFromText(description)
            val synthesizedDesc = RoutineAiSynthesizer.synthesizeDescription(title, extractedComponents)

            list.add(
                Recommendation(
                    id = idCounter.toString(),
                    title = title,
                    timeSlot = timeSlot,
                    description = synthesizedDesc,
                    category = title,
                    startHour = startHour,
                    endHour = endHour,
                    isCompleted = false,
                    completedDate = null,
                    components = extractedComponents
                )
            )
            idCounter++
        }

        return if (list.isNotEmpty()) list else getDefaultRecommendations()
    }
}
