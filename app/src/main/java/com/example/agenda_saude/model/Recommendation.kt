package com.example.agenda_saude.model

data class Recommendation(
    val id: String,
    val title: String,
    val timeSlot: String,
    val description: String,
    val category: String,
    val startHour: Int,
    val endHour: Int,
    val isCompleted: Boolean = false,
    val completedDate: String? = null,
    val components: List<String> = emptyList()
)
