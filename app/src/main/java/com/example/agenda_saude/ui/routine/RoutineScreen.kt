package com.example.agenda_saude.ui.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agenda_saude.data.IngredientInfo
import com.example.agenda_saude.data.IngredientKnowledgeBase
import com.example.agenda_saude.data.RoutineAiRefiner
import com.example.agenda_saude.model.Recommendation
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(viewModel: RoutineViewModel = viewModel()) {
    val recommendations by viewModel.recommendations.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val tempRemovedMap by viewModel.tempRemovedIngredients.collectAsState()

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            currentTime = LocalTime.now()
        }
    }

    val currentRecommendation = remember(recommendations, currentTime) {
        viewModel.getCurrentRecommendation(currentTime)
    }

    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val formattedTime = remember(currentTime) { currentTime.format(formatter) }

    var showMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var ingredientInput by remember { mutableStateOf("") }

    var showRemoveIngredientDialog by remember { mutableStateOf(false) }
    var showIngredientsListDialog by remember { mutableStateOf(false) }
    var selectedIngredientForInfo by remember { mutableStateOf<IngredientInfo?>(null) }

    // Warning dialog for duplicate ingredients
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Aviso de Duplicidade") },
            text = { Text(errorMessage ?: "", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Agenda Saúde", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Horário atual: $formattedTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opções do Plano"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lista de Ingredientes") },
                            onClick = {
                                showMenu = false
                                showIngredientsListDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Adicionar Ingrediente") },
                            onClick = {
                                showMenu = false
                                showAddDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar Ingrediente") },
                            onClick = {
                                showMenu = false
                                showRemoveIngredientDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Atualizar Plano") },
                            onClick = {
                                showMenu = false
                                viewModel.updatePlan()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Retornar ao Plano Padrão") },
                            onClick = {
                                showMenu = false
                                viewModel.restoreDefaultPlan()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Carregar Plano") },
                            onClick = {
                                showMenu = false
                                showImportDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        val visibleRecommendations = recommendations.filter { rec ->
            val visibleComps = rec.components.filter { it !in (tempRemovedMap[rec.id] ?: emptySet()) }
            rec.components.isEmpty() || visibleComps.isNotEmpty()
        }

        val currentVisible = currentRecommendation?.let { rec ->
            rec.components.filter { it !in (tempRemovedMap[rec.id] ?: emptySet()) }
        } ?: emptyList()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (currentRecommendation != null && currentVisible.isNotEmpty()) {
                item(key = "current_${currentRecommendation.id}") {
                    CurrentRecommendationCard(
                        recommendation = currentRecommendation,
                        tempRemovedIngredients = tempRemovedMap[currentRecommendation.id] ?: emptySet(),
                        onToggleComplete = { viewModel.toggleCompletion(currentRecommendation.id) },
                        onTempRemoveIngredient = { ing -> viewModel.tempRemoveIngredient(currentRecommendation.id, ing) }
                    )
                }
            }

            item(key = "section_header") {
                Text(
                    text = "Etapas do Plano Prático do Dia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(visibleRecommendations, key = { it.id }) { item ->
                RecommendationItemRow(
                    recommendation = item,
                    tempRemovedIngredients = tempRemovedMap[item.id] ?: emptySet(),
                    isCurrent = item.id == currentRecommendation?.id,
                    onTempRemoveIngredient = { ing -> viewModel.tempRemoveIngredient(item.id, ing) }
                )
            }
        }

        // Dialog for Ingredients Catalog / Knowledge Base List
        if (showIngredientsListDialog) {
            val activeIngredients = viewModel.getAllActiveIngredients()
            AlertDialog(
                onDismissRequest = { showIngredientsListDialog = false },
                title = { Text("Lista de Ingredientes Ativos") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (activeIngredients.isEmpty()) {
                            Text("Não há ingredientes ativos no plano atual.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                text = "Toque em qualquer ingrediente para ver suas propriedades, indicações, dosagens e modo de preparo:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            activeIngredients.forEach { ing ->
                                Surface(
                                    onClick = {
                                        selectedIngredientForInfo = IngredientKnowledgeBase.getInfo(ing)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ing,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Ver detalhes",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showIngredientsListDialog = false }) {
                        Text("Fechar")
                    }
                }
            )
        }

        // Dialog for displaying detailed info about a selected ingredient
        selectedIngredientForInfo?.let { info ->
            AlertDialog(
                onDismissRequest = { selectedIngredientForInfo = null },
                title = { Text(text = info.name, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                text = info.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(4.dp)
                            )
                        }

                        HorizontalDivider()

                        Text(text = "💡 Propriedades:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(text = info.properties, style = MaterialTheme.typography.bodySmall)

                        Text(text = "🎯 Indicações e Benefícios:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(text = info.indications, style = MaterialTheme.typography.bodySmall)

                        Text(text = "🥄 Dosagem Recomendada:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(text = info.dosage, style = MaterialTheme.typography.bodySmall)

                        Text(text = "🥣 Modo de Preparo e Uso:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(text = info.preparationAndUse, style = MaterialTheme.typography.bodySmall)

                        Text(text = "⏰ Cronobiologia & Timing:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(text = info.chronobiology, style = MaterialTheme.typography.bodySmall)

                        Text(text = "⚠️ Precauções & Informações:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(text = info.precautions, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedIngredientForInfo = null }) {
                        Text("Entendi")
                    }
                }
            )
        }

        // Dialog for adding ingredient with Chronobiology analysis
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Adicionar Ingrediente") },
                text = {
                    Column {
                        Text(
                            text = "Digite o nome do ingrediente (ex: 'Chia', 'Magnésio', 'Melatonina'). O aplicativo analisará a cronobiologia e o integrará na etapa correta:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ingredientInput,
                            onValueChange = { ingredientInput = it },
                            placeholder = { Text("Ex: Chia, Magnésio, Melatonina...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (ingredientInput.isNotBlank()) {
                                viewModel.addIngredientOrPlan(ingredientInput)
                                showAddDialog = false
                                ingredientInput = ""
                            }
                        }
                    ) {
                        Text("Integrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Dialog for eliminating specific ingredients permanently (via top menu)
        if (showRemoveIngredientDialog) {
            val activeIngredients = viewModel.getAllActiveIngredients()
            AlertDialog(
                onDismissRequest = { showRemoveIngredientDialog = false },
                title = { Text("Eliminar Ingrediente") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (activeIngredients.isEmpty()) {
                            Text("Não há ingredientes ativos no momento.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                text = "Selecione o ingrediente que deseja eliminar de forma definitiva do plano global:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            activeIngredients.forEach { ing ->
                                Surface(
                                    onClick = {
                                        viewModel.removeIngredient(ing)
                                        showRemoveIngredientDialog = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = ing, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRemoveIngredientDialog = false }) {
                        Text("Fechar")
                    }
                }
            )
        }

        // Dialog for loading external plan file/text
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Carregar Plano") },
                text = {
                    Column {
                        Text(
                            text = "Cole o texto descritivo do plano (ex: 'Em jejum: Beba...') ou um JSON estruturado:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = jsonInput,
                            onValueChange = { jsonInput = it },
                            placeholder = { Text("Ex:\nEm jejum: Beba vinagre...\nCafé da manhã: Coma linhaça...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (jsonInput.isNotBlank()) {
                                viewModel.loadCustomPlan(jsonInput)
                                showImportDialog = false
                                jsonInput = ""
                            }
                        }
                    ) {
                        Text("Carregar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun CurrentRecommendationCard(
    recommendation: Recommendation,
    tempRemovedIngredients: Set<String>,
    onToggleComplete: () -> Unit,
    onTempRemoveIngredient: (String) -> Unit
) {
    val visibleComponents = recommendation.components.filter { it !in tempRemovedIngredients }
    val cleanedDescription = RoutineAiRefiner.refineDescription(recommendation.description, tempRemovedIngredients)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (recommendation.isCompleted)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Etapa Atual: ${recommendation.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (recommendation.isCompleted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Concluído",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Text(
                        text = recommendation.timeSlot,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = cleanedDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // Ingredient Chips (Momentary removal on 'x')
            if (visibleComponents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    visibleComponents.forEach { ing ->
                        InputChip(
                            selected = false,
                            onClick = { onTempRemoveIngredient(ing) },
                            label = { Text(ing, style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover momentaneamente $ing",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
                if (tempRemovedIngredients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "*(Refeição ajustada sem: ${tempRemovedIngredients.joinToString(", ")})*",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recommendation.isCompleted)
                        MaterialTheme.colorScheme.outline
                    else
                        MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                if (recommendation.isCompleted) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Concluído")
                } else {
                    Text(text = "Marcar como feito")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationItemRow(
    recommendation: Recommendation,
    tempRemovedIngredients: Set<String>,
    isCurrent: Boolean,
    onTempRemoveIngredient: (String) -> Unit
) {
    val visibleComponents = recommendation.components.filter { it !in tempRemovedIngredients }
    val cleanedDescription = RoutineAiRefiner.refineDescription(recommendation.description, tempRemovedIngredients)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (recommendation.isCompleted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else if (isCurrent)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (recommendation.isCompleted)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    if (recommendation.isCompleted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Concluído",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = recommendation.timeSlot,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cleanedDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // Ingredient Chips for momentary removal on 'x'
            if (visibleComponents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    visibleComponents.forEach { ing ->
                        SuggestionChip(
                            onClick = { onTempRemoveIngredient(ing) },
                            label = { Text(ing, style = MaterialTheme.typography.labelSmall) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover momentaneamente $ing",
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
