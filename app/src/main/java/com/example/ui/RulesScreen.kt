package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutoReplyRule
import com.example.model.MatchType

private val TealHeaderColor = Color(0xFF00897B)
private val TealBannerColor = Color(0xFF00796B)
private val ChatBgColor = Color(0xFFE8E5E0)
private val IncomingBubbleColor = Color(0xFFFFFFFF)
private val OutgoingBubbleColor = Color(0xFFDCF8C6)
private val FieldBgColor = Color(0xFFF2F2F2)
private val TextSecondaryColor = Color(0xFF666666)

@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rules by viewModel.rulesState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var isCreateOrEditOpen by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoReplyRule?>(null) }

    if (isCreateOrEditOpen || editingRule != null) {
        CustomReplyEditScreen(
            ruleToEdit = editingRule,
            onBack = {
                isCreateOrEditOpen = false
                editingRule = null
            },
            onSave = { keyword, reply ->
                if (editingRule != null) {
                    val existing = editingRule!!
                    viewModel.saveRule(
                        context = context,
                        rule = existing.copy(
                            name = "Custom Reply: $keyword",
                            keyword = keyword,
                            replyText = reply,
                            matchType = MatchType.CONTAINS,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    viewModel.saveRule(
                        context = context,
                        rule = AutoReplyRule(
                            name = "Custom Reply: $keyword",
                            keyword = keyword,
                            replyText = reply,
                            matchType = MatchType.CONTAINS
                        )
                    )
                }
                isCreateOrEditOpen = false
                editingRule = null
            }
        )
    } else {
        CustomReplyListScreen(
            viewModel = viewModel,
            rules = rules,
            searchQuery = searchQuery,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            onToggleAll = { enabled -> viewModel.toggleAllRules(context, enabled) },
            onToggleRule = { rule -> viewModel.toggleRuleEnabled(context, rule) },
            onEditRule = { rule -> editingRule = rule },
            onDeleteRule = { rule -> viewModel.deleteRule(context, rule) },
            onAddClick = {
                editingRule = null
                isCreateOrEditOpen = true
            },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReplyListScreen(
    viewModel: RulesViewModel,
    rules: List<AutoReplyRule>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onToggleRule: (AutoReplyRule) -> Unit,
    onEditRule: (AutoReplyRule) -> Unit,
    onDeleteRule: (AutoReplyRule) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(false) }
    var masterSwitchOn by remember { mutableStateOf(true) }
    var isOptionsMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // System File Picker Launcher for Restore button
    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.restoreRulesFromUri(context, it) { count ->
                Toast.makeText(
                    context,
                    "Restored $count custom reply rules from XLS file.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Sync master switch state with rules enable status
    LaunchedEffect(rules) {
        if (rules.isNotEmpty()) {
            masterSwitchOn = rules.any { it.isEnabled }
        }
    }

    // Confirmation dialog for All Clear option
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("All Clear") },
            text = { Text("Are you sure you want to delete all reply rules and clear the XLS backup file?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAllRulesAndBackup(context) {
                            Toast.makeText(
                                context,
                                "All reply rules and XLS files cleared.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("Clear All", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                // Top App Bar matching design
                Surface(
                    color = TealHeaderColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(56.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { /* Navigation back */ }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Custom reply",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { /* Clock / History */ }) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "History",
                                    tint = Color.White
                                )
                            }

                            // 3-Dot Menu Button with Restore, Backup, All Clear options
                            Box {
                                IconButton(
                                    onClick = { isOptionsMenuExpanded = true },
                                    modifier = Modifier.testTag("rules_3dot_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = isOptionsMenuExpanded,
                                    onDismissRequest = { isOptionsMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Restore", fontSize = 16.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = "Restore",
                                                tint = TealHeaderColor
                                            )
                                        },
                                        onClick = {
                                            isOptionsMenuExpanded = false
                                            restoreFilePickerLauncher.launch("*/*")
                                        },
                                        modifier = Modifier.testTag("menu_restore_item")
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Backup", fontSize = 16.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Backup,
                                                contentDescription = "Backup",
                                                tint = TealHeaderColor
                                            )
                                        },
                                        onClick = {
                                            isOptionsMenuExpanded = false
                                            viewModel.backupRules(context) { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.testTag("menu_backup_item")
                                    )

                                    DropdownMenuItem(
                                        text = { Text("All Clear", fontSize = 16.sp, color = Color.Red) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = "All Clear",
                                                tint = Color.Red
                                            )
                                        },
                                        onClick = {
                                            isOptionsMenuExpanded = false
                                            showClearConfirmDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_all_clear_item")
                                    )
                                }
                            }
                        }
                    }
                }

                // Search field dropdown if active
                if (isSearchActive) {
                    Surface(
                        color = Color.White,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Search custom replies...") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .testTag("custom_reply_search_input")
                        )
                    }
                }

                // ON / OFF Toggle Banner directly under Top Bar
                Surface(
                    color = TealBannerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (masterSwitchOn) "ON" else "OFF",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Switch(
                            checked = masterSwitchOn,
                            onCheckedChange = { newState ->
                                masterSwitchOn = newState
                                onToggleAll(newState)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF004D40),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF80CBC4)
                            ),
                            modifier = Modifier.testTag("master_custom_reply_switch")
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = TealHeaderColor,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 12.dp)
                    .testTag("add_custom_reply_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add custom reply",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAF9F6))
                .padding(innerPadding)
        ) {
            if (rules.isEmpty()) {
                // Empty State Design matching Screenshot 1
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QuestionAnswer,
                            contentDescription = null,
                            tint = TealHeaderColor,
                            modifier = Modifier.size(76.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "To create custom reply text, tap + at bottom of your screen.",
                        color = Color(0xFF555555),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // List of created custom replies
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        CustomReplyItemCard(
                            rule = rule,
                            onToggle = { onToggleRule(rule) },
                            onEdit = { onEditRule(rule) },
                            onDelete = { onDeleteRule(rule) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomReplyItemCard(
    rule: AutoReplyRule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("custom_reply_card_${rule.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Contains", fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFFE0F2F1),
                            labelColor = TealHeaderColor
                        ),
                        modifier = Modifier.height(26.dp)
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TealHeaderColor
                    ),
                    modifier = Modifier.testTag("rule_item_switch_${rule.id}")
                )
            }

            // Preview Bubbles in card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChatBgColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Incoming
                Box(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IncomingBubbleColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = rule.keyword,
                        fontSize = 14.sp,
                        color = Color(0xFF222222)
                    )
                }

                // Outgoing
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OutgoingBubbleColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = rule.replyText,
                        fontSize = 14.sp,
                        color = Color(0xFF222222)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = TealHeaderColor
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReplyEditScreen(
    ruleToEdit: AutoReplyRule?,
    onBack: () -> Unit,
    onSave: (keyword: String, reply: String) -> Unit
) {
    var incomingKeyword by remember { mutableStateOf(ruleToEdit?.keyword ?: "hi") }
    var replyMessage by remember { mutableStateOf(ruleToEdit?.replyText ?: "hello") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                color = TealHeaderColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Custom reply",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = {
                            if (incomingKeyword.isNotBlank() && replyMessage.isNotBlank()) {
                                onSave(incomingKeyword.trim(), replyMessage.trim())
                            }
                        },
                        modifier = Modifier.testTag("save_custom_reply_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Chat Preview Wallpaper Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(ChatBgColor)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top-Left Incoming Chat Bubble Preview
                    Box(
                        modifier = Modifier
                            .align(Alignment.Start)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IncomingBubbleColor)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = incomingKeyword.ifBlank { "hi" },
                            fontSize = 16.sp,
                            color = Color(0xFF222222)
                        )
                    }

                    // Bottom-Right Outgoing Chat Bubble Preview
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(8.dp))
                            .background(OutgoingBubbleColor)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = replyMessage.ifBlank { "hello" },
                            fontSize = 16.sp,
                            color = Color(0xFF222222)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Fields Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Field 1: Incoming Keyword
                Column {
                    TextField(
                        value = incomingKeyword,
                        onValueChange = { incomingKeyword = it },
                        label = { Text("Incoming Keyword") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = FieldBgColor,
                            unfocusedContainerColor = FieldBgColor,
                            focusedIndicatorColor = TealHeaderColor,
                            unfocusedIndicatorColor = Color(0xFFCCCCCC),
                            focusedLabelColor = TealHeaderColor,
                            unfocusedLabelColor = TextSecondaryColor
                        ),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_incoming_keyword")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Example: Hi, how are you",
                        color = TextSecondaryColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Field 2: Reply message
                Column {
                    TextField(
                        value = replyMessage,
                        onValueChange = { replyMessage = it },
                        label = { Text("Reply message") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = FieldBgColor,
                            unfocusedContainerColor = FieldBgColor,
                            focusedIndicatorColor = TealHeaderColor,
                            unfocusedIndicatorColor = Color(0xFFCCCCCC),
                            focusedLabelColor = TealHeaderColor,
                            unfocusedLabelColor = TextSecondaryColor
                        ),
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_reply_message")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Example: Hi I am good.",
                        color = TextSecondaryColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
