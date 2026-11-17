package com.example.agenda_saude.data

import com.example.agenda_saude.model.Recommendation
import java.util.Locale

object RoutineAnalyzer {

    fun analyzeAndCreateRecommendation(input: String, existingCount: Int): Recommendation {
        val query = input.lowercase().trim()
        val id = (existingCount + 1).toString()
        val formattedName = if (input.isNotEmpty()) input.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } else "Item"

        val (title, description, startHour, endHour, timeSlot, category) = when {
            query.contains("chia") -> {
                Details(
                    "Adicionar Chia",
                    "Rica em fibras solúveis e ômega-3. Cronobiologia: Ideal no café da manhã ou lanche da tarde para retardar a absorção de glicose e prolongar a saciedade.",
                    8, 10, "08:30 - 10:00",
                    "Café da Manhã / Lanche"
                )
            }
            query.contains("magnésio") || query.contains("melatonina") || query.contains("triptofano") -> {
                Details(
                    "Suplementação Noturna ($input)",
                    "Cronobiologia Noturna: Auxilia no relaxamento muscular, melhora da qualidade do sono e redução do cortisol antes de dormir.",
                    21, 22, "21:00 - 22:00",
                    "Noite / Sono"
                )
            }
            query.contains("vitamina c") || query.contains("whey") || query.contains("creatina") -> {
                Details(
                    "Nutriente Matinal ($input)",
                    "Cronobiologia Matinal: Otimiza a síntese proteica e resposta imune quando consumido no período da manhã ou pré-treino.",
                    7, 9, "07:00 - 09:00",
                    "Manhã"
                )
            }
            query.contains("canela") || query.contains("gengibre") || query.contains("cromo") -> {
                Details(
                    "Termogênico / Sensibilidade à Insulina ($input)",
                    "Cronobiologia Pré-refeição: Ajuda a modular a curva glicêmica quando ingerido antes do almoço ou café.",
                    11, 12, "11:30 - 12:00",
                    "Pré-refeição"
                )
            }
            else -> {
                Details(
                    formattedName,
                    "Análise Cronobiológica: Incorporado ao plano com base nas propriedades descritas. Avalie a resposta individual e o timing ideal.",
                    14, 16, "14:00 - 16:00",
                    "Geral"
                )
            }
        }

        return Recommendation(
            id = id,
            title = title,
            timeSlot = timeSlot,
            description = description,
            category = category,
            startHour = startHour,
            endHour = endHour,
            isCompleted = false,
            components = listOf(formattedName)
        )
    }

    private data class Details(
        val title: String,
        val description: String,
        val startHour: Int,
        val endHour: Int,
        val timeSlot: String,
        val category: String
    )
}
