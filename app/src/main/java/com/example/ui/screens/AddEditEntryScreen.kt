package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultEntry
import com.example.generator.PasswordGenerator
import com.example.ui.VaultViewModel
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.RedError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEntryScreen(
    entryId: Long,
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Login") }
    var isFavorite by remember { mutableStateOf(false) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }

    var showPassword by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showGeneratorSheet by remember { mutableStateOf(false) }

    val categories = listOf("Login", "Card", "Note", "Identity")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load initial entry if editing
    LaunchedEffect(entryId) {
        if (entryId != 0L) {
            val entry = viewModel.getEntry(entryId)
            if (entry != null) {
                title = entry.title
                username = entry.username
                password = entry.password
                url = entry.url
                notes = entry.notes
                category = entry.category
                isFavorite = entry.isFavorite
                createdAt = entry.createdAt
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (entryId == 0L) "New Item" else "Edit Item",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("entry_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                },
                actions = {
                    // Favorite Toggle
                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier.testTag("entry_fav_toggle")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Toggle favorite",
                            tint = if (isFavorite) Amber400 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Save Button in TopBar
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                val newEntry = VaultEntry(
                                    id = entryId,
                                    title = title.trim(),
                                    username = username.trim(),
                                    password = password,
                                    url = url.trim(),
                                    notes = notes.trim(),
                                    category = category,
                                    isFavorite = isFavorite,
                                    createdAt = createdAt,
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.saveEntry(newEntry, onNavigateBack)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("entry_save_btn")
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
        ) {
            // Category Selector
            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                color = Amber400,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = category == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { category = cat },
                        label = { Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                imageVector = getCategoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber500,
                            selectedLabelColor = Color(0xFF0F172A),
                            selectedLeadingIconColor = Color(0xFF0F172A),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text("Title *") },
                placeholder = { Text("e.g., Google, Netflix, Bank Account") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("Title is required", color = RedError) }
                } else null,
                leadingIcon = {
                    Icon(Icons.Default.Title, contentDescription = null, tint = Amber400)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_title_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber400,
                    cursorColor = Amber400,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Username / Email Field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username / Email") },
                placeholder = { Text("e.g., user@example.com") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Amber400)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_username_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber400,
                    cursorColor = Amber400,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Field
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Amber400)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("entry_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber400,
                            cursorColor = Amber400
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Strength bar if password entered
                    if (password.isNotEmpty()) {
                        val strength = PasswordGenerator.evaluateStrength(password)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Strength: ${strength.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(strength.colorHex),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${password.length} characters",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { strength.score },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(strength.colorHex),
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Generator quick button
                    OutlinedButton(
                        onClick = { showGeneratorSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_password_for_entry_btn")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Generate Strong Password",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Website / URL Field
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website / URL") },
                placeholder = { Text("https://example.com") },
                leadingIcon = {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Amber400)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber400,
                    cursorColor = Amber400,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Notes Field
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Security questions, recovery codes, PINs, etc.") },
                leadingIcon = {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Amber400)
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_notes_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber400,
                    cursorColor = Amber400,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            )

            // If editing, provide Delete Action
            if (entryId != 0L) {
                Spacer(modifier = Modifier.height(28.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("delete_entry_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = RedError)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Entry", fontWeight = FontWeight.Bold, color = RedError)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Entry") },
                text = { Text("Are you sure you want to permanently delete '$title' from your vault? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteEntry(entryId, onNavigateBack)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedError)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Password Generator Modal Bottom Sheet
        if (showGeneratorSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGeneratorSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                PasswordGeneratorContent(
                    viewModel = viewModel,
                    onUsePassword = { generated ->
                        password = generated
                        showPassword = true
                        showGeneratorSheet = false
                    },
                    onClose = { showGeneratorSheet = false }
                )
            }
        }
    }
}
