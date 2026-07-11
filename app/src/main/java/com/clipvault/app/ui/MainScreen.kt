package com.clipvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPasteOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clipvault.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ClipViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    // Selection can't survive the list changing out from under it (e.g. new
    // capture trims history) - drop any ids that no longer exist.
    LaunchedEffect(state.entries) {
        val liveIds = state.entries.map { it.id }.toSet()
        if (selectedIds.isNotEmpty() && !liveIds.containsAll(selectedIds)) {
            selectedIds = selectedIds.intersect(liveIds)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedIds.isEmpty()) {
                SearchRow(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onOpenSettings = onOpenSettings
                )
            } else {
                SelectionRow(
                    count = selectedIds.size,
                    onClose = { selectedIds = emptySet() },
                    onDelete = { showDeleteConfirm = true }
                )
            }

            if (state.entries.isEmpty()) {
                EmptyState(
                    isSearching = state.query.isNotBlank(),
                    captureLikelyActive = state.rootCaptureEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        ClipEntryItem(
                            entry = entry,
                            selected = entry.id in selectedIds,
                            onClick = {
                                if (selectedIds.isEmpty()) {
                                    viewModel.activate(entry)
                                } else {
                                    selectedIds = toggled(selectedIds, entry.id)
                                }
                            },
                            onLongClick = {
                                selectedIds = toggled(selectedIds, entry.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (count == 1) "Delete entry?" else "Delete $count entries?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntries(selectedIds)
                    selectedIds = emptySet()
                    showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private fun toggled(ids: Set<Long>, id: Long): Set<Long> =
    if (id in ids) ids - id else ids + id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings"
            )
        }
    }
}

/**
 * Replaces the search row, in place, while one or more entries are long-press
 * selected - same row position/padding as the search bar it swaps out for.
 */
@Composable
private fun SelectionRow(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleLarge
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete selected",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    captureLikelyActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Filled.ClearAll else Icons.Filled.ContentPasteOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = if (isSearching) {
                    stringResource(R.string.empty_search)
                } else {
                    stringResource(R.string.empty_title)
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            if (!isSearching) {
                Text(
                    text = stringResource(
                        if (captureLikelyActive) R.string.empty_subtitle_active
                        else R.string.empty_subtitle_inactive
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
