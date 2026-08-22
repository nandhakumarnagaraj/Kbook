package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.remote.api.RoleTemplateDto
import com.khanabook.lite.pos.ui.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.StaffMemberPermissions
import com.khanabook.lite.pos.ui.viewmodel.StaffPermissionViewModel

@Composable
fun StaffPermissionScreen(
    onBack: () -> Unit,
    viewModel: StaffPermissionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing

    KhanaBookScreenScaffold(title = "Staff Permissions", onBack = onBack) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGold)
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "", color = ErrorPink, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(spacing.medium))
                    Button(onClick = { viewModel.loadData() }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) {
                        Text("Retry", color = DarkBrown1)
                    }
                }
            }
        } else {
            var showCreateTemplate by remember { mutableStateOf(false) }
            var applyTemplate by remember { mutableStateOf<RoleTemplateDto?>(null) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                state.infoMessage?.let { info ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(spacing.small),
                            color = DarkBrown2,
                            tonalElevation = spacing.extraSmall
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(info, style = MaterialTheme.typography.bodySmall, color = SuccessGreen, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.clearError() }) { Text("OK", color = PrimaryGold) }
                            }
                        }
                    }
                }

                // Role templates section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Role Templates (${state.templates.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextLight,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showCreateTemplate = true }) {
                            Icon(Icons.Filled.Add, "Create template", tint = PrimaryGold)
                        }
                    }
                }
                if (state.templates.isEmpty()) {
                    item {
                        Text(
                            "No templates yet. Create one to quickly assign a preset set of permissions to staff.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGold
                        )
                    }
                }
                items(state.templates) { template ->
                    RoleTemplateCard(
                        template = template,
                        onApply = { applyTemplate = template }
                    )
                }

                // Pending requests section
                if (state.pendingRequests.isNotEmpty()) {
                    item {
                        Text(
                            "Pending Requests (${state.pendingRequests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = WarningYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(state.pendingRequests) { request ->
                        PendingRequestCard(
                            userName = request.userName ?: "Staff #${request.userId}",
                            permissionName = request.permissionDisplayName ?: request.permissionKey,
                            reason = request.reason,
                            onApprove = { viewModel.approveRequest(request.id) },
                            onReject = { viewModel.rejectRequest(request.id) }
                        )
                    }
                    item { Spacer(Modifier.height(spacing.small)) }
                }

                // Staff permission toggles
                item {
                    Text(
                        "Staff Members",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextLight,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.staffList.isEmpty()) {
                    item {
                        Text(
                            "No staff members found. Add staff from Settings to manage their permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGold
                        )
                    }
                }

                items(state.staffList) { staff ->
                    StaffPermissionCard(
                        staff = staff,
                        categories = viewModel.permissionCategories,
                        onToggle = { key, granted -> viewModel.togglePermission(staff.userId, key, granted) }
                    )
                }
            }

            if (showCreateTemplate) {
                CreateTemplateDialog(
                    categories = viewModel.permissionCategories,
                    onDismiss = { showCreateTemplate = false },
                    onCreate = { name, desc, perms ->
                        viewModel.createTemplate(name, desc, perms)
                        showCreateTemplate = false
                    }
                )
            }

            applyTemplate?.let { template ->
                ApplyTemplateDialog(
                    template = template,
                    staffList = state.staffList,
                    onDismiss = { applyTemplate = null },
                    onApply = { userId ->
                        viewModel.applyTemplate(userId, template.id)
                        applyTemplate = null
                    }
                )
            }
        }
    }
}

@Composable
private fun PendingRequestCard(
    userName: String,
    permissionName: String,
    reason: String?,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Surface(
        shape = RoundedCornerShape(spacing.small),
        color = DarkBrown2,
        tonalElevation = spacing.extraSmall
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(userName, style = MaterialTheme.typography.bodyLarge, color = TextLight, fontWeight = FontWeight.SemiBold)
                Text("Wants: $permissionName", style = MaterialTheme.typography.bodySmall, color = TextGold)
                if (!reason.isNullOrBlank()) {
                    Text("Reason: $reason", style = MaterialTheme.typography.bodySmall, color = TextGold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                IconButton(onClick = onApprove) {
                    Icon(Icons.Filled.Check, "Approve", tint = SuccessGreen)
                }
                IconButton(onClick = onReject) {
                    Icon(Icons.Filled.Close, "Reject", tint = DangerRed)
                }
            }
        }
    }
}

@Composable
private fun StaffPermissionCard(
    staff: StaffMemberPermissions,
    categories: List<Pair<String, List<Pair<String, String>>>>,
    onToggle: (permissionKey: String, currentlyGranted: Boolean) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(spacing.small),
        color = DarkBrown2,
        tonalElevation = spacing.extraSmall
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Icon(Icons.Filled.Person, null, tint = PrimaryGold)
                    Column {
                        Text(staff.name, style = MaterialTheme.typography.bodyLarge, color = TextLight, fontWeight = FontWeight.SemiBold)
                        Text(staff.role, style = MaterialTheme.typography.bodySmall, color = TextGold)
                    }
                }
                val grantedCount = staff.permissions.count { it.granted }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) "Collapse" else "$grantedCount permissions ▸",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Permission toggles (expandable)
            if (expanded) {
                Spacer(Modifier.height(spacing.small))
                categories.forEach { (categoryName, permissions) ->
                    Text(
                        categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = spacing.small)
                    )
                    permissions.forEach { (key, displayName) ->
                        val permItem = staff.permissions.find { it.key == key }
                        val isGranted = permItem?.granted ?: false
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(displayName, style = MaterialTheme.typography.bodySmall, color = TextLight)
                            KhanaBookSwitch(
                                checked = isGranted,
                                onCheckedChange = { onToggle(key, isGranted) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleTemplateCard(
    template: RoleTemplateDto,
    onApply: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Surface(
        shape = RoundedCornerShape(spacing.small),
        color = DarkBrown2,
        tonalElevation = spacing.extraSmall
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.bodyLarge, color = TextLight, fontWeight = FontWeight.SemiBold)
                if (!template.description.isNullOrBlank()) {
                    Text(template.description, style = MaterialTheme.typography.bodySmall, color = TextGold)
                }
                Text(
                    "${template.permissions.size} permissions",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGold
                )
            }
            Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) {
                Text("Apply to\u2026", color = DarkBrown1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CreateTemplateDialog(
    categories: List<Pair<String, List<Pair<String, String>>>>,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, permissions: List<String>) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        shape = KhanaRadii.modal,
        title = { Text("New Role Template", style = MaterialTheme.typography.titleLarge, color = TextLight) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Template name", color = TextGold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(spacing.small))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = TextGold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(spacing.medium))
                categories.forEach { (categoryName, permissions) ->
                    Text(
                        categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGold,
                        fontWeight = FontWeight.Bold
                    )
                    permissions.forEach { (key, displayName) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected[key] == true,
                                onCheckedChange = { selected[key] = it },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryGold)
                            )
                            Text(displayName, style = MaterialTheme.typography.bodySmall, color = TextLight)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, selected.filterValues { it }.keys.toList()) },
                enabled = name.isNotBlank() && selected.containsValue(true),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
            ) { Text("Create", color = DarkBrown1) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PrimaryGold) }
        }
    )
}

@Composable
private fun ApplyTemplateDialog(
    template: RoleTemplateDto,
    staffList: List<StaffMemberPermissions>,
    onDismiss: () -> Unit,
    onApply: (userId: Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        shape = KhanaRadii.modal,
        title = {
            Column {
                Text("Apply \"${template.name}\"", style = MaterialTheme.typography.titleLarge, color = TextLight)
                Text(
                    "Replaces this staff member's permissions with the ${template.permissions.size} in the template.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGold
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (staffList.isEmpty()) {
                    Text("No staff members found.", style = MaterialTheme.typography.bodyMedium, color = TextLight)
                }
                staffList.forEach { staff ->
                    TextButton(onClick = { onApply(staff.userId) }) {
                        Icon(Icons.Filled.Person, null, tint = PrimaryGold)
                        Spacer(Modifier.width(spacing.small))
                        Text(staff.name, color = TextLight)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PrimaryGold) }
        },
        confirmButton = {}
    )
}
