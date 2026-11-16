package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.ShipSmartRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.OptimizerScreen
import com.example.ui.screens.PeopleAdminScreen
import com.example.ui.theme.MeeshoMagenta
import com.example.ui.theme.MeeshoPlumDark
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ProfitGold
import com.example.ui.theme.SavingsGreen
import com.example.ui.viewmodel.ShipSmartViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        MainFeatureScreen(
            currentUserId = user.uid,
            currentUserEmail = user.email,
            currentUserDisplayName = user.displayName
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureScreen(
    currentUserId: String,
    currentUserEmail: String?,
    currentUserDisplayName: String?
) {
    val viewModel: ShipSmartViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ShipSmartViewModel(
                    repository = ShipSmartRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName
                )
            }
        }
    )

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val skusState by viewModel.skusState.collectAsStateWithLifecycle()
    val membersState by viewModel.membersState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val optimizationResult by viewModel.optimizationResult.collectAsStateWithLifecycle()
    val isAdminLoggedIn by viewModel.isAdminLoggedIn.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // BackHandler on secondary tabs to return to primary Optimizer tab
    if (selectedTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    val totalCatalogMonthlySavings = remember(skusState) {
        (skusState as? UiState.Success)?.data?.sumOf { it.monthlySavings } ?: 0L
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = ProfitGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAdminLoggedIn) "Admin Mode • Full Control" else "Vendor Logistics Suite",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        color = if (isAdminLoggedIn) SavingsGreen else ProfitGold,
                        shape = CircleShape,
                        modifier = Modifier
                            .clickable { selectedTab = 2 }
                            .testTag("top_bar_admin_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isAdminLoggedIn) {
                                    Icons.Default.VerifiedUser
                                } else {
                                    Icons.Default.AdminPanelSettings
                                },
                                contentDescription = null,
                                tint = if (isAdminLoggedIn) Color.White else MeeshoPlumDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAdminLoggedIn) "Admin" else "Admin Login",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isAdminLoggedIn) Color.White else MeeshoPlumDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            signOut(
                                context = context,
                                credentialManager = credentialManager,
                                onSignOutComplete = {},
                                scope = coroutineScope
                            )
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(id = R.string.sign_out_description),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeeshoPlumDark,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                            contentDescription = stringResource(id = R.string.tab_optimizer)
                        )
                    },
                    label = { Text(stringResource(id = R.string.tab_optimizer)) },
                    modifier = Modifier.testTag("nav_tab_optimizer")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                            contentDescription = stringResource(id = R.string.tab_catalog)
                        )
                    },
                    label = { Text(stringResource(id = R.string.tab_catalog)) },
                    modifier = Modifier.testTag("nav_tab_catalog")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) {
                                Icons.Filled.AdminPanelSettings
                            } else {
                                Icons.Outlined.AdminPanelSettings
                            },
                            contentDescription = stringResource(id = R.string.tab_people_admin)
                        )
                    },
                    label = { Text(stringResource(id = R.string.tab_people_admin)) },
                    modifier = Modifier.testTag("nav_tab_people_admin")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = statusBanner != null) {
                Surface(
                    color = MeeshoMagenta,
                    shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusBanner ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusBanner() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> OptimizerScreen(
                        formState = formState,
                        optimizationResult = optimizationResult,
                        presets = viewModel.presets,
                        totalCatalogMonthlySavings = totalCatalogMonthlySavings,
                        onUpdateForm = viewModel::updateForm,
                        onSelectPreset = viewModel::applyPreset,
                        onApplySmartDimensions = viewModel::applySmartDimensionsToCalculator,
                        onSaveSku = viewModel::saveCurrentSkuToCatalog
                    )

                    1 -> CatalogScreen(
                        skusState = skusState,
                        onLoadSkuIntoOptimizer = { sku ->
                            viewModel.loadSkuForEditing(sku)
                            selectedTab = 0
                        },
                        onRenameSku = viewModel::renameSku,
                        onDeleteSku = { sku ->
                            viewModel.deleteSku(sku.id, sku.productName)
                        }
                    )

                    2 -> PeopleAdminScreen(
                        membersState = membersState,
                        isAdminLoggedIn = isAdminLoggedIn,
                        onAdminLogin = viewModel::loginAsAdmin,
                        onAdminLogout = viewModel::logoutAdmin,
                        onAddPersonOrAdmin = viewModel::addPersonOrAdmin,
                        onUpdatePersonOrAdmin = viewModel::updatePersonOrAdmin,
                        onDeletePersonOrAdmin = viewModel::deletePersonOrAdmin
                    )
                }
            }
        }
    }
}
