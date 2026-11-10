package com.rafsan.ottobudget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafsan.ottobudget.data.local.CategoryEntity
import com.rafsan.ottobudget.data.local.KeywordRuleEntity
import com.rafsan.ottobudget.data.local.TransactionEntity
import com.rafsan.ottobudget.data.local.WalletEntity
import com.rafsan.ottobudget.domain.RuleEngine
import com.rafsan.ottobudget.ui.theme.*
import com.rafsan.ottobudget.ui.viewmodel.OttoViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: OttoViewModel = viewModel()
            val darkTheme by vm.isDarkTheme.collectAsState()
            val showSplashWink by vm.showSplashWink.collectAsState()
            val showOnboarding by vm.showOnboarding.collectAsState()

            // Fix Light Mode Status Bar & Navigation Bar visibility (dark icons on light background)
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val bgInt = if (darkTheme) android.graphics.Color.parseColor("#0B0E11") else android.graphics.Color.parseColor("#F7F8FA")
                    window.statusBarColor = bgInt
                    window.navigationBarColor = bgInt
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }
            }

            OttoBudgetTheme(darkTheme = darkTheme) {
                when {
                    showSplashWink -> OttoWinkingSplashScreen(onFinished = { vm.finishSplashWink() })
                    showOnboarding -> OttoOnboardingScreen(vm = vm, onFinish = { vm.completeOnboarding() })
                    else -> OttoBudgetRootScreen(vm)
                }
            }
        }
    }
}

/**
 * Animated Winking Octopus Splash Screen using the exact artwork from the uploaded App Icon.
 * Animates the right eye at normalized coordinates (cx = 0.5801f, cy = 0.4980f, r = 0.024f) into a cute wink.
 */
@Composable
fun OttoWinkingSplashScreen(onFinished: () -> Unit) {
    val winkProgress = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.88f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
        )
        delay(180)
        // Wink closed
        winkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
        )
        delay(260)
        // Wink open
        winkProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        )
        delay(240)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OttoTurquoise),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.MD)
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(scaleAnim.value),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.otto_v6_launcher_fullbleed),
                    contentDescription = "Otto Winking",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(48.dp))
                )
                // Overlay exact right-eye wink choreography on the white octopus face
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = winkProgress.value
                    if (p > 0.02f) {
                        val cx = size.width * 0.5801f
                        val cy = size.height * 0.4980f
                        val eyeR = size.width * 0.027f

                        // 1. Cover open circular eye with pure white face color
                        drawCircle(
                            color = Color.White,
                            radius = eyeR * 1.35f,
                            center = Offset(cx, cy)
                        )

                        // 2. Draw happy curved winking eye in exact #6BD0C2 turquoise
                        val winkPath = Path().apply {
                            moveTo(cx - eyeR * 1.15f, cy + eyeR * 0.15f * p)
                            quadraticTo(
                                cx,
                                cy - eyeR * 0.95f * p,
                                cx + eyeR * 1.15f,
                                cy + eyeR * 0.15f * p
                            )
                        }
                        drawPath(
                            path = winkPath,
                            color = OttoTurquoise,
                            style = Stroke(width = (eyeR * 0.72f).coerceAtLeast(4f), cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Text(
                text = "OttoBudget",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun SingleLineText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = style,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun OttoOnboardingScreen(
    vm: OttoViewModel,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var nameInput by remember { mutableStateOf("") }
    var budgetInput by remember { mutableStateOf("10000") }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.XL),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) {
                    SingleLineText("Skip", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (step == 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.otto_v6_avatar),
                        contentDescription = "Otto",
                        modifier = Modifier.size(104.dp).clip(CircleShape)
                    )
                    SingleLineText("OttoBudget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Your money, your\nsystem, your pace.",
                        style = MaterialTheme.typography.displayLarge,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(22.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { step = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon("arrow_right", MaterialTheme.colorScheme.onPrimary, size = 22.dp, triggerKey = step)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Set up your\npersonal ledger.",
                        style = MaterialTheme.typography.displayLarge
                    )
                    SingleLineText(
                        "Offline-first SQLite with 200+ auto-category magic words",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { SingleLineText("Your Name (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { SingleLineText("Monthly Budget Target") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        vm.updateProfile(
                            newName = nameInput.ifBlank { "User" },
                            newBudget = budgetInput.toDoubleOrNull() ?: 10000.0
                        )
                        onFinish()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    SingleLineText("Start Using OttoBudget", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OttoBudgetRootScreen(vm: OttoViewModel) {
    val wallets by vm.wallets.collectAsState()
    val categories by vm.categories.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val userName by vm.userName.collectAsState()
    val excludeMajor by vm.excludeMajorGlobal.collectAsState()
    val darkTheme by vm.isDarkTheme.collectAsState()
    val monthlyBudget by vm.monthlyBudgetTarget.collectAsState()
    val statusMsg by vm.statusMessage.collectAsState()

    var currentTab by remember { mutableStateOf("home") }
    var settingsSubScreen by remember { mutableStateOf("menu") } // "menu", "backup", "magic_words", "profile"
    var navAnimTrigger by remember { mutableIntStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }
    var sheetDefaultMode by remember { mutableStateOf("EXPENSE") }
    var sheetPreselectedFrom by remember { mutableStateOf("bKash") }
    var sheetPreselectedTo by remember { mutableStateOf("Cash") }
    var walletToEditBalance by remember { mutableStateOf<WalletEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearStatusMessage()
        }
    }

    val balances = remember(wallets, transactions) {
        vm.computeWalletBalances(wallets, transactions)
    }
    val totalAvailable = balances.values.sum()

    // Full-expansion sheet state so user NEVER has to scroll to see the Save button!
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.otto_v6_avatar),
                        contentDescription = "Otto",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                currentTab = "profile"
                                settingsSubScreen = "menu"
                                navAnimTrigger++
                            }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        SingleLineText(
                            text = userName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        SingleLineText(
                            text = "OttoBudget",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            navAnimTrigger++
                            vm.isDarkTheme.value = !darkTheme
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrecisionVectorIcon(
                            name = "sun_moon",
                            tint = MaterialTheme.colorScheme.onSurface,
                            size = 18.dp,
                            triggerKey = darkTheme
                        )
                    }
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.LG, vertical = Spacing.SM)
            ) {
                Surface(
                    color = if (darkTheme) BinanceDarkCard else Color(0xFFE6E8EC),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.SM),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconOnlyNavButton(
                            iconName = "home",
                            selected = currentTab == "home",
                            darkTheme = darkTheme,
                            triggerKey = navAnimTrigger,
                            onClick = { currentTab = "home"; navAnimTrigger++ }
                        )
                        IconOnlyNavButton(
                            iconName = "ledger",
                            selected = currentTab == "ledger",
                            darkTheme = darkTheme,
                            triggerKey = navAnimTrigger,
                            onClick = { currentTab = "ledger"; navAnimTrigger++ }
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    navAnimTrigger++
                                    sheetDefaultMode = "EXPENSE"
                                    if (wallets.isNotEmpty()) {
                                        sheetPreselectedFrom = wallets.first().name
                                        sheetPreselectedTo = wallets.getOrNull(1)?.name ?: wallets.first().name
                                    }
                                    showAddSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            PrecisionVectorIcon(
                                name = "plus",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                size = 20.dp,
                                triggerKey = navAnimTrigger
                            )
                        }

                        IconOnlyNavButton(
                            iconName = "wallet",
                            selected = currentTab == "wallets",
                            darkTheme = darkTheme,
                            triggerKey = navAnimTrigger,
                            onClick = { currentTab = "wallets"; navAnimTrigger++ }
                        )
                        IconOnlyNavButton(
                            iconName = "profile",
                            selected = currentTab == "profile",
                            darkTheme = darkTheme,
                            triggerKey = navAnimTrigger,
                            onClick = {
                                currentTab = "profile"
                                settingsSubScreen = "menu"
                                navAnimTrigger++
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.MD)
        ) {
            when (currentTab) {
                "home" -> DashboardV6Screen(
                    totalAvailable = totalAvailable,
                    monthlyBudget = monthlyBudget,
                    transactions = transactions,
                    categories = categories,
                    excludeMajor = excludeMajor,
                    darkTheme = darkTheme,
                    onToggleMajor = { vm.excludeMajorGlobal.value = !excludeMajor },
                    onSeeAllLedger = { currentTab = "ledger"; navAnimTrigger++ },
                    onDeleteTx = { vm.deleteTransaction(it) }
                )
                "ledger" -> LedgerV6Screen(
                    transactions = transactions,
                    categories = categories,
                    darkTheme = darkTheme,
                    onDeleteTx = { vm.deleteTransaction(it) }
                )
                "wallets" -> WalletsV6Screen(
                    wallets = wallets,
                    balances = balances,
                    onMoveFromWallet = { fromW ->
                        sheetDefaultMode = "TRANSFER"
                        sheetPreselectedFrom = fromW
                        sheetPreselectedTo = wallets.firstOrNull { it.name != fromW }?.name ?: "Cash"
                        showAddSheet = true
                    },
                    onEditWalletBalance = { w -> walletToEditBalance = w }
                )
                "profile" -> SettingsHubV6Screen(
                    subScreen = settingsSubScreen,
                    onNavigateSubScreen = { settingsSubScreen = it },
                    vm = vm,
                    balances = balances,
                    onEditWalletBalance = { w -> walletToEditBalance = w }
                )
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                sheetState = addSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                QuickAddV6BottomSheet(
                    defaultMode = sheetDefaultMode,
                    initialFromWallet = sheetPreselectedFrom,
                    initialToWallet = sheetPreselectedTo,
                    balances = balances,
                    darkTheme = darkTheme,
                    vm = vm,
                    onDismiss = { showAddSheet = false }
                )
            }
        }

        walletToEditBalance?.let { targetWallet ->
            val currentLive = balances[targetWallet.name] ?: 0.0
            EditWalletBalanceDialog(
                wallet = targetWallet,
                currentBalance = currentLive,
                onDismiss = { walletToEditBalance = null },
                onConfirm = { newBalance, newColorHex ->
                    vm.setExactWalletBalanceAndColor(targetWallet, newBalance, currentLive, newColorHex)
                    walletToEditBalance = null
                }
            )
        }
    }
}

val WALLET_COLOR_PRESETS = listOf(
    0xFFE11D48L, // bKash Pink / Magenta
    0xFFC8F904L, // Cash Acid Lime
    0xFF1E293BL, // Bank Card Obsidian
    0xFFF97316L, // Nagad Orange
    0xFF8B5CF6L, // Rocket Purple
    0xFF0284C7L, // Transit Pass Blue
    0xFF059669L  // Savings Emerald
)

fun resolveWalletCardPalette(wallet: WalletEntity): Pair<Brush, Boolean> {
    val hex = if (wallet.accentHex != 0L) wallet.accentHex else 0xFF1E293BL
    return when (hex) {
        0xFFC8F904L -> Brush.radialGradient(listOf(Color(0xFFE3FF54), Color(0xFFC8F904), Color(0xFF8CE000))) to true
        0xFFE11D48L -> Brush.radialGradient(listOf(Color(0xFFFF3877), Color(0xFFDF1463), Color(0xFF4A0722))) to false
        0xFFF97316L -> Brush.radialGradient(listOf(Color(0xFFFF7A33), Color(0xFFEA580C), Color(0xFF431407))) to false
        0xFF8B5CF6L -> Brush.radialGradient(listOf(Color(0xFFA855F7), Color(0xFF6D28D9), Color(0xFF2E1065))) to false
        0xFF0284C7L -> Brush.radialGradient(listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8), Color(0xFF0F172A))) to false
        0xFF059669L -> Brush.radialGradient(listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF062E22))) to false
        else -> Brush.radialGradient(listOf(Color(0xFF2D3548), Color(0xFF161A23), Color(0xFF0B0D13))) to false
    }
}

@Composable
fun EditWalletBalanceDialog(
    wallet: WalletEntity,
    currentBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Long) -> Unit
) {
    var inputVal by remember { mutableStateOf(currentBalance.roundToInt().toString()) }
    var selectedColor by remember {
        mutableLongStateOf(
            if (wallet.accentHex in WALLET_COLOR_PRESETS) wallet.accentHex else WALLET_COLOR_PRESETS.first()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { SingleLineText(wallet.name, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                OutlinedTextField(
                    value = inputVal,
                    onValueChange = { inputVal = it },
                    label = { SingleLineText("Balance") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.XXS),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WALLET_COLOR_PRESETS.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                PrecisionVectorIcon(
                                    name = "check",
                                    tint = if (hex == 0xFFC8F904L) InkDark else Color.White,
                                    size = 13.dp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(inputVal.toDoubleOrNull() ?: currentBalance, selectedColor) },
                shape = RoundedCornerShape(12.dp)
            ) {
                SingleLineText("Save", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                SingleLineText("Cancel")
            }
        }
    )
}

@Composable
fun IconOnlyNavButton(
    iconName: String,
    selected: Boolean,
    darkTheme: Boolean,
    triggerKey: Int,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
    val pillBg = if (selected) {
        if (darkTheme) BinanceDarkTrack else Color.White
    } else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(56.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pillBg)
            .clickable(onClick = onClick)
    ) {
        PrecisionVectorIcon(
            name = iconName,
            tint = if (selected) activeColor else inactiveColor,
            size = 20.dp,
            triggerKey = if (selected) triggerKey else null
        )
        if (selected) {
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B))
            )
        }
    }
}

/**
 * V6 Dashboard (Matches Uploaded Image 3 with Sans Fonts & Monochrome Transaction Icons + Accurate Daily Average).
 */
@Composable
fun DashboardV6Screen(
    totalAvailable: Double,
    monthlyBudget: Double,
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    excludeMajor: Boolean,
    darkTheme: Boolean,
    onToggleMajor: () -> Unit,
    onSeeAllLedger: () -> Unit,
    onDeleteTx: (TransactionEntity) -> Unit
) {
    val today = remember { LocalDate.now() }
    val currentMonthPrefix = remember(today) { today.toString().substring(0, 7) } // YYYY-MM

    val expenses = remember(transactions, excludeMajor) {
        transactions.filter { it.type == "EXPENSE" && (!excludeMajor || !it.isMajor) }
    }
    val incomes = remember(transactions) {
        transactions.filter { it.type == "INCOME" }
    }

    val monthlyExpenses = remember(expenses, currentMonthPrefix) {
        expenses.filter { it.date.startsWith(currentMonthPrefix) }
    }
    val totalSpend = if (monthlyExpenses.isNotEmpty()) monthlyExpenses.sumOf { it.amount } else expenses.sumOf { it.amount }

    // Accurate Daily Average Calculation:
    // Divides total spend by the number of elapsed days since the earliest expense in the active set up to today (min 1 day).
    val dailyAvg = remember(expenses, totalSpend, today) {
        if (totalSpend <= 0.0 || expenses.isEmpty()) {
            0
        } else {
            val earliestIso = expenses.minOfOrNull { it.date } ?: today.toString()
            val earliestDate = runCatching { LocalDate.parse(earliestIso) }.getOrDefault(today)
            val elapsedDays = ChronoUnit.DAYS.between(earliestDate, today).coerceIn(0L, 30L) + 1L
            (totalSpend / elapsedDays.toDouble()).roundToInt()
        }
    }

    val last7Dates = remember(today) {
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val dayShortFormatter = remember { DateTimeFormatter.ofPattern("E") }

    var chartMode by remember { mutableStateOf("EXPENSE") }
    var selectedDayIndex by remember { mutableIntStateOf(6) }

    val dayValues = remember(transactions, chartMode, excludeMajor, last7Dates) {
        last7Dates.map { dateObj ->
            val iso = dateObj.toString()
            if (chartMode == "INCOME") {
                incomes.filter { it.date == iso }.sumOf { it.amount }
            } else {
                expenses.filter { it.date == iso }.sumOf { it.amount }
            }
        }
    }
    val maxDayVal = (dayValues.maxOrNull() ?: 0.0).coerceAtLeast(100.0)
    val activeWindowSum = dayValues.sum()

    val animatedBalance by animateIntAsState(
        targetValue = totalAvailable.roundToInt(),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "animBal"
    )
    val animatedSpend by animateIntAsState(
        targetValue = totalSpend.roundToInt(),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "animSpend"
    )
    val budgetRatio by animateFloatAsState(
        targetValue = if (totalSpend <= 0.0) 0f else (totalSpend / monthlyBudget.coerceAtLeast(1.0)).coerceIn(0.04, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "animRatio"
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        contentPadding = PaddingValues(vertical = Spacing.XS)
    ) {
        // 1. Hero Total Balance & Daily Average Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.LG),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SM)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("TOTAL BALANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            SingleLineText("৳$animatedBalance", style = MaterialTheme.typography.displayLarge)
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onToggleMajor() }
                        ) {
                            SingleLineText(
                                text = if (excludeMajor) "Daily" else "All",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleLineText(
                            text = "Spent ৳$animatedSpend / ৳${monthlyBudget.roundToInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        SingleLineText(
                            text = "Avg ৳$dailyAvg/d",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 13.sp,
                            color = BinanceGreen
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (budgetRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(budgetRatio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        // 2. 7D Spend / Income / Line Card (With Sans-Serif Pills for Out | In | Line & ৳0 badge!)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.LG),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SM)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText(
                                text = when (chartMode) {
                                    "INCOME" -> "7D INCOME"
                                    "PACE" -> "7D TREND"
                                    else -> "7D SPEND"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val headerNum = when (chartMode) {
                                "INCOME" -> "+৳${activeWindowSum.roundToInt()}"
                                "PACE" -> "৳$dailyAvg/d"
                                else -> "৳${activeWindowSum.roundToInt()}"
                            }
                            SingleLineText(text = headerNum, style = MaterialTheme.typography.headlineLarge)
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf("EXPENSE" to "Out", "INCOME" to "In", "PACE" to "Line").forEach { (key, lbl) ->
                                val active = chartMode == key
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable { chartMode = key }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    SingleLineText(
                                        text = lbl,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 12.sp,
                                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (chartMode == "PACE") {
                        val lineColor = if (darkTheme) BinanceGold else InkDark
                        val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            val w = size.width
                            val h = size.height * 0.76f
                            val pts = dayValues.mapIndexed { i, v ->
                                val x = (i.toFloat() / 6f) * w
                                val ratio = (v / maxDayVal).toFloat().coerceIn(0f, 1f)
                                val y = h - (ratio * (h * 0.80f)) - (h * 0.10f)
                                Offset(x, y)
                            }

                            val splinePath = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                for (i in 0 until pts.size - 1) {
                                    val p0 = pts[i]
                                    val p1 = pts[i + 1]
                                    val cx = (p0.x + p1.x) / 2f
                                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                                }
                            }
                            drawPath(splinePath, color = lineColor, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
                            pts.forEachIndexed { idx, pt ->
                                if (idx == selectedDayIndex) {
                                    drawCircle(lineColor, radius = 5.dp.toPx(), center = pt)
                                }
                            }

                            val tickCount = 18
                            for (i in 0..tickCount) {
                                val tx = (i.toFloat() / tickCount) * w
                                val major = i % 3 == 0
                                drawLine(
                                    color = tickColor,
                                    start = Offset(tx, h + 8.dp.toPx()),
                                    end = Offset(tx, h + (if (major) 15.dp.toPx() else 11.dp.toPx())),
                                    strokeWidth = 1.3.dp.toPx()
                                )
                            }
                        }
                    } else {
                        val accent = if (chartMode == "INCOME") BinanceGreen else MaterialTheme.colorScheme.primary
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.XS),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7Dates.forEachIndexed { idx, dateObj ->
                                val isSelected = idx == selectedDayIndex
                                val v = dayValues[idx]
                                val targetRatio = if (v <= 0.0) 0.08f else (v / maxDayVal).toFloat().coerceIn(0.16f, 1f)
                                val animRatio by animateFloatAsState(
                                    targetValue = targetRatio,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                                    label = "stem_$idx"
                                )
                                val dLabel = dateObj.format(dayShortFormatter).take(1)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                        .clickable { selectedDayIndex = idx }
                                        .padding(vertical = 6.dp)
                                ) {
                                    if (isSelected) {
                                        SingleLineText(
                                            text = "৳${v.roundToInt()}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(bottom = 3.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.height(16.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (v > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(68.dp * animRatio)
                                            .background(if (v > 0) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(Spacing.DayCircle)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SingleLineText(
                                            text = dLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 11.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Recent Transactions Header & Monochrome List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleLineText("Recent Transactions", style = MaterialTheme.typography.titleLarge)
                if (transactions.isNotEmpty()) {
                    TextButton(onClick = onSeeAllLedger) {
                        SingleLineText("See All", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                    }
                }
            }
        }

        if (transactions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.XL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                    ) {
                        SingleLineText("No transactions yet", style = MaterialTheme.typography.titleMedium)
                        SingleLineText(
                            "Tap '+' below to add your first entry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(transactions.take(6), key = { it.id }) { tx ->
                val catObj = categories.find { it.name == tx.category }
                TransactionV6MonochromeRow(
                    tx = tx,
                    iconName = if (tx.type == "TRANSFER") "transfer" else (catObj?.iconName ?: "ledger"),
                    darkTheme = darkTheme,
                    onDelete = { onDeleteTx(tx) }
                )
            }
        }
    }
}

/**
 * Monochrome Transaction Row (Matches Uploaded Images 2 & 3: "liked the icons" & "has to be monochrome").
 * Uses soft neutral rounded-square box (#ECEEF2 in Light Mode / #2B3139 in Dark Mode) with crisp monochrome vector strokes.
 */
@Composable
fun TransactionV6MonochromeRow(
    tx: TransactionEntity,
    iconName: String,
    darkTheme: Boolean,
    onDelete: () -> Unit
) {
    var iconTapCount by remember { mutableIntStateOf(0) }
    val prefix = when (tx.type) {
        "INCOME" -> "+"
        "TRANSFER" -> ""
        else -> "-"
    }
    val amountColor = when (tx.type) {
        "INCOME" -> BinanceGreen
        else -> MaterialTheme.colorScheme.onSurface
    }
    val boxBg = if (darkTheme) MonochromeBoxDark else MonochromeBoxLight
    val iconTint = if (darkTheme) BinanceLightText else InkDark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { iconTapCount++ }
            .padding(horizontal = Spacing.SM, vertical = Spacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(boxBg),
                contentAlignment = Alignment.Center
            ) {
                PrecisionVectorIcon(name = iconName, tint = iconTint, size = 20.dp, triggerKey = iconTapCount)
            }
            Column(modifier = Modifier.weight(1f)) {
                SingleLineText(
                    text = tx.description.replace("\n", " "),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                val walletRoute = if (tx.type == "TRANSFER") "${tx.wallet} → ${tx.toWallet ?: "Cash"}" else tx.wallet
                SingleLineText(
                    text = "${tx.category} • $walletRoute",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SingleLineText(
                text = "${prefix}৳${tx.amount.roundToInt()}",
                style = MaterialTheme.typography.titleLarge,
                color = amountColor
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                PrecisionVectorIcon(
                    name = "trash",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    size = 16.dp,
                    triggerKey = iconTapCount
                )
            }
        }
    }
}

/**
 * V6 Ledger Screen (Matches Uploaded Image 2: Sans-Serif All/Out/In/Move pills, Date Group Headers "Today"/"Yesterday", and Monochrome Icons).
 */
@Composable
fun LedgerV6Screen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    darkTheme: Boolean,
    onDeleteTx: (TransactionEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedType by remember { mutableStateOf("ALL") }

    val todayStr = remember { LocalDate.now().toString() }
    val yesterdayStr = remember { LocalDate.now().minusDays(1).toString() }

    val filtered = remember(transactions, selectedType, selectedCategory, searchQuery) {
        transactions.filter { tx ->
            (selectedType == "ALL" || tx.type == selectedType) &&
                (selectedCategory == "ALL" || tx.category == selectedCategory) &&
                (searchQuery.isBlank() || tx.description.contains(searchQuery, ignoreCase = true) || tx.category.contains(searchQuery, ignoreCase = true))
        }
    }

    val groupedByDate = remember(filtered) {
        filtered.groupBy { it.date }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
        contentPadding = PaddingValues(vertical = Spacing.XS)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { SingleLineText("Search description or category...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            // Sans-Serif Type Filter Pills (Fixes the circled "change fonts" on All | Out | In | Move)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                listOf("ALL" to "All", "EXPENSE" to "Out", "INCOME" to "In", "TRANSFER" to "Move").forEach { (key, lbl) ->
                    val isSel = selectedType == key
                    val pillBg = if (isSel) {
                        if (darkTheme) BinanceGold else SoftLavenderPill
                    } else MaterialTheme.colorScheme.surface
                    val pillText = if (isSel) {
                        if (darkTheme) BinanceDarkCard else SoftLavenderText
                    } else MaterialTheme.colorScheme.onSurface

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pillBg,
                        border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clickable { selectedType = key }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SingleLineText(
                                text = lbl,
                                style = MaterialTheme.typography.titleMedium,
                                color = pillText
                            )
                        }
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                items(listOf("ALL") + categories.map { it.name }) { catName ->
                    val isSel = selectedCategory == catName
                    val pillBg = if (isSel) {
                        if (darkTheme) BinanceGold else SoftLavenderPill
                    } else MaterialTheme.colorScheme.surface
                    val pillText = if (isSel) {
                        if (darkTheme) BinanceDarkCard else SoftLavenderText
                    } else MaterialTheme.colorScheme.onSurface

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pillBg,
                        border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable { selectedCategory = catName }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            SingleLineText(
                                text = catName,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 13.sp,
                                color = pillText
                            )
                        }
                    }
                }
            }
        }

        groupedByDate.forEach { (dateStr, dayTxs) ->
            item(key = "hdr_$dateStr") {
                val friendlyDate = when (dateStr) {
                    todayStr -> "Today"
                    yesterdayStr -> "Yesterday"
                    else -> dateStr
                }
                SingleLineText(
                    text = friendlyDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.XS, bottom = 2.dp)
                )
            }
            items(dayTxs, key = { it.id }) { tx ->
                val catObj = categories.find { it.name == tx.category }
                TransactionV6MonochromeRow(
                    tx = tx,
                    iconName = if (tx.type == "TRANSFER") "transfer" else (catObj?.iconName ?: "ledger"),
                    darkTheme = darkTheme,
                    onDelete = { onDeleteTx(tx) }
                )
            }
        }
    }
}

@Composable
fun WalletsV6Screen(
    wallets: List<WalletEntity>,
    balances: Map<String, Double>,
    onMoveFromWallet: (String) -> Unit,
    onEditWalletBalance: (WalletEntity) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        contentPadding = PaddingValues(vertical = Spacing.XS)
    ) {
        item {
            SingleLineText("Wallets", style = MaterialTheme.typography.headlineLarge)
        }

        items(wallets, key = { it.id }) { wallet ->
            val bal = balances[wallet.name] ?: 0.0
            val animBal by animateIntAsState(targetValue = bal.roundToInt(), animationSpec = tween(450), label = "wBal")
            val (bgBrush, isLightCard) = resolveWalletCardPalette(wallet)
            val textColor = if (isLightCard) InkDark else Color.White
            val subTextColor = textColor.copy(alpha = 0.78f)
            val actionPillBg = if (isLightCard) InkDark.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.16f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(bgBrush)
                    .clickable { onMoveFromWallet(wallet.name) }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val cx = size.width * 0.85f
                    val cy = size.height * 0.82f
                    drawCircle(
                        color = textColor.copy(alpha = 0.08f),
                        radius = 96.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 18.dp.toPx())
                    )
                    drawCircle(
                        color = textColor.copy(alpha = 0.08f),
                        radius = 60.dp.toPx(),
                        center = Offset(cx + 12.dp.toPx(), cy + 8.dp.toPx()),
                        style = Stroke(width = 14.dp.toPx())
                    )
                    drawCircle(
                        color = textColor.copy(alpha = 0.09f),
                        radius = 28.dp.toPx(),
                        center = Offset(cx + 22.dp.toPx(), cy + 16.dp.toPx())
                    )
                }

                Column(
                    modifier = Modifier.padding(Spacing.LG),
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleLineText(
                            text = "Balance • ${wallet.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = subTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val st = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawArc(textColor, -45f, 90f, false, Offset(2.dp.toPx(), 6.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()), style = st)
                            drawArc(textColor, -45f, 90f, false, Offset(0.dp.toPx(), 3.dp.toPx()), Size(14.dp.toPx(), 14.dp.toPx()), style = st)
                            drawArc(textColor, -45f, 90f, false, Offset(-2.dp.toPx(), 0.dp.toPx()), Size(20.dp.toPx(), 20.dp.toPx()), style = st)
                        }
                    }

                    SingleLineText(
                        text = "৳$animBal",
                        style = MaterialTheme.typography.displayLarge,
                        color = textColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.width(30.dp).height(18.dp)) {
                                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFF43F5E).copy(alpha = 0.9f)))
                                Box(Modifier.size(18.dp).offset(x = 11.dp).clip(CircleShape).background(Color(0xFFFBBF24).copy(alpha = 0.9f)))
                            }
                            SingleLineText(
                                text = wallet.numberTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = subTextColor
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(actionPillBg)
                                    .clickable { onEditWalletBalance(wallet) },
                                contentAlignment = Alignment.Center
                            ) {
                                PrecisionVectorIcon("edit", textColor, size = 16.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(actionPillBg)
                                    .clickable { onMoveFromWallet(wallet.name) },
                                contentAlignment = Alignment.Center
                            ) {
                                PrecisionVectorIcon("transfer", textColor, size = 16.dp, triggerKey = animBal)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Uncluttered V6 Settings Hub with Dedicated Sub-Screens:
 * 1. Main Menu ("menu")
 * 2. Settings / Backup & Cloud Sync ("backup") — Exact implementation of Uploaded Image 4!
 * 3. Settings / Categories & Magic Words ("magic_words") — Minimalist tag, category & magic word studio!
 * 4. Settings / Profile & Wallets ("profile")
 */
@Composable
fun SettingsHubV6Screen(
    subScreen: String,
    onNavigateSubScreen: (String) -> Unit,
    vm: OttoViewModel,
    balances: Map<String, Double>,
    onEditWalletBalance: (WalletEntity) -> Unit
) {
    if (subScreen != "menu") {
        BackHandler { onNavigateSubScreen("menu") }
    }

    when (subScreen) {
        "backup" -> BackupAndCloudSyncSubScreen(
            vm = vm,
            onBack = { onNavigateSubScreen("menu") }
        )
        "magic_words" -> CategoriesAndMagicWordsSubScreen(
            vm = vm,
            onBack = { onNavigateSubScreen("menu") }
        )
        "profile" -> ProfileAndWalletsSubScreen(
            vm = vm,
            balances = balances,
            onEditWalletBalance = onEditWalletBalance,
            onBack = { onNavigateSubScreen("menu") }
        )
        else -> {
            val rules by vm.keywordRules.collectAsState()
            val categories by vm.categories.collectAsState()
            val wallets by vm.wallets.collectAsState()
            val lastSync by vm.lastSyncedText.collectAsState()

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.SM),
                contentPadding = PaddingValues(vertical = Spacing.XS)
            ) {
                item {
                    SingleLineText("Settings", style = MaterialTheme.typography.headlineLarge)
                }

                item {
                    SettingsNavCard(
                        iconName = "profile",
                        title = "Profile & Wallets",
                        subtitle = "${wallets.size} wallets • Name & monthly budget cap",
                        onClick = { onNavigateSubScreen("profile") }
                    )
                }

                item {
                    SettingsNavCard(
                        iconName = "book",
                        title = "Categories & Magic Words",
                        subtitle = "${categories.size} categories • ${rules.size} auto-match magic words",
                        onClick = { onNavigateSubScreen("magic_words") }
                    )
                }

                item {
                    SettingsNavCard(
                        iconName = "upload",
                        title = "Backup & Cloud Sync",
                        subtitle = "CSV/JSON backup & encrypted cloud sync • $lastSync",
                        onClick = { onNavigateSubScreen("backup") }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsNavCard(
    iconName: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.LG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon(iconName, MaterialTheme.colorScheme.onSurface, size = 20.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    SingleLineText(title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(2.dp))
                    SingleLineText(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PrecisionVectorIcon("arrow_right", MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
        }
    }
}

/**
 * Exact Implementation of Uploaded Image 4 (`Settings / Backup & Cloud Sync`).
 */
@Composable
fun BackupAndCloudSyncSubScreen(
    vm: OttoViewModel,
    onBack: () -> Unit
) {
    val autoSync by vm.autoSyncState.collectAsState()
    val lastSynced by vm.lastSyncedText.collectAsState()
    var supabaseUrl by remember { mutableStateOf(vm.syncManager.supabaseUrl) }
    var supabaseKey by remember { mutableStateOf(vm.syncManager.supabaseAnonKey) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) vm.exportCsv(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importBackup(uri) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        contentPadding = PaddingValues(vertical = Spacing.XS)
    ) {
        // Breadcrumb Header: ← Settings / Backup & Cloud Sync
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XXS)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    PrecisionVectorIcon("arrow_left", MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                }
                SingleLineText(
                    text = "Settings /",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleLineText(
                    text = "Backup & Cloud Sync",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Main Card matching Uploaded Image 4
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.LG),
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD)
                ) {
                    SingleLineText(
                        text = "BACKUP & RESTORE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Side-by-side gray pill buttons: Export CSV/JSON | Import Backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.SM)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { exportLauncher.launch("ottobudget_backup.csv") }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = Spacing.SM)
                            ) {
                                PrecisionVectorIcon("download", MaterialTheme.colorScheme.onSurface, size = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                SingleLineText("Export CSV/JSON", style = MaterialTheme.typography.titleMedium, fontSize = 13.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { importLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = Spacing.SM)
                            ) {
                                PrecisionVectorIcon("upload", MaterialTheme.colorScheme.onSurface, size = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                SingleLineText("Import Backup", style = MaterialTheme.typography.titleMedium, fontSize = 13.sp)
                            }
                        }
                    }

                    // Centered Red Clear Data Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showClearConfirm = true }
                            .padding(vertical = Spacing.XS),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrecisionVectorIcon("trash", BinanceRed, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        SingleLineText(
                            text = "Clear Data",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    SingleLineText(
                        text = "CLOUD SYNC",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Cloud Sync Status + Auto-sync Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleLineText("Cloud Sync Status", style = MaterialTheme.typography.titleMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
                        ) {
                            SingleLineText("Auto-sync", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = autoSync,
                                onCheckedChange = { vm.toggleAutoSync(it) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = {
                            supabaseUrl = it
                            vm.syncManager.supabaseUrl = it
                        },
                        placeholder = { SingleLineText("Supabase URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = supabaseKey,
                        onValueChange = {
                            supabaseKey = it
                            vm.syncManager.supabaseAnonKey = it
                        },
                        placeholder = { SingleLineText("Supabase Anon Key") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bottom Action Row: Sync Now (Dark Pill) + Restore (Outlined Pill)
                    // Uses Zero-Schema-Exposure Encrypted Vault Auto-Sync so no SQL schema is ever exposed!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { vm.syncNowToCloud() },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1.6f).height(50.dp)
                        ) {
                            SingleLineText("Sync Now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }

                        OutlinedButton(
                            onClick = { vm.pullFromCloud() },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            SingleLineText("Pull Vault", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        SingleLineText(
                            text = "Last synced: $lastSynced",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Clear All Local Data?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("This deletes all local transactions and resets wallet balances to 0.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearAllLocalData()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BinanceRed)
                ) {
                    SingleLineText("Clear Data", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    SingleLineText("Cancel")
                }
            }
        )
    }
}

/**
 * Minimalist Categories & Magic Words Studio Sub-Screen:
 * Allows viewing, searching, adding, renaming, and deleting Categories and their auto-matching Magic Words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesAndMagicWordsSubScreen(
    vm: OttoViewModel,
    onBack: () -> Unit
) {
    val categories by vm.categories.collectAsState()
    val rules by vm.keywordRules.collectAsState()

    var kindTab by remember { mutableStateOf("EXPENSE") }
    var searchWord by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<CategoryEntity?>(null) }
    var newMagicWord by remember { mutableStateOf("") }
    var wordToEdit by remember { mutableStateOf<KeywordRuleEntity?>(null) }
    var editedWordText by remember { mutableStateOf("") }

    val availableIcons = listOf("coffee", "bag", "bus", "book", "tech", "bolt", "health", "home", "wallet", "ledger")

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
        contentPadding = PaddingValues(vertical = Spacing.XS)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XXS)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    PrecisionVectorIcon("arrow_left", MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                }
                SingleLineText("Settings /", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleLineText("Categories & Magic Words", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                listOf("EXPENSE" to "Expense Categories", "INCOME" to "Income Categories").forEach { (k, lbl) ->
                    FilterChip(
                        selected = kindTab == k,
                        onClick = { kindTab = k; selectedCat = null },
                        label = { SingleLineText(lbl, style = MaterialTheme.typography.titleMedium, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Add New Category Bar
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { SingleLineText("New category name...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        vm.addCategory(newCategoryName, kindTab, "ledger")
                        newCategoryName = ""
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    PrecisionVectorIcon("plus", MaterialTheme.colorScheme.onPrimary, size = 18.dp)
                }
            }
        }

        // Search Magic Words across all categories
        item {
            OutlinedTextField(
                value = searchWord,
                onValueChange = { searchWord = it },
                placeholder = { SingleLineText("Search magic words (e.g. uber, coffee, book)...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val visibleCategories = categories.filter { it.kind == kindTab }

        items(visibleCategories, key = { it.id }) { cat ->
            val catRules = rules.filter {
                it.category == cat.name && (searchWord.isBlank() || it.keyword.contains(searchWord, ignoreCase = true) || cat.name.contains(searchWord, ignoreCase = true))
            }
            if (searchWord.isNotBlank() && catRules.isEmpty() && !cat.name.contains(searchWord, ignoreCase = true)) {
                return@items
            }
            val isExpanded = selectedCat?.id == cat.id || searchWord.isNotBlank()

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.MD),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SM)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCat = if (selectedCat?.id == cat.id) null else cat },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                PrecisionVectorIcon(cat.iconName, MaterialTheme.colorScheme.onSurface, size = 18.dp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                SingleLineText(cat.name, style = MaterialTheme.typography.titleMedium)
                                SingleLineText("${catRules.size} magic words", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    vm.deleteCategory(cat)
                                    if (selectedCat?.id == cat.id) selectedCat = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                PrecisionVectorIcon("trash", BinanceRed, size = 15.dp)
                            }
                        }
                    }

                    if (isExpanded) {
                        // Icon selector row for category
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableIcons) { icName ->
                                val isIcSel = cat.iconName == icName
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isIcSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { vm.renameCategory(cat, cat.name, icName) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    PrecisionVectorIcon(
                                        name = icName,
                                        tint = if (isIcSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        size = 16.dp
                                    )
                                }
                            }
                        }

                        // Add Magic Word input
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newMagicWord,
                                onValueChange = { newMagicWord = it },
                                placeholder = { SingleLineText("Add magic word to ${cat.name}...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    vm.addKeywordToCategory(newMagicWord, cat)
                                    newMagicWord = ""
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                PrecisionVectorIcon("plus", MaterialTheme.colorScheme.onPrimary, size = 16.dp)
                            }
                        }

                        // Wrapping FlowRow of Magic Word Chips (Tap chip to edit or delete!)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            catRules.forEach { rule ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        wordToEdit = rule
                                        editedWordText = rule.keyword
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        SingleLineText(rule.keyword, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "×",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.clickable {
                                                vm.removeKeywordRule(rule.keyword, cat.name)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    wordToEdit?.let { targetRule ->
        val parentCat = categories.find { it.name == targetRule.category }
        AlertDialog(
            onDismissRequest = { wordToEdit = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Edit Magic Word", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = editedWordText,
                    onValueChange = { editedWordText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (parentCat != null) {
                            vm.updateKeywordRule(targetRule.keyword, editedWordText, parentCat)
                        }
                        wordToEdit = null
                    }
                ) {
                    SingleLineText("Save", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vm.removeKeywordRule(targetRule.keyword, targetRule.category)
                        wordToEdit = null
                    }
                ) {
                    SingleLineText("Delete", color = BinanceRed)
                }
            }
        )
    }
}

@Composable
fun ProfileAndWalletsSubScreen(
    vm: OttoViewModel,
    balances: Map<String, Double>,
    onEditWalletBalance: (WalletEntity) -> Unit,
    onBack: () -> Unit
) {
    val userName by vm.userName.collectAsState()
    val monthlyBudget by vm.monthlyBudgetTarget.collectAsState()
    val wallets by vm.wallets.collectAsState()

    var editName by remember(userName) { mutableStateOf(userName) }
    var editBudget by remember(monthlyBudget) { mutableStateOf(monthlyBudget.roundToInt().toString()) }
    var newWalletName by remember { mutableStateOf("") }
    var newWalletBalance by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        contentPadding = PaddingValues(vertical = Spacing.XS)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XXS)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    PrecisionVectorIcon("arrow_left", MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                }
                SingleLineText("Settings /", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleLineText("Profile & Wallets", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.MD), verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    SingleLineText("Profile & Monthly Budget", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { SingleLineText("Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editBudget,
                            onValueChange = { editBudget = it },
                            label = { SingleLineText("Monthly Cap") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { vm.updateProfile(editName, editBudget.toDoubleOrNull() ?: 10000.0) },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            PrecisionVectorIcon("check", MaterialTheme.colorScheme.onPrimary, size = 18.dp)
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.MD), verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    SingleLineText("Manage Wallets", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newWalletName,
                            onValueChange = { newWalletName = it },
                            label = { SingleLineText("Wallet Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = newWalletBalance,
                            onValueChange = { newWalletBalance = it },
                            label = { SingleLineText("Balance") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.8f)
                        )
                        IconButton(
                            onClick = {
                                vm.addWallet(newWalletName, newWalletBalance.toDoubleOrNull() ?: 0.0, "Account")
                                newWalletName = ""; newWalletBalance = ""
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            PrecisionVectorIcon("plus", MaterialTheme.colorScheme.onPrimary, size = 18.dp)
                        }
                    }

                    wallets.forEach { w ->
                        val liveBal = balances[w.name] ?: 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = Spacing.SM, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleLineText(
                                text = "${w.name} • ৳${liveBal.roundToInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onEditWalletBalance(w) }, modifier = Modifier.size(32.dp)) {
                                    PrecisionVectorIcon("edit", MaterialTheme.colorScheme.onSurfaceVariant, size = 15.dp)
                                }
                                if (wallets.size > 1) {
                                    IconButton(onClick = { vm.removeWallet(w.id) }, modifier = Modifier.size(32.dp)) {
                                        PrecisionVectorIcon("trash", BinanceRed, size = 15.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Exact Implementation of Uploaded Image 5 (`Quick-Add Bottom Sheet`):
 * - Opens fully expanded (`skipPartiallyExpanded = true`) so `Save ৳0` is immediately visible without scrolling.
 * - Supports multi-line Description input (`singleLine = false, maxLines = 3`).
 * - Auto-locks Category to `Transport` and grays out all other categories when `Transit Pass` / `Rapid Pass` is selected.
 * - Shows an Insufficient Wallet Balance Alert Popup if the selected wallet does not have enough balance.
 */
@Composable
fun QuickAddV6BottomSheet(
    defaultMode: String,
    initialFromWallet: String,
    initialToWallet: String,
    balances: Map<String, Double>,
    darkTheme: Boolean,
    vm: OttoViewModel,
    onDismiss: () -> Unit
) {
    val wallets by vm.wallets.collectAsState()
    val categories by vm.categories.collectAsState()
    val rules by vm.keywordRules.collectAsState()

    var mode by remember { mutableStateOf(defaultMode) }
    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull { it.kind == defaultMode }?.name ?: "Education") }
    var fromWallet by remember { mutableStateOf(initialFromWallet) }
    var toWallet by remember { mutableStateOf(initialToWallet) }
    var swapTrigger by remember { mutableIntStateOf(0) }
    var insufficientPopupMessage by remember { mutableStateOf<String?>(null) }

    val evaluatedAmt = RuleEngine.evaluateMathExpression(amount)

    // Check if the selected wallet is a Transit / Rapid Pass
    val isTransitWalletSelected = remember(fromWallet, mode) {
        val lower = fromWallet.lowercase()
        mode == "EXPENSE" && (lower.contains("transit") || lower.contains("rapid") || lower.contains("metro"))
    }

    LaunchedEffect(isTransitWalletSelected) {
        if (isTransitWalletSelected) {
            selectedCategory = "Transport"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.LG, vertical = Spacing.SM)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.SM)
    ) {
        // 1. Transaction Type
        SingleLineText("Transaction Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM), modifier = Modifier.fillMaxWidth()) {
            listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (key, label) ->
                val isSel = mode == key
                val pillBg = if (isSel) {
                    if (darkTheme) BinanceGold else SoftLavenderPill
                } else MaterialTheme.colorScheme.surface
                val pillText = if (isSel) {
                    if (darkTheme) BinanceDarkCard else SoftLavenderText
                } else MaterialTheme.colorScheme.onSurface

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pillBg,
                    border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable {
                            mode = key
                            if (key != "TRANSFER") {
                                selectedCategory = if (isTransitWalletSelected && key == "EXPENSE") {
                                    "Transport"
                                } else {
                                    categories.firstOrNull { it.kind == key }?.name ?: "Other"
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SingleLineText(label, style = MaterialTheme.typography.titleMedium, color = pillText)
                    }
                }
            }
        }

        // 2. Amount Input (Matches Image 5: `৳ 0` on left, `Amount (৳)` on right, Decimal Phone Keypad!)
        SingleLineText("Amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.MD),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "৳ ",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    BasicTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        decorationBox = { innerTextField ->
                            if (amount.isEmpty()) {
                                Text("0", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SingleLineText(
                    text = "Amount (৳)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Description (Multi-line capable!)
        SingleLineText("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = desc,
            onValueChange = {
                desc = it
                if (mode != "TRANSFER" && !isTransitWalletSelected) {
                    RuleEngine.matchCategory(it, mode, rules)?.let { matched ->
                        selectedCategory = matched.category
                    }
                }
            },
            placeholder = { SingleLineText("Description", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (mode == "TRANSFER") {
            // Transfer Dual-Rail Pill Selection
            SingleLineText("From Wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                items(wallets) { w ->
                    val isSel = fromWallet == w.name
                    WalletChoicePill(
                        label = "${w.name} (৳${(balances[w.name] ?: 0.0).roundToInt()})",
                        selected = isSel,
                        darkTheme = darkTheme,
                        onClick = {
                            fromWallet = w.name
                            if (toWallet == w.name) {
                                toWallet = wallets.firstOrNull { it.name != w.name }?.name ?: w.name
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleLineText("To Wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = {
                        val temp = fromWallet
                        fromWallet = toWallet
                        toWallet = temp
                        swapTrigger++
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    PrecisionVectorIcon("transfer", TransferBlue, size = 16.dp, triggerKey = swapTrigger)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                items(wallets) { w ->
                    val isSel = toWallet == w.name
                    WalletChoicePill(
                        label = "${w.name} (৳${(balances[w.name] ?: 0.0).roundToInt()})",
                        selected = isSel,
                        darkTheme = darkTheme,
                        onClick = {
                            toWallet = w.name
                            if (fromWallet == w.name) {
                                fromWallet = wallets.firstOrNull { it.name != w.name }?.name ?: w.name
                            }
                        }
                    )
                }
            }
        } else {
            // 4. Category (optional) — Locks to Transport & grays out others when Transit Pass is selected!
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleLineText("Category ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SingleLineText(
                    text = if (isTransitWalletSelected) "(locked to Transport for Transit Pass)" else "(optional)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                items(categories.filter { it.kind == mode }) { cat ->
                    val isSel = selectedCategory == cat.name
                    val isDisabledByTransit = isTransitWalletSelected && cat.name != "Transport"
                    WalletChoicePill(
                        label = cat.name,
                        selected = isSel,
                        enabled = !isDisabledByTransit,
                        darkTheme = darkTheme,
                        onClick = {
                            if (!isDisabledByTransit) {
                                selectedCategory = cat.name
                            }
                        }
                    )
                }
            }

            // 5. Payment Method/Wallet Selection (With Insufficient Balance Check!)
            SingleLineText("Payment Method/Wallet Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                items(wallets) { w ->
                    val isSel = fromWallet == w.name
                    val avail = balances[w.name] ?: 0.0
                    WalletChoicePill(
                        label = w.name,
                        selected = isSel,
                        darkTheme = darkTheme,
                        onClick = {
                            fromWallet = w.name
                            val lower = w.name.lowercase()
                            if (mode == "EXPENSE" && (lower.contains("transit") || lower.contains("rapid") || lower.contains("metro"))) {
                                selectedCategory = "Transport"
                            }
                            if (mode == "EXPENSE" && evaluatedAmt > 0.0 && avail < evaluatedAmt) {
                                insufficientPopupMessage = "You only have ৳${avail.roundToInt()} in ${w.name}, which is less than ৳${evaluatedAmt.roundToInt()}."
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.XXS))

        // 6. Full-Width Dark Pill Save Button (`Save ৳0`)
        Button(
            onClick = {
                if (evaluatedAmt <= 0.0) return@Button
                val avail = balances[fromWallet] ?: 0.0
                if ((mode == "EXPENSE" || mode == "TRANSFER") && avail < evaluatedAmt) {
                    insufficientPopupMessage = "Insufficient balance in $fromWallet (Available: ৳${avail.roundToInt()}, Required: ৳${evaluatedAmt.roundToInt()})."
                    return@Button
                }
                vm.addTransaction(
                    type = mode,
                    rawAmount = amount,
                    description = desc,
                    category = if (isTransitWalletSelected) "Transport" else selectedCategory,
                    wallet = fromWallet,
                    toWallet = if (mode == "TRANSFER") toWallet else null,
                    date = LocalDate.now().toString()
                )
                onDismiss()
            },
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            SingleLineText(
                text = "Save ৳${evaluatedAmt.roundToInt()}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    insufficientPopupMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { insufficientPopupMessage = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Insufficient Wallet Balance", style = MaterialTheme.typography.titleLarge, color = BinanceRed) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { insufficientPopupMessage = null }) {
                    SingleLineText("OK", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
    }
}

@Composable
fun WalletChoicePill(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val pillBg = if (selected) {
        if (darkTheme) BinanceGold else SoftLavenderPill
    } else MaterialTheme.colorScheme.surface
    val pillText = if (selected) {
        if (darkTheme) BinanceDarkCard else SoftLavenderText
    } else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = pillBg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .height(38.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SingleLineText(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 13.sp,
                color = pillText
            )
        }
    }
}

class OttoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.otto_app_widget)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_add_btn, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

/**
 * Monochrome Precision 24x24 Vector Icons (Matches Uploaded Images 2, 3 & 4: Coffee Cup, Wallet, Shopping Bag, Bus, Book, Upgraded Trash Bin).
 */
@Composable
fun PrecisionVectorIcon(
    name: String,
    tint: Color,
    size: Dp = 20.dp,
    triggerKey: Any? = null
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        if (triggerKey != null) {
            progress.snapTo(1f)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val p = progress.value

        when (name) {
            "home" -> {
                translate(top = -p * 2.dp.toPx()) {
                    val path = Path().apply {
                        moveTo(w * 0.16f, h * 0.46f)
                        lineTo(w * 0.50f, h * 0.16f)
                        lineTo(w * 0.84f, h * 0.46f)
                        lineTo(w * 0.84f, h * 0.86f)
                        lineTo(w * 0.16f, h * 0.86f)
                        close()
                    }
                    drawPath(path, tint, style = stroke)
                }
            }
            "ledger" -> {
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.14f), Size(w * 0.64f, h * 0.72f), CornerRadius(5.dp.toPx()), stroke)
                translate(left = p * 2.dp.toPx()) {
                    drawLine(tint, Offset(w * 0.34f, h * 0.38f), Offset(w * 0.66f, h * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                translate(left = -p * 2.dp.toPx()) {
                    drawLine(tint, Offset(w * 0.34f, h * 0.56f), Offset(w * 0.66f, h * 0.56f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "wallet" -> {
                translate(top = -p * 2.dp.toPx()) {
                    drawRoundRect(tint, Offset(w * 0.14f, h * 0.24f), Size(w * 0.72f, h * 0.54f), CornerRadius(4.dp.toPx()), stroke)
                    drawLine(tint, Offset(w * 0.14f, h * 0.42f), Offset(w * 0.86f, h * 0.42f), strokeWidth = stroke.width)
                    drawCircle(tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.70f, h * 0.56f))
                }
            }
            "profile" -> {
                translate(top = -p * 2.dp.toPx()) {
                    drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.32f), style = stroke)
                }
                drawArc(tint, 180f, 180f, false, Offset(w * 0.20f, h * 0.58f), Size(w * 0.60f, h * 0.42f), style = stroke)
            }
            "plus" -> {
                rotate(degrees = p * 90f) {
                    drawLine(tint, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "check" -> {
                val path = Path().apply {
                    moveTo(w * 0.20f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.74f)
                    lineTo(w * 0.80f, h * 0.28f)
                }
                drawPath(path, tint, style = stroke)
            }
            "edit" -> {
                val path = Path().apply {
                    moveTo(w * 0.68f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.32f)
                    lineTo(w * 0.34f, h * 0.82f)
                    lineTo(w * 0.16f, h * 0.84f)
                    lineTo(w * 0.18f, h * 0.66f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            // Upgraded Clean Trash Bin Icon (Matches Uploaded Image 4: Lid + Handle + Tapered Bin + 2 Inner Vertical Ribs)
            "trash" -> {
                translate(top = -p * 2.dp.toPx()) {
                    drawLine(tint, Offset(w * 0.16f, h * 0.24f), Offset(w * 0.84f, h * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawRoundRect(tint, Offset(w * 0.38f, h * 0.13f), Size(w * 0.24f, h * 0.11f), CornerRadius(2.dp.toPx()), stroke)
                }
                drawRoundRect(tint, Offset(w * 0.24f, h * 0.24f), Size(w * 0.52f, h * 0.62f), CornerRadius(4.dp.toPx()), stroke)
                drawLine(tint, Offset(w * 0.42f, h * 0.38f), Offset(w * 0.42f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.58f, h * 0.38f), Offset(w * 0.58f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "download" -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.48f), Offset(w * 0.5f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.48f), Offset(w * 0.5f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.84f), Offset(w * 0.80f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "upload" -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.34f), Offset(w * 0.5f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.34f), Offset(w * 0.5f, h * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.84f), Offset(w * 0.80f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "arrow_right" -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.5f), Offset(w * 0.80f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.56f, h * 0.26f), Offset(w * 0.80f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.56f, h * 0.74f), Offset(w * 0.80f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "arrow_left" -> {
                drawLine(tint, Offset(w * 0.80f, h * 0.5f), Offset(w * 0.20f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.44f, h * 0.26f), Offset(w * 0.20f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.44f, h * 0.74f), Offset(w * 0.20f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "sun_moon" -> {
                rotate(degrees = p * 60f) {
                    drawCircle(tint, radius = w * 0.22f, style = stroke)
                    for (angle in 0 until 360 step 45) {
                        rotate(angle.toFloat()) {
                            drawLine(tint, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.20f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                        }
                    }
                }
            }
            "transfer" -> {
                translate(left = p * 3.dp.toPx()) {
                    drawLine(tint, Offset(w * 0.18f, h * 0.34f), Offset(w * 0.82f, h * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.64f, h * 0.18f), Offset(w * 0.82f, h * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                translate(left = -p * 3.dp.toPx()) {
                    drawLine(tint, Offset(w * 0.82f, h * 0.66f), Offset(w * 0.18f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.36f, h * 0.82f), Offset(w * 0.18f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            // Coffee Cup with Steam (From Uploaded Image 2: "liked the icons")
            "coffee", "food" -> {
                translate(top = -p * 2.dp.toPx()) {
                    drawLine(tint, Offset(w * 0.36f, h * 0.16f), Offset(w * 0.36f, h * 0.26f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.54f, h * 0.16f), Offset(w * 0.54f, h * 0.26f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.34f), Size(w * 0.48f, h * 0.38f), CornerRadius(6.dp.toPx()), stroke)
                drawArc(tint, -90f, 180f, false, Offset(w * 0.58f, h * 0.38f), Size(w * 0.20f, h * 0.24f), style = stroke)
                drawLine(tint, Offset(w * 0.16f, h * 0.82f), Offset(w * 0.74f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            // Shopping Bag (From Uploaded Image 2: "liked the icons")
            "bag", "groceries" -> {
                drawArc(tint, 180f, 180f, false, Offset(w * 0.34f, h * 0.16f), Size(w * 0.32f, h * 0.28f), style = stroke)
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.32f), Size(w * 0.60f, h * 0.52f), CornerRadius(4.dp.toPx()), stroke)
            }
            // Bus Front View (From Uploaded Image 2: "liked the icons")
            "bus", "transport" -> {
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.16f), Size(w * 0.64f, h * 0.58f), CornerRadius(5.dp.toPx()), stroke)
                drawLine(tint, Offset(w * 0.18f, h * 0.48f), Offset(w * 0.82f, h * 0.48f), strokeWidth = stroke.width)
                drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.32f, h * 0.62f))
                drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.68f, h * 0.62f))
                drawLine(tint, Offset(w * 0.28f, h * 0.74f), Offset(w * 0.28f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.74f), Offset(w * 0.72f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            // Book Icon (From Uploaded Image 2: "liked the icons")
            "book", "edu" -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.16f), Size(w * 0.56f, h * 0.68f), CornerRadius(3.dp.toPx()), stroke)
                drawLine(tint, Offset(w * 0.34f, h * 0.16f), Offset(w * 0.34f, h * 0.84f), strokeWidth = stroke.width)
            }
            "health" -> {
                drawCircle(tint, radius = w * 0.34f, style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "tech" -> {
                drawRoundRect(tint, Offset(w * 0.26f, h * 0.14f), Size(w * 0.48f, h * 0.72f), CornerRadius(4.dp.toPx()), stroke)
                drawCircle(tint, radius = 1.5.dp.toPx(), center = Offset(w * 0.5f, h * 0.74f))
            }
            "bolt" -> {
                val zap = Path().apply {
                    moveTo(w * 0.55f, h * 0.14f)
                    lineTo(w * 0.22f, h * 0.54f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.44f, h * 0.86f)
                    lineTo(w * 0.78f, h * 0.46f)
                    lineTo(w * 0.52f, h * 0.46f)
                    close()
                }
                drawPath(zap, tint, style = stroke)
            }
            else -> {
                drawCircle(tint, radius = w * 0.34f, style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.30f), Offset(w * 0.5f, h * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}
