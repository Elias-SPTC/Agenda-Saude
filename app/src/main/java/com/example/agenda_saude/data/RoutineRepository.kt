package com.example.agenda_saude.data

import android.content.Context
import com.example.agenda_saude.model.Recommendation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

object RoutineRepository {

    private const val SAVED_PLAN_FILENAME = "saved_routine_plan.json"

    // Sorted by length descending so compound keywords match before sub-words
    private val knownKeywords = listOf(
        "vinagre de maçã", "cominho negro", "açafrão-da-terra",
        "pimenta-do-reino", "oleaginosas", "sopa de legumes", "iogurte natural",
        "linhaça", "pistache", "nozes", "omelete", "psyllium",
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

    fun getDefaultRecommendations(): List<Recommendation> {
        val rawList = listOf(
            Recommendation(
                id = "1",
                title = "Em Jejum / Pré-café",
                timeSlot = "06:00 - 08:00",
                description = "Beba 1 colher de sopa de vinagre de maçã diluída em 200 ml de água. Use canudo ou enxágue a boca para proteger os dentes.",
                category = "Jejum",
                startHour = 6,
                endHour = 8,
                components = listOf("Vinagre de Maçã")
            ),
            Recommendation(
                id = "2",
                title = "Café da Manhã",
                timeSlot = "08:00 - 10:30",
                description = "Misture 1 a 2 colheres de sopa de linhaça moída em iogurte natural, frutas ou aveia. Se comer ovos mexidos, tempere com açafrão-da-terra, uma pitada de pimenta-do-reino e um fio de azeite.",
                category = "Café da Manhã",
                startHour = 8,
                endHour = 11,
                components = listOf("Linhaça", "Açafrão-da-terra", "Pimenta-do-reino")
            ),
            Recommendation(
                id = "3",
                title = "Pré-Almoço",
                timeSlot = "11:30 - 12:00",
                description = "Repita o vinagre de maçã diluído em água (10 a 15 min antes) se a refeição tiver arroz, massas ou batata.",
                category = "Pré-Almoço",
                startHour = 11,
                endHour = 12,
                components = listOf("Vinagre de Maçã")
            ),
            Recommendation(
                id = "4",
                title = "Almoço",
                timeSlot = "12:00 - 14:00",
                description = "Comece pela salada regada com azeite e salpique 1 colher de chá de cominho negro levemente triturado. Use o cominho comum e o açafrão-da-terra no tempero das carnes ou legumes.",
                category = "Almoço",
                startHour = 12,
                endHour = 15,
                components = listOf("Cominho Negro", "Açafrão-da-terra", "Azeite")
            ),
            Recommendation(
                id = "5",
                title = "Lanche da Tarde",
                timeSlot = "15:00 - 17:30",
                description = "Um punhado pequeno de oleaginosas (como pistache ou nozes), que trazem gorduras boas e saciedade sem elevar a insulina.",
                category = "Lanche",
                startHour = 15,
                endHour = 18,
                components = listOf("Oleaginosas")
            ),
            Recommendation(
                id = "6",
                title = "Jantar",
                timeSlot = "19:00 - 21:30",
                description = "Sopa de legumes, omelete ou proteínas grelhadas finalizadas com o mix de linhaça moída e cominho. Evite carboidratos refinados à noite para manter a insulina baixa durante o sono.",
                category = "Jantar",
                startHour = 19,
                endHour = 23,
                components = listOf("Linhaça", "Cominho")
            )
        )

        return rawList.map { rec ->
            rec.copy(components = extractIngredientsFromText(rec.description, rec.components))
        }.sortedBy { it.startHour }
    }

    fun loadRecommendations(context: Context): List<Recommendation> {
        val localFile = File(context.filesDir, SAVED_PLAN_FILENAME)
        if (localFile.exists()) {
            try {
                val jsonString = localFile.readText()
                val parsed = parseJsonRecommendations(jsonString)
                if (parsed.isNotEmpty()) return parsed.sortedBy { it.startHour }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return loadRecommendationsFromAssets(context).sortedBy { it.startHour }
    }

    fun saveRecommendations(context: Context, recommendations: List<Recommendation>) {
        try {
            val jsonArray = JSONArray()
            val sorted = recommendations.sortedBy { it.startHour }
            for (rec in sorted) {
                val obj = JSONObject().apply {
                    put("id", rec.id)
                    put("title", rec.title)
                    put("timeSlot", rec.timeSlot)
                    put("description", rec.description)
                    put("category", rec.category)
                    put("startHour", rec.startHour)
                    put("endHour", rec.endHour)
                    put("isCompleted", rec.isCompleted)
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
            parseJsonRecommendations(jsonString).sortedBy { it.startHour }
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
                val desc = obj.optString("description", "")
                val explicit = mutableListOf<String>()
                val compArray = obj.optJSONArray("components")
                if (compArray != null) {
                    for (j in 0 until compArray.length()) {
                        explicit.add(compArray.getString(j))
                    }
                }
                val extractedComponents = extractIngredientsFromText(desc, explicit)

                list.add(
                    Recommendation(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        timeSlot = obj.getString("timeSlot"),
                        description = desc,
                        category = obj.getString("category"),
                        startHour = obj.getInt("startHour"),
                        endHour = obj.getInt("endHour"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        components = extractedComponents
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return if (list.isNotEmpty()) list.sortedBy { it.startHour } else getDefaultRecommendations()
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

            list.add(
                Recommendation(
                    id = idCounter.toString(),
                    title = title,
                    timeSlot = timeSlot,
                    description = description,
                    category = title,
                    startHour = startHour,
                    endHour = endHour,
                    isCompleted = false,
                    components = extractedComponents
                )
            )
            idCounter++
        }

        return if (list.isNotEmpty()) list.sortedBy { it.startHour } else getDefaultRecommendations()
    }
}
