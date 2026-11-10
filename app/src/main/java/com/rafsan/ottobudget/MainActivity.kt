package com.rafsan.ottobudget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafsan.ottobudget.data.local.CategoryEntity
import com.rafsan.ottobudget.data.local.KeywordRuleEntity
import com.rafsan.ottobudget.data.local.TransactionEntity
import com.rafsan.ottobudget.data.local.WalletEntity
import com.rafsan.ottobudget.domain.RuleEngine
import com.rafsan.ottobudget.ui.theme.*
import com.rafsan.ottobudget.ui.viewmodel.OttoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * MainActivity inherits from FragmentActivity so AndroidX BiometricPrompt works natively
 * alongside Jetpack Compose.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: OttoViewModel = viewModel()
            val darkTheme by vm.isDarkTheme.collectAsState()
            val showSplashWink by vm.showSplashWink.collectAsState()
            val showOnboarding by vm.showOnboarding.collectAsState()
            val isAppLocked by vm.isAppLocked.collectAsState()
            val isPinEnabled by vm.isPinLockEnabled.collectAsState()

            // Re-lock automatically when app is backgrounded if PIN lock is enabled
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, isPinEnabled) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && isPinEnabled) {
                        vm.isAppLocked.value = true
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Keep Status Bar & Navigation Bar icons crisp in both Light and Dark Mode
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
                Crossfade(
                    targetState = when {
                        showSplashWink -> "splash"
                        isAppLocked && isPinEnabled -> "lock"
                        showOnboarding -> "onboarding"
                        else -> "main"
                    },
                    animationSpec = tween(320),
                    label = "rootCrossfade"
                ) { screenState ->
                    when (screenState) {
                        "splash" -> OttoWinkingSplashScreen(onFinished = { vm.finishSplashWink() })
                        "lock" -> IosStyleSecurityLockScreen(vm = vm, activity = this@MainActivity)
                        "onboarding" -> OttoOnboardingScreen(vm = vm, onFinish = { vm.completeOnboarding() })
                        else -> OttoBudgetRootScreen(vm = vm, activity = this@MainActivity)
                    }
                }
            }
        }
    }
}

/**
 * Tactile Spring Press Modifier: Animates scale down to 0.95f on press and springs back on release.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "bounceScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Helper to trigger native Android BiometricPrompt (Fingerprint / Face Unlock).
 */
fun triggerDeviceBiometricPrompt(
    activity: FragmentActivity,
    title: String = "Unlock OttoBudget",
    subtitle: String = "Use your fingerprint or face to unlock your financial ledger",
    onSuccess: () -> Unit,
    onError: (String) -> Unit = {}
) {
    val biometricManager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    val canAuth = biometricManager.canAuthenticate(authenticators)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometric sensor not enrolled or unavailable on this device")
        return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Use Passcode")
        .setAllowedAuthenticators(authenticators)
        .build()

    runCatching { prompt.authenticate(promptInfo) }.onFailure { onError(it.message ?: "Biometric error") }
}

/**
 * iOS Lock Screen-Inspired PIN & Biometric Authentication Screen:
 * - Translucent frosted obsidian/turquoise aura backdrop.
 * - Animated padlock + "Enter Passcode" prompt.
 * - 4 passcode dots that fill with spring physics and shake horizontally on wrong PIN.
 * - 3x4 circular frosted glass keypad (0-9 with iOS sub-letters) + Biometric trigger + Backspace.
 */
@Composable
fun IosStyleSecurityLockScreen(
    vm: OttoViewModel,
    activity: FragmentActivity
) {
    val biometricEnabled by vm.isBiometricEnabled.collectAsState()
    var enteredPin by remember { mutableStateOf("") }
    var promptSubtitle by remember { mutableStateOf("Enter your 4-digit passcode") }
    var isErrorState by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val lockIconBounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Automatically trigger BiometricPrompt on launch if enabled
    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) {
            delay(220)
            triggerDeviceBiometricPrompt(
                activity = activity,
                onSuccess = { vm.onBiometricUnlockSuccess() },
                onError = { promptSubtitle = it }
            )
        }
    }

    fun onDigitTapped(digit: String) {
        if (enteredPin.length >= 4) return
        isErrorState = false
        val nextPin = enteredPin + digit
        enteredPin = nextPin
        if (nextPin.length == 4) {
            scope.launch {
                delay(90)
                val unlocked = vm.tryUnlockWithPin(nextPin)
                if (!unlocked) {
                    isErrorState = true
                    promptSubtitle = "Incorrect Passcode. Try again."
                    // iOS-style horizontal shake animation
                    val keyframes = listOf(-20f, 20f, -15f, 15f, -8f, 8f, 0f)
                    for (offset in keyframes) {
                        shakeOffset.animateTo(offset, animationSpec = tween(42, easing = LinearEasing))
                    }
                    enteredPin = ""
                } else {
                    lockIconBounce.animateTo(1.25f, spring(dampingRatio = 0.45f))
                }
            }
        }
    }

    val keypadRows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F171E),
                        Color(0xFF0B0E11),
                        Color(0xFF080A0D)
                    )
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Subtle radial OttoTurquoise glass aura behind the lock screen
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OttoTurquoise.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.5f, size.height * 0.22f),
                radius = size.width * 0.65f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(8.dp))

            // Top Lock Icon + Passcode Dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(lockIconBounce.value)
                        .clip(CircleShape)
                        .background(OttoTurquoise.copy(alpha = 0.16f))
                        .border(1.dp, OttoTurquoise.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon(
                        name = "lock",
                        tint = OttoTurquoise,
                        size = 24.dp,
                        triggerKey = enteredPin.length
                    )
                }

                SingleLineText(
                    text = "Enter Passcode",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )

                SingleLineText(
                    text = promptSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isErrorState) BinanceRed else BinanceMutedText
                )

                Spacer(Modifier.height(6.dp))

                // 4 iOS Passcode Dots with horizontal error shake
                Row(
                    modifier = Modifier.offset(x = shakeOffset.value.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val filled = i < enteredPin.length
                        val dotScale by animateFloatAsState(
                            targetValue = if (filled) 1.18f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
                            label = "dotScale_$i"
                        )
                        val dotColor = when {
                            isErrorState -> BinanceRed
                            filled -> OttoTurquoise
                            else -> Color.Transparent
                        }
                        val borderColor = when {
                            isErrorState -> BinanceRed
                            filled -> OttoTurquoise
                            else -> Color.White.copy(alpha = 0.45f)
                        }
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .scale(dotScale)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.5.dp, borderColor, CircleShape)
                        )
                    }
                }
            }

            // iOS Frosted Circular Keypad Grid (1-9, Biometric, 0, Backspace)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                keypadRows.forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowItems.forEach { (digit, letters) ->
                            IosKeypadCircleButton(
                                digit = digit,
                                letters = letters,
                                onClick = { onDigitTapped(digit) }
                            )
                        }
                    }
                }

                // Bottom Row: Biometric | 0 | Backspace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Biometric Fingerprint / Face ID Trigger
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(
                                if (biometricEnabled) OttoTurquoise.copy(alpha = 0.16f) else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (biometricEnabled) OttoTurquoise.copy(alpha = 0.45f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .bounceClick(enabled = biometricEnabled) {
                                triggerDeviceBiometricPrompt(
                                    activity = activity,
                                    onSuccess = { vm.onBiometricUnlockSuccess() },
                                    onError = { promptSubtitle = it }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (biometricEnabled) {
                            PrecisionVectorIcon(
                                name = "fingerprint",
                                tint = OttoTurquoise,
                                size = 28.dp,
                                triggerKey = enteredPin.length
                            )
                        }
                    }

                    // Center: 0
                    IosKeypadCircleButton(
                        digit = "0",
                        letters = "",
                        onClick = { onDigitTapped("0") }
                    )

                    // Right: Backspace / Cancel
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .bounceClick(enabled = enteredPin.isNotEmpty()) {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (enteredPin.isNotEmpty()) {
                            PrecisionVectorIcon(
                                name = "backspace",
                                tint = Color.White.copy(alpha = 0.85f),
                                size = 24.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IosKeypadCircleButton(
    digit: String,
    letters: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = CircleShape
            )
            .bounceClick(scaleDown = 0.91f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 27.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
    }
}

/**
 * Animated Winking Octopus Splash Screen using the exact artwork from the uploaded App Icon.
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
        winkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
        )
        delay(260)
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
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = winkProgress.value
                    if (p > 0.02f) {
                        val cx = size.width * 0.5801f
                        val cy = size.height * 0.4980f
                        val eyeR = size.width * 0.027f

                        drawCircle(
                            color = Color.White,
                            radius = eyeR * 1.35f,
                            center = Offset(cx, cy)
                        )

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
    val mergedStyle = if (fontSize != TextUnit.Unspecified || fontWeight != null) {
        style.copy(
            fontSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize,
            fontWeight = fontWeight ?: style.fontWeight
        )
    } else style

    Text(
        text = text,
        style = mergedStyle,
        color = color,
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
                        .bounceClick { step = 1 },
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
                    Text("Set up your\npersonal ledger.", style = MaterialTheme.typography.displayLarge)
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
                        label = { SingleLineText("Monthly Budget Cap (৳)") },
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
fun OttoBudgetRootScreen(
    vm: OttoViewModel,
    activity: FragmentActivity
) {
    val wallets by vm.wallets.collectAsState()
    val categories by vm.categories.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val userName by vm.userName.collectAsState()
    val excludeMajor by vm.excludeMajorGlobal.collectAsState()
    val darkTheme by vm.isDarkTheme.collectAsState()
    val monthlyBudget by vm.monthlyBudgetTarget.collectAsState()
    val statusMsg by vm.statusMessage.collectAsState()
    val isPinEnabled by vm.isPinLockEnabled.collectAsState()

    var currentTab by remember { mutableStateOf("home") }
    var settingsSubScreen by remember { mutableStateOf("menu") } // "menu", "backup", "magic_words", "profile", "security", "faq"
    var navAnimTrigger by remember { mutableIntStateOf(0) }
    var avatarWinkTrigger by remember { mutableIntStateOf(0) }
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

    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Animated rotation for the Sun <-> Crescent Half-Moon theme button
    val themeIconRotation by animateFloatAsState(
        targetValue = if (darkTheme) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "themeRot"
    )

    // Animated mini-bounce when tapping Otto avatar in top bar
    val avatarScale = remember { Animatable(1f) }
    LaunchedEffect(avatarWinkTrigger) {
        if (avatarWinkTrigger > 0) {
            avatarScale.animateTo(1.16f, spring(dampingRatio = 0.4f))
            avatarScale.animateTo(1.0f, spring(dampingRatio = 0.6f))
        }
    }

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
                            .scale(avatarScale.value)
                            .clip(CircleShape)
                            .border(1.5.dp, OttoTurquoise.copy(alpha = 0.65f), CircleShape)
                            .bounceClick {
                                avatarWinkTrigger++
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPinEnabled) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(40.dp)
                                .bounceClick { vm.lockAppNow() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrecisionVectorIcon(
                                    name = "lock",
                                    tint = if (darkTheme) OttoTurquoise else MaterialTheme.colorScheme.onSurface,
                                    size = 17.dp,
                                    triggerKey = navAnimTrigger
                                )
                            }
                        }
                    }

                    // Theme Toggle Button: Morphs between Sun (Light Mode) and Crescent Half-Moon (Dark Mode)!
                    Surface(
                        shape = CircleShape,
                        color = if (darkTheme) OttoTurquoise.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (darkTheme) OttoTurquoise.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .bounceClick {
                                navAnimTrigger++
                                vm.toggleDarkTheme()
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.graphicsLayer { rotationZ = themeIconRotation * 0.2f }
                        ) {
                            PrecisionVectorIcon(
                                name = if (darkTheme) "half_moon" else "sun",
                                tint = if (darkTheme) OttoTurquoise else MaterialTheme.colorScheme.onSurface,
                                size = 19.dp,
                                triggerKey = darkTheme
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // iOS-Style Glassmorphic Floating Bottom Navigation Dock (NO bottom dot indicator!)
            IosGlassmorphicBottomNav(
                currentTab = currentTab,
                darkTheme = darkTheme,
                navAnimTrigger = navAnimTrigger,
                onSelectTab = { tab ->
                    currentTab = tab
                    if (tab == "profile") settingsSubScreen = "menu"
                    navAnimTrigger++
                },
                onQuickAdd = {
                    navAnimTrigger++
                    sheetDefaultMode = "EXPENSE"
                    if (wallets.isNotEmpty()) {
                        sheetPreselectedFrom = wallets.first().name
                        sheetPreselectedTo = wallets.getOrNull(1)?.name ?: wallets.first().name
                    }
                    showAddSheet = true
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.MD)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(animationSpec = tween(160)))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    "home" -> DashboardV7Screen(
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
                    "ledger" -> LedgerV7Screen(
                        transactions = transactions,
                        categories = categories,
                        darkTheme = darkTheme,
                        onDeleteTx = { vm.deleteTransaction(it) }
                    )
                    "wallets" -> WalletsV7Screen(
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
                    "profile" -> SettingsHubV7Screen(
                        subScreen = settingsSubScreen,
                        onNavigateSubScreen = { settingsSubScreen = it },
                        vm = vm,
                        activity = activity,
                        balances = balances,
                        onEditWalletBalance = { w -> walletToEditBalance = w }
                    )
                }
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                sheetState = addSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                QuickAddV7BottomSheet(
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

/**
 * iOS-Style Glassmorphic Floating Bottom Bar:
 * - Translucent multi-stop glass gradient with top specular rim highlight.
 * - Floating frosted glass pill on the active tab (NO dot indicator underneath!).
 * - Multi-part animated vector icons (`itshover.com` style) with spring bounce & path motion.
 */
@Composable
fun IosGlassmorphicBottomNav(
    currentTab: String,
    darkTheme: Boolean,
    navAnimTrigger: Int,
    onSelectTab: (String) -> Unit,
    onQuickAdd: () -> Unit
) {
    val dockBrush = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1F2631).copy(alpha = 0.88f),
                Color(0xFF13171E).copy(alpha = 0.94f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.90f),
                Color(0xFFEDF1F7).copy(alpha = 0.94f)
            )
        )
    }

    val specularBorderBrush = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                OttoTurquoise.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                OttoTurquoise.copy(alpha = 0.35f),
                Color(0xFFD5DCE6).copy(alpha = 0.65f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.LG, vertical = Spacing.SM)
    ) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = if (darkTheme) 16.dp else 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(dockBrush)
                .border(1.2.dp, specularBorderBrush, RoundedCornerShape(30.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlassNavTabItem(
                    iconName = "home",
                    selected = currentTab == "home",
                    darkTheme = darkTheme,
                    triggerKey = navAnimTrigger,
                    onClick = { onSelectTab("home") }
                )
                GlassNavTabItem(
                    iconName = "ledger",
                    selected = currentTab == "ledger",
                    darkTheme = darkTheme,
                    triggerKey = navAnimTrigger,
                    onClick = { onSelectTab("ledger") }
                )

                // Center Glassmorphic Quick-Add Squircle Button
                val fabBgBrush = if (darkTheme) {
                    Brush.linearGradient(listOf(OttoTurquoise, OttoTurquoiseDeep))
                } else {
                    Brush.linearGradient(listOf(InkDark, Color(0xFF262D3B)))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(fabBgBrush)
                        .border(
                            width = 1.dp,
                            color = if (darkTheme) Color.White.copy(alpha = 0.35f) else OttoTurquoise.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(17.dp)
                        )
                        .bounceClick(scaleDown = 0.90f, onClick = onQuickAdd),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon(
                        name = "plus",
                        tint = if (darkTheme) InkDark else Color.White,
                        size = 21.dp,
                        triggerKey = navAnimTrigger
                    )
                }

                GlassNavTabItem(
                    iconName = "wallet",
                    selected = currentTab == "wallets",
                    darkTheme = darkTheme,
                    triggerKey = navAnimTrigger,
                    onClick = { onSelectTab("wallets") }
                )
                GlassNavTabItem(
                    iconName = "profile",
                    selected = currentTab == "profile",
                    darkTheme = darkTheme,
                    triggerKey = navAnimTrigger,
                    onClick = { onSelectTab("profile") }
                )
            }
        }
    }
}

/**
 * Individual Glassmorphic Tab Item (No bottom dot! Uses frosted glass capsule + animated icon scale/morph).
 */
@Composable
fun GlassNavTabItem(
    iconName: String,
    selected: Boolean,
    darkTheme: Boolean,
    triggerKey: Int,
    onClick: () -> Unit
) {
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.88f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "navPillScale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium),
        label = "navIconScale"
    )

    val activeTint = if (darkTheme) OttoTurquoise else InkDark
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)

    val glassPillBrush = if (selected) {
        if (darkTheme) {
            Brush.verticalGradient(
                listOf(
                    OttoTurquoise.copy(alpha = 0.24f),
                    OttoTurquoise.copy(alpha = 0.10f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.96f),
                    OttoTurquoise.copy(alpha = 0.18f)
                )
            )
        }
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    val pillBorderColor = if (selected) {
        if (darkTheme) OttoTurquoise.copy(alpha = 0.48f) else OttoTurquoise.copy(alpha = 0.55f)
    } else Color.Transparent

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(56.dp)
            .height(46.dp)
            .scale(pillScale)
            .clip(RoundedCornerShape(18.dp))
            .background(glassPillBrush)
            .border(1.dp, pillBorderColor, RoundedCornerShape(18.dp))
            .bounceClick(scaleDown = 0.90f, onClick = onClick)
    ) {
        Box(modifier = Modifier.scale(iconScale)) {
            PrecisionVectorIcon(
                name = iconName,
                tint = if (selected) activeTint else inactiveTint,
                size = 22.dp,
                triggerKey = if (selected) triggerKey else null
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
                                .bounceClick { selectedColor = hex },
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

/**
 * V7 Dashboard Screen:
 * - Strictly Fixed Height & Layout Bounds on the 7D SPEND (`Out | In | Line`) Card (`238.dp` outer card, `148.dp` inner chart viewport)
 *   so switching between `Out`, `In`, and `Line` or tapping any day NEVER shifts a single pixel!
 * - Uses `#6BD0C2` (OttoTurquoise) in Dark Mode instead of yellow.
 */
@Composable
fun DashboardV7Screen(
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
    val currentMonthPrefix = remember(today) { today.toString().substring(0, 7) }

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
    val animatedWindowSum by animateIntAsState(
        targetValue = activeWindowSum.roundToInt(),
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "animWindowSum"
    )
    val budgetRatio by animateFloatAsState(
        targetValue = if (totalSpend <= 0.0) 0f else (totalSpend / monthlyBudget.coerceAtLeast(1.0)).coerceIn(0.04, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "animRatio"
    )

    // Animated reveal for the Line chart mode
    val lineReveal = remember { Animatable(0f) }
    LaunchedEffect(chartMode, dayValues) {
        if (chartMode == "PACE") {
            lineReveal.snapTo(0f)
            lineReveal.animateTo(1f, animationSpec = tween(520, easing = FastOutSlowInEasing))
        }
    }

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
                            color = if (darkTheme) OttoTurquoise.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (darkTheme) BorderStroke(1.dp, OttoTurquoise.copy(alpha = 0.40f)) else null,
                            modifier = Modifier.bounceClick { onToggleMajor() }
                        ) {
                            SingleLineText(
                                text = if (excludeMajor) "Daily" else "All",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 12.sp,
                                color = if (darkTheme) OttoTurquoise else MaterialTheme.colorScheme.onSurface,
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
                            color = if (darkTheme) OttoTurquoise else BinanceGreen
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
                            val barColor = if (totalSpend > monthlyBudget * 0.85) BinanceRed else MaterialTheme.colorScheme.primary
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(budgetRatio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
            }
        }

        // 2. Strictly Fixed-Height 7D Spend / Income / Line Card (NEVER shifts size or position!)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(238.dp) // Strictly locked card height!
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.LG, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Fixed 44dp Top Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
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
                                "INCOME" -> "+৳$animatedWindowSum"
                                "PACE" -> "৳$dailyAvg/d"
                                else -> "৳$animatedWindowSum"
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
                                val activeBg = if (darkTheme) OttoTurquoise else MaterialTheme.colorScheme.surface
                                val activeText = if (darkTheme) InkDark else MaterialTheme.colorScheme.onSurface
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (active) activeBg else Color.Transparent)
                                        .bounceClick { chartMode = key }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    SingleLineText(
                                        text = lbl,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 12.sp,
                                        color = if (active) activeText else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Strictly Fixed 148.dp Chart & Day Selector Container (Identical height & day-circle positions across Out, In, and Line!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                    ) {
                        if (chartMode == "PACE") {
                            val lineColor = if (darkTheme) OttoTurquoise else InkDark
                            val selectedVal = dayValues.getOrElse(selectedDayIndex) { 0.0 }

                            // Top 110.dp Spline Canvas + Selected Day Floating Callout
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(108.dp)
                                    .align(Alignment.TopCenter)
                            ) {
                                val w = size.width
                                val h = size.height
                                val padX = 20.dp.toPx()
                                val usableW = (w - padX * 2f).coerceAtLeast(1f)

                                val pts = dayValues.mapIndexed { i, v ->
                                    val x = padX + (i.toFloat() / 6f) * usableW
                                    val ratio = (v / maxDayVal).toFloat().coerceIn(0f, 1f)
                                    val y = (h * 0.82f) - (ratio * (h * 0.56f))
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

                                val fillPath = Path().apply {
                                    addPath(splinePath)
                                    lineTo(pts.last().x, h)
                                    lineTo(pts.first().x, h)
                                    close()
                                }

                                clipRect(right = w * lineReveal.value) {
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                                            startY = 0f,
                                            endY = h
                                        )
                                    )
                                    drawPath(
                                        path = splinePath,
                                        color = lineColor,
                                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                val selPt = pts.getOrElse(selectedDayIndex) { pts.last() }
                                drawLine(
                                    color = lineColor.copy(alpha = 0.30f),
                                    start = Offset(selPt.x, selPt.y),
                                    end = Offset(selPt.x, h),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawCircle(color = lineColor.copy(alpha = 0.28f), radius = 9.dp.toPx(), center = selPt)
                                drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = selPt)
                            }
                        }

                        // Fixed 7-Column Row (148.dp tall) used for Out, In, AND Line day selection!
                        val accent = when {
                            chartMode == "INCOME" -> BinanceGreen
                            darkTheme -> OttoTurquoise
                            else -> InkDark
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7Dates.forEachIndexed { idx, dateObj ->
                                val isSelected = idx == selectedDayIndex
                                val v = dayValues[idx]
                                val targetRatio = if (v <= 0.0) 0.10f else (v / maxDayVal).toFloat().coerceIn(0.18f, 1f)
                                val animRatio by animateFloatAsState(
                                    targetValue = targetRatio,
                                    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                                    label = "stem_$idx"
                                )
                                val dLabel = dateObj.format(dayShortFormatter).take(1)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (chartMode == "PACE") 0.45f else 1f)
                                            else Color.Transparent
                                        )
                                        .bounceClick { selectedDayIndex = idx }
                                        .padding(vertical = 6.dp)
                                ) {
                                    // Slot 1: Strictly Fixed 20.dp Value Badge Slot
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            SingleLineText(
                                                text = "৳${v.roundToInt()}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontSize = 10.sp,
                                                color = if (darkTheme) OttoTurquoise else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Slot 2: Strictly Fixed 74.dp Stem Track Slot (Stems grow inside this fixed 74.dp box so parent height never changes!)
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .height(74.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        if (chartMode != "PACE") {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Bottom,
                                                modifier = Modifier.fillMaxHeight()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (v > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.5.dp)
                                                        .height(62.dp * animRatio)
                                                        .background(if (v > 0) accent.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
                                                )
                                            }
                                        }
                                    }

                                    // Slot 3: Strictly Fixed 30.dp Day Circle Slot
                                    val circleBg = when {
                                        isSelected && darkTheme -> OttoTurquoise
                                        isSelected -> InkDark
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                    val circleText = when {
                                        isSelected && darkTheme -> InkDark
                                        isSelected -> Color.White
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(circleBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SingleLineText(
                                            text = dLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 11.sp,
                                            color = circleText
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
                        SingleLineText(
                            "See All",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 12.sp,
                            color = if (darkTheme) OttoTurquoise else MaterialTheme.colorScheme.primary
                        )
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
                TransactionV7MonochromeRow(
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
 * Monochrome Transaction Row with Spring Entrance & Icon Micro-Animation on Tap.
 */
@Composable
fun TransactionV7MonochromeRow(
    tx: TransactionEntity,
    iconName: String,
    darkTheme: Boolean,
    onDelete: () -> Unit
) {
    var iconTapCount by remember { mutableIntStateOf(0) }
    val enterScale = remember { Animatable(0.96f) }
    LaunchedEffect(tx.id) {
        enterScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow))
    }

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
            .scale(enterScale.value)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .bounceClick(scaleDown = 0.98f) { iconTapCount++ }
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
 * V7 Ledger Screen (Uses OttoTurquoise in Dark Mode instead of yellow!).
 */
@Composable
fun LedgerV7Screen(
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                listOf("ALL" to "All", "EXPENSE" to "Out", "INCOME" to "In", "TRANSFER" to "Move").forEach { (key, lbl) ->
                    val isSel = selectedType == key
                    val pillBg = if (isSel) {
                        if (darkTheme) OttoTurquoise else SoftLavenderPill
                    } else MaterialTheme.colorScheme.surface
                    val pillText = if (isSel) {
                        if (darkTheme) InkDark else SoftLavenderText
                    } else MaterialTheme.colorScheme.onSurface

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pillBg,
                        border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .bounceClick { selectedType = key }
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
                        if (darkTheme) OttoTurquoise else SoftLavenderPill
                    } else MaterialTheme.colorScheme.surface
                    val pillText = if (isSel) {
                        if (darkTheme) InkDark else SoftLavenderText
                    } else MaterialTheme.colorScheme.onSurface

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pillBg,
                        border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .height(36.dp)
                            .bounceClick { selectedCategory = catName }
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
                TransactionV7MonochromeRow(
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
fun WalletsV7Screen(
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
                    .bounceClick(scaleDown = 0.98f) { onMoveFromWallet(wallet.name) }
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
                                Box(Modifier.size(18.dp).offset(x = 11.dp).clip(CircleShape).background(OttoTurquoise.copy(alpha = 0.9f)))
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
                                    .bounceClick { onEditWalletBalance(wallet) },
                                contentAlignment = Alignment.Center
                            ) {
                                PrecisionVectorIcon("edit", textColor, size = 16.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(actionPillBg)
                                    .bounceClick { onMoveFromWallet(wallet.name) },
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
 * V7 Settings Hub with Dedicated Sub-Screens:
 * 1. Main Menu ("menu")
 * 2. Profile & Wallets ("profile")
 * 3. Security & App Lock ("security") — iOS Lock Screen PIN & Biometrics!
 * 4. Categories & Magic Words ("magic_words")
 * 5. Backup & Cloud Sync ("backup")
 * 6. FAQ & Help ("faq") — Answers "What is the Monthly Cap?" and core doubts!
 */
@Composable
fun SettingsHubV7Screen(
    subScreen: String,
    onNavigateSubScreen: (String) -> Unit,
    vm: OttoViewModel,
    activity: FragmentActivity,
    balances: Map<String, Double>,
    onEditWalletBalance: (WalletEntity) -> Unit
) {
    if (subScreen != "menu") {
        BackHandler { onNavigateSubScreen("menu") }
    }

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = {
            fadeIn(tween(200)).togetherWith(fadeOut(tween(150)))
        },
        label = "settingsSubTransition"
    ) { targetSub ->
        when (targetSub) {
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
            "security" -> SecurityAndLockSubScreen(
                vm = vm,
                activity = activity,
                onBack = { onNavigateSubScreen("menu") }
            )
            "faq" -> FaqAndHelpSubScreen(
                onBack = { onNavigateSubScreen("menu") }
            )
            else -> {
                val rules by vm.keywordRules.collectAsState()
                val categories by vm.categories.collectAsState()
                val wallets by vm.wallets.collectAsState()
                val lastSync by vm.lastSyncedText.collectAsState()
                val isPinOn by vm.isPinLockEnabled.collectAsState()
                val isBioOn by vm.isBiometricEnabled.collectAsState()

                val securitySubtitle = when {
                    isPinOn && isBioOn -> "PIN + Biometric Unlock active"
                    isPinOn -> "4-Digit iOS PIN Lock active"
                    else -> "Set up 4-digit PIN & Fingerprint / Face Lock"
                }

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
                            iconName = "lock",
                            title = "Security & App Lock",
                            subtitle = securitySubtitle,
                            onClick = { onNavigateSubScreen("security") }
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

                    item {
                        SettingsNavCard(
                            iconName = "help",
                            title = "FAQ & Guide",
                            subtitle = "What is the Monthly Cap? • Cloud Sync & Magic Words",
                            onClick = { onNavigateSubScreen("faq") }
                        )
                    }
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
    val darkTheme = MaterialTheme.colorScheme.background == BinanceDarkBg
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
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
                        .background(if (darkTheme) OttoTurquoise.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon(
                        name = iconName,
                        tint = if (darkTheme) OttoTurquoise else MaterialTheme.colorScheme.onSurface,
                        size = 20.dp
                    )
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
 * Settings / Security & App Lock Sub-Screen:
 * - Set or change 4-digit PIN using an interactive iOS-style passcode dialog.
 * - Enable or disable Device Biometric Unlock (Fingerprint / Face ID).
 * - Lock App Immediately button to test the lock screen.
 */
@Composable
fun SecurityAndLockSubScreen(
    vm: OttoViewModel,
    activity: FragmentActivity,
    onBack: () -> Unit
) {
    val isPinEnabled by vm.isPinLockEnabled.collectAsState()
    val isBioEnabled by vm.isBiometricEnabled.collectAsState()
    var showPinSetupDialog by remember { mutableStateOf(false) }

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
                SingleLineText("Security & App Lock", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.LG),
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD)
                ) {
                    SingleLineText("CONFIDENTIALITY & APP LOCK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)

                    // 1. 4-Digit iOS Passcode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("4-Digit Passcode Lock", style = MaterialTheme.typography.titleMedium)
                            SingleLineText(
                                if (isPinEnabled) "Required on app launch & resume" else "Protect your wallets & ledger",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPinEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable) {
                                    showPinSetupDialog = true
                                } else {
                                    vm.disablePinLock()
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // 2. Biometric Unlock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("Biometric Unlock (Fingerprint / Face)", style = MaterialTheme.typography.titleMedium)
                            SingleLineText(
                                "Use device biometric sensor alongside PIN",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBioEnabled,
                            onCheckedChange = { wantBio ->
                                if (!isPinEnabled && wantBio) {
                                    showPinSetupDialog = true
                                } else if (wantBio) {
                                    triggerDeviceBiometricPrompt(
                                        activity = activity,
                                        title = "Verify Biometric Sensor",
                                        subtitle = "Confirm your fingerprint or face to enable biometric unlock",
                                        onSuccess = { vm.toggleBiometricUnlock(true) },
                                        onError = { vm.statusMessage.value = it }
                                    )
                                } else {
                                    vm.toggleBiometricUnlock(false)
                                }
                            }
                        )
                    }

                    // 3. Action Buttons: Change Passcode & Lock App Now
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.SM)
                    ) {
                        Button(
                            onClick = { showPinSetupDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            SingleLineText(
                                text = if (isPinEnabled) "Change PIN" else "Set 4-Digit PIN",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        if (isPinEnabled) {
                            OutlinedButton(
                                onClick = { vm.lockAppNow() },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                SingleLineText("Lock Now", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinSetupDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var errText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Set 4-Digit Passcode", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    Text(
                        "Enter a 4-digit PIN for your iOS-style Lock Screen. You can also unlock with your fingerprint or face.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { SingleLineText("New 4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { SingleLineText("Confirm 4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errText?.let {
                        SingleLineText(it, style = MaterialTheme.typography.bodyMedium, color = BinanceRed)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            pinInput.length != 4 -> errText = "Passcode must be exactly 4 digits"
                            pinInput != confirmPinInput -> errText = "Passcodes do not match"
                            else -> {
                                vm.saveOrUpdatePin(pinInput, enableBiometric = true)
                                showPinSetupDialog = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SingleLineText("Enable Lock", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    SingleLineText("Cancel")
                }
            }
        )
    }
}

/**
 * Settings / FAQ & Help Sub-Screen:
 * Answers user doubts with expandable accordion cards, starting with "What is the Monthly Cap?".
 */
data class FaqItem(val question: String, val answer: String)

@Composable
fun FaqAndHelpSubScreen(onBack: () -> Unit) {
    val faqs = remember {
        listOf(
            FaqItem(
                question = "What is the Monthly Cap, and how does it work?",
                answer = "Your Monthly Cap (configured in Settings → Profile & Wallets) is your target spending ceiling for the current month. On the Home screen, 'Spent ৳X / ৳Monthly Cap' fills the progress bar as you log expenses and turns red if you cross 85% of your cap. Unlike your Wallet Balance—which strictly blocks spending money a wallet doesn't have—the Monthly Cap never blocks transactions; it acts as a visual pacing guide alongside your Daily Average (Avg ৳X/d)."
            ),
            FaqItem(
                question = "What does the 'Daily' vs. 'All' pill on the Total Balance card do?",
                answer = "When set to 'Daily', OttoBudget excludes one-off major expenses (such as house rent or semester tuition fees marked as major) from your monthly progress bar and Daily Average so your everyday spending pace stays accurate. Tapping it switches to 'All' to include every expense."
            ),
            FaqItem(
                question = "How does Zero-Schema Encrypted Cloud Sync protect my data?",
                answer = "Traditional apps require you to run SQL schema scripts on Supabase, which exposes your table structure and fails if tables aren't configured. OttoBudget instead serializes your wallets, categories, magic words, and transactions into a single JSON snapshot, encrypts it locally with AES-256-GCM using your key, and uploads a single encrypted file ('otto_vault/vault.enc') to an auto-created private Supabase Storage bucket."
            ),
            FaqItem(
                question = "How do Magic Words auto-select categories without AI?",
                answer = "OttoBudget includes 200+ pre-loaded keywords (such as 'cng', 'uber', 'coffee', 'kacchi', 'shwapno', 'desco', 'tuition'). As you type a description in the Quick-Add sheet, the local rule engine matches multi-word phrases and tokens in under 1 millisecond completely offline. You can add, edit, or delete trigger words anytime in Settings → Categories & Magic Words."
            ),
            FaqItem(
                question = "Why does selecting Transit Pass / Rapid Pass gray out other categories?",
                answer = "Transit cards (Rapid Pass, MRT Pass, Transit Pass) are dedicated commuter cards. When logging an Expense from a transit card, OttoBudget automatically locks the category to 'Transport' and dims other categories so you never have to select it manually. You can still top up your Transit Pass anytime using the 'Transfer' tab."
            ),
            FaqItem(
                question = "How does moving money (Transfer) between wallets work?",
                answer = "When you withdraw cash from a Bank Card or Send Money / Cash Out via bKash, choose 'Transfer' in the Quick-Add sheet or tap the transfer icon on any wallet card. OttoBudget deducts the amount from 'From Wallet' and adds it to 'To Wallet' without counting it as an expense."
            ),
            FaqItem(
                question = "How do the iOS Passcode and Biometric Lock work?",
                answer = "When enabled in Settings → Security & App Lock, OttoBudget locks automatically whenever the app is closed or backgrounded. Your 4-digit PIN is hashed with SHA-256 locally, and you can unlock instantly using your phone's fingerprint or face sensor."
            )
        )
    }

    var expandedIndex by remember { mutableIntStateOf(0) } // Expand "What is the Monthly Cap?" by default!

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
                SingleLineText("FAQ & Guide", style = MaterialTheme.typography.titleLarge)
            }
        }

        items(faqs.size) { idx ->
            val item = faqs[idx]
            val isOpen = expandedIndex == idx
            val darkTheme = MaterialTheme.colorScheme.background == BinanceDarkBg

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isOpen && darkTheme) OttoTurquoise.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(scaleDown = 0.99f) {
                        expandedIndex = if (isOpen) -1 else idx
                    }
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.LG),
                    verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.question,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isOpen && darkTheme) OttoTurquoise else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(end = Spacing.SM)
                        )
                        Box(
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (isOpen) 90f else 0f
                            }
                        ) {
                            PrecisionVectorIcon(
                                name = "arrow_right",
                                tint = if (isOpen && darkTheme) OttoTurquoise else MaterialTheme.colorScheme.onSurfaceVariant,
                                size = 16.dp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isOpen,
                        enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
                    ) {
                        Text(
                            text = item.answer,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.XS)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backup & Cloud Sync Sub-Screen.
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
                                .bounceClick { exportLauncher.launch("ottobudget_backup.csv") }
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
                                .bounceClick { importLauncher.launch(arrayOf("*/*")) }
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .bounceClick { showClearConfirm = true }
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
 * Categories & Magic Words Studio Sub-Screen.
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableIcons) { icName ->
                                val isIcSel = cat.iconName == icName
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isIcSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .bounceClick { vm.renameCategory(cat, cat.name, icName) },
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

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            catRules.forEach { rule ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.bounceClick {
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
                    SingleLineText("Profile & Monthly Budget Cap", style = MaterialTheme.typography.titleLarge)
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
 * V7 Quick-Add Bottom Sheet (Uses OttoTurquoise in Dark Mode instead of yellow!).
 */
@Composable
fun QuickAddV7BottomSheet(
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
        SingleLineText("Transaction Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM), modifier = Modifier.fillMaxWidth()) {
            listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (key, label) ->
                val isSel = mode == key
                val pillBg = if (isSel) {
                    if (darkTheme) OttoTurquoise else SoftLavenderPill
                } else MaterialTheme.colorScheme.surface
                val pillText = if (isSel) {
                    if (darkTheme) InkDark else SoftLavenderText
                } else MaterialTheme.colorScheme.onSurface

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pillBg,
                    border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .bounceClick {
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
                    PrecisionVectorIcon("transfer", if (darkTheme) OttoTurquoise else TransferBlue, size = 16.dp, triggerKey = swapTrigger)
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
    // Uses OttoTurquoise (#6BD0C2) in Dark Mode instead of yellow!
    val pillBg = if (selected) {
        if (darkTheme) OttoTurquoise else SoftLavenderPill
    } else MaterialTheme.colorScheme.surface
    val pillText = if (selected) {
        if (darkTheme) InkDark else SoftLavenderText
    } else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = pillBg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .height(38.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .bounceClick(enabled = enabled, onClick = onClick)
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
 * Multi-Part Animated 24x24 Vector Icons (`itshover.com/icons` style):
 * Includes Sun (`sun`), Crescent Half-Moon (`half_moon`), Lock (`lock`), Fingerprint (`fingerprint`),
 * Backspace (`backspace`), Help (`help`), plus animated Home, Ledger, Wallet, Profile, and Category icons.
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
                animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = 1.85.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val p = progress.value

        when (name) {
            // Animated Home Icon: Roof springs upward & door frame animates
            "home" -> {
                translate(top = -p * 3.dp.toPx()) {
                    val roof = Path().apply {
                        moveTo(w * 0.12f, h * 0.48f)
                        lineTo(w * 0.50f, h * 0.14f)
                        lineTo(w * 0.88f, h * 0.48f)
                    }
                    drawPath(roof, tint, style = stroke)
                }
                val body = Path().apply {
                    moveTo(w * 0.22f, h * 0.44f)
                    lineTo(w * 0.22f, h * 0.86f)
                    lineTo(w * 0.78f, h * 0.86f)
                    lineTo(w * 0.78f, h * 0.44f)
                }
                drawPath(body, tint, style = stroke)
                // Animated door panel
                val doorH = h * (0.26f + 0.06f * p)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.41f, h * 0.86f - doorH),
                    size = Size(w * 0.18f, doorH),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke
                )
            }
            // Animated Ledger Icon: Document frame tilts slightly while inner lines slide in wave
            "ledger" -> {
                rotate(degrees = p * -6f) {
                    drawRoundRect(tint, Offset(w * 0.18f, h * 0.12f), Size(w * 0.64f, h * 0.76f), CornerRadius(5.dp.toPx()), stroke)
                    translate(left = p * 3.dp.toPx()) {
                        drawLine(tint, Offset(w * 0.32f, h * 0.34f), Offset(w * 0.68f, h * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    }
                    translate(left = -p * 3.dp.toPx()) {
                        drawLine(tint, Offset(w * 0.32f, h * 0.50f), Offset(w * 0.68f, h * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    }
                    translate(left = p * 2.dp.toPx()) {
                        drawLine(tint, Offset(w * 0.32f, h * 0.66f), Offset(w * 0.54f, h * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    }
                }
            }
            // Animated Wallet Icon: Card peeks upward out of the wallet pocket on click
            "wallet" -> {
                translate(top = -p * 3.5.dp.toPx()) {
                    val cardPeek = Path().apply {
                        moveTo(w * 0.24f, h * 0.28f)
                        lineTo(w * 0.24f, h * 0.16f)
                        lineTo(w * 0.74f, h * 0.16f)
                        lineTo(w * 0.74f, h * 0.28f)
                    }
                    drawPath(cardPeek, tint.copy(alpha = 0.85f), style = stroke)
                }
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.28f), Size(w * 0.72f, h * 0.56f), CornerRadius(5.dp.toPx()), stroke)
                drawRoundRect(tint, Offset(w * 0.58f, h * 0.46f), Size(w * 0.28f, h * 0.20f), CornerRadius(4.dp.toPx()), stroke)
                drawCircle(tint, radius = 1.7.dp.toPx(), center = Offset(w * 0.68f, h * 0.56f))
            }
            // Animated Profile Icon: Head bounces gently + subtle shoulder arc
            "profile" -> {
                translate(top = -p * 3.dp.toPx()) {
                    drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.31f), style = stroke)
                }
                drawArc(tint, 190f, 160f, false, Offset(w * 0.18f, h * 0.56f), Size(w * 0.64f, h * 0.44f), style = stroke)
            }
            "plus" -> {
                rotate(degrees = p * 90f) {
                    drawLine(tint, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            // Light Mode Sun Icon
            "sun", "sun_moon" -> {
                rotate(degrees = p * 60f) {
                    drawCircle(tint, radius = w * 0.21f, style = stroke)
                    for (angle in 0 until 360 step 45) {
                        rotate(angle.toFloat()) {
                            drawLine(tint, Offset(w * 0.5f, h * 0.11f), Offset(w * 0.5f, h * 0.20f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                        }
                    }
                }
            }
            // Dark Mode Crescent Half-Moon Icon!
            "half_moon" -> {
                rotate(degrees = -15f + p * 25f) {
                    val moonPath = Path().apply {
                        val outerRect = Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f)
                        addArc(outerRect, 35f, 250f)
                        quadraticTo(
                            w * 0.36f,
                            h * 0.50f,
                            w * 0.76f,
                            h * 0.68f
                        )
                        close()
                    }
                    drawPath(moonPath, color = tint, style = stroke)
                    // Subtle twinkling star beside the crescent moon
                    drawCircle(tint, radius = (1.5f + p * 0.8f).dp.toPx(), center = Offset(w * 0.76f, h * 0.26f))
                }
            }
            // Padlock Icon for Security & iOS Lock Screen
            "lock" -> {
                translate(top = -p * 2.5.dp.toPx()) {
                    drawArc(
                        color = tint,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.30f, h * 0.14f),
                        size = Size(w * 0.40f, h * 0.38f),
                        style = stroke
                    )
                }
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.42f),
                    size = Size(w * 0.60f, h * 0.44f),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = stroke
                )
                drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.50f, h * 0.61f))
                drawLine(tint, Offset(w * 0.50f, h * 0.63f), Offset(w * 0.50f, h * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            // Fingerprint Biometric Icon
            "fingerprint" -> {
                drawArc(tint, 195f, 150f, false, Offset(w * 0.16f, h * 0.16f), Size(w * 0.68f, h * 0.68f), style = stroke)
                drawArc(tint, 190f, 160f, false, Offset(w * 0.26f, h * 0.26f), Size(w * 0.48f, h * 0.52f), style = stroke)
                drawArc(tint, 180f, 180f, false, Offset(w * 0.36f, h * 0.38f), Size(w * 0.28f, h * 0.38f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.48f), Offset(w * 0.50f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            // iOS Keypad Backspace Icon
            "backspace" -> {
                val path = Path().apply {
                    moveTo(w * 0.36f, h * 0.26f)
                    lineTo(w * 0.84f, h * 0.26f)
                    lineTo(w * 0.84f, h * 0.74f)
                    lineTo(w * 0.36f, h * 0.74f)
                    lineTo(w * 0.16f, h * 0.50f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.48f, h * 0.40f), Offset(w * 0.68f, h * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.40f), Offset(w * 0.48f, h * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            // FAQ & Help Question Circle Icon
            "help" -> {
                drawCircle(tint, radius = w * 0.36f, style = stroke)
                drawArc(tint, 200f, 190f, false, Offset(w * 0.37f, h * 0.28f), Size(w * 0.26f, h * 0.22f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.50f, h * 0.70f))
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
            "coffee", "food" -> {
                translate(top = -p * 2.dp.toPx()) {
                    drawLine(tint, Offset(w * 0.36f, h * 0.16f), Offset(w * 0.36f, h * 0.26f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.54f, h * 0.16f), Offset(w * 0.54f, h * 0.26f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.34f), Size(w * 0.48f, h * 0.38f), CornerRadius(6.dp.toPx()), stroke)
                drawArc(tint, -90f, 180f, false, Offset(w * 0.58f, h * 0.38f), Size(w * 0.20f, h * 0.24f), style = stroke)
                drawLine(tint, Offset(w * 0.16f, h * 0.82f), Offset(w * 0.74f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "bag", "groceries" -> {
                drawArc(tint, 180f, 180f, false, Offset(w * 0.34f, h * 0.16f), Size(w * 0.32f, h * 0.28f), style = stroke)
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.32f), Size(w * 0.60f, h * 0.52f), CornerRadius(4.dp.toPx()), stroke)
            }
            "bus", "transport" -> {
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.16f), Size(w * 0.64f, h * 0.58f), CornerRadius(5.dp.toPx()), stroke)
                drawLine(tint, Offset(w * 0.18f, h * 0.48f), Offset(w * 0.82f, h * 0.48f), strokeWidth = stroke.width)
                drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.32f, h * 0.62f))
                drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.68f, h * 0.62f))
                drawLine(tint, Offset(w * 0.28f, h * 0.74f), Offset(w * 0.28f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.74f), Offset(w * 0.72f, h * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
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
