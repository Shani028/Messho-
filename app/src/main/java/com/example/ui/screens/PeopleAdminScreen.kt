package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.MemberProfile
import com.example.data.model.ShippingOptimizerEngine
import com.example.ui.theme.MeeshoMagenta
import com.example.ui.theme.MeeshoPlumDark
import com.example.ui.theme.ProfitGold
import com.example.ui.theme.SavingsGreen
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleAdminScreen(
    membersState: UiState<List<MemberProfile>>,
    isAdminLoggedIn: Boolean,
    onAdminLogin: (pin: String, adminDisplayName: String) -> Boolean,
    onAdminLogout: () -> Unit,
    onAddPersonOrAdmin: (name: String, role: String, hub: String, isAdmin: Boolean) -> Unit,
    onUpdatePersonOrAdmin: (member: MemberProfile, newName: String, newRole: String, newHub: String, newIsAdmin: Boolean) -> Unit,
    onDeletePersonOrAdmin: (member: MemberProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterMode by remember { mutableStateOf("ALL") } // ALL, ADMIN, PERSON
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var memberBeingEdited by remember { mutableStateOf<MemberProfile?>(null) }
    var memberPendingDelete by remember { mutableStateOf<MemberProfile?>(null) }

    // Admin Login Dialog
    if (showAdminLoginDialog) {
        val currentMembers = (membersState as? UiState.Success)?.data.orEmpty()
        val currentPrimaryAdmin = currentMembers.firstOrNull { it.isAdminPerson }
        var pinInput by remember { mutableStateOf("1234") }
        var adminNameInput by remember(currentPrimaryAdmin) {
            mutableStateOf(currentPrimaryAdmin?.personName ?: "Rajesh Sharma (Admin)")
        }
        var loginError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAdminLoginDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MeeshoMagenta
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin Portal Login")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Log in as Admin to unlock full workspace controls and set or change the Admin Name (Default PIN: 1234).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = adminNameInput,
                        onValueChange = { adminNameInput = it },
                        label = { Text("Admin Name (Can change anytime)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_name_input")
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Admin PIN (Default: 1234)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_pin_input")
                    )
                    if (loginError != null) {
                        Text(
                            text = loginError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = onAdminLogin(pinInput, adminNameInput)
                        if (ok) {
                            showAdminLoginDialog = false
                        } else {
                            loginError = "Incorrect PIN. Use 1234."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeeshoMagenta),
                    modifier = Modifier.testTag("confirm_admin_login_button")
                ) {
                    Text("Login as Admin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminLoginDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add New Person or Admin Dialog
    if (showAddPersonDialog) {
        var newName by remember { mutableStateOf("") }
        var newRole by remember { mutableStateOf("Vendor") }
        var newHub by remember { mutableStateOf("Surat Hub") }
        var newIsAdmin by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddPersonDialog = false },
            title = { Text("Add Admin or Person") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Person or Admin Full Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_person_name_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Set as Admin Account",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Switch(
                            checked = newIsAdmin,
                            onCheckedChange = { checked ->
                                newIsAdmin = checked
                                if (checked) newRole = "Admin"
                            },
                            modifier = Modifier.testTag("add_person_is_admin_switch")
                        )
                    }

                    Text(
                        text = "Role Designation",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShippingOptimizerEngine.roleOptions.forEach { role ->
                            FilterChip(
                                selected = newRole == role,
                                onClick = {
                                    newRole = role
                                    newIsAdmin = (role == "Admin")
                                },
                                label = { Text(role) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newHub,
                        onValueChange = { newHub = it },
                        label = { Text("Hub / City Location") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_person_hub_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAddPersonOrAdmin(newName, newRole, newHub, newIsAdmin)
                            showAddPersonDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeeshoMagenta),
                    modifier = Modifier.testTag("confirm_add_person_button")
                ) {
                    Text("Add to Directory")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPersonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename / Edit Person or Admin Dialog
    if (memberBeingEdited != null) {
        val target = memberBeingEdited!!
        var editedName by remember(target) { mutableStateOf(target.personName) }
        var editedRole by remember(target) { mutableStateOf(target.roleTitle) }
        var editedHub by remember(target) { mutableStateOf(target.hubLocation) }
        var editedIsAdmin by remember(target) { mutableStateOf(target.isAdminPerson) }

        AlertDialog(
            onDismissRequest = { memberBeingEdited = null },
            title = {
                Text(
                    if (target.isAdminPerson) {
                        "Change Admin Name / Role"
                    } else {
                        "Change Person Name / Role"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("New Name (Admin or Person)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_person_name_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Admin Privileges",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Checkbox(
                            checked = editedIsAdmin,
                            onCheckedChange = { checked ->
                                editedIsAdmin = checked
                                if (checked) editedRole = "Admin"
                                else if (editedRole == "Admin") editedRole = "Vendor"
                            },
                            modifier = Modifier.testTag("edit_person_admin_checkbox")
                        )
                    }

                    Text(
                        text = "Select Role",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShippingOptimizerEngine.roleOptions.forEach { role ->
                            FilterChip(
                                selected = editedRole == role,
                                onClick = {
                                    editedRole = role
                                    editedIsAdmin = (role == "Admin")
                                },
                                label = { Text(role) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editedHub,
                        onValueChange = { editedHub = it },
                        label = { Text("Hub / Location") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_person_hub_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdatePersonOrAdmin(
                            target,
                            editedName,
                            editedRole,
                            editedHub,
                            editedIsAdmin
                        )
                        memberBeingEdited = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeeshoMagenta),
                    modifier = Modifier.testTag("confirm_edit_person_button")
                ) {
                    Text("Save Name Change")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberBeingEdited = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog for Admin or Any Person
    if (memberPendingDelete != null) {
        val target = memberPendingDelete!!
        AlertDialog(
            onDismissRequest = { memberPendingDelete = null },
            title = {
                Text(
                    if (target.isAdminPerson) {
                        "Delete Admin '${target.personName}'?"
                    } else {
                        "Delete Person '${target.personName}'?"
                    }
                )
            },
            text = {
                Text(
                    "Are you sure you want to permanently remove '${target.personName}' (${target.roleTitle}) from the directory?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePersonOrAdmin(target)
                        memberPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_person_button")
                ) {
                    Text("Delete Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (membersState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = membersState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is UiState.Success -> {
                val allMembers = membersState.data
                val filteredMembers = when (filterMode) {
                    "ADMIN" -> allMembers.filter { it.isAdminPerson || it.roleTitle == "Admin" }
                    "PERSON" -> allMembers.filter { !it.isAdminPerson && it.roleTitle != "Admin" }
                    else -> allMembers
                }
                val primaryAdmin = allMembers.firstOrNull { it.isAdminPerson || it.roleTitle == "Admin" }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Admin Login & Control Banner
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MeeshoPlumDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isAdminLoggedIn) {
                                                Icons.Default.VerifiedUser
                                            } else {
                                                Icons.Default.Security
                                            },
                                            contentDescription = null,
                                            tint = if (isAdminLoggedIn) SavingsGreen else ProfitGold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isAdminLoggedIn) {
                                                "Admin Portal: Logged In"
                                            } else {
                                                "Admin Login & Access"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Surface(
                                        color = if (isAdminLoggedIn) SavingsGreen else ProfitGold,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = if (isAdminLoggedIn) "ADMIN ACTIVE" else "PIN: 1234",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isAdminLoggedIn) Color.White else MeeshoPlumDark,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = if (primaryAdmin != null) {
                                        "Current Admin: ${primaryAdmin.personName} (${primaryAdmin.hubLocation})"
                                    } else {
                                        "No Admin Entry — Tap 'Add Admin / Person' or 'Admin Login' to create one."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = "Yahan se aap Admin login kar sakte hain aur Admin ya kisi bhi person ka naam change (rename) ya delete kar sakte hain.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.76f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!isAdminLoggedIn) {
                                        Button(
                                            onClick = { showAdminLoginDialog = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ProfitGold,
                                                contentColor = MeeshoPlumDark
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("open_admin_login_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LockOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Admin Login",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        if (primaryAdmin != null) {
                                            Button(
                                                onClick = { memberBeingEdited = primaryAdmin },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ProfitGold,
                                                    contentColor = MeeshoPlumDark
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("change_primary_admin_name_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Change Admin Name",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = onAdminLogout,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("admin_logout_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Logout,
                                                contentDescription = "Logout Admin",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Exit Admin")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Add Person / Admin & Filter Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Admin & People Directory (${filteredMembers.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Rename or delete any Admin or Person below",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { showAddPersonDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeeshoMagenta,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("add_person_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Person")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterMode == "ALL",
                                onClick = { filterMode = "ALL" },
                                label = { Text("All (${allMembers.size})") },
                                modifier = Modifier.testTag("filter_all_chip")
                            )
                            FilterChip(
                                selected = filterMode == "ADMIN",
                                onClick = { filterMode = "ADMIN" },
                                label = {
                                    Text(
                                        "Admins (${allMembers.count { it.isAdminPerson || it.roleTitle == "Admin" }})"
                                    )
                                },
                                modifier = Modifier.testTag("filter_admin_chip")
                            )
                            FilterChip(
                                selected = filterMode == "PERSON",
                                onClick = { filterMode = "PERSON" },
                                label = {
                                    Text(
                                        "Vendors & Staff (${allMembers.count { !it.isAdminPerson && it.roleTitle != "Admin" }})"
                                    )
                                },
                                modifier = Modifier.testTag("filter_person_chip")
                            )
                        }
                    }

                    // 3. People & Admin Cards
                    items(filteredMembers, key = { it.id }) { member ->
                        PersonOrAdminCard(
                            member = member,
                            onEditClick = { memberBeingEdited = member },
                            onDeleteClick = { memberPendingDelete = member }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonOrAdminCard(
    member: MemberProfile,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isAdmin = member.isAdminPerson || member.roleTitle == "Admin"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("member_card_${member.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAdmin) MeeshoMagenta.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAdmin) {
                                Icons.Default.AdminPanelSettings
                            } else {
                                Icons.Default.Person
                            },
                            contentDescription = null,
                            tint = if (isAdmin) MeeshoMagenta else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = member.personName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${member.roleTitle} • ${member.hubLocation}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (isAdmin) MeeshoMagenta else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Text(
                        text = if (isAdmin) "ADMIN" else "PERSON",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAdmin) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("edit_person_button_${member.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Change Name",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Name / Role")
                }

                Button(
                    onClick = onDeleteClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("delete_person_button_${member.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Delete Person or Admin",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}
