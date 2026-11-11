package com.rafsan.ottobudget

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafsan.ottobudget.data.local.*
import com.rafsan.ottobudget.domain.RuleEngine
import com.rafsan.ottobudget.ui.theme.*
import com.rafsan.ottobudget.ui.viewmodel.OttoViewModel
import com.rafsan.ottobudget.ui.viewmodel.UndoDeletedPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToInt

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

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, isPinEnabled) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        if (isPinEnabled) vm.isAppLocked.value = true
                        vm.lockVaultSession()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme && !showSplashWink
                    controller.isAppearanceLightNavigationBars = !darkTheme && !showSplashWink
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
                    animationSpec = tween(280),
                    label = "rootCrossfade"
                ) { screenState ->
                    when (screenState) {
                        "splash" -> OttoWinkingSplashScreen(onFinished = { vm.finishSplashWink() })
                        "lock" -> IosStyleSecurityLockScreen(
                            titleText = "Enter Passcode",
                            subtitleText = "Unlock OttoBudget",
                            biometricEnabled = vm.isBiometricEnabled.collectAsState().value,
                            activity = this@MainActivity,
                            onVerifyPin = { vm.tryUnlockWithPin(it) },
                            onBiometricSuccess = { vm.onBiometricUnlockSuccess() }
                        )
                        "onboarding" -> OttoChatOnboardingScreen(vm = vm)
                        else -> OttoBudgetRootScreen(vm = vm, activity = this@MainActivity)
                    }
                }
            }
        }
    }
}

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

fun triggerDeviceBiometricPrompt(
    activity: FragmentActivity,
    title: String = "Unlock OttoBudget",
    subtitle: String = "Use your fingerprint or face to unlock",
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
 * iOS Lock Screen-Inspired Passcode & Biometric Gate:
 * - Bottom-Right slot now houses the Fingerprint / Biometric icon for effortless right-thumb reach!
 * - Bottom-Left slot houses the Backspace button.
 */
@Composable
fun IosStyleSecurityLockScreen(
    titleText: String,
    subtitleText: String,
    biometricEnabled: Boolean,
    activity: FragmentActivity,
    onVerifyPin: (String) -> Boolean,
    onBiometricSuccess: () -> Unit,
    onCancelOptional: (() -> Unit)? = null
) {
    var enteredPin by remember { mutableStateOf("") }
    var promptSubtitle by remember(subtitleText) { mutableStateOf(subtitleText) }
    var isErrorState by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val lockIconBounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) {
            delay(200)
            triggerDeviceBiometricPrompt(
                activity = activity,
                title = titleText,
                subtitle = subtitleText,
                onSuccess = onBiometricSuccess,
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
                delay(80)
                val unlocked = onVerifyPin(nextPin)
                if (!unlocked) {
                    isErrorState = true
                    promptSubtitle = "Incorrect Passcode. Try again."
                    val keyframes = listOf(-20f, 20f, -15f, 15f, -8f, 8f, 0f)
                    for (offset in keyframes) {
                        shakeOffset.animateTo(offset, animationSpec = tween(42, easing = LinearEasing))
                    }
                    enteredPin = ""
                } else {
                    lockIconBounce.animateTo(1.22f, spring(dampingRatio = 0.45f))
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
            .background(Color(0xFF0B0E11))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (onCancelOptional != null) {
                    TextButton(onClick = onCancelOptional) {
                        SingleLineText("Close", style = MaterialTheme.typography.titleMedium, color = BinanceMutedText)
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .scale(lockIconBounce.value)
                        .clip(CircleShape)
                        .background(Color(0xFF1A222B))
                        .border(1.dp, Color(0xFF2B3543), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon(
                        name = "lock",
                        tint = OttoTurquoise,
                        size = 22.dp,
                        triggerKey = enteredPin.length
                    )
                }

                SingleLineText(
                    text = titleText,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )

                SingleLineText(
                    text = promptSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isErrorState) BinanceRed else BinanceMutedText
                )

                Spacer(Modifier.height(6.dp))

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
                            else -> Color.White.copy(alpha = 0.40f)
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(dotScale)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.5.dp, borderColor, CircleShape)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 12.dp)
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

                // Bottom Row: Backspace (Left) | 0 (Center) | Fingerprint Biometric (Bottom-Right for easy thumb reach!)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bottom-Left: Backspace
                    Box(
                        modifier = Modifier
                            .size(72.dp)
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
                                size = 23.dp
                            )
                        }
                    }

                    // Bottom-Center: 0
                    IosKeypadCircleButton(
                        digit = "0",
                        letters = "",
                        onClick = { onDigitTapped("0") }
                    )

                    // Bottom-Right: Fingerprint / Biometric Icon (Always reachable by right thumb!)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A222B))
                            .border(1.dp, OttoTurquoise.copy(alpha = 0.45f), CircleShape)
                            .bounceClick {
                                triggerDeviceBiometricPrompt(
                                    activity = activity,
                                    title = titleText,
                                    subtitle = subtitleText,
                                    onSuccess = onBiometricSuccess,
                                    onError = { promptSubtitle = it }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PrecisionVectorIcon(
                            name = "fingerprint",
                            tint = OttoTurquoise,
                            size = 28.dp,
                            triggerKey = enteredPin.length
                        )
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
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xFF1A2029))
            .border(1.dp, Color(0xFF28303D), CircleShape)
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
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
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
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}

/**
 * Single Seamless Winking Otto Splash Screen:
 * - Uses `R.drawable.otto_v9_transparent` (pure white octopus on 100% transparent background)
 *   over the full-screen `#6BD0C2` turquoise canvas so there is ZERO square border around Otto!
 * - Animates the right eye at normalized coordinates (cx = 0.6057f, cy = 0.4979f, eyeR = 0.048f) into a cute wink.
 */
@Composable
fun OttoWinkingSplashScreen(onFinished: () -> Unit) {
    val winkProgress = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.86f) }
    val floatOffset = remember { Animatable(6f) }

    LaunchedEffect(Unit) {
        launch {
            floatOffset.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow)
        )
        delay(140)
        winkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
        delay(240)
        winkProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
        )
        delay(220)
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
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
            modifier = Modifier.offset(y = floatOffset.value.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .scale(scaleAnim.value),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.otto_v9_transparent),
                    contentDescription = "Otto Winking",
                    modifier = Modifier.fillMaxSize()
                )
                // Right-eye wink overlay on the transparent-background white octopus
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = winkProgress.value
                    if (p > 0.02f) {
                        val cx = size.width * 0.6057f
                        val cy = size.height * 0.4979f
                        val eyeR = size.width * 0.048f

                        // 1. Cover the open circular eye hole with pure white
                        drawCircle(
                            color = Color.White,
                            radius = eyeR * 1.25f,
                            center = Offset(cx, cy)
                        )

                        // 2. Draw happy curved winking arc in #6BD0C2 turquoise
                        val winkPath = Path().apply {
                            moveTo(cx - eyeR * 1.05f, cy + eyeR * 0.15f * p)
                            quadraticTo(
                                cx,
                                cy - eyeR * 0.90f * p,
                                cx + eyeR * 1.05f,
                                cy + eyeR * 0.15f * p
                            )
                        }
                        drawPath(
                            path = winkPath,
                            color = OttoTurquoise,
                            style = Stroke(width = (eyeR * 0.58f).coerceAtLeast(4f), cap = StrokeCap.Round)
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

/**
 * Zero-API Conversational AI-Chat Onboarding Screen (`OttoChatOnboardingScreen`):
 * - Minimalistic animated progress bar + "Skip" button at the top.
 * - Typing "skip" in the chat bar also skips immediately.
 * - Animated 3-dot typing indicator (`• • •`) before each question from Otto.
 * - Selectable quick-reply chips + custom text input bar at the bottom.
 */
data class ChatOnboardingQuestion(
    val questionText: String,
    val quickOptions: List<String>,
    val placeholder: String
)

data class ChatBubbleMessage(
    val id: String,
    val isFromOtto: Boolean,
    val text: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OttoChatOnboardingScreen(vm: OttoViewModel) {
    val questions = remember {
        listOf(
            ChatOnboardingQuestion(
                questionText = "Hi there! I'm Otto, your offline financial guardian. What should I call you?",
                quickOptions = listOf("Rafsan", "Alex", "Skip"),
                placeholder = "Type your name or 'skip'..."
            ),
            ChatOnboardingQuestion(
                questionText = "Nice to meet you! Where does your income usually come from? I'll tune your categories & magic words.",
                quickOptions = listOf("Tuition & Part-Time", "Salary & Job", "Freelance & Gigs", "Family & Allowance", "All of these"),
                placeholder = "Select an option or type custom..."
            ),
            ChatOnboardingQuestion(
                questionText = "Which wallets do you use most day-to-day?",
                quickOptions = listOf("bKash + Cash + Card", "bKash + Nagad + Cash", "All Wallets (incl. Transit Pass)"),
                placeholder = "e.g. bKash, Nagad, Cash..."
            ),
            ChatOnboardingQuestion(
                questionText = "Would you like a Monthly Spending Cap to pace your daily average, or prefer tracking without a cap?",
                quickOptions = listOf("৳10,000 / month", "৳15,000 / month", "৳25,000 / month", "No Monthly Cap"),
                placeholder = "Type a monthly cap (e.g. 12000) or 'No'..."
            ),
            ChatOnboardingQuestion(
                questionText = "Last question! Are you saving up for anything special right now? I'll create a goal in your private Savings Vault.",
                quickOptions = listOf("New Phone / Tech", "Gift for Parents", "Emergency Reserve", "Set up later"),
                placeholder = "Type a savings goal (e.g. MacBook, Trip)..."
            )
        )
    }

    var currentStep by remember { mutableIntStateOf(0) }
    var isOttoTyping by remember { mutableStateOf(true) }
    var customInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatBubbleMessage>() }
    val answers = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val progressTarget = ((currentStep + 1).toFloat() / questions.size.toFloat()).coerceIn(0.15f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "onbProgress"
    )

    // Trigger Otto typing animation whenever `currentStep` advances
    LaunchedEffect(currentStep) {
        if (currentStep < questions.size) {
            isOttoTyping = true
            delay(420)
            isOttoTyping = false
            messages.add(
                ChatBubbleMessage(
                    id = "otto_$currentStep",
                    isFromOtto = true,
                    text = questions[currentStep].questionText
                )
            )
            delay(60)
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    fun submitAnswer(rawAnswer: String) {
        val clean = rawAnswer.trim()
        if (clean.isEmpty()) return
        if (clean.equals("skip", ignoreCase = true) && currentStep == 0) {
            vm.skipChatOnboarding()
            return
        }
        if (clean.equals("skip", ignoreCase = true)) {
            vm.skipChatOnboarding()
            return
        }

        messages.add(
            ChatBubbleMessage(
                id = "user_${currentStep}_${System.currentTimeMillis()}",
                isFromOtto = false,
                text = clean
            )
        )
        answers.add(clean)
        customInput = ""

        scope.launch {
            delay(80)
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
            if (currentStep + 1 < questions.size) {
                currentStep += 1
            } else {
                isOttoTyping = true
                delay(360)
                isOttoTyping = false
                messages.add(
                    ChatBubbleMessage(
                        id = "otto_done",
                        isFromOtto = true,
                        text = "All set! Launching your personalized ledger and quick interactive tour..."
                    )
                )
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
                delay(550)
                vm.completeChatOnboarding(
                    chosenName = answers.getOrElse(0) { "User" },
                    incomePref = answers.getOrElse(1) { "All" },
                    walletPref = answers.getOrElse(2) { "bKash + Cash" },
                    capAnswer = answers.getOrElse(3) { "10000" },
                    vaultGoalAnswer = answers.getOrElse(4) { "New Phone" }
                )
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Minimalist Progress Bar + Skip Button
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(OttoTurquoise),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.otto_v9_transparent),
                                contentDescription = "Otto",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            SingleLineText("Otto Assistant", style = MaterialTheme.typography.titleMedium)
                            SingleLineText(
                                "Step ${(currentStep + 1).coerceAtMost(questions.size)} of ${questions.size} • Offline Setup",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(onClick = { vm.skipChatOnboarding() }) {
                        SingleLineText(
                            text = "Skip",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Minimalist Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(OttoTurquoise)
                    )
                }
            }

            // Chat Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = Spacing.SM),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val bubbleScale = remember { Animatable(0.90f) }
                    LaunchedEffect(msg.id) {
                        bubbleScale.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(bubbleScale.value),
                        horizontalArrangement = if (msg.isFromOtto) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (msg.isFromOtto) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(OttoTurquoise),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.otto_v9_transparent),
                                    contentDescription = "Otto",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (msg.isFromOtto) 4.dp else 18.dp,
                                bottomEnd = if (msg.isFromOtto) 18.dp else 4.dp
                            ),
                            color = if (msg.isFromOtto) MaterialTheme.colorScheme.surface else OttoTurquoise,
                            border = if (msg.isFromOtto) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                            modifier = Modifier.widthIn(max = 290.dp)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                                color = if (msg.isFromOtto) MaterialTheme.colorScheme.onSurface else InkDark,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                // Animated 3-dot typing indicator when Otto is preparing the next question
                if (isOttoTyping) {
                    item(key = "typing_indicator") {
                        val inf = rememberInfiniteTransition(label = "typingDots")
                        val dot1 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "d1")
                        val dot2 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, delayMillis = 140), RepeatMode.Reverse), label = "d2")
                        val dot3 by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, delayMillis = 280), RepeatMode.Reverse), label = "d3")

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(OttoTurquoise),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.otto_v9_transparent),
                                    contentDescription = "Otto",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(7.dp).alpha(dot1).clip(CircleShape).background(OttoTurquoise))
                                    Box(Modifier.size(7.dp).alpha(dot2).clip(CircleShape).background(OttoTurquoise))
                                    Box(Modifier.size(7.dp).alpha(dot3).clip(CircleShape).background(OttoTurquoise))
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Quick-Reply Chips + Custom Chat Input Field
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                if (!isOttoTyping && currentStep < questions.size) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        questions[currentStep].quickOptions.forEach { option ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, OttoTurquoise.copy(alpha = 0.45f)),
                                modifier = Modifier.bounceClick { submitAnswer(option) }
                            ) {
                                SingleLineText(
                                    text = option,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        placeholder = {
                            SingleLineText(
                                text = questions.getOrNull(currentStep)?.placeholder ?: "Type your answer...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(OttoTurquoise)
                            .bounceClick { submitAnswer(customInput) },
                        contentAlignment = Alignment.Center
                    ) {
                        PrecisionVectorIcon("arrow_right", InkDark, size = 20.dp)
                    }
                }
            }
        }
    }
}

/**
 * Animated Interactive Spotlight Tour Overlay (`OttoInteractiveTourOverlay`):
 * Guides the user across the 4 key areas of OttoBudget V9 with animated pulsing rings & switching tabs.
 */
data class SpotlightStepInfo(
    val tabToActivate: String,
    val title: String,
    val description: String,
    val badgeLabel: String
)

@Composable
fun OttoInteractiveTourOverlay(
    currentStep: Int,
    onStepChange: (Int, String) -> Unit,
    onFinishTour: () -> Unit
) {
    val steps = remember {
        listOf(
            SpotlightStepInfo(
                tabToActivate = "home",
                title = "1. Clean Home & Smart Alert FAB",
                description = "Your Home screen shows your net available balance and a fixed-size 7D Expense, Income & Trend chart. Whenever a new bKash/Nagad SMS alert or scheduled bill arrives, a floating notification button pops up at the bottom-right!",
                badgeLabel = "HOME & ALERTS"
            ),
            SpotlightStepInfo(
                tabToActivate = "home",
                title = "2. Quick-Add & Magic Word Auto-Scroll",
                description = "Tap the center '+' button to log an Expense, Income, or Transfer. Type words like 'cng' or 'coffee' and watch the Category bar automatically scroll to the right category! Tap the Date bar to log forgotten past dates.",
                badgeLabel = "QUICK-ADD (+)"
            ),
            SpotlightStepInfo(
                tabToActivate = "wallets",
                title = "3. Available vs. Total & Floating Vault",
                description = "On the Wallets screen, switch between 'Available' (net spendable balance) and 'Total' (actual balance). Tap the floating 'Vault' button at the bottom-right to open your PIN/Biometric Savings Vault!",
                badgeLabel = "WALLETS & VAULT"
            ),
            SpotlightStepInfo(
                tabToActivate = "ledger",
                title = "4. Press & Hold to Edit + Undo Delete",
                description = "Press and hold any transaction in Recent Transactions or Ledger to edit its amount, date, wallet, or category. Deleting any item asks for confirmation and includes a 1-tap Undo snackbar!",
                badgeLabel = "LEDGER & EDITING"
            )
        )
    }

    val stepInfo = steps.getOrElse(currentStep) { steps.first() }
    val pulseTrans = rememberInfiniteTransition(label = "tourPulse")
    val ringScale by pulseTrans.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(enabled = true, onClick = {})
            .padding(Spacing.LG),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, OttoTurquoise),
            modifier = Modifier
                .fillMaxWidth()
                .scale(ringScale.coerceIn(0.98f, 1.02f))
        ) {
            Column(
                modifier = Modifier.padding(Spacing.LG),
                verticalArrangement = Arrangement.spacedBy(Spacing.MD)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = OttoTurquoise.copy(alpha = 0.18f)
                    ) {
                        SingleLineText(
                            text = stepInfo.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = OttoTurquoiseDeep,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    TextButton(onClick = onFinishTour) {
                        SingleLineText("Skip Tour", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SM)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .scale(ringScale)
                            .clip(CircleShape)
                            .background(OttoTurquoise),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.otto_v9_transparent),
                            contentDescription = "Otto",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = stepInfo.title,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = stepInfo.description,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Step Dots + Next / Done Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.indices.forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .width(if (idx == currentStep) 22.dp else 7.dp)
                                    .height(7.dp)
                                    .clip(CircleShape)
                                    .background(if (idx == currentStep) OttoTurquoise else MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStep + 1 < steps.size) {
                                val nextIdx = currentStep + 1
                                onStepChange(nextIdx, steps[nextIdx].tabToActivate)
                            } else {
                                onFinishTour()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OttoTurquoise, contentColor = InkDark)
                    ) {
                        SingleLineText(
                            text = if (currentStep + 1 < steps.size) "Next" else "Done",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkDark
                        )
                    }
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
    val vaultGoals by vm.vaultGoals.collectAsState()
    val recurringRules by vm.recurringRules.collectAsState()
    val pendingSmsDetections by vm.pendingSmsDetections.collectAsState()

    val userName by vm.userName.collectAsState()
    val excludeMajor by vm.excludeMajorGlobal.collectAsState()
    val darkTheme by vm.isDarkTheme.collectAsState()
    val monthlyBudget by vm.monthlyBudgetTarget.collectAsState()
    val monthlyCapEnabled by vm.isMonthlyCapEnabled.collectAsState()
    val recurringEnabled by vm.isRecurringEnabled.collectAsState()
    val statusMsg by vm.statusMessage.collectAsState()
    val pendingUndo by vm.pendingUndoEvent.collectAsState()
    val isPinEnabled by vm.isPinLockEnabled.collectAsState()
    val isVaultLockEnabled by vm.isVaultLockEnabled.collectAsState()
    val isVaultUnlocked by vm.isVaultSessionUnlocked.collectAsState()
    val showSpotlightTour by vm.showSpotlightTour.collectAsState()

    var currentTab by remember { mutableStateOf("home") }
    var tourStep by remember { mutableIntStateOf(0) }
    var walletsSubMode by remember { mutableStateOf("wallets") } // "wallets" or "vault"
    var walletBalancePillMode by remember { mutableStateOf("AVAILABLE") } // "AVAILABLE" or "TOTAL"
    var showVaultPinGate by remember { mutableStateOf(false) }
    var settingsSubScreen by remember { mutableStateOf("menu") } // "menu", "backup", "magic_words", "profile", "security", "scheduler", "sms_parser", "faq"
    var navAnimTrigger by remember { mutableIntStateOf(0) }
    var avatarWinkTrigger by remember { mutableIntStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }
    var sheetDefaultMode by remember { mutableStateOf("EXPENSE") }
    var sheetPreselectedFrom by remember { mutableStateOf("bKash") }
    var sheetPreselectedTo by remember { mutableStateOf("Cash") }
    var walletToEditBalance by remember { mutableStateOf<WalletEntity?>(null) }

    // Global Delete Confirmation Dialog State + Edit Transaction Dialog State
    var deleteConfirmTitle by remember { mutableStateOf<String?>(null) }
    var deleteConfirmMessage by remember { mutableStateOf("") }
    var pendingDeleteConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }

    fun askDeleteConfirmation(title: String, message: String, onConfirmDelete: () -> Unit) {
        deleteConfirmTitle = title
        deleteConfirmMessage = message
        pendingDeleteConfirmAction = onConfirmDelete
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Status message snackbar
    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearStatusMessage()
        }
    }

    // Undo Delete Snackbar (Pops up with an "Undo" button whenever any item is deleted!)
    LaunchedEffect(pendingUndo) {
        pendingUndo?.let { payload ->
            val result = snackbarHostState.showSnackbar(
                message = "Deleted ${payload.label}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                vm.executeUndo(payload)
            } else {
                vm.clearUndoEvent()
            }
        }
    }

    // Available (Spendable) Balances vs. Total (Actual) Balances
    val availableBalances = remember(wallets, transactions) {
        vm.computeAvailableWalletBalances(wallets, transactions)
    }
    val totalActualBalances = remember(wallets, transactions) {
        vm.computeTotalActualWalletBalances(wallets, transactions)
    }
    val totalAvailableSum = availableBalances.values.sum()

    val vaultHoldings = remember(transactions) {
        vm.computeVaultGoalHoldings(transactions)
    }

    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val themeIconRotation by animateFloatAsState(
        targetValue = if (darkTheme) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "themeRot"
    )

    val avatarScale = remember { Animatable(1f) }
    LaunchedEffect(avatarWinkTrigger) {
        if (avatarWinkTrigger > 0) {
            avatarScale.animateTo(1.16f, spring(dampingRatio = 0.4f))
            avatarScale.animateTo(1.0f, spring(dampingRatio = 0.6f))
        }
    }

    fun requestOpenSavingsVault() {
        if (isVaultLockEnabled && !isVaultUnlocked) {
            showVaultPinGate = true
        } else {
            currentTab = "wallets"
            walletsSubMode = "vault"
            navAnimTrigger++
        }
    }

    if (showVaultPinGate) {
        IosStyleSecurityLockScreen(
            titleText = "Savings Vault Locked",
            subtitleText = if (isPinEnabled) "Enter your 4-digit passcode or use biometrics" else "Enter any 4 digits or set PIN in Settings → Security",
            biometricEnabled = vm.isBiometricEnabled.collectAsState().value,
            activity = activity,
            onVerifyPin = { entered ->
                val ok = vm.syncManager.verifyPinCode(entered)
                if (ok) {
                    vm.unlockVaultSession()
                    showVaultPinGate = false
                    currentTab = "wallets"
                    walletsSubMode = "vault"
                }
                ok
            },
            onBiometricSuccess = {
                vm.unlockVaultSession()
                showVaultPinGate = false
                currentTab = "wallets"
                walletsSubMode = "vault"
            },
            onCancelOptional = { showVaultPinGate = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                .border(1.5.dp, OttoTurquoise, CircleShape)
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
                                        tint = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep,
                                        size = 18.dp,
                                        triggerKey = navAnimTrigger
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (darkTheme) Color(0xFF1A2426) else OttoTurquoiseSoftLight,
                            border = BorderStroke(
                                width = 1.dp,
                                color = OttoTurquoise.copy(alpha = 0.55f)
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
                                    tint = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep,
                                    size = 19.dp,
                                    triggerKey = darkTheme
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Redesigned Concentric Equal-Spaced Bottom Nav Bar with 24.dp Icons & Circular Pill Indicator!
                CleanConcentricBottomNav(
                    currentTab = currentTab,
                    darkTheme = darkTheme,
                    navAnimTrigger = navAnimTrigger,
                    onSelectTab = { tab ->
                        if (tab != "wallets") {
                            walletsSubMode = "wallets"
                        }
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
                        "home" -> DashboardV9Screen(
                            totalAvailable = totalAvailableSum,
                            monthlyBudget = monthlyBudget,
                            monthlyCapEnabled = monthlyCapEnabled,
                            recurringEnabled = recurringEnabled,
                            recurringRules = recurringRules,
                            pendingSmsDetections = pendingSmsDetections,
                            balances = availableBalances,
                            transactions = transactions,
                            categories = categories,
                            excludeMajor = excludeMajor,
                            darkTheme = darkTheme,
                            vm = vm,
                            onToggleMajor = { vm.excludeMajorGlobal.value = !excludeMajor },
                            onSeeAllLedger = { currentTab = "ledger"; navAnimTrigger++ },
                            onRequestEditTx = { transactionToEdit = it },
                            onRequestDeleteTx = { tx ->
                                askDeleteConfirmation(
                                    title = "Delete Transaction?",
                                    message = "Delete '${tx.description.ifBlank { tx.category }}' (৳${tx.amount.roundToInt()})?",
                                    onConfirmDelete = { vm.deleteTransactionWithUndo(tx) }
                                )
                            }
                        )
                        "ledger" -> LedgerV9Screen(
                            transactions = transactions.filter { it.type != "VAULT_LOCK" && it.type != "VAULT_RELEASE" },
                            categories = categories,
                            darkTheme = darkTheme,
                            onRequestEditTx = { transactionToEdit = it },
                            onRequestDeleteTx = { tx ->
                                askDeleteConfirmation(
                                    title = "Delete Transaction?",
                                    message = "Delete '${tx.description.ifBlank { tx.category }}' (৳${tx.amount.roundToInt()})?",
                                    onConfirmDelete = { vm.deleteTransactionWithUndo(tx) }
                                )
                            }
                        )
                        "wallets" -> WalletsAndVaultV9Screen(
                            subMode = walletsSubMode,
                            balancePillMode = walletBalancePillMode,
                            onChangeBalancePillMode = { walletBalancePillMode = it },
                            onOpenVault = { requestOpenSavingsVault() },
                            onBackToWallets = { walletsSubMode = "wallets" },
                            darkTheme = darkTheme,
                            wallets = wallets,
                            availableBalances = availableBalances,
                            totalActualBalances = totalActualBalances,
                            vaultGoals = vaultGoals,
                            vaultHoldings = vaultHoldings,
                            vm = vm,
                            onMoveFromWallet = { fromW ->
                                sheetDefaultMode = "TRANSFER"
                                sheetPreselectedFrom = fromW
                                sheetPreselectedTo = wallets.firstOrNull { it.name != fromW }?.name ?: "Cash"
                                showAddSheet = true
                            },
                            onEditWalletBalance = { w -> walletToEditBalance = w },
                            onRequestDeleteGoal = { goal ->
                                askDeleteConfirmation(
                                    title = "Delete Vault Goal?",
                                    message = "Delete '${goal.title}'? Any assigned funds will be returned to your available wallet balances.",
                                    onConfirmDelete = { vm.deleteVaultGoalWithUndo(goal) }
                                )
                            }
                        )
                        "profile" -> SettingsHubV9Screen(
                            subScreen = settingsSubScreen,
                            onNavigateSubScreen = { settingsSubScreen = it },
                            vm = vm,
                            activity = activity,
                            balances = availableBalances,
                            onEditWalletBalance = { w -> walletToEditBalance = w },
                            onAskDeleteConfirmation = { title, msg, act -> askDeleteConfirmation(title, msg, act) }
                        )
                    }
                }
            }

            if (showAddSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAddSheet = false },
                    sheetState = addSheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.statusBarsPadding()
                ) {
                    QuickAddV9BottomSheet(
                        defaultMode = sheetDefaultMode,
                        initialFromWallet = sheetPreselectedFrom,
                        initialToWallet = sheetPreselectedTo,
                        balances = availableBalances,
                        darkTheme = darkTheme,
                        vm = vm,
                        onDismiss = { showAddSheet = false }
                    )
                }
            }

            walletToEditBalance?.let { targetWallet ->
                val currentLive = availableBalances[targetWallet.name] ?: 0.0
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

            // Long-Press Edit Past Transaction Dialog
            transactionToEdit?.let { targetTx ->
                EditTransactionDialog(
                    tx = targetTx,
                    wallets = wallets,
                    categories = categories,
                    darkTheme = darkTheme,
                    onDismiss = { transactionToEdit = null },
                    onSave = { newType, newAmt, newDesc, newCat, newW, newToW, newDate ->
                        vm.updateExistingTransaction(targetTx, newType, newAmt, newDesc, newCat, newW, newToW, newDate)
                        transactionToEdit = null
                    },
                    onDelete = {
                        val toDel = targetTx
                        transactionToEdit = null
                        askDeleteConfirmation(
                            title = "Delete Transaction?",
                            message = "Delete '${toDel.description.ifBlank { toDel.category }}' (৳${toDel.amount.roundToInt()})?",
                            onConfirmDelete = { vm.deleteTransactionWithUndo(toDel) }
                        )
                    }
                )
            }

            // Global Delete Confirmation Popup Dialog
            if (deleteConfirmTitle != null) {
                AlertDialog(
                    onDismissRequest = {
                        deleteConfirmTitle = null
                        pendingDeleteConfirmAction = null
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { SingleLineText(deleteConfirmTitle ?: "Confirm Delete", style = MaterialTheme.typography.titleLarge) },
                    text = { Text(deleteConfirmMessage, style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingDeleteConfirmAction?.invoke()
                                deleteConfirmTitle = null
                                pendingDeleteConfirmAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BinanceRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            SingleLineText("Delete", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                deleteConfirmTitle = null
                                pendingDeleteConfirmAction = null
                            }
                        ) {
                            SingleLineText("Cancel")
                        }
                    }
                )
            }
        }

        // Animated Interactive Spotlight Tour Overlay
        if (showSpotlightTour) {
            OttoInteractiveTourOverlay(
                currentStep = tourStep,
                onStepChange = { nextIdx, targetTab ->
                    tourStep = nextIdx
                    currentTab = targetTab
                },
                onFinishTour = {
                    tourStep = 0
                    currentTab = "home"
                    vm.finishSpotlightTour()
                }
            )
        }
    }
}

/**
 * Redesigned Concentric Floating Bottom Navigation Bar:
 * - Equal `Modifier.weight(1f)` 5-slot distribution so spacing is mathematically 100% even!
 * - Larger `24.dp` vector icons.
 * - Concentric `CircleShape` (`46.dp`) active indicator pill inside a `CircleShape` (`height = 64.dp`) dock—eliminating the rectangular indicator look!
 */
@Composable
fun CleanConcentricBottomNav(
    currentTab: String,
    darkTheme: Boolean,
    navAnimTrigger: Int,
    onSelectTab: (String) -> Unit,
    onQuickAdd: () -> Unit
) {
    val dockSurfaceColor = if (darkTheme) {
        Color(0xFF151921).copy(alpha = 0.96f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.97f)
    }
    val dockBorderColor = if (darkTheme) {
        Color(0xFF232934)
    } else {
        Color(0xFFD9EBE7)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.LG, vertical = Spacing.SM)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(CircleShape)
                .background(dockSurfaceColor)
                .border(1.dp, dockBorderColor, CircleShape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ConcentricNavTabItem(
                    iconName = "home",
                    selected = currentTab == "home",
                    darkTheme = darkTheme,
                    triggerKey = navAnimTrigger,
                    onClick = { onSelectTab("home") }
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ConcentricNavTabItem(
                    iconName = "ledger",
                    selected = currentTab == "ledger",
                    darkTheme = darkTheme,
                    triggerKey = navAnimTrigger,
                    onClick = { onSelectTab("ledger") }
                )
            }

            // Center Slot: Circular Quick-Add Button in signature #6BD0C2 OttoTurquoise!
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(OttoTurquoise)
                        .bounceClick(scaleDown = 0.90f, onClick = onQuickAdd),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon(
                        name = "plus",
                        tint = InkDark,
                        size = 24.dp,
                        triggerKey = navAnimTrigger
                    )
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ConcentricNavTabItem(
                    iconName = "wallet",
                    selected = currentTab == "wallets",
                    darkTheme = darkTheme,
                    triggerKey = navAnimTrigger,
                    onClick = { onSelectTab("wallets") }
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ConcentricNavTabItem(
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

@Composable
fun ConcentricNavTabItem(
    iconName: String,
    selected: Boolean,
    darkTheme: Boolean,
    triggerKey: Int,
    onClick: () -> Unit
) {
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.75f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "navIndScale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.10f else 1.0f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium),
        label = "navIconScale"
    )

    val activeTint = if (darkTheme) OttoTurquoise else InkDark
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)

    // Concentric CircleShape indicator matching the outer capsule dock curvature!
    val circleIndicatorColor = if (selected) {
        if (darkTheme) Color(0xFF212934) else OttoTurquoise.copy(alpha = 0.28f)
    } else {
        Color.Transparent
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .scale(if (selected) indicatorScale else 1f)
            .clip(CircleShape)
            .background(circleIndicatorColor)
            .bounceClick(scaleDown = 0.90f, onClick = onClick)
    ) {
        Box(modifier = Modifier.scale(iconScale)) {
            PrecisionVectorIcon(
                name = iconName,
                tint = if (selected) activeTint else inactiveTint,
                size = 24.dp,
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
                    label = { SingleLineText("Balance (৳)") },
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
 * Press & Hold (Long-Press) Edit Past Transaction Dialog (`EditTransactionDialog`):
 * Allows editing Type (`Expense`, `Income`, `Transfer`), Amount, Date (with Calendar Picker),
 * Description, Category, and Wallet (`From` / `To`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    tx: TransactionEntity,
    wallets: List<WalletEntity>,
    categories: List<CategoryEntity>,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, String, String?, String) -> Unit,
    onDelete: () -> Unit
) {
    var editType by remember { mutableStateOf(tx.type) }
    var editAmount by remember { mutableStateOf(tx.amount.roundToInt().toString()) }
    var editDesc by remember { mutableStateOf(tx.description) }
    var editCategory by remember { mutableStateOf(tx.category) }
    var editWallet by remember { mutableStateOf(tx.wallet) }
    var editToWallet by remember { mutableStateOf(tx.toWallet ?: wallets.firstOrNull { it.name != tx.wallet }?.name ?: "Cash") }
    var editDate by remember { mutableStateOf(tx.date) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleLineText("Edit Transaction", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    PrecisionVectorIcon("trash", BinanceRed, size = 18.dp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.SM)
            ) {
                // Consistent terminology: Expense | Income | Transfer
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (k, lbl) ->
                        val isSel = editType == k
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) OttoTurquoise else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .bounceClick {
                                    editType = k
                                    if (k != "TRANSFER") {
                                        editCategory = categories.firstOrNull { it.kind == k }?.name ?: "Other"
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SingleLineText(
                                    text = lbl,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 12.sp,
                                    color = if (isSel) InkDark else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = editAmount,
                    onValueChange = { editAmount = it },
                    label = { SingleLineText("Amount (৳)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Bar inside Edit Dialog
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .bounceClick { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.MD),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleLineText("Date: $editDate", style = MaterialTheme.typography.titleMedium)
                        PrecisionVectorIcon("calendar", MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                    }
                }

                OutlinedTextField(
                    value = editDesc,
                    onValueChange = { editDesc = it },
                    label = { SingleLineText("Description") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (editType != "TRANSFER") {
                    SingleLineText("Category", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories.filter { it.kind == editType }) { cat ->
                            WalletChoicePill(
                                label = cat.name,
                                selected = editCategory == cat.name,
                                darkTheme = darkTheme,
                                onClick = { editCategory = cat.name }
                            )
                        }
                    }
                }

                SingleLineText(if (editType == "TRANSFER") "From Wallet" else "Wallet", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(wallets) { w ->
                        WalletChoicePill(
                            label = w.name,
                            selected = editWallet == w.name,
                            darkTheme = darkTheme,
                            onClick = { editWallet = w.name }
                        )
                    }
                }

                if (editType == "TRANSFER") {
                    SingleLineText("To Wallet", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wallets) { w ->
                            WalletChoicePill(
                                label = w.name,
                                selected = editToWallet == w.name,
                                darkTheme = darkTheme,
                                onClick = { editToWallet = w.name }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = RuleEngine.evaluateMathExpression(editAmount)
                    if (amt > 0.0) {
                        onSave(
                            editType,
                            amt,
                            editDesc,
                            editCategory,
                            editWallet,
                            if (editType == "TRANSFER") editToWallet else null,
                            editDate
                        )
                    }
                },
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

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            editDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        }
                        showDatePicker = false
                    }
                ) {
                    SingleLineText("Select", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { SingleLineText("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * V9 Dashboard Screen:
 * - Removed the Vault button/trigger from the Home screen.
 * - Uses consistent terminology (`Expense | Income | Trend`).
 * - Shows a Floating Notification Button at the bottom-right ONLY when there are pending SMS alerts or due Scheduler rules!
 * - Supports Press & Hold on any transaction row to edit, and Confirmation + Undo on delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardV9Screen(
    totalAvailable: Double,
    monthlyBudget: Double,
    monthlyCapEnabled: Boolean,
    recurringEnabled: Boolean,
    recurringRules: List<RecurringRuleEntity>,
    pendingSmsDetections: List<PendingSmsDetectionEntity>,
    balances: Map<String, Double>,
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    excludeMajor: Boolean,
    darkTheme: Boolean,
    vm: OttoViewModel,
    onToggleMajor: () -> Unit,
    onSeeAllLedger: () -> Unit,
    onRequestEditTx: (TransactionEntity) -> Unit,
    onRequestDeleteTx: (TransactionEntity) -> Unit
) {
    val today = remember { LocalDate.now() }
    val currentMonthPrefix = remember(today) { today.toString().substring(0, 7) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var alertPopupError by remember { mutableStateOf<String?>(null) }

    val visibleTransactions = remember(transactions) {
        transactions.filter { it.type != "VAULT_LOCK" && it.type != "VAULT_RELEASE" }
    }

    val expenses = remember(visibleTransactions, excludeMajor) {
        visibleTransactions.filter { it.type == "EXPENSE" && (!excludeMajor || !it.isMajor) }
    }
    val incomes = remember(visibleTransactions) {
        visibleTransactions.filter { it.type == "INCOME" }
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

    val dueRecurringRules = remember(recurringRules, recurringEnabled, today, currentMonthPrefix) {
        if (!recurringEnabled) emptyList()
        else recurringRules.filter { rule ->
            rule.enabled && rule.lastConfirmedPeriod != currentMonthPrefix && today.dayOfMonth >= rule.dayOfMonth
        }
    }

    val totalPendingAlertsCount = pendingSmsDetections.size + dueRecurringRules.size

    val last7Dates = remember(today) {
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val dayShortFormatter = remember { DateTimeFormatter.ofPattern("E") }

    var chartMode by remember { mutableStateOf("EXPENSE") }
    var selectedDayIndex by remember { mutableIntStateOf(6) }

    val dayValues = remember(visibleTransactions, chartMode, excludeMajor, last7Dates) {
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

    val lineReveal = remember { Animatable(0f) }
    LaunchedEffect(chartMode, dayValues) {
        if (chartMode == "TREND") {
            lineReveal.snapTo(0f)
            lineReveal.animateTo(1f, animationSpec = tween(520, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
            contentPadding = PaddingValues(top = Spacing.XS, bottom = 76.dp)
        ) {
            // 1. Hero Total Balance Card (No Vault option here—Vault lives on the Wallets screen!)
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
                                SingleLineText(
                                    text = "AVAILABLE BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                SingleLineText("৳$animatedBalance", style = MaterialTheme.typography.displayLarge)
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (darkTheme) Color(0xFF212733) else OttoTurquoiseSoftLight,
                                border = BorderStroke(1.dp, OttoTurquoise.copy(alpha = 0.45f)),
                                modifier = Modifier.bounceClick { onToggleMajor() }
                            ) {
                                SingleLineText(
                                    text = if (excludeMajor) "Daily" else "All",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 12.sp,
                                    color = if (darkTheme) OttoTurquoise else InkDark,
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
                                text = if (monthlyCapEnabled) {
                                    "Expense ৳$animatedSpend / ৳${monthlyBudget.roundToInt()}"
                                } else {
                                    "Monthly Expense: ৳$animatedSpend"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            SingleLineText(
                                text = "Avg ৳$dailyAvg/d",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 13.sp,
                                color = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep
                            )
                        }

                        AnimatedVisibility(visible = monthlyCapEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (budgetRatio > 0f) {
                                    val barColor = if (totalSpend > monthlyBudget * 0.85) BinanceRed else OttoTurquoise
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
            }

            // 2. Strictly Fixed-Height 7D Expense / Income / Trend Card (`238.dp` outer, `148.dp` inner)
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(238.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.LG, vertical = 16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
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
                                        "TREND" -> "7D TREND"
                                        else -> "7D EXPENSE"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val headerNum = when (chartMode) {
                                    "INCOME" -> "+৳$animatedWindowSum"
                                    "TREND" -> "৳$dailyAvg/d"
                                    else -> "৳$animatedWindowSum"
                                }
                                SingleLineText(text = headerNum, style = MaterialTheme.typography.headlineLarge)
                            }

                            // Unified Terminology: Expense | Income | Trend
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TREND" to "Trend").forEach { (key, lbl) ->
                                    val active = chartMode == key
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (active) OttoTurquoise else Color.Transparent)
                                            .bounceClick { chartMode = key }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        SingleLineText(
                                            text = lbl,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 11.sp,
                                            color = if (active) InkDark else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                        ) {
                            if (chartMode == "TREND") {
                                val lineColor = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep

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
                                                colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent),
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

                            val accent = when {
                                chartMode == "INCOME" -> BinanceGreen
                                else -> OttoTurquoise
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
                                                if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (chartMode == "TREND") 0.45f else 1f)
                                                else Color.Transparent
                                            )
                                            .bounceClick { selectedDayIndex = idx }
                                            .padding(vertical = 6.dp)
                                    ) {
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
                                                    color = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .height(74.dp),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            if (chartMode != "TREND") {
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
                                                            .background(if (v > 0) accent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
                                                    )
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) OttoTurquoise else MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            SingleLineText(
                                                text = dLabel,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontSize = 11.sp,
                                                color = if (isSelected) InkDark else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Recent Transactions Header & Monochrome List (Press & Hold to Edit!)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        SingleLineText("Recent Transactions", style = MaterialTheme.typography.titleLarge)
                        if (visibleTransactions.isNotEmpty()) {
                            SingleLineText(
                                "Press & hold any entry to edit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (visibleTransactions.isNotEmpty()) {
                        TextButton(onClick = onSeeAllLedger) {
                            SingleLineText(
                                "See All",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 12.sp,
                                color = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep
                            )
                        }
                    }
                }
            }

            if (visibleTransactions.isEmpty()) {
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
                items(visibleTransactions.take(6), key = { it.id }) { tx ->
                    val catObj = categories.find { it.name == tx.category }
                    TransactionV9MonochromeRow(
                        tx = tx,
                        iconName = if (tx.type == "TRANSFER") "transfer" else (catObj?.iconName ?: "ledger"),
                        darkTheme = darkTheme,
                        onLongPressEdit = { onRequestEditTx(tx) },
                        onDelete = { onRequestDeleteTx(tx) }
                    )
                }
            }
        }

        // FLOATING NOTIFICATION / SMS BUTTON AT BOTTOM-RIGHT OF HOME
        // Appears ONLY when there is a new SMS alert or a due Scheduler confirmation!
        AnimatedVisibility(
            visible = totalPendingAlertsCount > 0,
            enter = scaleIn(spring(dampingRatio = 0.55f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 14.dp, end = 4.dp)
        ) {
            val pulseInf = rememberInfiniteTransition(label = "notifFabPulse")
            val fabScale by pulseInf.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                label = "fabScale"
            )

            Surface(
                shape = CircleShape,
                color = OttoTurquoise,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .scale(fabScale)
                    .bounceClick { showNotificationsSheet = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)
                ) {
                    PrecisionVectorIcon("bell", InkDark, size = 20.dp, triggerKey = totalPendingAlertsCount)
                    SingleLineText(
                        text = "$totalPendingAlertsCount New ${if (totalPendingAlertsCount == 1) "Alert" else "Alerts"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkDark
                    )
                }
            }
        }
    }

    // Modal Sheet for Pending SMS Alerts & Due Scheduler Items (Opened by the Floating Notification Button)
    if (showNotificationsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.LG, vertical = Spacing.MD)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.MD)
            ) {
                SingleLineText("Pending Notifications & Confirmations", style = MaterialTheme.typography.headlineLarge)

                if (pendingSmsDetections.isEmpty() && dueRecurringRules.isEmpty()) {
                    SingleLineText(
                        "All caught up! No pending SMS alerts or scheduled items.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
                        modifier = Modifier.heightIn(max = 420.dp)
                    ) {
                        items(pendingSmsDetections, key = { it.id }) { detection ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, OttoTurquoise.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.MD),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                                ) {
                                    SingleLineText(
                                        text = "SMS / NOTIFICATION • ${detection.provider.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep
                                    )
                                    Text(
                                        text = detection.summaryText,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                val err = vm.approvePendingSmsDetection(detection, balances)
                                                if (err != null) alertPopupError = err
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            SingleLineText("Confirm", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                        OutlinedButton(
                                            onClick = { vm.dismissPendingSmsDetection(detection.id) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(0.8f).height(38.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            SingleLineText("Dismiss", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        items(dueRecurringRules, key = { it.id }) { rule ->
                            val isInc = rule.type == "INCOME"
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (isInc) BinanceGreen else OttoTurquoise),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.MD),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                                ) {
                                    SingleLineText(
                                        text = "SCHEDULER DUE (DAY ${rule.dayOfMonth}) • ${rule.type}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isInc) BinanceGreen else OttoTurquoiseDeep
                                    )
                                    SingleLineText(
                                        text = "${rule.title}: ${if (isInc) "+" else "-"}৳${rule.amount.roundToInt()} (${rule.wallet})",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                val err = vm.confirmDueRecurringRule(rule, currentMonthPrefix, balances)
                                                if (err != null) alertPopupError = err
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1.2f).height(38.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            SingleLineText("Confirm", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                        OutlinedButton(
                                            onClick = { vm.skipDueRecurringRuleThisMonth(rule, currentMonthPrefix) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(0.8f).height(38.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            SingleLineText("Skip", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
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

    alertPopupError?.let { msg ->
        AlertDialog(
            onDismissRequest = { alertPopupError = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Insufficient Wallet Balance", style = MaterialTheme.typography.titleLarge, color = BinanceRed) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { alertPopupError = null }) {
                    SingleLineText("OK", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionV9MonochromeRow(
    tx: TransactionEntity,
    iconName: String,
    darkTheme: Boolean,
    onLongPressEdit: () -> Unit,
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
            .combinedClickable(
                onClick = { iconTapCount++ },
                onLongClick = onLongPressEdit
            )
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
                    text = "${tx.category} • $walletRoute • ${tx.date}",
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
 * V9 Ledger Screen:
 * - Uses consistent terminology (`All | Expense | Income | Transfer`) instead of `All | Out | In | Move`!
 * - Supports Press & Hold (`onRequestEditTx`) to edit any past transaction and Confirmation + Undo on delete.
 */
@Composable
fun LedgerV9Screen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    darkTheme: Boolean,
    onRequestEditTx: (TransactionEntity) -> Unit,
    onRequestDeleteTx: (TransactionEntity) -> Unit
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
                (searchQuery.isBlank() || tx.description.contains(searchQuery, ignoreCase = true) || tx.category.contains(searchQuery, ignoreCase = true) || tx.date.contains(searchQuery))
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
                placeholder = { SingleLineText("Search description, category, or date...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            // Unified Terminology: All | Expense | Income | Transfer
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                listOf("ALL" to "All", "EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (key, lbl) ->
                    val isSel = selectedType == key
                    val pillBg = if (isSel) OttoTurquoise else MaterialTheme.colorScheme.surface
                    val pillText = if (isSel) InkDark else MaterialTheme.colorScheme.onSurface

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
                                fontSize = 13.sp,
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
                    val pillBg = if (isSel) OttoTurquoise else MaterialTheme.colorScheme.surface
                    val pillText = if (isSel) InkDark else MaterialTheme.colorScheme.onSurface

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
                TransactionV9MonochromeRow(
                    tx = tx,
                    iconName = if (tx.type == "TRANSFER") "transfer" else (catObj?.iconName ?: "ledger"),
                    darkTheme = darkTheme,
                    onLongPressEdit = { onRequestEditTx(tx) },
                    onDelete = { onRequestDeleteTx(tx) }
                )
            }
        }
    }
}

/**
 * V9 Redesigned Wallets & Savings Vault Screen:
 * - Top of Wallets has two pills: `Available` (Spendable balance = Actual minus Vault) and `Total` (Actual balance).
 * - Cards say `Balance • <Wallet>` (never "Spendable • <Wallet>"!).
 * - Savings Vault is opened via a sleek Floating Button at the bottom-right of the Wallets screen!
 * - Inside the Savings Vault, every Goal is 100% editable (rename, select icon, adjust target amount, edit wallet criteria, and adjust assigned amounts per wallet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletsAndVaultV9Screen(
    subMode: String,
    balancePillMode: String,
    onChangeBalancePillMode: (String) -> Unit,
    onOpenVault: () -> Unit,
    onBackToWallets: () -> Unit,
    darkTheme: Boolean,
    wallets: List<WalletEntity>,
    availableBalances: Map<String, Double>,
    totalActualBalances: Map<String, Double>,
    vaultGoals: List<VaultGoalEntity>,
    vaultHoldings: Map<String, Map<String, Double>>,
    vm: OttoViewModel,
    onMoveFromWallet: (String) -> Unit,
    onEditWalletBalance: (WalletEntity) -> Unit,
    onRequestDeleteGoal: (VaultGoalEntity) -> Unit
) {
    if (subMode == "vault") {
        BackHandler { onBackToWallets() }
    }

    var goalToAssign by remember { mutableStateOf<VaultGoalEntity?>(null) }
    var goalToRelease by remember { mutableStateOf<VaultGoalEntity?>(null) }
    var goalToEditFull by remember { mutableStateOf<VaultGoalEntity?>(null) }
    var showCreateGoalDialog by remember { mutableStateOf(false) }

    val availableIcons = listOf("vault", "tech", "home", "bag", "book", "bus", "coffee", "health", "wallet", "bolt")

    AnimatedContent(
        targetState = subMode,
        transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(160))) },
        label = "walletsOrVault"
    ) { mode ->
        if (mode == "wallets") {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.MD),
                    contentPadding = PaddingValues(top = Spacing.XS, bottom = 84.dp)
                ) {
                    // Top Row: "Wallets" Title + Two Pills: Available | Total
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleLineText("Wallets", style = MaterialTheme.typography.headlineLarge)

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf("AVAILABLE" to "Available", "TOTAL" to "Total").forEach { (key, label) ->
                                    val active = balancePillMode == key
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(if (active) OttoTurquoise else Color.Transparent)
                                            .bounceClick { onChangeBalancePillMode(key) }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        SingleLineText(
                                            text = label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 12.sp,
                                            color = if (active) InkDark else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(wallets, key = { it.id }) { wallet ->
                        val displayedBal = if (balancePillMode == "AVAILABLE") {
                            availableBalances[wallet.name] ?: 0.0
                        } else {
                            totalActualBalances[wallet.name] ?: 0.0
                        }
                        val animBal by animateIntAsState(
                            targetValue = displayedBal.roundToInt(),
                            animationSpec = tween(420),
                            label = "wBal_${wallet.id}"
                        )
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
                                    // Clean "Balance • <Wallet>" header (no awkward "Spendable" text!)
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

                // FLOATING SAVINGS VAULT BUTTON AT BOTTOM-RIGHT OF WALLETS SCREEN!
                Surface(
                    shape = CircleShape,
                    color = OttoTurquoise,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 14.dp, end = 4.dp)
                        .bounceClick(scaleDown = 0.92f) { onOpenVault() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        PrecisionVectorIcon("vault", InkDark, size = 20.dp)
                        SingleLineText(
                            text = "Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = InkDark
                        )
                    }
                }
            }
        } else {
            // SAVINGS VAULT VIEW (Opened via the Bottom-Right Floating Button!)
            val totalSavedAllGoals = vaultHoldings.values.sumOf { it.values.sum() }
            val animVaultTotal by animateIntAsState(targetValue = totalSavedAllGoals.roundToInt(), animationSpec = tween(420), label = "animVaultTot")
            val projected5Yr = (totalSavedAllGoals * 1.08.pow(5.0)).roundToInt()

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.MD),
                contentPadding = PaddingValues(vertical = Spacing.XS)
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XXS)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
                        ) {
                            IconButton(onClick = onBackToWallets, modifier = Modifier.size(32.dp)) {
                                PrecisionVectorIcon("arrow_left", MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                            }
                            SingleLineText("Wallets /", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SingleLineText("Savings Vault", style = MaterialTheme.typography.titleLarge)
                        }
                        Button(
                            onClick = { showCreateGoalDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OttoTurquoise, contentColor = InkDark)
                        ) {
                            SingleLineText("+ Goal", style = MaterialTheme.typography.titleMedium, color = InkDark)
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F292A), Color(0xFF0A181A), Color(0xFF081012))
                                )
                            )
                            .border(1.dp, OttoTurquoise.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
                            .padding(Spacing.LG)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                            SingleLineText(
                                text = "TOTAL ASSIGNED IN VAULT",
                                style = MaterialTheme.typography.labelSmall,
                                color = OttoTurquoise
                            )
                            SingleLineText(
                                text = "৳$animVaultTotal",
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Subtracted from your 'Available' wallet balances so you know exactly how much you can spend.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SingleLineText("5-Year Halal Projection (8%/yr)", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                                SingleLineText("৳$projected5Yr", style = MaterialTheme.typography.titleMedium, color = OttoTurquoise)
                            }
                        }
                    }
                }

                items(vaultGoals, key = { it.id }) { goal ->
                    val holdingsForGoal = vaultHoldings[goal.id] ?: emptyMap()
                    val savedInGoal = holdingsForGoal.values.sum()
                    val progress = (savedInGoal / goal.targetAmount.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
                    val animProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                        label = "goalProg_${goal.id}"
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(if (darkTheme) Color(0xFF1A2829) else MonochromeBoxLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PrecisionVectorIcon(
                                            name = goal.iconName,
                                            tint = if (darkTheme) OttoTurquoise else InkDark,
                                            size = 20.dp
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        SingleLineText(goal.title, style = MaterialTheme.typography.titleLarge)
                                        SingleLineText(
                                            text = "৳${savedInGoal.roundToInt()} / ৳${goal.targetAmount.roundToInt()} (${(progress * 100).roundToInt()}%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    // Full Edit Button for Vault Goal (Rename, Icon, Target, Criteria & Assigned Amounts!)
                                    IconButton(
                                        onClick = { goalToEditFull = goal },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        PrecisionVectorIcon("edit", MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                                    }
                                    IconButton(
                                        onClick = { onRequestDeleteGoal(goal) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        PrecisionVectorIcon("trash", BinanceRed, size = 16.dp)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (animProgress > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animProgress)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(OttoTurquoise)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SingleLineText(
                                    text = "Allowed: ${goal.allowedWallets} • Min Floor ৳${goal.minWalletFloor.roundToInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (holdingsForGoal.values.any { it > 0.0 }) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        holdingsForGoal.filter { it.value > 0.0 }.forEach { (wName, amt) ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.bounceClick { goalToEditFull = goal }
                                            ) {
                                                SingleLineText(
                                                    text = "$wName: ৳${amt.roundToInt()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.SM)
                            ) {
                                Button(
                                    onClick = { goalToAssign = goal },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1.3f).height(42.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    SingleLineText(
                                        text = "+ Assign",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }

                                if (savedInGoal > 0.0) {
                                    OutlinedButton(
                                        onClick = { goalToRelease = goal },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        SingleLineText(
                                            text = "Release",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 13.sp
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

    // FULL EDIT VAULT GOAL DIALOG (Rename, Change Icon, Target Amount, Criteria & Assigned Amounts per Wallet!)
    goalToEditFull?.let { targetGoal ->
        val currentHoldings = vaultHoldings[targetGoal.id] ?: emptyMap()
        var editTitle by remember(targetGoal) { mutableStateOf(targetGoal.title) }
        var editTarget by remember(targetGoal) { mutableStateOf(targetGoal.targetAmount.roundToInt().toString()) }
        var editFloor by remember(targetGoal) { mutableStateOf(targetGoal.minWalletFloor.roundToInt().toString()) }
        var editIcon by remember(targetGoal) { mutableStateOf(targetGoal.iconName) }
        val allowedSet = remember(targetGoal) {
            mutableStateListOf<String>().apply {
                addAll(targetGoal.allowedWallets.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }
        }
        val editableHoldingsMap = remember(targetGoal, wallets) {
            mutableStateMapOf<String, String>().apply {
                wallets.forEach { w ->
                    put(w.name, (currentHoldings[w.name] ?: 0.0).roundToInt().toString())
                }
            }
        }

        AlertDialog(
            onDismissRequest = { goalToEditFull = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Edit Vault Goal", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SM)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { SingleLineText("Goal Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SingleLineText("Icon", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableIcons) { ic ->
                            val isSel = editIcon == ic
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) OttoTurquoise else MaterialTheme.colorScheme.surfaceVariant)
                                    .bounceClick { editIcon = ic },
                                contentAlignment = Alignment.Center
                            ) {
                                PrecisionVectorIcon(
                                    name = ic,
                                    tint = if (isSel) InkDark else MaterialTheme.colorScheme.onSurface,
                                    size = 18.dp
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        OutlinedTextField(
                            value = editTarget,
                            onValueChange = { editTarget = it },
                            label = { SingleLineText("Target (৳)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editFloor,
                            onValueChange = { editFloor = it },
                            label = { SingleLineText("Min Floor (৳)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SingleLineText("Allowed Wallets Criteria", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wallets.map { it.name }) { wName ->
                            val isSel = wName in allowedSet
                            WalletChoicePill(
                                label = wName,
                                selected = isSel,
                                darkTheme = darkTheme,
                                onClick = {
                                    if (isSel && allowedSet.size > 1) allowedSet.remove(wName)
                                    else if (!isSel) allowedSet.add(wName)
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SingleLineText("Assigned Amounts per Wallet (৳)", style = MaterialTheme.typography.titleMedium)
                    wallets.forEach { w ->
                        OutlinedTextField(
                            value = editableHoldingsMap[w.name] ?: "0",
                            onValueChange = { editableHoldingsMap[w.name] = it },
                            label = { SingleLineText("Assigned from ${w.name} (৳)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedHoldings = editableHoldingsMap.mapValues { (_, v) ->
                            (v.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        }
                        vm.updateVaultGoalAndHoldings(
                            goal = targetGoal,
                            newTitle = editTitle,
                            newTargetAmount = editTarget.toDoubleOrNull() ?: targetGoal.targetAmount,
                            newIconName = editIcon,
                            newAllowedWallets = allowedSet.joinToString(","),
                            newMinFloor = editFloor.toDoubleOrNull() ?: targetGoal.minWalletFloor,
                            updatedWalletHoldings = parsedHoldings
                        )
                        goalToEditFull = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SingleLineText("Save", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToEditFull = null }) {
                    SingleLineText("Cancel")
                }
            }
        )
    }

    goalToAssign?.let { targetGoal ->
        val allowedNames = remember(targetGoal, wallets) {
            val configured = targetGoal.allowedWallets.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val matching = wallets.map { it.name }.filter { it in configured }
            matching.ifEmpty { wallets.map { it.name } }
        }
        var selectedWallet by remember(allowedNames) { mutableStateOf(allowedNames.firstOrNull() ?: "bKash") }
        var assignAmountText by remember { mutableStateOf("") }
        var criteriaError by remember { mutableStateOf<String?>(null) }

        val currentAvail = availableBalances[selectedWallet] ?: 0.0
        val maxSafeAssignable = (currentAvail - targetGoal.minWalletFloor).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = { goalToAssign = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Assign to ${targetGoal.title}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    SingleLineText("Eligible Wallets", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allowedNames) { wName ->
                            val avail = availableBalances[wName] ?: 0.0
                            WalletChoicePill(
                                label = "$wName (৳${avail.roundToInt()})",
                                selected = selectedWallet == wName,
                                darkTheme = darkTheme,
                                onClick = { selectedWallet = wName; criteriaError = null }
                            )
                        }
                    }

                    SingleLineText(
                        text = "Min Floor: ৳${targetGoal.minWalletFloor.roundToInt()} • Max Assignable: ৳${maxSafeAssignable.roundToInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep
                    )

                    OutlinedTextField(
                        value = assignAmountText,
                        onValueChange = { assignAmountText = it; criteriaError = null },
                        label = { SingleLineText("Amount (৳)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    criteriaError?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = BinanceRed)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = assignAmountText.toDoubleOrNull() ?: 0.0
                        val err = vm.assignMoneyToVaultGoal(targetGoal, selectedWallet, amt, availableBalances)
                        if (err != null) criteriaError = err else goalToAssign = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SingleLineText("Save", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToAssign = null }) { SingleLineText("Cancel") }
            }
        )
    }

    goalToRelease?.let { targetGoal ->
        val holdingsForGoal = vaultHoldings[targetGoal.id] ?: emptyMap()
        val nonZeroWallets = holdingsForGoal.filter { it.value > 0.0 }.keys.toList()
        var selectedWallet by remember(nonZeroWallets) { mutableStateOf(nonZeroWallets.firstOrNull() ?: "bKash") }
        val maxInSelected = holdingsForGoal[selectedWallet] ?: 0.0
        var releaseAmountText by remember(selectedWallet) { mutableStateOf(maxInSelected.roundToInt().toString()) }
        var releaseError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { goalToRelease = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("Release from ${targetGoal.title}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(nonZeroWallets) { wName ->
                            val held = holdingsForGoal[wName] ?: 0.0
                            WalletChoicePill(
                                label = "$wName (৳${held.roundToInt()})",
                                selected = selectedWallet == wName,
                                darkTheme = darkTheme,
                                onClick = { selectedWallet = wName; releaseError = null }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = releaseAmountText,
                        onValueChange = { releaseAmountText = it; releaseError = null },
                        label = { SingleLineText("Amount to Release (৳)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    releaseError?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = BinanceRed)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = releaseAmountText.toDoubleOrNull() ?: 0.0
                        val err = vm.releaseMoneyFromVaultGoal(targetGoal, selectedWallet, amt, holdingsForGoal)
                        if (err != null) releaseError = err else goalToRelease = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SingleLineText("Release", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToRelease = null }) { SingleLineText("Cancel") }
            }
        )
    }

    if (showCreateGoalDialog) {
        var titleInput by remember { mutableStateOf("") }
        var targetInput by remember { mutableStateOf("") }
        var floorInput by remember { mutableStateOf("500") }
        var chosenIcon by remember { mutableStateOf("vault") }
        val nonTransitWallets = remember(wallets) {
            wallets.map { it.name }.filter { !it.lowercase().contains("transit") && !it.lowercase().contains("rapid") }
        }
        val selectedAllowed = remember(nonTransitWallets) { mutableStateListOf<String>().apply { addAll(nonTransitWallets) } }

        AlertDialog(
            onDismissRequest = { showCreateGoalDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { SingleLineText("New Savings Vault Goal", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { SingleLineText("Goal Name (e.g. New iPhone, Gift)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SingleLineText("Select Icon", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableIcons) { ic ->
                            val isSel = chosenIcon == ic
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) OttoTurquoise else MaterialTheme.colorScheme.surfaceVariant)
                                    .bounceClick { chosenIcon = ic },
                                contentAlignment = Alignment.Center
                            ) {
                                PrecisionVectorIcon(
                                    name = ic,
                                    tint = if (isSel) InkDark else MaterialTheme.colorScheme.onSurface,
                                    size = 16.dp
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { SingleLineText("Target Amount (৳)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = floorInput,
                        onValueChange = { floorInput = it },
                        label = { SingleLineText("Min Wallet Floor Criteria (৳)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SingleLineText("Allowed Wallets Criteria:", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wallets.map { it.name }) { wName ->
                            val isSel = wName in selectedAllowed
                            WalletChoicePill(
                                label = wName,
                                selected = isSel,
                                darkTheme = darkTheme,
                                onClick = {
                                    if (isSel && selectedAllowed.size > 1) selectedAllowed.remove(wName)
                                    else if (!isSel) selectedAllowed.add(wName)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.addVaultGoal(
                            title = titleInput,
                            targetAmount = targetInput.toDoubleOrNull() ?: 10000.0,
                            allowedWallets = selectedAllowed.joinToString(","),
                            minWalletFloor = floorInput.toDoubleOrNull() ?: 0.0,
                            iconName = chosenIcon
                        )
                        showCreateGoalDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SingleLineText("Save", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGoalDialog = false }) { SingleLineText("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsHubV9Screen(
    subScreen: String,
    onNavigateSubScreen: (String) -> Unit,
    vm: OttoViewModel,
    activity: FragmentActivity,
    balances: Map<String, Double>,
    onEditWalletBalance: (WalletEntity) -> Unit,
    onAskDeleteConfirmation: (String, String, () -> Unit) -> Unit
) {
    if (subScreen != "menu") {
        BackHandler { onNavigateSubScreen("menu") }
    }

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(150))) },
        label = "settingsSubTransition"
    ) { targetSub ->
        when (targetSub) {
            "backup" -> BackupAndCloudSyncSubScreen(vm = vm, onBack = { onNavigateSubScreen("menu") })
            "magic_words" -> CategoriesAndMagicWordsSubScreen(
                vm = vm,
                onBack = { onNavigateSubScreen("menu") },
                onAskDeleteConfirmation = onAskDeleteConfirmation
            )
            "profile" -> ProfileAndWalletsSubScreen(
                vm = vm,
                balances = balances,
                onEditWalletBalance = onEditWalletBalance,
                onBack = { onNavigateSubScreen("menu") },
                onAskDeleteConfirmation = onAskDeleteConfirmation
            )
            "security" -> SecurityAndLockSubScreen(vm = vm, activity = activity, onBack = { onNavigateSubScreen("menu") })
            "scheduler" -> SchedulerSubScreen(
                vm = vm,
                onBack = { onNavigateSubScreen("menu") },
                onAskDeleteConfirmation = onAskDeleteConfirmation
            )
            "sms_parser" -> SmartSmsParserSubScreen(vm = vm, onBack = { onNavigateSubScreen("menu") })
            "faq" -> FaqAndHelpSubScreen(vm = vm, onBack = { onNavigateSubScreen("menu") })
            else -> {
                val rules by vm.keywordRules.collectAsState()
                val categories by vm.categories.collectAsState()
                val wallets by vm.wallets.collectAsState()
                val recurringRules by vm.recurringRules.collectAsState()
                val lastSync by vm.lastSyncedText.collectAsState()
                val isPinOn by vm.isPinLockEnabled.collectAsState()
                val isVaultLockOn by vm.isVaultLockEnabled.collectAsState()
                val isSmsOn by vm.isSmsParserEnabled.collectAsState()
                val isCapOn by vm.isMonthlyCapEnabled.collectAsState()

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
                            title = "Profile, Wallets & Monthly Cap",
                            subtitle = "${wallets.size} wallets • Monthly Cap ${if (isCapOn) "ON" else "OFF"}",
                            onClick = { onNavigateSubScreen("profile") }
                        )
                    }

                    item {
                        SettingsNavCard(
                            iconName = "calendar",
                            title = "Scheduler",
                            subtitle = "${recurringRules.size} scheduled Expense & Income rules • Confirms on due date",
                            onClick = { onNavigateSubScreen("scheduler") }
                        )
                    }

                    item {
                        SettingsNavCard(
                            iconName = "bolt",
                            title = "Smart SMS & Alert Parser",
                            subtitle = "${if (isSmsOn) "Active" else "Opt-in"} • Offline bKash, Nagad, Rocket & Bank alerts",
                            onClick = { onNavigateSubScreen("sms_parser") }
                        )
                    }

                    item {
                        SettingsNavCard(
                            iconName = "lock",
                            title = "Security & Vault Lock",
                            subtitle = "App PIN ${if (isPinOn) "ON" else "OFF"} • Vault Lock ${if (isVaultLockOn) "ON" else "OFF"}",
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
                            title = "FAQ, Guide & App Tour",
                            subtitle = "Monthly Cap, Savings Vault, Scheduler & Replay Tour",
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
                        .background(if (darkTheme) Color(0xFF1F2733) else OttoTurquoiseSoftLight),
                    contentAlignment = Alignment.Center
                ) {
                    PrecisionVectorIcon(
                        name = iconName,
                        tint = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep,
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
 * Dedicated Settings / Scheduler Sub-Screen:
 * - Configures all details for recurring Expenses and Income (Title, Type, Amount, Wallet, Category, Day of Month, Notification Time).
 * - Deletion requires confirmation and triggers an Undo snackbar!
 */
@Composable
fun SchedulerSubScreen(
    vm: OttoViewModel,
    onBack: () -> Unit,
    onAskDeleteConfirmation: (String, String, () -> Unit) -> Unit
) {
    val recurringEnabled by vm.isRecurringEnabled.collectAsState()
    val rules by vm.recurringRules.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val categories by vm.categories.collectAsState()
    val darkTheme by vm.isDarkTheme.collectAsState()

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var amount by remember { mutableStateOf("") }
    var selectedWallet by remember(wallets) { mutableStateOf(wallets.firstOrNull()?.name ?: "bKash") }
    var selectedCategory by remember(categories, type) {
        mutableStateOf(categories.firstOrNull { it.kind == type }?.name ?: "Housing & Rent")
    }
    var dayOfMonthText by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    var hourText by remember { mutableStateOf("09") }
    var minuteText by remember { mutableStateOf("00") }

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
                SingleLineText("Scheduler", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.LG),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SingleLineText("Enable Scheduler & Reminders", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Balances update only after you confirm on the due date via the Home notification button.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = recurringEnabled,
                        onCheckedChange = { vm.toggleRecurringFeature(it) }
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.LG), verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    SingleLineText("Add Scheduled Expense or Income", style = MaterialTheme.typography.titleLarge)

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                        listOf("EXPENSE" to "Expense", "INCOME" to "Income").forEach { (k, lbl) ->
                            WalletChoicePill(
                                label = lbl,
                                selected = type == k,
                                darkTheme = darkTheme,
                                onClick = {
                                    type = k
                                    selectedCategory = categories.firstOrNull { it.kind == k }?.name ?: "Other"
                                }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { SingleLineText("Title (e.g. Rent, Tuition)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { SingleLineText("Amount (৳)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    SingleLineText("Wallet", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wallets) { w ->
                            WalletChoicePill(
                                label = w.name,
                                selected = selectedWallet == w.name,
                                darkTheme = darkTheme,
                                onClick = { selectedWallet = w.name }
                            )
                        }
                    }

                    SingleLineText("Category", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories.filter { it.kind == type }) { c ->
                            WalletChoicePill(
                                label = c.name,
                                selected = selectedCategory == c.name,
                                darkTheme = darkTheme,
                                onClick = { selectedCategory = c.name }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = dayOfMonthText,
                            onValueChange = { dayOfMonthText = it },
                            label = { SingleLineText("Day (1-31)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it },
                            label = { SingleLineText("Hour (0-23)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it },
                            label = { SingleLineText("Min (0-59)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                runCatching { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            }
                            vm.addRecurringRule(
                                title = title,
                                type = type,
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                category = selectedCategory,
                                wallet = selectedWallet,
                                dayOfMonth = dayOfMonthText.toIntOrNull() ?: 1,
                                notifyHour = hourText.toIntOrNull() ?: 9,
                                notifyMinute = minuteText.toIntOrNull() ?: 0
                            )
                            title = ""; amount = ""
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        SingleLineText("Save", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.MD),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SingleLineText(
                            text = "${rule.title} • ${if (rule.type == "INCOME") "+" else "-"}৳${rule.amount.roundToInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (rule.type == "INCOME") BinanceGreen else MaterialTheme.colorScheme.onSurface
                        )
                        SingleLineText(
                            text = "${rule.wallet} • ${rule.category} • Day ${rule.dayOfMonth} at %02d:%02d".format(rule.notifyHour, rule.notifyMinute),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { vm.toggleRecurringRuleItem(rule, it) }
                        )
                        IconButton(
                            onClick = {
                                onAskDeleteConfirmation(
                                    "Delete Scheduled Item?",
                                    "Delete '${rule.title}' (৳${rule.amount.roundToInt()})?",
                                    { vm.deleteRecurringRuleWithUndo(rule) }
                                )
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            PrecisionVectorIcon("trash", BinanceRed, size = 16.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartSmsParserSubScreen(
    vm: OttoViewModel,
    onBack: () -> Unit
) {
    val smsParserOn by vm.isSmsParserEnabled.collectAsState()
    val context = LocalContext.current
    var sampleSmsInput by remember {
        mutableStateOf("Payment of Tk 250.00 to Cafe Sylhet is successful. Fee Tk 0.00. Balance Tk 4,250.00. TrxID 9K3M7X2P at 11/11/2026 14:20")
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.toggleSmsParser(true)
    }

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
                SingleLineText("Smart SMS & Alert Parser", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.LG), verticalArrangement = Arrangement.spacedBy(Spacing.MD)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("On-Device MFS & Bank Alert Parser", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "100% offline parser for bKash, Nagad, Rocket & BD Bank alerts. Shows a floating notification button at the bottom-right of Home when an alert arrives.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = smsParserOn,
                            onCheckedChange = { wantOn ->
                                if (wantOn) {
                                    runCatching { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) }
                                    vm.toggleSmsParser(true)
                                } else {
                                    vm.toggleSmsParser(false)
                                }
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { runCatching { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) } },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            SingleLineText("Grant SMS Access", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            SingleLineText("Notification Access", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    SingleLineText("TEST / PASTE AN MFS OR BANK SMS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(
                        value = sampleSmsInput,
                        onValueChange = { sampleSmsInput = it },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { vm.simulateOrParseSmsText("bKash", sampleSmsInput) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            SingleLineText("Simulate bKash Alert", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                sampleSmsInput = "Cash In Tk 3,500.00 from 017XXXXXXXX successful. Fee Tk 0.00. Balance Tk 8,200.00. TxnId NGD88421B"
                                vm.simulateOrParseSmsText("Nagad", sampleSmsInput)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(0.9f).height(46.dp)
                        ) {
                            SingleLineText("Simulate Nagad", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityAndLockSubScreen(
    vm: OttoViewModel,
    activity: FragmentActivity,
    onBack: () -> Unit
) {
    val isPinEnabled by vm.isPinLockEnabled.collectAsState()
    val isBioEnabled by vm.isBiometricEnabled.collectAsState()
    val isVaultLockOn by vm.isVaultLockEnabled.collectAsState()
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
                SingleLineText("Security & Vault Lock", style = MaterialTheme.typography.titleLarge)
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
                    SingleLineText("APP PASSCODE & BIOMETRICS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("4-Digit iOS Passcode Lock", style = MaterialTheme.typography.titleMedium)
                            SingleLineText(
                                if (isPinEnabled) "Required on app launch & resume" else "Protect your wallets & ledger",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPinEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable) showPinSetupDialog = true else vm.disablePinLock()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("Biometric Unlock (Bottom-Right Sensor)", style = MaterialTheme.typography.titleMedium)
                            SingleLineText(
                                "Use fingerprint or face unlock alongside PIN",
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    SingleLineText("SAVINGS VAULT PRIVACY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("Lock Savings Vault with PIN / Biometrics", style = MaterialTheme.typography.titleMedium)
                            SingleLineText(
                                "Require authentication when tapping the floating Vault button on Wallets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isVaultLockOn,
                            onCheckedChange = { vm.toggleVaultLock(it) }
                        )
                    }

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
                        "Enter a 4-digit PIN for your iOS-style Lock Screen and Savings Vault.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { SingleLineText("New 4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { SingleLineText("Confirm 4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
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
                    SingleLineText("Save", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { SingleLineText("Cancel") }
            }
        )
    }
}

data class FaqItem(val question: String, val answer: String)

@Composable
fun FaqAndHelpSubScreen(
    vm: OttoViewModel,
    onBack: () -> Unit
) {
    val faqs = remember {
        listOf(
            FaqItem(
                question = "What is the Monthly Cap, and can I toggle it off?",
                answer = "Your Monthly Cap is your target monthly spending ceiling. On the Home screen, 'Expense ৳X / ৳Monthly Cap' fills the progress bar as you log expenses. You can toggle the Monthly Cap ON or OFF anytime in Settings → Profile, Wallets & Monthly Cap."
            ),
            FaqItem(
                question = "How do 'Available' vs. 'Total' pills and the Floating Vault Button on Wallets work?",
                answer = "At the top of the Wallets screen, 'Available' shows your spendable balance after subtracting any money assigned to your Savings Vault, while 'Total' shows the full actual balance physically sitting in the wallet. Tap the floating 'Vault' button at the bottom-right of Wallets to open your PIN/Biometric Savings Vault and edit or assign goals."
            ),
            FaqItem(
                question = "How do I edit a past transaction or undo an accidental delete?",
                answer = "Press and hold (long-press) any transaction in Recent Transactions or Ledger to open the Edit Transaction dialog where you can change its Type, Amount, Date, Description, Category, or Wallet. Whenever you delete an item, a confirmation popup prevents mistakes, and an 'Undo' snackbar lets you restore it immediately."
            ),
            FaqItem(
                question = "Where do new SMS Alerts and Scheduler Confirmations appear?",
                answer = "To keep your Home screen clean, OttoBudget only displays a floating Notification button at the bottom-right of the Home screen when a new bKash/Nagad/Rocket/Bank SMS alert or due Scheduler item is waiting for your confirmation."
            ),
            FaqItem(
                question = "How can onboarding data be received without using a database?",
                answer = "In Settings → Backup & Cloud Sync, you can paste a stateless Webhook URL (such as a 5-line Google Apps Script Web App attached to a Google Sheet, a Google Forms endpoint, a Discord Webhook URL, or a Telegram Bot webhook). OttoBudget sends the onboarding JSON directly to that webhook with zero database server required."
            )
        )
    }

    var expandedIndex by remember { mutableIntStateOf(0) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
        contentPadding = PaddingValues(vertical = Spacing.XS)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XXS)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        PrecisionVectorIcon("arrow_left", MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                    }
                    SingleLineText("Settings /", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleLineText("FAQ & Guide", style = MaterialTheme.typography.titleLarge)
                }

                Button(
                    onClick = { vm.replaySpotlightTour() },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    SingleLineText("Replay Tour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        items(faqs.size) { idx ->
            val item = faqs[idx]
            val isOpen = expandedIndex == idx

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isOpen) OttoTurquoise else MaterialTheme.colorScheme.outline
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
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(end = Spacing.SM)
                        )
                        Box(modifier = Modifier.graphicsLayer { rotationZ = if (isOpen) 90f else 0f }) {
                            PrecisionVectorIcon(
                                name = "arrow_right",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
fun BackupAndCloudSyncSubScreen(
    vm: OttoViewModel,
    onBack: () -> Unit
) {
    val autoSync by vm.autoSyncState.collectAsState()
    val lastSynced by vm.lastSyncedText.collectAsState()
    var supabaseUrl by remember { mutableStateOf(vm.syncManager.supabaseUrl) }
    var supabaseKey by remember { mutableStateOf(vm.syncManager.supabaseAnonKey) }
    var zeroDbWebhook by remember { mutableStateOf(vm.syncManager.zeroDbWebhookUrl) }
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
                SingleLineText("Settings /", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleLineText("Backup & Cloud Sync", style = MaterialTheme.typography.titleLarge)
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
                    SingleLineText("BACKUP & RESTORE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)

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
                        SingleLineText("Clear Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    SingleLineText("CLOUD SYNC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)

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
                            Switch(checked = autoSync, onCheckedChange = { vm.toggleAutoSync(it) })
                        }
                    }

                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = { supabaseUrl = it; vm.syncManager.supabaseUrl = it },
                        placeholder = { SingleLineText("Supabase URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = supabaseKey,
                        onValueChange = { supabaseKey = it; vm.syncManager.supabaseAnonKey = it },
                        placeholder = { SingleLineText("Supabase Anon Key") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = zeroDbWebhook,
                        onValueChange = { zeroDbWebhook = it; vm.syncManager.zeroDbWebhookUrl = it },
                        placeholder = { SingleLineText("Optional Zero-DB Webhook (Apps Script / Discord)") },
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
                    onClick = { vm.clearAllLocalData(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BinanceRed)
                ) {
                    SingleLineText("Clear Data", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { SingleLineText("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesAndMagicWordsSubScreen(
    vm: OttoViewModel,
    onBack: () -> Unit,
    onAskDeleteConfirmation: (String, String, () -> Unit) -> Unit
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
                    onClick = { vm.addCategory(newCategoryName, kindTab, "ledger"); newCategoryName = "" },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary)
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
                        IconButton(
                            onClick = {
                                onAskDeleteConfirmation(
                                    "Delete Category?",
                                    "Delete '${cat.name}' and its magic words?",
                                    {
                                        vm.deleteCategoryWithUndo(cat)
                                        if (selectedCat?.id == cat.id) selectedCat = null
                                    }
                                )
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            PrecisionVectorIcon("trash", BinanceRed, size = 15.dp)
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
                                onClick = { vm.addKeywordToCategory(newMagicWord, cat); newMagicWord = "" },
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary)
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
                        if (parentCat != null) vm.updateKeywordRule(targetRule.keyword, editedWordText, parentCat)
                        wordToEdit = null
                    }
                ) {
                    SingleLineText("Save", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.removeKeywordRule(targetRule.keyword, targetRule.category); wordToEdit = null }
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
    onBack: () -> Unit,
    onAskDeleteConfirmation: (String, String, () -> Unit) -> Unit
) {
    val userName by vm.userName.collectAsState()
    val monthlyBudget by vm.monthlyBudgetTarget.collectAsState()
    val monthlyCapEnabled by vm.isMonthlyCapEnabled.collectAsState()
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
                SingleLineText("Profile, Wallets & Cap", style = MaterialTheme.typography.titleLarge)
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
                    SingleLineText("Profile & Monthly Cap", style = MaterialTheme.typography.titleLarge)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText("Enable Monthly Cap", style = MaterialTheme.typography.titleMedium)
                            SingleLineText(
                                "Show monthly spending limit & progress bar on Home",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = monthlyCapEnabled,
                            onCheckedChange = { vm.toggleMonthlyCap(it) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { SingleLineText("Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        if (monthlyCapEnabled) {
                            OutlinedTextField(
                                value = editBudget,
                                onValueChange = { editBudget = it },
                                label = { SingleLineText("Monthly Cap") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
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
                                    IconButton(
                                        onClick = {
                                            onAskDeleteConfirmation(
                                                "Delete Wallet?",
                                                "Remove '${w.name}' from your wallets?",
                                                { vm.removeWalletWithUndo(w) }
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
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
 * V9 Quick-Add Bottom Sheet (`QuickAddV9BottomSheet`):
 * 1. Fixes the status bar white-icon overlap when the keyboard opens by explicitly configuring
 *    `WindowCompat.getInsetsController(dialogWindow, view).isAppearanceLightStatusBars = !darkTheme` on the DialogWindow!
 * 2. Removes the `Repeat Monthly` checkbox (recurring rules are managed in `Settings → Scheduler`).
 * 3. Replaces the multi-chip date row with a single clean **Date Bar** showing today's date (`YYYY-MM-DD`).
 * 4. Animates the Category `LazyRow` (`categoryListState.animateScrollToItem(matchedIdx)`) with a spring pulse
 *    whenever a Magic Word auto-selects a category!
 * 5. Displays simply **`Save`** on the bottom button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddV9BottomSheet(
    defaultMode: String,
    initialFromWallet: String,
    initialToWallet: String,
    balances: Map<String, Double>,
    darkTheme: Boolean,
    vm: OttoViewModel,
    onDismiss: () -> Unit
) {
    // Fix status bar icons turning white when ModalBottomSheet's DialogWindow opens over Light Mode!
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            dialogWindow?.let { win ->
                val controller = WindowCompat.getInsetsController(win, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val wallets by vm.wallets.collectAsState()
    val categories by vm.categories.collectAsState()
    val rules by vm.keywordRules.collectAsState()

    val todayStr = remember { LocalDate.now().toString() }

    var mode by remember { mutableStateOf(defaultMode) }
    var selectedDate by remember { mutableStateOf(todayStr) }
    var showDatePickerModal by remember { mutableStateOf(false) }

    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull { it.kind == defaultMode }?.name ?: "Education") }
    var fromWallet by remember { mutableStateOf(initialFromWallet) }
    var toWallet by remember { mutableStateOf(initialToWallet) }
    var swapTrigger by remember { mutableIntStateOf(0) }
    var autoMatchedPulseKey by remember { mutableIntStateOf(0) }
    var insufficientPopupMessage by remember { mutableStateOf<String?>(null) }

    val categoryListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val evaluatedAmt = RuleEngine.evaluateMathExpression(amount)

    val visibleModeCategories = remember(categories, mode) {
        categories.filter { it.kind == mode }
    }

    val isTransitWalletSelected = remember(fromWallet, mode) {
        val lower = fromWallet.lowercase()
        mode == "EXPENSE" && (lower.contains("transit") || lower.contains("rapid") || lower.contains("metro"))
    }

    LaunchedEffect(isTransitWalletSelected) {
        if (isTransitWalletSelected) {
            selectedCategory = "Transport"
            val idx = visibleModeCategories.indexOfFirst { it.name == "Transport" }
            if (idx >= 0) {
                autoMatchedPulseKey++
                categoryListState.animateScrollToItem(idx)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.LG, vertical = Spacing.XS)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Transaction Type (Consistent terminology: Expense | Income | Transfer)
        SingleLineText("Transaction Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM), modifier = Modifier.fillMaxWidth()) {
            listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (key, label) ->
                val isSel = mode == key
                val pillBg = if (isSel) OttoTurquoise else MaterialTheme.colorScheme.surface
                val pillText = if (isSel) InkDark else MaterialTheme.colorScheme.onSurface

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pillBg,
                    border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
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

        // 2. Single Clean Date Bar Showing Selected Date (Defaults to Today's Date; no Today/Yesterday tags!)
        SingleLineText("Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .bounceClick { showDatePickerModal = true }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.MD),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SingleLineText(
                    text = selectedDate,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                PrecisionVectorIcon(
                    name = "calendar",
                    tint = if (darkTheme) OttoTurquoise else OttoTurquoiseDeep,
                    size = 18.dp
                )
            }
        }

        // 3. Amount Input
        SingleLineText("Amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().height(52.dp)
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

        // 4. Description (Triggers Smooth Auto-Scroll in Category LazyRow on Magic Word Match!)
        SingleLineText("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = desc,
            onValueChange = { newText ->
                desc = newText
                if (mode != "TRANSFER" && !isTransitWalletSelected) {
                    RuleEngine.matchCategory(newText, mode, rules)?.let { matched ->
                        if (selectedCategory != matched.category) {
                            selectedCategory = matched.category
                            val targetIdx = visibleModeCategories.indexOfFirst { it.name == matched.category }
                            if (targetIdx >= 0) {
                                autoMatchedPulseKey++
                                scope.launch {
                                    categoryListState.animateScrollToItem(targetIdx)
                                }
                            }
                        }
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
                    PrecisionVectorIcon("transfer", if (darkTheme) OttoTurquoise else OttoTurquoiseDeep, size = 16.dp, triggerKey = swapTrigger)
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
            // 5. Category Row with Smooth Auto-Scroll (`categoryListState`) & Spring Pulse on Magic Word Match!
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleLineText("Category ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SingleLineText(
                    text = if (isTransitWalletSelected) "(locked to Transport for Transit Pass)" else "(optional)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyRow(
                state = categoryListState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                itemsIndexed(visibleModeCategories, key = { _, c -> c.id }) { _, cat ->
                    val isSel = selectedCategory == cat.name
                    val isDisabledByTransit = isTransitWalletSelected && cat.name != "Transport"
                    val pulseScale = remember { Animatable(1f) }
                    LaunchedEffect(isSel, autoMatchedPulseKey) {
                        if (isSel && autoMatchedPulseKey > 0) {
                            pulseScale.snapTo(1.15f)
                            pulseScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                        }
                    }

                    Box(modifier = Modifier.scale(pulseScale.value)) {
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
            }

            SingleLineText("Wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                insufficientPopupMessage = "You only have ৳${avail.roundToInt()} available in ${w.name}, which is less than ৳${evaluatedAmt.roundToInt()}."
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.XXS))

        // 6. Save Button (Displays ONLY "Save" as requested!)
        Button(
            onClick = {
                if (evaluatedAmt <= 0.0) return@Button
                val avail = balances[fromWallet] ?: 0.0
                if ((mode == "EXPENSE" || mode == "TRANSFER") && avail < evaluatedAmt) {
                    insufficientPopupMessage = "Insufficient available balance in $fromWallet (Available: ৳${avail.roundToInt()}, Required: ৳${evaluatedAmt.roundToInt()})."
                    return@Button
                }
                val finalCategory = if (isTransitWalletSelected) "Transport" else selectedCategory
                vm.addTransaction(
                    type = mode,
                    rawAmount = amount,
                    description = desc,
                    category = finalCategory,
                    wallet = fromWallet,
                    toWallet = if (mode == "TRANSFER") toWallet else null,
                    date = selectedDate
                )
                onDismiss()
            },
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OttoTurquoise,
                contentColor = InkDark
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            SingleLineText(
                text = "Save",
                style = MaterialTheme.typography.titleLarge,
                color = InkDark
            )
        }
    }

    if (showDatePickerModal) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            selectedDate = picked.toString()
                        }
                        showDatePickerModal = false
                    }
                ) {
                    SingleLineText("Select", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerModal = false }) {
                    SingleLineText("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
    // Signature #6BD0C2 OttoTurquoise active pill in both Light Mode and Dark Mode!
    val pillBg = if (selected) OttoTurquoise else MaterialTheme.colorScheme.surface
    val pillText = if (selected) InkDark else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = pillBg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .bounceClick(enabled = enabled, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
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
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val p = progress.value

        when (name) {
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
                val doorH = h * (0.26f + 0.06f * p)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.41f, h * 0.86f - doorH),
                    size = Size(w * 0.18f, doorH),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke
                )
            }
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
            "vault" -> {
                rotate(degrees = p * 45f) {
                    drawRoundRect(tint, Offset(w * 0.16f, h * 0.16f), Size(w * 0.68f, h * 0.68f), CornerRadius(6.dp.toPx()), stroke)
                    drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                    drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(w * 0.50f, h * 0.50f))
                }
            }
            "bell" -> {
                rotate(degrees = p * 18f) {
                    val bellPath = Path().apply {
                        moveTo(w * 0.22f, h * 0.68f)
                        lineTo(w * 0.78f, h * 0.68f)
                        lineTo(w * 0.72f, h * 0.38f)
                        quadraticTo(w * 0.50f, h * 0.14f, w * 0.28f, h * 0.38f)
                        close()
                    }
                    drawPath(bellPath, tint, style = stroke)
                    drawArc(tint, 0f, 180f, false, Offset(w * 0.40f, h * 0.68f), Size(w * 0.20f, h * 0.18f), style = stroke)
                }
            }
            "calendar" -> {
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.22f), Size(w * 0.68f, h * 0.62f), CornerRadius(4.dp.toPx()), stroke)
                drawLine(tint, Offset(w * 0.16f, h * 0.40f), Offset(w * 0.84f, h * 0.40f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * 0.34f, h * 0.14f), Offset(w * 0.34f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.14f), Offset(w * 0.66f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
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
                    drawCircle(tint, radius = (1.5f + p * 0.8f).dp.toPx(), center = Offset(w * 0.76f, h * 0.26f))
                }
            }
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
            "fingerprint" -> {
                drawArc(tint, 195f, 150f, false, Offset(w * 0.16f, h * 0.16f), Size(w * 0.68f, h * 0.68f), style = stroke)
                drawArc(tint, 190f, 160f, false, Offset(w * 0.26f, h * 0.26f), Size(w * 0.48f, h * 0.52f), style = stroke)
                drawArc(tint, 180f, 180f, false, Offset(w * 0.36f, h * 0.38f), Size(w * 0.28f, h * 0.38f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.48f), Offset(w * 0.50f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
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
