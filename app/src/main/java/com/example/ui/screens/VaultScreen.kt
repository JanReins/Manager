package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultEntry
import com.example.ui.VaultUiState
import com.example.ui.VaultViewModel
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.BlueInfo
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.PurpleBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    entries: List<VaultEntry>,
    onAddEntry: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    val categories = listOf("All", "Favorites", "Login", "Card", "Note", "Identity")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Amber500.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VaultLock",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Encrypted Vault • Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenGenerator,
                        modifier = Modifier.testTag("nav_generator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Password Generator",
                            tint = Amber400
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("nav_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onLock,
                        modifier = Modifier.testTag("nav_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddEntry,
                containerColor = Amber500,
                contentColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("add_entry_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add new entry")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Item", fontWeight = FontWeight.Bold)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search by title, username, website...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Amber400)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = uiState.selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(category) },
                        label = {
                            Text(
                                text = category,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber500,
                            selectedLabelColor = Color(0xFF0F172A),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("filter_chip_${category.lowercase()}")
                    )
                }
            }

            // Entries List or Empty State
            if (entries.isEmpty()) {
                EmptyVaultView(
                    searchQuery = uiState.searchQuery,
                    selectedCategory = uiState.selectedCategory,
                    onAddEntry = onAddEntry
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        VaultEntryCard(
                            entry = entry,
                            activeCopiedLabel = uiState.activeCopiedLabel,
                            onCardClick = { onEditEntry(entry.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(entry.id) },
                            onCopyUsername = {
                                if (entry.username.isNotBlank()) {
                                    viewModel.copyToClipboard(context, entry.username, "Username")
                                }
                            },
                            onCopyPassword = {
                                if (entry.password.isNotBlank()) {
                                    viewModel.copyToClipboard(context, entry.password, "Password")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultEntryCard(
    entry: VaultEntry,
    activeCopiedLabel: String?,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick)
            .testTag("vault_card_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category / Domain Avatar Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                getCategoryColor(entry.category).copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (entry.category == "Login" && entry.displayBadge.length <= 2) {
                    Text(
                        text = entry.displayBadge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Amber400
                    )
                } else {
                    Icon(
                        imageVector = getCategoryIcon(entry.category),
                        contentDescription = null,
                        tint = getCategoryColor(entry.category),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                if (entry.username.isNotBlank()) {
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (entry.displayHost.isNotBlank()) {
                    Text(
                        text = entry.displayHost,
                        style = MaterialTheme.typography.bodySmall,
                        color = BlueInfo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Buttons: Copy Username, Copy Password, Favorite
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.username.isNotBlank()) {
                    IconButton(
                        onClick = onCopyUsername,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("copy_user_btn_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Copy username",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (entry.password.isNotBlank()) {
                    IconButton(
                        onClick = onCopyPassword,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("copy_pass_btn_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Copy password",
                            tint = Amber400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("fav_btn_${entry.id}")
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (entry.isFavorite) "Unmark favorite" else "Mark favorite",
                        tint = if (entry.isFavorite) Amber400 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyVaultView(
    searchQuery: String,
    selectedCategory: String,
    onAddEntry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.Lock,
                contentDescription = null,
                tint = Amber400,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (searchQuery.isNotBlank()) "No matching entries" else "Your Vault is Empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (searchQuery.isNotBlank()) {
                "Try searching for a different title, username, or website."
            } else if (selectedCategory != "All") {
                "No entries in category '$selectedCategory'."
            } else {
                "Store your passwords, cards, and secure notes safely with zero online tracking."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (searchQuery.isBlank() && selectedCategory == "All") {
            Spacer(modifier = Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = onAddEntry,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Amber500),
                modifier = Modifier.testTag("empty_add_entry_btn")
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Your First Entry", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "Card" -> Icons.Default.CreditCard
        "Note" -> Icons.Default.Description
        "Identity" -> Icons.Default.Person
        else -> Icons.Default.Lock
    }
}

fun getCategoryColor(category: String): Color {
    return when (category) {
        "Card" -> BlueInfo
        "Note" -> PurpleBadge
        "Identity" -> EmeraldSuccess
        else -> Amber400
    }
}
