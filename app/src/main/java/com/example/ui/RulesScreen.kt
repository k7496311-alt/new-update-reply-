package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.AutoReplyRule
import com.example.model.MatchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    modifier: Modifier = Modifier
) {
    val rules by viewModel.rulesState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<AutoReplyRule?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by name, keyword, reply...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_rules_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Sort Dropdown Selector
                var sortExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sort_rules_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (sortOrder) {
                                RuleSortOrder.PRIORITY_DESC -> "Sorted by: Priority (High to Low)"
                                RuleSortOrder.UPDATED_DESC -> "Sorted by: Last Updated"
                                RuleSortOrder.NAME_ASC -> "Sorted by: Name (A-Z)"
                                RuleSortOrder.CATEGORY_ASC -> "Sorted by: Category (A-Z)"
                            }
                        )
                    }

                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Priority (High to Low)") },
                            onClick = {
                                viewModel.setSortOrder(RuleSortOrder.PRIORITY_DESC)
                                sortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last Updated") },
                            onClick = {
                                viewModel.setSortOrder(RuleSortOrder.UPDATED_DESC)
                                sortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)") },
                            onClick = {
                                viewModel.setSortOrder(RuleSortOrder.NAME_ASC)
                                sortExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Category (A-Z)") },
                            onClick = {
                                viewModel.setSortOrder(RuleSortOrder.CATEGORY_ASC)
                                sortExpanded = false
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_rule_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Auto Reply Rule"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (rules.isEmpty()) {
                RulesEmptyState(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RuleItemCard(
                            rule = rule,
                            onToggleEnabled = { viewModel.toggleRuleEnabled(rule) },
                            onEdit = { ruleToEdit = rule },
                            onDuplicate = { viewModel.duplicateRule(rule) },
                            onDelete = { viewModel.deleteRule(rule) }
                        )
                    }
                }
            }

            if (showAddDialog) {
                RuleDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { name, keyword, reply, matchType, delay, priority, cooldown, maxReplies, category ->
                        viewModel.saveRule(
                            AutoReplyRule(
                                name = name,
                                keyword = keyword,
                                replyText = reply,
                                matchType = matchType,
                                replyDelayMillis = delay,
                                priority = priority,
                                cooldownMillis = cooldown,
                                maxReplies = maxReplies,
                                category = category
                            )
                        )
                        showAddDialog = false
                    }
                )
            }

            if (ruleToEdit != null) {
                RuleDialog(
                    ruleToEdit = ruleToEdit,
                    onDismiss = { ruleToEdit = null },
                    onConfirm = { name, keyword, reply, matchType, delay, priority, cooldown, maxReplies, category ->
                        ruleToEdit?.let { existing ->
                            viewModel.saveRule(
                                existing.copy(
                                    name = name,
                                    keyword = keyword,
                                    replyText = reply,
                                    matchType = matchType,
                                    replyDelayMillis = delay,
                                    priority = priority,
                                    cooldownMillis = cooldown,
                                    maxReplies = maxReplies,
                                    category = category,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        ruleToEdit = null
                    }
                )
            }
        }
    }
}

@Composable
fun RulesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Rules Configured",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the '+' button below to create your first automated text response rule or adjust your search filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleItemCard(
    rule: AutoReplyRule,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Name + Switches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category tag
                        SuggestionChip(
                            onClick = {},
                            label = { Text(rule.category) },
                            modifier = Modifier.height(24.dp)
                        )
                        // Match type tag
                        SuggestionChip(
                            onClick = {},
                            label = { Text(rule.matchType.name) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier
                            .testTag("rule_switch_${rule.id}")
                            .minimumInteractiveComponentSize()
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Keyword Trigger Info
            Column {
                Text(
                    text = "When incoming text matches:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\"${rule.keyword}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Auto-Reply Response Info
            Column {
                Text(
                    text = "Auto-Reply with:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\"${rule.replyText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Metadata row (Priority, Delay, Cooldown, Max Replies)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Priority",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Priority: ${rule.priority}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Reply Delay
                if (rule.replyDelayMillis > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = "Delay",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delay: ${rule.replyDelayMillis / 1000}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Cooldown
                if (rule.cooldownMillis > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Cooldown",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cooldown: ${rule.cooldownMillis / 1000}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Maximum Replies
                if (rule.maxReplies > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Max Replies",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Max Replies: ${rule.maxReplies}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Actions: Edit, Duplicate, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .testTag("edit_rule_button_${rule.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Rule",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier
                        .testTag("duplicate_rule_button_${rule.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicate Rule",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .testTag("delete_rule_button_${rule.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Rule",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDialog(
    ruleToEdit: AutoReplyRule? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        keyword: String,
        reply: String,
        matchType: MatchType,
        delay: Long,
        priority: Int,
        cooldown: Long,
        maxReplies: Int,
        category: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(ruleToEdit?.name ?: "") }
    var keyword by remember { mutableStateOf(ruleToEdit?.keyword ?: "") }
    var replyText by remember { mutableStateOf(ruleToEdit?.replyText ?: "") }
    var selectedMatchType by remember { mutableStateOf(ruleToEdit?.matchType ?: MatchType.CONTAINS) }
    var delaySecs by remember { mutableStateOf((ruleToEdit?.replyDelayMillis?.div(1000) ?: 0L).toString()) }
    var cooldownSecs by remember { mutableStateOf((ruleToEdit?.cooldownMillis?.div(1000) ?: 0L).toString()) }
    var maxReplies by remember { mutableStateOf((ruleToEdit?.maxReplies ?: 0).toString()) }
    var priority by remember { mutableStateOf((ruleToEdit?.priority ?: 0).toString()) }
    var category by remember { mutableStateOf(ruleToEdit?.category ?: "General") }
    
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (ruleToEdit == null) "New Reply Rule" else "Edit Reply Rule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_name")
                )

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Keyword Trigger") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_keyword")
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedMatchType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Match Rule") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        MatchType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedMatchType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Reply Message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reply_text")
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_category")
                )

                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it },
                    label = { Text("Priority (Higher first)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_priority")
                )

                OutlinedTextField(
                    value = delaySecs,
                    onValueChange = { delaySecs = it },
                    label = { Text("Send Delay (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_delay")
                )

                OutlinedTextField(
                    value = cooldownSecs,
                    onValueChange = { cooldownSecs = it },
                    label = { Text("Cooldown Duration (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_cooldown")
                )

                OutlinedTextField(
                    value = maxReplies,
                    onValueChange = { maxReplies = it },
                    label = { Text("Maximum Replies") },
                    placeholder = { Text("0 for unlimited") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_max_replies")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && keyword.isNotBlank() && replyText.isNotBlank()) {
                        val delayVal = delaySecs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val cooldownVal = cooldownSecs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val maxRepliesVal = maxReplies.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val priorityVal = priority.toIntOrNull() ?: 0
                        onConfirm(
                            name,
                            keyword,
                            replyText,
                            selectedMatchType,
                            delayVal * 1000L,
                            priorityVal,
                            cooldownVal * 1000L,
                            maxRepliesVal,
                            category.ifBlank { "General" }
                        )
                    }
                },
                modifier = Modifier.testTag("btn_confirm_add_rule")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_add_rule")
            ) {
                Text("Cancel")
            }
        }
    )
}
