package com.example.agenda_saude.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.agenda_saude.data.RoutineAnalyzer
import com.example.agenda_saude.data.RoutineRepository
import com.example.agenda_saude.model.Recommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.util.Locale

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val _recommendations = MutableStateFlow(
        RoutineRepository.loadRecommendations(application)
    )
    val recommendations: StateFlow<List<Recommendation>> = _recommendations.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Tracks ingredients temporarily removed/skipped for specific recommendation instances (momentary)
    private val _tempRemovedIngredients = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val tempRemovedIngredients: StateFlow<Map<String, Set<String>>> = _tempRemovedIngredients.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun toggleCompletion(id: String) {
        _recommendations.update { list ->
            val updated = list.map { item ->
                if (item.id == id) item.copy(isCompleted = !item.isCompleted) else item
            }
            RoutineRepository.saveRecommendations(getApplication(), updated)
            updated
        }
    }

    /**
     * Adiciona um ingrediente de forma permanente.
     * Se houver uma etapa compatível existente, o ingrediente é incorporado a ela.
     * Caso contrário (ou se exigir um horário/janela distinta), uma nova etapa cronológica é criada e incorporada permanentemente.
     */
    fun addIngredientOrPlan(input: String) {
        if (input.isBlank()) return
        val formattedName = input.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }.trim()

        val activeIngredients = getAllActiveIngredients()
        if (activeIngredients.any { it.equals(formattedName, ignoreCase = true) }) {
            _errorMessage.value = "O ingrediente '$formattedName' já está presente no plano!"
            return
        }

        val query = input.lowercase(Locale.getDefault()).trim()

        // Determine if it fits an existing structural stage or warrants a new dedicated chronobiological stage
        val targetCategory = when {
            query.contains("chia") || query.contains("linhaça") || query.contains("aveia") || query.contains("whey") || query.contains("psyllium") || query.contains("fibra") -> "Café da Manhã"
            query.contains("magnésio") || query.contains("melatonina") || query.contains("triptofano") || query.contains("chá") -> "Jantar"
            query.contains("cominho") || query.contains("açafrão") || query.contains("azeite") || query.contains("pimenta") || query.contains("sal") -> "Almoço"
            query.contains("vinagre") || query.contains("cromo") || query.contains("canela") || query.contains("gengibre") -> "Pré-Almoço"
            query.contains("nozes") || query.contains("pistache") || query.contains("oleaginosas") || query.contains("fruta") -> "Lanche da Tarde"
            else -> null // If no standard category matches, create a dedicated new stage via RoutineAnalyzer!
        }

        _recommendations.update { currentList ->
            val newList = if (targetCategory != null) {
                // Incorporate into existing stage
                currentList.map { item ->
                    if (item.category.contains(targetCategory, ignoreCase = true) || item.title.contains(targetCategory, ignoreCase = true)) {
                        if (!item.components.contains(formattedName)) {
                            val updatedComponents = item.components + formattedName
                            val updatedDescription = "${item.description} Inclui $formattedName para otimização metabólica."
                            item.copy(components = updatedComponents, description = updatedDescription)
                        } else {
                            item
                        }
                    } else {
                        item
                    }
                }
            } else {
                // Create a new permanent chronobiological stage for this unique ingredient/plan
                val newItem = RoutineAnalyzer.analyzeAndCreateRecommendation(input, currentList.size)
                currentList + newItem
            }

            // Automatically clean up any stages that became empty (safety check)
            val cleanedList = newList.filter { it.components.isNotEmpty() }

            RoutineRepository.saveRecommendations(getApplication(), cleanedList)
            cleanedList
        }
    }

    /**
     * Remove temporariamente um ingrediente apenas da refeição/etapa do momento presente.
     */
    fun tempRemoveIngredient(recommendationId: String, ingredientName: String) {
        _tempRemovedIngredients.update { map ->
            val existing = map[recommendationId] ?: emptySet()
            map + (recommendationId to (existing + ingredientName))
        }
    }

    /**
     * Elimina um ingrediente de forma definitiva do plano global (banco de dados local).
     * Se a etapa ficar sem nenhum ingrediente remanescente, ela é eliminada automaticamente do aplicativo.
     */
    fun removeIngredient(ingredientName: String) {
        _recommendations.update { currentList ->
            val updatedList = currentList.mapNotNull { item ->
                if (item.components.any { it.equals(ingredientName, ignoreCase = true) }) {
                    val newComponents = item.components.filter { !it.equals(ingredientName, ignoreCase = true) }
                    val cleanDesc = sanitizeDescriptionPermanently(item.description, ingredientName)

                    // Automatically eliminate stage if it has zero ingredients remaining in the database
                    if (newComponents.isEmpty()) {
                        null // Eliminate stage automatically
                    } else {
                        item.copy(components = newComponents, description = cleanDesc)
                    }
                } else {
                    item
                }
            }
            RoutineRepository.saveRecommendations(getApplication(), updatedList)
            updatedList
        }

        _tempRemovedIngredients.update { map ->
            map.mapValues { (_, set) -> set.filter { !it.equals(ingredientName, ignoreCase = true) }.toSet() }
        }
    }

    private fun sanitizeDescriptionPermanently(description: String, ingredient: String): String {
        var cleaned = description
        val regex = Regex("(?i)\\b${Regex.escape(ingredient)}\\b")
        cleaned = cleaned.replace(regex, "")

        cleaned = cleaned.replace(Regex("(?i)\\b(e|com|de)\\s*,"), ",")
        cleaned = cleaned.replace(Regex("(?i)\\b(e|com|de)\\s*\\."), ".")
        cleaned = cleaned.replace(Regex("\\s*,\\s*,"), ",")
        cleaned = cleaned.replace(Regex("\\s+\\."), ".")
        cleaned = cleaned.replace(Regex("\\s+e\\s+e\\s+"), " e ")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }
        return if (cleaned.isNotBlank()) cleaned else description
    }

    fun getAllActiveIngredients(): List<String> {
        val ingredients = mutableSetOf<String>()
        _recommendations.value.forEach { rec ->
            ingredients.addAll(rec.components)
        }
        return ingredients.toList()
    }

    /**
     * Salva/atualiza o plano com os ingredientes e estado atual.
     */
    fun updatePlan() {
        RoutineRepository.saveRecommendations(getApplication(), _recommendations.value)
    }

    /**
     * Retorna ao plano padrão (hardcoded/assets).
     */
    fun restoreDefaultPlan() {
        RoutineRepository.clearSavedRecommendations(getApplication())
        _recommendations.value = RoutineRepository.loadRecommendationsFromAssets(getApplication())
        _tempRemovedIngredients.value = emptyMap()
    }

    fun loadCustomPlan(inputText: String) {
        val trimmed = inputText.trim()
        val parsed = if (trimmed.startsWith("[")) {
            RoutineRepository.parseJsonRecommendations(trimmed)
        } else {
            RoutineRepository.parsePlainTextRecommendations(trimmed)
        }
        if (parsed.isNotEmpty()) {
            _recommendations.value = parsed
            RoutineRepository.saveRecommendations(getApplication(), parsed)
            _tempRemovedIngredients.value = emptyMap()
        }
    }

    fun getCurrentRecommendation(currentTime: LocalTime = LocalTime.now()): Recommendation? {
        val hour = currentTime.hour
        val list = _recommendations.value
        return list.find { hour in it.startHour..it.endHour }
            ?: list.firstOrNull { hour < it.startHour }
            ?: list.lastOrNull()
    }
}
