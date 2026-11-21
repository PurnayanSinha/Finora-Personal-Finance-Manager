package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.ThemePreference
import com.example.domain.TransactionType
import com.example.ui.auth.AuthFlow
import com.example.ui.auth.AuthViewModel
import com.example.ui.main.FinanceViewModel
import com.example.ui.main.UiEvent
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RecurringTransactionsScreen
import com.example.ui.screens.SavingsGoalsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionDetailScreen
import com.example.ui.screens.TransactionFormScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.FinoraTheme
import com.example.ui.theme.LocalFinoraColors

private object FinoraRoutes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val ANALYTICS = "analytics"
    const val BUDGETS = "budgets"
    const val PROFILE = "profile"
    const val ADD_TRANSACTION = "add_transaction/{type}"
    const val EDIT_TRANSACTION = "edit_transaction/{id}"
    const val TRANSACTION_DETAIL = "transaction_detail/{id}"
    const val GOALS = "goals"
    const val RECURRING = "recurring"
    const val CALENDAR = "calendar"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"

    fun addTransaction(type: TransactionType) = "add_transaction/${type.name}"
    fun editTransaction(id: String) = "edit_transaction/$id"
    fun transactionDetail(id: String) = "transaction_detail/$id"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(FinoraRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(FinoraRoutes.TRANSACTIONS, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    BottomNavItem(FinoraRoutes.ANALYTICS, "Analytics", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    BottomNavItem(FinoraRoutes.BUDGETS, "Budgets", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    BottomNavItem(FinoraRoutes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FinoraApp

        setContent {
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(app.authRepository)
            )
            val activeUserId by authViewModel.activeUserId.collectAsStateWithLifecycle()
            val fallbackTheme by authViewModel.fallbackTheme.collectAsStateWithLifecycle()

            if (activeUserId == null) {
                val dark = when (fallbackTheme) {
                    ThemePreference.SYSTEM -> isSystemInDarkTheme()
                    ThemePreference.LIGHT -> false
                    ThemePreference.DARK -> true
                }
                FinoraTheme(darkTheme = dark) {
                    AuthFlow(authViewModel = authViewModel)
                }
            } else {
                val uid = activeUserId!!
                key(uid) {
                    val financeViewModel: FinanceViewModel = viewModel(
                        key = uid,
                        factory = FinanceViewModel.Factory(
                            userId = uid,
                            authRepository = app.authRepository,
                            financeRepository = app.financeRepository
                        )
                    )
                    val settings by financeViewModel.settings.collectAsStateWithLifecycle()
                    val dark = when (settings.themePreference) {
                        ThemePreference.SYSTEM -> isSystemInDarkTheme()
                        ThemePreference.LIGHT -> false
                        ThemePreference.DARK -> true
                    }
                    FinoraTheme(darkTheme = dark) {
                        FinoraAuthenticatedApp(viewModel = financeViewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinoraAuthenticatedApp(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showQuickAddSheet by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topLevelRoutes = remember { bottomNavItems.map { it.route }.toSet() }
    val showTopLevelChrome = currentRoute in topLevelRoutes

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.ShareFile -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, event.title))
                }
            }
        }
    }

    if (showQuickAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickAddSheet = false }
        ) {
            QuickAddSheetContent(
                onSelectType = { type ->
                    showQuickAddSheet = false
                    navController.navigate(FinoraRoutes.addTransaction(type))
                }
            )
        }
    }

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val historyTransactions by viewModel.filteredHistoryTransactions.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val analyticsState by viewModel.analyticsState.collectAsStateWithLifecycle()
    val budgetMonthYear by viewModel.budgetMonthYear.collectAsStateWithLifecycle()
    val budgetStatuses by viewModel.selectedMonthBudgetStatuses.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val recurring by viewModel.recurringTransactions.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && showTopLevelChrome) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag("nav_${item.route}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.statusBars,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (currentRoute == FinoraRoutes.HOME || currentRoute == FinoraRoutes.TRANSACTIONS) {
                        FloatingActionButton(
                            onClick = { showQuickAddSheet = true },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("quick_add_fab")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Quick Add Transaction")
                        }
                    }
                },
                bottomBar = {
                    if (!useRail && showTopLevelChrome) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = FinoraRoutes.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(FinoraRoutes.HOME) {
                        DashboardScreen(
                            state = dashboardState,
                            onSelectDateFilter = viewModel::setDashboardDateFilter,
                            onQuickAdd = { type -> navController.navigate(FinoraRoutes.addTransaction(type)) },
                            onOpenTransaction = { id -> navController.navigate(FinoraRoutes.transactionDetail(id)) },
                            onOpenAllTransactions = { navigateTopLevel(FinoraRoutes.TRANSACTIONS) },
                            onOpenNotifications = { navController.navigate(FinoraRoutes.NOTIFICATIONS) },
                            onOpenCalendar = { navController.navigate(FinoraRoutes.CALENDAR) },
                            onOpenGoals = { navController.navigate(FinoraRoutes.GOALS) },
                            onOpenRecurring = { navController.navigate(FinoraRoutes.RECURRING) },
                            onOpenBudgets = { navigateTopLevel(FinoraRoutes.BUDGETS) }
                        )
                    }

                    composable(FinoraRoutes.TRANSACTIONS) {
                        TransactionsScreen(
                            transactions = historyTransactions,
                            filter = historyFilter,
                            onUpdateFilter = viewModel::updateHistoryFilter,
                            onOpenTransaction = { id -> navController.navigate(FinoraRoutes.transactionDetail(id)) },
                            onAddTransaction = { showQuickAddSheet = true }
                        )
                    }

                    composable(FinoraRoutes.ANALYTICS) {
                        AnalyticsScreen(
                            state = analyticsState,
                            onSelectPeriod = viewModel::setAnalyticsPeriod,
                            onShareExport = { fmt -> viewModel.shareExportReport(context, fmt) },
                            onSaveExportToStream = viewModel::writeExportToOutputStream
                        )
                    }

                    composable(FinoraRoutes.BUDGETS) {
                        BudgetsScreen(
                            month = budgetMonthYear.first,
                            year = budgetMonthYear.second,
                            budgetStatuses = budgetStatuses,
                            onChangeMonthYear = viewModel::setBudgetMonthYear,
                            onSaveBudget = viewModel::saveBudget,
                            onDeleteBudget = viewModel::deleteBudget,
                            onOpenGoals = { navController.navigate(FinoraRoutes.GOALS) },
                            onOpenRecurring = { navController.navigate(FinoraRoutes.RECURRING) },
                            onOpenCalendar = { navController.navigate(FinoraRoutes.CALENDAR) }
                        )
                    }

                    composable(FinoraRoutes.PROFILE) {
                        ProfileScreen(
                            user = user,
                            settings = settings,
                            onUpdateProfile = viewModel::updateProfile,
                            onUpdatePhoto = viewModel::updateProfilePhoto,
                            onChangePassword = viewModel::changePassword,
                            onUpdateTheme = viewModel::updateTheme,
                            onOpenSettings = { navController.navigate(FinoraRoutes.SETTINGS) },
                            onOpenNotifications = { navController.navigate(FinoraRoutes.NOTIFICATIONS) },
                            onOpenGoals = { navController.navigate(FinoraRoutes.GOALS) },
                            onOpenRecurring = { navController.navigate(FinoraRoutes.RECURRING) },
                            onOpenCalendar = { navController.navigate(FinoraRoutes.CALENDAR) },
                            onLogout = viewModel::logout,
                            onDeleteAccount = viewModel::deleteAccount
                        )
                    }

                    composable(
                        route = FinoraRoutes.ADD_TRANSACTION,
                        arguments = listOf(navArgument("type") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val typeName = backStackEntry.arguments?.getString("type") ?: TransactionType.EXPENSE.name
                        val initialType = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.EXPENSE)
                        TransactionFormScreen(
                            initialType = initialType,
                            existingTransaction = null,
                            onSave = { type, amount, title, cat, sender, method, notes, ref, date ->
                                viewModel.saveTransaction(
                                    editingId = null,
                                    type = type,
                                    amountInput = amount,
                                    title = title,
                                    category = cat,
                                    sourceOrSender = sender,
                                    paymentMethod = method,
                                    notes = notes,
                                    referenceNumber = ref,
                                    dateMillis = date,
                                    onSuccess = { navController.popBackStack() }
                                )
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = FinoraRoutes.EDIT_TRANSACTION,
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val txId = backStackEntry.arguments?.getString("id").orEmpty()
                        val existing by viewModel.observeTransaction(txId).collectAsStateWithLifecycle(initialValue = null)
                        TransactionFormScreen(
                            initialType = existing?.type ?: TransactionType.EXPENSE,
                            existingTransaction = existing,
                            onSave = { type, amount, title, cat, sender, method, notes, ref, date ->
                                viewModel.saveTransaction(
                                    editingId = txId,
                                    type = type,
                                    amountInput = amount,
                                    title = title,
                                    category = cat,
                                    sourceOrSender = sender,
                                    paymentMethod = method,
                                    notes = notes,
                                    referenceNumber = ref,
                                    dateMillis = date,
                                    onSuccess = { navController.popBackStack() }
                                )
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = FinoraRoutes.TRANSACTION_DETAIL,
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val txId = backStackEntry.arguments?.getString("id").orEmpty()
                        val tx by viewModel.observeTransaction(txId).collectAsStateWithLifecycle(initialValue = null)
                        TransactionDetailScreen(
                            transaction = tx,
                            onEdit = { navController.navigate(FinoraRoutes.editTransaction(txId)) },
                            onDelete = {
                                viewModel.deleteTransaction(txId) {
                                    navController.popBackStack()
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(FinoraRoutes.GOALS) {
                        SavingsGoalsScreen(
                            goals = goals,
                            onSaveGoal = viewModel::saveGoal,
                            onContributeGoal = viewModel::contributeToGoal,
                            onDeleteGoal = viewModel::deleteGoal,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(FinoraRoutes.RECURRING) {
                        RecurringTransactionsScreen(
                            items = recurring,
                            onSaveRecurring = viewModel::saveRecurring,
                            onToggleActive = viewModel::toggleRecurring,
                            onProcessDueNow = viewModel::processDueRecurringNow,
                            onDeleteRecurring = viewModel::deleteRecurring,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(FinoraRoutes.CALENDAR) {
                        CalendarScreen(
                            allTransactions = allTransactions,
                            onOpenTransaction = { id -> navController.navigate(FinoraRoutes.transactionDetail(id)) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(FinoraRoutes.NOTIFICATIONS) {
                        NotificationsScreen(
                            notifications = notifications,
                            onMarkRead = viewModel::markNotificationRead,
                            onMarkAllRead = viewModel::markAllNotificationsRead,
                            onClearAll = viewModel::clearNotifications,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(FinoraRoutes.SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            onUpdateSettings = viewModel::updateSettings,
                            onTriggerTestReminder = viewModel::triggerTestReminder,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddSheetContent(
    onSelectType: (TransactionType) -> Unit
) {
    val semantic = LocalFinoraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Quick Add Transaction",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Choose the entry type to record accurately in your ledger.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        QuickSheetOption(
            title = "Add Income",
            subtitle = "Salary, freelancing, business, dividends",
            icon = Icons.Filled.ArrowDownward,
            tint = semantic.income,
            container = semantic.incomeContainer,
            onClick = { onSelectType(TransactionType.INCOME) },
            tag = "sheet_add_income"
        )
        QuickSheetOption(
            title = "Add Expense",
            subtitle = "Food, shopping, rent, bills, groceries",
            icon = Icons.Filled.ArrowUpward,
            tint = semantic.expense,
            container = semantic.expenseContainer,
            onClick = { onSelectType(TransactionType.EXPENSE) },
            tag = "sheet_add_expense"
        )
        QuickSheetOption(
            title = "Money Credited / Received",
            subtitle = "Refunds, friends, family, loan repayments (not income)",
            icon = Icons.AutoMirrored.Filled.CallReceived,
            tint = semantic.credit,
            container = semantic.creditContainer,
            onClick = { onSelectType(TransactionType.CREDIT) },
            tag = "sheet_add_credit"
        )
        QuickSheetOption(
            title = "Outgoing Transfer",
            subtitle = "Money lent or transferred out (not an expense)",
            icon = Icons.AutoMirrored.Filled.CallMade,
            tint = semantic.warning,
            container = semantic.warningContainer,
            onClick = { onSelectType(TransactionType.OUTGOING_TRANSFER) },
            tag = "sheet_add_transfer"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun QuickSheetOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = container,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = tint)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
