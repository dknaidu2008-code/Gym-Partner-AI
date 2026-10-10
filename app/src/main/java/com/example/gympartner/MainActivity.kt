@file:Suppress("DEPRECATION", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "MissingPermission", "UnsafeOptInUsageError")

package com.example.gympartner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.PointF
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import android.os.Build
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import android.view.View
import android.widget.VideoView
import android.net.Uri
import androidx.core.net.toUri
import android.provider.Settings
import android.os.Bundle
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.Calendar
import kotlin.math.min
import java.time.LocalDate
import kotlinx.coroutines.delay

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(fallback)
    }
}

private fun areGymPartnerNotificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) return false

    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

@SuppressLint("MissingPermission")
private fun sendTestMotivationNotification(context: Context): Boolean {
    if (!areGymPartnerNotificationsEnabled(context)) return false
    createMotivationNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, MOTIVATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("GYM PARTNER AI 💪")
        .setContentText("🔔 Notifications are working! Keep your workout streak alive.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        NotificationManagerCompat.from(context).notify(MOTIVATION_NOTIFICATION_ID + 99, notification)
        return true
    } catch (_: SecurityException) {
        return false
    }
}


class MainActivity : ComponentActivity() {

    private var gymPartnerVoiceAssistant: VoiceAssistantManager? = null
    private var voiceCommandHandler: ((String) -> String)? = null
    private var voicePermissionRequested = false
    private var voiceAppSessionActive = false

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && voiceAppSessionActive) {
                startVoiceAssistantManager()
            }
        }

    private fun startVoiceAssistantManager() {
        if (!voiceAppSessionActive) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!voicePermissionRequested) {
                voicePermissionRequested = true
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }

        // Do not recreate the recognizer every time onStart/onResume/toggle is called.
        // One manager lives for the whole app session.
        if (gymPartnerVoiceAssistant == null) {
            gymPartnerVoiceAssistant = VoiceAssistantManager(this, "Gymmy") { command ->
                voiceCommandHandler?.invoke(command) ?: "I'm ready. Tell me what you need."
            }
        }

        gymPartnerVoiceAssistant?.start()
    }

    private fun stopVoiceAssistantManager() {
        gymPartnerVoiceAssistant?.release()
        gymPartnerVoiceAssistant = null
    }

    private fun setVoiceCommandHandler(handler: (String) -> String) {
        voiceCommandHandler = handler
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val prefs = getSharedPreferences("gym_partner_preferences", Context.MODE_PRIVATE)
                if (prefs.getBoolean("motivation_notifications_enabled", false) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !canUseExactMotivationAlarms(this)
                ) {
                    openExactAlarmSettings(this)
                }
            } else {
                openAppNotificationSettings(this)
            }
        }

    override fun onResume() {
        super.onResume()
        // If the user just granted Alarms & reminders access in Settings,
        // immediately replace any fallback inexact alarms with exact ones.
        val prefs = getSharedPreferences("gym_partner_preferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean("motivation_notifications_enabled", false)) {
            val hour = prefs.getInt("motivation_notification_hour", 7)
            val minute = prefs.getInt("motivation_notification_minute", 0)
            val mode = prefs.getString("motivation_repeat_mode", "daily") ?: "daily"
            val days = prefs.getStringSet(
                "motivation_selected_days",
                (Calendar.SUNDAY..Calendar.SATURDAY).map { it.toString() }.toSet()
            ).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
            scheduleMotivationNotifications(this, true, hour, minute, mode, days)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Voice input is available only when the user taps the mic in the chatbot.
        getSharedPreferences("gym_partner_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("voice_assistant_enabled", false)
            .apply()

        setContent {
            GymPartnerApp(
                onRequestCameraPermission = {
                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onVoiceAssistantToggle = { _ ->
                    // Legacy app-wide voice toggle is intentionally disabled.
                    getSharedPreferences("gym_partner_preferences", Context.MODE_PRIVATE)
                        .edit().putBoolean("voice_assistant_enabled", false).apply()
                    stopVoiceAssistantManager()
                },
                onVoiceCommandHandlerReady = { handler ->
                    setVoiceCommandHandler(handler)
                }
            )
        }
    }

    override fun onDestroy() {
        voiceAppSessionActive = false
        stopVoiceAssistantManager()
        super.onDestroy()
    }
}

// ============================================================
// COLORS
// ============================================================

val Black = Color(0xFF050505)
val DarkBlack = Color(0xFF0B0B0B)

val GymGreen = Color(0xFF55FF22)
val DarkGreen = Color(0xFF173D10)

val GymPink = Color(0xFFFF4FA3)
val DarkPink = Color(0xFF4A102D)

val White = Color(0xFFFFFFFF)
val LightGray = Color(0xFFBDBDBD)
val DarkGray = Color(0xFF242424)

// ============================================================
// STEP 13 - PROGRESS DATA
// ============================================================

data class WorkoutHistoryEntry(
    val date: String,
    val workoutName: String,
    val goal: String
)

// ============================================================
// STEP 17 - LOCAL STORAGE HELPERS
// ============================================================

private fun loadWorkoutHistory(preferences: SharedPreferences): List<WorkoutHistoryEntry> {
    val raw = preferences.getString("workout_history", "[]") ?: "[]"

    return try {
        val jsonArray = JSONArray(raw)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                add(
                    WorkoutHistoryEntry(
                        date = item.optString("date", ""),
                        workoutName = item.optString("workoutName", "Today's Workout"),
                        goal = item.optString("goal", "General Fitness")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveWorkoutHistory(
    preferences: SharedPreferences,
    history: List<WorkoutHistoryEntry>
) {
    val jsonArray = JSONArray()

    history.forEach { entry ->
        jsonArray.put(
            JSONObject().apply {
                put("date", entry.date)
                put("workoutName", entry.workoutName)
                put("goal", entry.goal)
            }
        )
    }

    preferences.edit()
        .putString("workout_history", jsonArray.toString())
        .apply()
}

// ============================================================
// STEP 14 - OFFLINE AI CHAT DATA
// ============================================================

data class AiChatMessage(
    val text: String,
    val isUser: Boolean
)

// Complete offline context that the AI can read from the app's saved state.
data class UserFitnessContext(
    val name: String,
    val age: Int,
    val ageGroup: String,
    val gender: String,
    val heightCm: Float,
    val weightKg: Float,
    val bmi: Float,
    val goal: String,
    val vegetarian: Boolean,
    val completedWorkouts: Int,
    val workoutStreak: Int,
    val lastWorkoutDate: String,
    val workoutHistory: List<WorkoutHistoryEntry>
)

// ============================================================
// MAIN APP
// ============================================================

@Composable
fun GymPartnerApp(
    onRequestCameraPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onVoiceAssistantToggle: (Boolean) -> Unit = {},
    onVoiceCommandHandlerReady: (((String) -> String)) -> Unit = {}
) {

    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("gym_partner_preferences", Context.MODE_PRIVATE)
    }

    // ------------------------------------------------------------
    // OFFLINE SESSION / PROFILE RESTORE
    // SharedPreferences survives closing the app and clearing it
    // from the recent-apps screen. It is only removed if the user
    // explicitly clears the app's storage/data.
    // ------------------------------------------------------------
    val profileComplete = remember {
        val hasName = preferences.getString("user_name", "").orEmpty().isNotBlank()
        val hasHeight = preferences.getFloat("height_cm", 0f) > 0f
        val hasWeight = preferences.getFloat("weight_kg", 0f) > 0f
        val hasAge = preferences.getInt("age", 0) > 0
        val hasGender = preferences.getString("gender", "").orEmpty().isNotBlank()
        val hasGoal = preferences.getString("selected_goal", "").orEmpty().isNotBlank()

        hasName && hasHeight && hasWeight && hasAge && hasGender && hasGoal
    }

    val savedScreen = remember {
        preferences.getString("current_screen", "").orEmpty()
    }

    val validSavedScreens = setOf(
        "workout", "ai", "diet", "camera", "progress", "profile",
        "exercise", "workout_completed", "goal_completed"
    )

    // App-start intro video.
    // The existing saved screen/profile logic is preserved and restored
    // automatically after the intro video finishes.
    val introDestination = remember {
        when {
            profileComplete -> {
                if (savedScreen in validSavedScreens) savedScreen else "workout"
            }
            savedScreen.isNotBlank() -> savedScreen
            else -> "welcome"
        }
    }

    var currentScreen by remember {
        mutableStateOf("intro")
    }

    // Every navigation change is saved, so reopening the app restores
    // the correct screen instead of starting onboarding again.
    fun navigateTo(screen: String) {
        currentScreen = screen
        preferences.edit()
            .putString("current_screen", screen)
            .apply()
    }

    // Prevent accidental double-completion from rapid taps, timer callbacks,
    // or camera callbacks firing close together. It resets when a new workout
    // screen is entered.
    var workoutCompletionHandled by remember { mutableStateOf(false) }
    LaunchedEffect(currentScreen) {
        if (currentScreen == "workout") workoutCompletionHandled = false
    }


    var userName by remember {
        mutableStateOf(preferences.getString("user_name", "") ?: "")
    }

    var profilePhotoUri by remember {
        mutableStateOf(preferences.getString("profile_photo_uri", "") ?: "")
    }

    // STEP 15 - Remember food preference offline
    var vegetarian by remember {
        mutableStateOf(preferences.getBoolean("vegetarian", false))
    }

    // STEP 13 - Progress Tracker state
    var completedWorkouts by remember {
        mutableStateOf(preferences.getInt("completed_workouts", 0))
    }

    var workoutStreak by remember {
        mutableStateOf(preferences.getInt("workout_streak", 0))
    }

    var workoutHistory by remember {
        mutableStateOf(loadWorkoutHistory(preferences))
    }

    // STEP 14 - Offline AI chat state
    var aiMessages by remember { mutableStateOf(listOf<AiChatMessage>()) }

    // AI follow-up state. Example: water question -> ask workout duration -> personalize answer.
    var aiWaitingForHydrationDuration by remember { mutableStateOf(false) }

    var heightCm by remember {
        mutableStateOf(preferences.getFloat("height_cm", 0f))
    }

    var heightUnit by remember {
        mutableStateOf(preferences.getString("height_unit", "cm") ?: "cm")
    }

    var weightKg by remember {
        mutableStateOf(preferences.getFloat("weight_kg", 0f))
    }

    var age by remember {
        mutableStateOf(preferences.getInt("age", 0))
    }

    var ageGroup by remember {
        mutableStateOf(preferences.getString("age_group", "") ?: "")
    }

    var gender by remember {
        mutableStateOf(preferences.getString("gender", "") ?: "")
    }

    // STEP 6
    var bmi by remember {
        mutableStateOf(preferences.getFloat("bmi", 0f))
    }

    // STEP 7
    var selectedGoal by remember {
        mutableStateOf(preferences.getString("selected_goal", "") ?: "")
    }

    fun completeWorkoutSafely() {
        if (workoutCompletionHandled) return
        workoutCompletionHandled = true

        preferences.edit()
            .putBoolean("profile_complete", true)
            .putString("current_screen", "workout_completed")
            .apply()

        val today = LocalDate.now()
        val todayString = today.toString()
        val lastWorkoutDate = preferences.getString("last_workout_date", "")
        val firstWorkoutDate = preferences.getString("first_workout_date", "")

        if (firstWorkoutDate.isNullOrBlank()) {
            preferences.edit()
                .putString("first_workout_date", todayString)
                .apply()
        }

        completedWorkouts += 1
        workoutStreak = when {
            lastWorkoutDate == todayString -> workoutStreak
            lastWorkoutDate == today.minusDays(1).toString() -> workoutStreak + 1
            else -> 1
        }

        // Keep local history bounded so SharedPreferences cannot grow indefinitely.
        workoutHistory = (listOf(
            WorkoutHistoryEntry(
                date = todayString,
                workoutName = "Today's Workout",
                goal = selectedGoal.ifEmpty { "General Fitness" }
            )
        ) + workoutHistory).take(100)

        preferences.edit()
            .putInt("completed_workouts", completedWorkouts)
            .putInt("workout_streak", workoutStreak)
            .putString("last_workout_date", todayString)
            .apply()

        saveWorkoutHistory(preferences, workoutHistory)
        navigateTo("workout_completed")
    }


    var voiceWaitingForHydrationDuration by remember { mutableStateOf(false) }

    val appUserContext = UserFitnessContext(
        name = userName,
        age = age,
        ageGroup = ageGroup,
        gender = gender,
        heightCm = heightCm,
        weightKg = weightKg,
        bmi = bmi,
        goal = selectedGoal,
        vegetarian = vegetarian,
        completedWorkouts = completedWorkouts,
        workoutStreak = workoutStreak,
        lastWorkoutDate = preferences.getString("last_workout_date", "") ?: "",
        workoutHistory = workoutHistory
    )

    LaunchedEffect(
        userName, age, ageGroup, gender, heightCm, weightKg, bmi, selectedGoal,
        vegetarian, completedWorkouts, workoutStreak, workoutHistory, voiceWaitingForHydrationDuration
    ) {
        onVoiceCommandHandlerReady { command ->
            val result = offlineAiReply(
                question = command,
                context = appUserContext,
                waitingForHydrationDuration = voiceWaitingForHydrationDuration
            )
            voiceWaitingForHydrationDuration = result.needsHydrationFollowUp
            result.text
        }
    }

    val primaryColor =
        if (gender == "Female") {
            GymPink
        } else {
            GymGreen
        }

    val darkAccentColor =
        if (gender == "Female") {
            DarkPink
        } else {
            DarkGreen
        }

    GymPartnerTheme(
        primaryColor = primaryColor
    ) {

        val showBottomNavigation = currentScreen in setOf(
            "workout",
            "progress",
            "ai",
            "diet",
            "more"
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (currentScreen) {

                    // =================================================
                    // APP START INTRO VIDEO
                    // =================================================

                    "intro" -> {
                        IntroVideoScreen(
                            onFinished = {
                                navigateTo(introDestination)
                            }
                        )
                    }

                    // =================================================
                    // STEP 1
                    // =================================================

                    "welcome" -> {

                        WelcomeScreen(
                            primaryColor = primaryColor,
                            onGetStarted = {
                                navigateTo("name")
                            }
                        )
                    }

                    "name" -> {

                        NameScreen(
                            primaryColor = primaryColor,
                            onNext = { name ->

                                userName = name
                                preferences.edit().putString("user_name", name).apply()
                                navigateTo("name_completed")
                            }
                        )
                    }

                    "name_completed" -> {

                        NameCompletedScreen(
                            name = userName,
                            primaryColor = primaryColor,
                            onContinue = {
                                navigateTo("height")
                            }
                        )
                    }

                    // =================================================
                    // STEP 2
                    // =================================================

                    "height" -> {

                        HeightScreen(
                            primaryColor = primaryColor,
                            onNext = { value, unit ->

                                heightUnit = unit

                                heightCm =
                                    if (unit == "feet") {
                                        value * 30.48f
                                    } else {
                                        value
                                    }

                                preferences.edit()
                                    .putFloat("height_cm", heightCm)
                                    .putString("height_unit", heightUnit)
                                    .apply()

                                navigateTo("height_completed")
                            }
                        )
                    }

                    "height_completed" -> {

                        HeightCompletedScreen(
                            heightCm = heightCm,
                            heightUnit = heightUnit,
                            primaryColor = primaryColor,
                            onContinue = {
                                navigateTo("weight")
                            }
                        )
                    }

                    // =================================================
                    // STEP 3
                    // =================================================

                    "weight" -> {

                        WeightScreen(
                            primaryColor = primaryColor,
                            onNext = { weight ->

                                weightKg = weight
                                preferences.edit().putFloat("weight_kg", weightKg).apply()
                                navigateTo("weight_completed")
                            }
                        )
                    }

                    "weight_completed" -> {

                        WeightCompletedScreen(
                            weight = weightKg,
                            primaryColor = primaryColor,
                            onContinue = {
                                navigateTo("age")
                            }
                        )
                    }

                    // =================================================
                    // STEP 4
                    // =================================================

                    "age" -> {

                        AgeScreen(
                            primaryColor = primaryColor,
                            onNext = { enteredAge ->

                                age = enteredAge

                                ageGroup = when {
                                    enteredAge < 18 -> "Child"
                                    enteredAge <= 60 -> "Adult"
                                    else -> "Old"
                                }

                                preferences.edit()
                                    .putInt("age", age)
                                    .putString("age_group", ageGroup)
                                    .apply()

                                navigateTo("age_completed")
                            }
                        )
                    }

                    "age_completed" -> {

                        AgeCompletedScreen(
                            age = age,
                            ageGroup = ageGroup,
                            primaryColor = primaryColor,
                            onContinue = {
                                navigateTo("gender")
                            }
                        )
                    }

                    // =================================================
                    // STEP 5
                    // =================================================

                    "gender" -> {

                        GenderScreen(
                            primaryColor = primaryColor,
                            selectedGender = gender,
                            onGenderSelected = { selected ->

                                gender = selected
                                preferences.edit().putString("gender", gender).apply()
                            },
                            onContinue = {

                                if (gender.isNotEmpty()) {
                                    navigateTo("gender_completed")
                                }
                            }
                        )
                    }

                    "gender_completed" -> {

                        GenderCompletedScreen(
                            gender = gender,
                            primaryColor = primaryColor,
                            darkAccentColor = darkAccentColor,
                            onContinue = {

                                navigateTo("bmi")
                            }
                        )
                    }

                    // =================================================
                    // STEP 6
                    // =================================================

                    "bmi" -> {

                        BmiScreen(
                            name = userName,
                            age = age,
                            ageGroup = ageGroup,
                            heightCm = heightCm,
                            weightKg = weightKg,
                            primaryColor = primaryColor,
                            onContinue = { calculatedBmi ->

                                bmi = calculatedBmi
                                preferences.edit().putFloat("bmi", bmi).apply()
                                navigateTo("bmi_completed")
                            }
                        )
                    }

                    "bmi_completed" -> {

                        BmiCompletedScreen(
                            name = userName,
                            age = age,
                            ageGroup = ageGroup,
                            bmi = bmi,
                            primaryColor = primaryColor,
                            onContinue = {

                                // STEP 7 COMES NEXT
                                navigateTo("goal")
                            }
                        )
                    }

                    // =================================================
                    // STEP 7 - GOAL SELECTION
                    // =================================================

                    "goal" -> {

                        GoalScreen(
                            name = userName,
                            ageGroup = ageGroup,
                            primaryColor = primaryColor,
                            selectedGoal = selectedGoal,
                            onGoalSelected = { goal ->
                                selectedGoal = goal
                                preferences.edit().putString("selected_goal", goal).apply()
                            },
                            onContinue = {
                                if (selectedGoal.isNotEmpty()) {
                                    navigateTo("goal_completed")
                                }
                            }
                        )
                    }

                    "goal_completed" -> {

                        GoalCompletedScreen(
                            name = userName,
                            ageGroup = ageGroup,
                            goal = selectedGoal,
                            primaryColor = primaryColor,
                            onContinue = {
                                preferences.edit()
                                    .putBoolean("profile_complete", true)
                                    .apply()
                                navigateTo("exercise")
                            }
                        )
                    }

                    // =================================================
                    // STEP 8 - EXERCISE RECOMMENDATION
                    // =================================================

                    "exercise" -> {

                        ExerciseRecommendationScreen(
                            name = userName,
                            age = age,
                            ageGroup = ageGroup,
                            gender = gender,
                            goal = selectedGoal,
                            primaryColor = primaryColor,
                            onContinue = {
                                // STEP 9 COMES NEXT
                                navigateTo("workout")
                            }
                        )
                    }

                    // =================================================
                    // STEP 9 - TODAY'S WORKOUT
                    // =================================================

                    "workout" -> {

                        TodaysWorkoutScreen(
                            name = userName,
                            ageGroup = ageGroup,
                            goal = selectedGoal,
                            primaryColor = primaryColor,
                            onRequestCameraPermission = onRequestCameraPermission,
                            onComplete = {
                                completeWorkoutSafely()
                            }
                        )
                    }

                    // =================================================
                    // STEP 10 - FRONT CAMERA + SPLIT SCREEN
                    // =================================================

                    "camera" -> {

                        CameraWorkoutScreen(
                            name = userName,
                            primaryColor = primaryColor,
                            onRequestCameraPermission = onRequestCameraPermission,
                            onFinishCamera = {
                                // STEP 11 - FINAL WORKOUT COMPLETION
                                completeWorkoutSafely()
                            }
                        )
                    }

                    // =================================================
                    // STEP 11 - WORKOUT COMPLETION
                    // =================================================

                    "workout_completed" -> {

                        WorkoutCompletedScreen(
                            name = userName,
                            goal = selectedGoal,
                            primaryColor = primaryColor,
                            onContinue = {
                                // STEP 13 - Progress Tracker
                                navigateTo("progress")
                            }
                        )
                    }
                    // =================================================
                    // STEP 13 - PROGRESS TRACKER + STREAK + BADGES
                    // =================================================

                    "progress" -> {
                        ProgressTrackerScreen(
                            name = userName,
                            goal = selectedGoal,
                            gender = gender,
                            completedWorkouts = completedWorkouts,
                            workoutStreak = workoutStreak,
                            workoutHistory = workoutHistory,
                            primaryColor = primaryColor,
                            onContinue = {
                                navigateTo("ai")
                            }
                        )
                    }

                    // =================================================
                    // IMPROVED PROGRESS TRACKER - opened from More
                    // =================================================

                    "improved_progress" -> {
                        ImprovedProgressTrackerScreen(
                            name = userName,
                            goal = selectedGoal,
                            gender = gender,
                            completedWorkouts = completedWorkouts,
                            workoutStreak = workoutStreak,
                            workoutHistory = workoutHistory,
                            primaryColor = primaryColor,
                            onBack = { navigateTo("more") }
                        )
                    }

                    "ai" -> {
                        OfflineAiChatScreen(
                            name = userName,
                            age = age,
                            ageGroup = ageGroup,
                            gender = gender,
                            heightCm = heightCm,
                            weightKg = weightKg,
                            bmi = bmi,
                            goal = selectedGoal,
                            vegetarian = vegetarian,
                            completedWorkouts = completedWorkouts,
                            workoutStreak = workoutStreak,
                            lastWorkoutDate = preferences.getString("last_workout_date", "") ?: "",
                            workoutHistory = workoutHistory,
                            onVegetarianChanged = { value ->
                                vegetarian = value
                                preferences.edit().putBoolean("vegetarian", value).apply()
                            },
                            primaryColor = primaryColor,
                            messages = aiMessages,
                            onMessagesChanged = { aiMessages = it },
                            waitingForHydrationDuration = aiWaitingForHydrationDuration,
                            onWaitingForHydrationDurationChanged = { aiWaitingForHydrationDuration = it },
                            onContinue = { navigateTo("diet") },
                            onStartWorkout = { navigateTo("workout") }
                        )
                    }

                    "diet" -> {
                        DailyDietPlannerScreen(
                            name = userName,
                            age = age,
                            ageGroup = ageGroup,
                            goal = selectedGoal,
                            vegetarian = vegetarian,
                            onVegetarianChanged = { value ->
                                vegetarian = value
                                preferences.edit().putBoolean("vegetarian", value).apply()
                            },
                            primaryColor = primaryColor
                        )
                    }

                    "demo" -> {
                        DemoModeScreen(
                            primaryColor = primaryColor,
                            onExitDemo = { navigateTo("more") },
                            onOpenFeature = { screen -> navigateTo(screen) }
                        )
                    }

                    "more" -> {
                        MoreScreen(
                            name = userName.ifBlank { "Gym Partner" },
                            profilePhotoUri = profilePhotoUri,
                            age = age,
                            heightCm = heightCm,
                            weightKg = weightKg,
                            completedWorkouts = completedWorkouts,
                            workoutStreak = workoutStreak,
                            goal = selectedGoal,
                            primaryColor = primaryColor,
                            preferences = preferences,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onVoiceAssistantToggle = onVoiceAssistantToggle,
                            onNavigate = { screen -> navigateTo(screen) },
                            onProfileUpdated = { newName, newAge, newHeightCm, newWeightKg, newGoal ->
                                userName = newName
                                age = newAge
                                ageGroup = when {
                                    newAge < 18 -> "Child"
                                    newAge <= 60 -> "Adult"
                                    else -> "Old"
                                }
                                heightCm = newHeightCm
                                weightKg = newWeightKg
                                selectedGoal = newGoal
                                bmi = if (newHeightCm > 0f && newWeightKg > 0f) {
                                    newWeightKg / ((newHeightCm / 100f) * (newHeightCm / 100f))
                                } else bmi
                                preferences.edit()
                                    .putString("user_name", userName)
                                    .putInt("age", age)
                                    .putString("age_group", ageGroup)
                                    .putFloat("height_cm", heightCm)
                                    .putFloat("weight_kg", weightKg)
                                    .putFloat("bmi", bmi)
                                    .putString("selected_goal", selectedGoal)
                                    .apply()
                            },
                            onProfilePhotoChanged = { uri ->
                                profilePhotoUri = uri
                                preferences.edit().putString("profile_photo_uri", uri).apply()
                            }
                        )
                    }

                    else -> {
                        navigateTo("welcome")
                    }
                }

                // Back arrow is shown only while collecting the user's initial profile data.
                // After login/onboarding is complete, the main modules use bottom navigation instead.
                val showInitialDataBackButton = currentScreen in setOf(
                    "name", "name_completed",
                    "height", "height_completed",
                    "weight", "weight_completed",
                    "age", "age_completed",
                    "gender", "gender_completed",
                    "bmi", "bmi_completed",
                    "goal", "goal_completed"
                )
                if (showInitialDataBackButton) {
                    val previousScreen = when (currentScreen) {
                        "name" -> "welcome"
                        "name_completed" -> "name"
                        "height" -> "name_completed"
                        "height_completed" -> "height"
                        "weight" -> "height_completed"
                        "weight_completed" -> "weight"
                        "age" -> "weight_completed"
                        "age_completed" -> "age"
                        "gender" -> "age_completed"
                        "gender_completed" -> "gender"
                        "bmi" -> "gender_completed"
                        "bmi_completed" -> "bmi"
                        "goal" -> "bmi_completed"
                        "goal_completed" -> "goal"
                        "exercise" -> "goal_completed"
                        "workout" -> "exercise"
                        "camera" -> "workout"
                        "workout_completed" -> "workout"
                        "progress" -> "workout_completed"
                        "ai" -> "progress"
                        "diet" -> "ai"
                        "more" -> "diet"
                        else -> "welcome"
                    }

                    Button(
                        onClick = { navigateTo(previousScreen) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            // Move the complete back button about 1 cm left and 1 cm down.
                            // 1 cm is approximately 38 dp on the Compose density-independent scale.
                            .offset(x = 19.dp, y = 19.dp)
                            // Large touch target so one-finger taps are easy.
                            .size(64.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Black.copy(alpha = 0.82f),
                            contentColor = primaryColor
                        )
                    ) {
                        Text(
                            text = "←",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (showBottomNavigation) {
                    GymPartnerBottomNavigation(
                        currentScreen = currentScreen,
                        primaryColor = primaryColor,
                        onHomeClick = { navigateTo("workout") },
                        onAiClick = { navigateTo("ai") },
                        onDietClick = { navigateTo("diet") },
                        onMoreClick = { navigateTo("more") }
                    )
                }
            }
        }
    }
}

// ============================================================
// STEP 16 - BOTTOM NAVIGATION
// ============================================================

@Composable
fun GymPartnerBottomNavigation(
    currentScreen: String,
    primaryColor: Color,
    onHomeClick: () -> Unit,
    onAiClick: () -> Unit,
    onDietClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    // FINAL NAVIGATION: fully invisible/transparent bar overlay.
    // Only the four navigation icons remain visible. No background, border,
    // selected pill, or text is drawn, and the bar overlays the screen.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Modifier.weight(1f), "⌂", currentScreen == "workout", primaryColor, onHomeClick)
            BottomNavItem(Modifier.weight(1f), "🤖", currentScreen == "ai", primaryColor, onAiClick)
            BottomNavItem(Modifier.weight(1f), "🍴", currentScreen == "diet", primaryColor, onDietClick)
            BottomNavItem(Modifier.weight(1f), "•••", currentScreen == "more", primaryColor, onMoreClick)
        }
    }
}

@Composable
fun BottomNavItem(
    modifier: Modifier = Modifier,
    icon: String,
    selected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (selected) primaryColor else White
            )
        ) {
            Text(
                text = icon,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// THEME
// ============================================================

@Composable
fun GymPartnerTheme(
    primaryColor: Color,
    content: @Composable () -> Unit
) {

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = primaryColor,
            background = Black,
            surface = DarkBlack,
            onPrimary = Black,
            onBackground = White,
            onSurface = White
        ),
        content = content
    )
}

// ============================================================
// APP START INTRO VIDEO
// ============================================================

@Composable
fun IntroVideoScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                VideoView(context).apply {
                    setVideoURI(
                        "android.resource://${context.packageName}/${R.raw.intro_gym_partner}".toUri()
                    )

                    setOnCompletionListener {
                        onFinished()
                    }

                    setOnErrorListener { _, _, _ ->
                        // If the video cannot be played, do not block the app.
                        onFinished()
                        true
                    }

                    start()
                }
            },
            update = { }
        )
    }
}

// ============================================================
// STEP 1 - WELCOME
// ============================================================

@Composable
fun WelcomeScreen(
    primaryColor: Color,
    onGetStarted: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(DarkGray)
                .border(
                    2.dp,
                    primaryColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "GYM",
                    color = primaryColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "PARTNER",
                    color = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "AI",
                    color = primaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Gym Partner",
            color = White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your AI Fitness Partner",
            color = primaryColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Train smarter. Stay consistent. Become stronger.",
            color = LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Get Started",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// NAME
// ============================================================

@Composable
fun NameScreen(
    primaryColor: Color,
    onNext: (String) -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = "Welcome to Gym Partner",
            color = White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Let's know more about you",
            color = LightGray,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(45.dp))

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("Your Name")
            },
            placeholder = {
                Text("Enter your name")
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = DarkGray,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor,
                focusedTextColor = White,
                unfocusedTextColor = White
            )
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                if (name.trim().isNotEmpty()) {
                    onNext(name.trim())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Next",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// NAME COMPLETED
// ============================================================

@Composable
fun NameCompletedScreen(
    name: String,
    primaryColor: Color,
    onContinue: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Hi, $name! 👋",
            color = primaryColor,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = "Your Gym Partner journey starts here.",
            color = LightGray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// HEIGHT
// ============================================================

@Composable
fun HeightScreen(
    primaryColor: Color,
    onNext: (Float, String) -> Unit
) {

    var height by remember {
        mutableStateOf("")
    }

    var unit by remember {
        mutableStateOf("cm")
    }

    val value = height.toFloatOrNull()

    val isValid =
        if (unit == "cm") {
            value != null && value in 50f..250f
        } else {
            value != null && value in 1.6f..8.2f
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(65.dp))

        Text(
            text = "What is your Height?",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(35.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            horizontalArrangement = Arrangement.Center
        ) {

            UnitButton(
                text = "CM",
                selected = unit == "cm",
                primaryColor = primaryColor,
                onClick = {
                    unit = "cm"
                    height = ""
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            UnitButton(
                text = "Feet",
                selected = unit == "feet",
                primaryColor = primaryColor,
                onClick = {
                    unit = "feet"
                    height = ""
                }
            )
        }

        Spacer(modifier = Modifier.height(35.dp))

        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(CircleShape)
                .background(DarkGray)
                .border(
                    2.dp,
                    primaryColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = if (height.isEmpty()) "0" else height,
                    color = primaryColor,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (unit == "cm") "cm" else "ft",
                    color = LightGray,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(35.dp))

        OutlinedTextField(
            value = height,
            onValueChange = {
                height = it
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(
                    if (unit == "cm")
                        "Height in cm"
                    else
                        "Height in feet"
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = DarkGray,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor,
                focusedTextColor = White,
                unfocusedTextColor = White
            )
        )

        Spacer(modifier = Modifier.height(25.dp))

        Button(
            onClick = {
                if (isValid && value != null) {
                    onNext(value, unit)
                }
            },
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black,
                disabledContainerColor = DarkGray,
                disabledContentColor = LightGray
            )
        ) {

            Text(
                text = "Next",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// UNIT BUTTON
// ============================================================

@Composable
fun UnitButton(
    text: String,
    selected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (selected) primaryColor
                else DarkGray,
            contentColor =
                if (selected) Black
                else White
        )
    ) {

        Text(
            text = text,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================
// HEIGHT COMPLETED
// ============================================================

@Composable
fun HeightCompletedScreen(
    heightCm: Float,
    heightUnit: String,
    primaryColor: Color,
    onContinue: () -> Unit
) {

    val displayHeight =
        if (heightUnit == "cm") {
            "${heightCm.toInt()} cm"
        } else {
            String.format(
                Locale.getDefault(),
                "%.1f ft",
                heightCm / 30.48f
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Height Saved ✓",
            color = primaryColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = displayHeight,
            color = White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Approx. ${heightCm.toInt()} cm",
            color = LightGray,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// WEIGHT
// ============================================================

@Composable
fun WeightScreen(
    primaryColor: Color,
    onNext: (Float) -> Unit
) {

    var weight by remember {
        mutableStateOf("")
    }

    val value = weight.toFloatOrNull()

    val isValid =
        value != null && value in 10f..300f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(70.dp))

        Text(
            text = "What is your Weight?",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(35.dp))

        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(CircleShape)
                .background(DarkGray)
                .border(
                    2.dp,
                    primaryColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = if (weight.isEmpty()) "0" else weight,
                    color = primaryColor,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "kg",
                    color = LightGray,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(35.dp))

        OutlinedTextField(
            value = weight,
            onValueChange = {
                weight = it
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("Weight in kg")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = DarkGray,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor,
                focusedTextColor = White,
                unfocusedTextColor = White
            )
        )

        Spacer(modifier = Modifier.height(25.dp))

        Button(
            onClick = {
                if (isValid && value != null) {
                    onNext(value)
                }
            },
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black,
                disabledContainerColor = DarkGray,
                disabledContentColor = LightGray
            )
        ) {

            Text(
                text = "Next",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// WEIGHT COMPLETED
// ============================================================

@Composable
fun WeightCompletedScreen(
    weight: Float,
    primaryColor: Color,
    onContinue: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Weight Saved ✓",
            color = primaryColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "${weight.toInt()} kg",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Perfect! We have your weight.",
            color = LightGray,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// AGE
// ============================================================

@Composable
fun AgeScreen(
    primaryColor: Color,
    onNext: (Int) -> Unit
) {

    var ageText by remember {
        mutableStateOf("")
    }

    val enteredAge = ageText.toIntOrNull()

    val isValid =
        enteredAge != null && enteredAge in 1..120

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(65.dp))

        Text(
            text = "What is your Age?",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "This helps Gym Partner personalize your plan.",
            color = LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(CircleShape)
                .background(DarkGray)
                .border(
                    2.dp,
                    primaryColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text =
                        if (ageText.isEmpty())
                            "0"
                        else
                            ageText,
                    color = primaryColor,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "years",
                    color = LightGray,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(35.dp))

        OutlinedTextField(
            value = ageText,
            onValueChange = {

                if (it.length <= 3) {

                    ageText =
                        it.filter { char ->
                            char.isDigit()
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("Age")
            },
            placeholder = {
                Text("Enter your age")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = DarkGray,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor,
                focusedTextColor = White,
                unfocusedTextColor = White
            )
        )

        Spacer(modifier = Modifier.height(25.dp))

        Button(
            onClick = {
                if (isValid && enteredAge != null) {
                    onNext(enteredAge)
                }
            },
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black,
                disabledContainerColor = DarkGray,
                disabledContentColor = LightGray
            )
        ) {

            Text(
                text = "Next",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// AGE COMPLETED
// ============================================================

@Composable
fun AgeCompletedScreen(
    age: Int,
    ageGroup: String,
    primaryColor: Color,
    onContinue: () -> Unit
) {

    val groupDescription =
        when (ageGroup) {

            "Child" ->
                "Healthy movement and fitness activities"

            "Adult" ->
                "Personalized fitness and strength guidance"

            "Old" ->
                "Gentle movement, mobility and yoga guidance"

            else ->
                "Personalized fitness guidance"
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Age Saved ✓",
            color = primaryColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "$age years",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = ageGroup,
            color = primaryColor,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = groupDescription,
            color = LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// GENDER
// ============================================================

@Composable
fun GenderScreen(
    primaryColor: Color,
    selectedGender: String,
    onGenderSelected: (String) -> Unit,
    onContinue: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(55.dp))

        Text(
            text = "Select your Gender",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "This helps us personalize your Gym Partner experience.",
            color = LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(30.dp))

        GenderCard(
            title = "Male",
            imageRes = R.drawable.male_gym,
            selected = selectedGender == "Male",
            selectedColor = GymGreen,
            onClick = {
                onGenderSelected("Male")
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        GenderCard(
            title = "Female",
            imageRes = R.drawable.female_gym,
            selected = selectedGender == "Female",
            selectedColor = GymPink,
            onClick = {
                onGenderSelected("Female")
            }
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onContinue,
            enabled = selectedGender.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black,
                disabledContainerColor = DarkGray,
                disabledContentColor = LightGray
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// GENDER CARD
// ============================================================

@Composable
fun GenderCard(
    title: String,
    imageRes: Int,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {

    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(cardShape)
            .clickable {
                onClick()
            }
            .then(
                if (selected) {
                    Modifier.border(
                        width = 3.dp,
                        color = selectedColor,
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
    ) {

        Image(
            painter = painterResource(id = imageRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            alignment = if (title == "Female") {
                Alignment.TopCenter
            } else {
                Alignment.Center
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = 0.42f)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 22.dp,
                    end = 18.dp,
                    top = 18.dp,
                    bottom = 18.dp
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    text = title,
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (title == "Male")
                        "Let's build strength"
                    else
                        "Let's build confidence",
                    color = White.copy(alpha = 0.82f),
                    fontSize = 13.sp
                )
            }

            if (selected) {

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(selectedColor)
                        .padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        )
                ) {

                    Text(
                        text = "✓ Selected",
                        color = Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


// ============================================================
// GENDER COMPLETED
// ============================================================

@Composable
fun GenderCompletedScreen(
    gender: String,
    primaryColor: Color,
    darkAccentColor: Color,
    onContinue: () -> Unit
) {

    val genderEmoji =
        if (gender == "Female") "👩" else "👨"

    val themeText =
        if (gender == "Female")
            "Pink + Black theme activated"
        else
            "Green + Black theme activated"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(darkAccentColor)
                .border(
                    2.dp,
                    primaryColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = genderEmoji,
                fontSize = 50.sp
            )
        }

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "Gender Saved ✓",
            color = primaryColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = gender,
            color = White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = themeText,
            color = LightGray,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// STEP 6 - BMI SCREEN
// ============================================================

@Composable
fun BmiScreen(
    name: String,
    age: Int,
    ageGroup: String,
    heightCm: Float,
    weightKg: Float,
    primaryColor: Color,
    onContinue: (Float) -> Unit
) {

    val heightMeters = heightCm / 100f

    val calculatedBmi =
        if (heightMeters > 0f) {
            weightKg / (heightMeters * heightMeters)
        } else {
            0f
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(55.dp))

        Text(
            text = "Your BMI",
            color = White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Gym Partner AI is checking your profile.",
            color = LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(35.dp))

        Box(
            modifier = Modifier
                .size(175.dp)
                .clip(CircleShape)
                .background(DarkGray)
                .border(
                    width = 3.dp,
                    color = primaryColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%.1f",
                        calculatedBmi
                    ),
                    color = primaryColor,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "BMI",
                    color = LightGray,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Hi, $name 👋",
            color = primaryColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Age: $age  •  Group: $ageGroup",
            color = LightGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "BMI is a general screening measure. " +
                    "It does not by itself determine your health or fitness.",
            color = LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(35.dp))

        Button(
            onClick = {
                onContinue(calculatedBmi)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "See AI Suggestion",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// STEP 6 - BMI COMPLETED / AI SUGGESTION
// ============================================================

@Composable
fun BmiCompletedScreen(
    name: String,
    age: Int,
    ageGroup: String,
    bmi: Float,
    primaryColor: Color,
    onContinue: () -> Unit
) {

    val suggestion = when (ageGroup) {

        "Child" -> {
            "Hi $name! Your focus should be healthy movement, " +
                    "fun physical activities, good sleep and balanced meals. " +
                    "There is no need to chase a BMI target."
        }

        "Adult" -> {

            when {
                bmi < 18.5f ->
                    "Hi $name! Focus on balanced meals, regular movement " +
                            "and gradual strength development."

                bmi < 25f ->
                    "Hi $name! Your profile can focus on regular exercise, " +
                            "strength, mobility and consistent healthy habits."

                bmi < 30f ->
                    "Hi $name! Focus on regular movement, strength training " +
                            "and balanced meals. Consistency matters more than quick changes."

                else ->
                    "Hi $name! Start with comfortable regular movement, " +
                            "gradual fitness progress and balanced meals. " +
                            "A healthcare professional can help interpret BMI when needed."
            }
        }

        "Old" -> {

            "Hi $name! Focus on comfortable movement, mobility, balance, " +
                    "light strength work and activities that feel safe for you."
        }

        else -> {

            "Hi $name! Focus on regular movement, balanced meals and healthy habits."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(95.dp)
                .clip(CircleShape)
                .background(
                    primaryColor.copy(alpha = 0.15f)
                )
                .border(
                    2.dp,
                    primaryColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "AI",
                color = primaryColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "GYM PARTNER AI",
            color = primaryColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your personalized suggestion",
            color = White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(25.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    DarkGray,
                    RoundedCornerShape(18.dp)
                )
                .padding(20.dp)
        ) {

            Text(
                text = suggestion,
                color = White,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "BMI: ${
                String.format(
                    Locale.getDefault(),
                    "%.1f",
                    bmi
                )
            }",
            color = primaryColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Age: $age",
            color = LightGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {

            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// STEP 7 - GOAL SELECTION
// ============================================================

@Composable
fun GoalScreen(
    name: String,
    ageGroup: String,
    primaryColor: Color,
    selectedGoal: String,
    onGoalSelected: (String) -> Unit,
    onContinue: () -> Unit
) {

    val goals = when (ageGroup) {
        "Child" -> listOf(
            "Stay Active",
            "Improve Fitness",
            "Improve Mobility"
        )

        "Old" -> listOf(
            "Improve Mobility",
            "Stay Active",
            "Build Strength"
        )

        else -> listOf(
            "Improve Fitness",
            "Build Strength",
            "Improve Mobility",
            "Stay Active"
        )
    }

    val recommendation = when (ageGroup) {
        "Child" ->
            "Recommended: Stay Active — focus on fun movement, coordination and healthy activity."

        "Old" ->
            "Recommended: Improve Mobility — focus on comfortable movement, balance and gentle strength."

        else ->
            "Recommended: Improve Fitness — build consistent exercise, strength and healthy movement habits."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(55.dp))

        Text(
            text = "Choose Your Goal",
            color = White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hi, $name 👋",
            color = primaryColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    primaryColor.copy(alpha = 0.12f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    primaryColor.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = recommendation,
                color = White,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        goals.forEach { goal ->

            Button(
                onClick = {
                    onGoalSelected(goal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        if (selectedGoal == goal)
                            primaryColor.copy(alpha = 0.18f)
                        else
                            DarkGray,
                    contentColor =
                        if (selectedGoal == goal)
                            primaryColor
                        else
                            White
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = goal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (selectedGoal == goal) {
                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "✓",
                            color = primaryColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            enabled = selectedGoal.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black,
                disabledContainerColor = DarkGray,
                disabledContentColor = LightGray
            )
        ) {
            Text(
                text = "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ============================================================
// STEP 7 - GOAL COMPLETED
// ============================================================

@Composable
fun GoalCompletedScreen(
    name: String,
    ageGroup: String,
    goal: String,
    primaryColor: Color,
    onContinue: () -> Unit
) {

    val message = when (ageGroup) {
        "Child" ->
            "Great choice! Your plan will focus on fun, age-appropriate movement and healthy activity."

        "Old" ->
            "Great choice! Your plan will focus on comfortable movement, mobility, balance and gentle strength."

        else ->
            "Great choice! Your exercise recommendations will be personalized around your selected goal."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.15f))
                .border(2.dp, primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = primaryColor,
                fontSize = 45.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Goal Selected",
            color = primaryColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = goal,
            color = White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Hi $name! $message",
            color = LightGray,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {
            Text(
                text = "See Exercises",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ============================================================
// STEP 8 - EXERCISE RECOMMENDATION
// ============================================================

@Composable
fun ExerciseRecommendationScreen(
    name: String,
    age: Int,
    ageGroup: String,
    gender: String,
    goal: String,
    primaryColor: Color,
    onContinue: () -> Unit
) {

    val exercises = when {
        ageGroup == "Child" -> listOf(
            "Walking / Light Jogging",
            "Bodyweight Squats",
            "Wall Push-ups",
            "Jumping Jacks",
            "Stretching & Mobility"
        )

        ageGroup == "Old" -> listOf(
            "Comfortable Walking",
            "Chair Squats",
            "Wall Push-ups",
            "Calf Raises",
            "Gentle Mobility"
        )

        goal == "Build Strength" -> listOf(
            "Bodyweight Squats",
            "Push-ups",
            "Glute Bridges",
            "Reverse Lunges",
            "Plank"
        )

        goal == "Improve Mobility" -> listOf(
            "Shoulder Mobility",
            "Hip Mobility",
            "Cat-Cow Stretch",
            "Bodyweight Squats",
            "Gentle Full-body Stretch"
        )

        goal == "Stay Active" -> listOf(
            "Brisk Walking",
            "Bodyweight Squats",
            "Wall Push-ups",
            "Glute Bridges",
            "Light Stretching"
        )

        else -> listOf(
            "Bodyweight Squats",
            "Push-ups",
            "Glute Bridges",
            "Reverse Lunges",
            "Plank"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(45.dp))

        Text(
            text = "Recommended Exercises",
            color = White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "GYM PARTNER AI",
            color = primaryColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Personalized for $name • Age $age • $ageGroup",
            color = LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Goal: $goal",
            color = primaryColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Profile: $gender",
            color = LightGray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        exercises.forEachIndexed { index, exercise ->

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        DarkGray,
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        if (index == 0)
                            primaryColor.copy(alpha = 0.7f)
                        else
                            Color.Transparent,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 15.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.15f))
                            .border(
                                1.dp,
                                primaryColor,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(15.dp))

                    Text(
                        text = exercise,
                        color = White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Start comfortably and use good form. Stop if an exercise causes pain or feels unsafe.",
            color = LightGray,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {
            Text(
                text = "Continue to Workout",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}




// ============================================================
// STEP 10 - FRONT CAMERA + SPLIT SCREEN
// ============================================================

@Composable
fun CameraWorkoutScreen(
    name: String,
    primaryColor: Color,
    onRequestCameraPermission: () -> Unit,
    onFinishCamera: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Camera can now be turned ON/OFF without leaving the workout screen.
    var cameraEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            onRequestCameraPermission()
        }
    }

    // Re-check permission after the Android permission dialog.
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            delay(500)
            hasCameraPermission =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp)
    ) {
        Text(
            text = "Camera Workout",
            color = White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Hi, $name! Use the camera as a mirror.",
            color = primaryColor,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // TOP HALF - EXERCISE + TIMER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkBlack)
                .border(
                    2.dp,
                    primaryColor,
                    RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏋️",
                    fontSize = 58.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "YOUR EXERCISE",
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Workout Timer",
                    color = White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "30s",
                    color = primaryColor,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Follow your selected workout",
                    color = LightGray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Camera ON/OFF control.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (cameraEnabled) "📷 Camera ON" else "📷 Camera OFF",
                    color = if (cameraEnabled) primaryColor else LightGray,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (cameraEnabled) "Mirror is active" else "Camera is stopped; workout can continue",
                    color = LightGray,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { cameraEnabled = !cameraEnabled },
                enabled = hasCameraPermission,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (cameraEnabled) primaryColor else DarkGray,
                    contentColor = if (cameraEnabled) Black else White,
                    disabledContainerColor = DarkGray,
                    disabledContentColor = LightGray
                )
            ) {
                Text(
                    text = if (cameraEnabled) "TURN OFF" else "TURN ON",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // BOTTOM HALF - FRONT CAMERA MIRROR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkGray)
                .border(
                    2.dp,
                    primaryColor,
                    RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission && cameraEnabled) {
                FrontCameraPreview(
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Black)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "FRONT CAMERA • ON",
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (hasCameraPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(text = "📷", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camera is OFF",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your workout can continue without the camera.",
                        color = LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "📷",
                        fontSize = 48.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Camera permission is needed",
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You can allow camera access to use this screen as a mirror.",
                        color = LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onRequestCameraPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Black
                        )
                    ) {
                        Text(
                            text = "Allow Camera",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onFinishCamera,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {
            Text(
                text = "Finish Workout",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Camera is optional. With permission, live pose skeleton and rep tracking run on-device. Form tips are estimates; stop if you feel pain.",
            color = LightGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FrontCameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )

    DisposableEffect(previewView, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (!disposed) {
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    } catch (_: Exception) {
                        // Camera may be unavailable on an emulator/device.
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            disposed = true
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // Ignore cleanup errors when the camera provider is unavailable.
            }
        }
    }
}

data class PoseWorkoutResult(
    val reps: Int = 0,
    val kneeAngle: Float = 0f,
    val feedback: String = "Move your full body into the camera",
    val personDetected: Boolean = false,
    val formScore: Int = 0,
    val backAngle: Float = 0f,
    val depthGood: Boolean = false,
    val skeleton: List<SkeletonPoint> = emptyList()
)

data class SkeletonPoint(val landmarkType: Int, val x: Float, val y: Float)

private class ExercisePoseAnalyzer(private val exerciseName: String) {

    /*
     * REAL POSE-BASED REP COUNTER
     *
     * ML Kit supplies body landmarks. We convert those landmarks into:
     *   1) joint angles, and
     *   2) scale-normalized distances / vertical positions.
     *
     * A repetition is accepted only after a complete cycle:
     *   START -> DOWN -> START
     *
     * This replaces the old presentation/demo timer counting. Reps are now
     * driven by the person's actual movement in the camera frame.
     */
    private enum class Phase { WAITING_FOR_START, DOWN }

    private var phase = Phase.WAITING_FOR_START
    private var repCount = 0

    private val metricHistory = ArrayDeque<Float>()
    private val distanceHistory = ArrayDeque<Float>()
    private val backHistory = ArrayDeque<Float>()

    private var downFrames = 0
    private var upFrames = 0
    private var lastRepAt = 0L

    // A short lock prevents camera jitter from producing two reps at the same
    // instant, while still allowing normal exercise speed.
    private val repCooldownMs = 450L

    fun reset() {
        phase = Phase.WAITING_FOR_START
        repCount = 0
        metricHistory.clear()
        distanceHistory.clear()
        backHistory.clear()
        downFrames = 0
        upFrames = 0
        lastRepAt = 0L
    }

    private fun matches(value: String): Boolean =
        exerciseName.contains(value, ignoreCase = true)

    private fun isPushUp() = matches("push-up") || matches("pushup")
    private fun isSquat() = matches("squat")
    private fun isLunge() = matches("lunge")
    private fun isGluteBridge() = matches("glute bridge") || matches("glute bridges")
    private fun isCalfRaise() = matches("calf raise") || matches("calf raises")
    private fun isJumpingJack() = matches("jumping jack") || matches("jumping jacks")
    private fun isBicep() = matches("bicep") || matches("biceps") || matches("curl")
    private fun isShoulderPress() = matches("shoulder press") || matches("shoulder presses")

    private fun usable(l: PoseLandmark?): Boolean =
        l != null && l.inFrameLikelihood >= 0.55f

    private fun distance(a: PointF, b: PointF): Float =
        kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun angleIfUsable(
        a: PoseLandmark?, b: PoseLandmark?, c: PoseLandmark?
    ): Float? {
        if (!usable(a) || !usable(b) || !usable(c)) return null
        return calculateJointAngle(a!!.position, b!!.position, c!!.position)
    }

    private fun average(values: List<Float>): Float? =
        if (values.isEmpty()) null else values.average().toFloat()

    private fun smooth(history: ArrayDeque<Float>, value: Float, size: Int = 5): Float {
        if (!value.isFinite()) return value
        history.addLast(value)
        while (history.size > size) history.removeFirst()
        return history.average().toFloat()
    }

    private fun normalizedVertical(
        point: PoseLandmark?, reference: PoseLandmark?, scale: Float
    ): Float? {
        if (!usable(point) || !usable(reference) || scale <= 1f) return null
        return (point!!.position.y - reference!!.position.y) / scale
    }

    private fun thresholds(): Pair<Float, Float> {
        // down threshold, up threshold. Hysteresis avoids chatter around one value.
        return when {
            isSquat() -> 110f to 155f
            isPushUp() -> 100f to 155f
            isLunge() -> 105f to 155f
            isGluteBridge() -> 145f to 165f
            isBicep() -> 75f to 155f
            isShoulderPress() -> 100f to 155f
            isJumpingJack() -> 95f to 150f
            isCalfRaise() -> 0.72f to 0.58f
            else -> 110f to 155f
        }
    }

    private fun metricData(
        ls: PoseLandmark?, lh: PoseLandmark?, lk: PoseLandmark?, la: PoseLandmark?,
        le: PoseLandmark?, lw: PoseLandmark?,
        rs: PoseLandmark?, rh: PoseLandmark?, rk: PoseLandmark?, ra: PoseLandmark?,
        re: PoseLandmark?, rw: PoseLandmark?
    ): Pair<Float?, String> {
        val leftKnee = angleIfUsable(lh, lk, la)
        val rightKnee = angleIfUsable(rh, rk, ra)
        val knee = listOfNotNull(leftKnee, rightKnee).minOrNull()

        val leftElbow = angleIfUsable(ls, le, lw)
        val rightElbow = angleIfUsable(rs, re, rw)
        val elbow = listOfNotNull(leftElbow, rightElbow).minOrNull()

        val leftHip = angleIfUsable(ls, lh, lk)
        val rightHip = angleIfUsable(rs, rh, rk)
        val hip = listOfNotNull(leftHip, rightHip).minOrNull()

        val leftShoulder = angleIfUsable(lh, ls, le)
        val rightShoulder = angleIfUsable(rh, rs, re)
        val shoulder = listOfNotNull(leftShoulder, rightShoulder).maxOrNull()

        return when {
            isPushUp() -> elbow to "elbow"
            isBicep() -> elbow to "elbow"
            isSquat() -> knee to "knee"
            isLunge() -> knee to "knee"
            isGluteBridge() -> hip to "hip"
            isShoulderPress() -> shoulder to "shoulder"
            isJumpingJack() -> shoulder to "shoulder"
            isCalfRaise() -> {
                val values = mutableListOf<Float>()
                if (usable(lk) && usable(la) && usable(lh)) {
                    val leg = distance(lk!!.position, la!!.position).coerceAtLeast(1f)
                    values += (la.position.y - lh!!.position.y) / leg
                }
                if (usable(rk) && usable(ra) && usable(rh)) {
                    val leg = distance(rk!!.position, ra!!.position).coerceAtLeast(1f)
                    values += (ra.position.y - rh!!.position.y) / leg
                }
                average(values) to "legs"
            }
            else -> knee to "knee"
        }
    }

    private fun updateRepCounter(
        rawMetric: Float,
        secondaryDistance: Float?,
        downThreshold: Float,
        upThreshold: Float
    ) {
        val now = System.currentTimeMillis()
        val metric = smooth(metricHistory, rawMetric)
        val distanceMetric = secondaryDistance?.let { smooth(distanceHistory, it) }

        // For calf raises the metric is a normalized vertical distance, so its
        // direction is opposite to the angle-based exercises.
        val isCalf = isCalfRaise()
        val isDown = if (isCalf) metric <= downThreshold else metric <= downThreshold
        val isUp = if (isCalf) metric >= upThreshold else metric >= upThreshold

        // Require a small number of consecutive frames at each endpoint. This
        // rejects isolated landmark jumps and random body motion.
        val stableFrames = 2

        when (phase) {
            Phase.WAITING_FOR_START -> {
                if (isUp) {
                    upFrames++
                    downFrames = 0
                } else if (isDown) {
                    downFrames++
                    upFrames = 0
                    if (downFrames >= stableFrames) {
                        phase = Phase.DOWN
                        downFrames = 0
                        upFrames = 0
                    }
                } else {
                    downFrames = 0
                    upFrames = 0
                }
            }

            Phase.DOWN -> {
                if (isUp) {
                    upFrames++
                    downFrames = 0
                    if (upFrames >= stableFrames && now - lastRepAt >= repCooldownMs) {
                        // Secondary normalized-distance check is intentionally
                        // permissive: angles are primary, distances help reject
                        // frames where the joint angle is noisy.
                        val distanceLooksValid = secondaryDistance == null ||
                                distanceMetric == null || distanceMetric.isFinite()
                        if (distanceLooksValid) {
                            repCount++
                            lastRepAt = now
                        }
                        phase = Phase.WAITING_FOR_START
                        upFrames = 0
                        downFrames = 0
                    }
                } else if (isDown) {
                    upFrames = 0
                } else {
                    upFrames = 0
                }
            }
        }
    }

    fun analyze(pose: Pose): PoseWorkoutResult {
        val allLandmarks = pose.allPoseLandmarks
            .filter { it.inFrameLikelihood >= 0.35f }
        val minX = allLandmarks.minOfOrNull { it.position.x } ?: 0f
        val maxX = allLandmarks.maxOfOrNull { it.position.x } ?: 1f
        val minY = allLandmarks.minOfOrNull { it.position.y } ?: 0f
        val maxY = allLandmarks.maxOfOrNull { it.position.y } ?: 1f
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)
        val skeleton = allLandmarks.map {
            SkeletonPoint(it.landmarkType, ((it.position.x - minX) / spanX).coerceIn(0f, 1f),
                ((it.position.y - minY) / spanY).coerceIn(0f, 1f))
        }
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)

        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val bodyVisible =
            (usable(ls) || usable(rs)) &&
                    (usable(lh) || usable(rh)) &&
                    (
                            (usable(lh) && usable(lk) && usable(la)) ||
                                    (usable(rh) && usable(rk) && usable(ra)) ||
                                    (usable(ls) && usable(le) && usable(lw)) ||
                                    (usable(rs) && usable(re) && usable(rw))
                            )

        if (!bodyVisible) {
            downFrames = 0
            upFrames = 0
            return PoseWorkoutResult(
                reps = repCount,
                feedback = "Move your full body into the camera",
                personDetected = false,
                skeleton = skeleton
            )
        }

        val leftKneeAngle = angleIfUsable(lh, lk, la)
        val rightKneeAngle = angleIfUsable(rh, rk, ra)
        val kneeAngle = average(listOfNotNull(leftKneeAngle, rightKneeAngle)) ?: 0f

        val leftElbowAngle = angleIfUsable(ls, le, lw)
        val rightElbowAngle = angleIfUsable(rs, re, rw)
        val elbowAngle = average(listOfNotNull(leftElbowAngle, rightElbowAngle)) ?: 0f

        val leftHipAngle = angleIfUsable(ls, lh, lk)
        val rightHipAngle = angleIfUsable(rs, rh, rk)
        val hipAngle = average(listOfNotNull(leftHipAngle, rightHipAngle)) ?: 0f

        val leftShoulderAngle = angleIfUsable(lh, ls, le)
        val rightShoulderAngle = angleIfUsable(rh, rs, re)
        val shoulderAngle = average(listOfNotNull(leftShoulderAngle, rightShoulderAngle)) ?: 0f

        val shoulder = ls?.position ?: rs?.position
        val hip = lh?.position ?: rh?.position
        val torsoScale = if (shoulder != null && hip != null) {
            distance(shoulder, hip).coerceAtLeast(1f)
        } else 1f

        val backAngle = if (shoulder != null && hip != null) {
            smooth(
                backHistory,
                Math.toDegrees(
                    atan2(
                        abs((shoulder.x - hip.x).toDouble()),
                        abs((shoulder.y - hip.y).toDouble()).coerceAtLeast(0.001)
                    )
                ).toFloat(),
                5
            )
        } else 0f

        val (rawMetric, label) = metricData(
            ls, lh, lk, la, le, lw,
            rs, rh, rk, ra, re, rw
        )

        if (rawMetric == null || !rawMetric.isFinite()) {
            return PoseWorkoutResult(
                reps = repCount,
                kneeAngle = kneeAngle,
                feedback = "Keep the $label visible to the camera",
                personDetected = true,
                formScore = 50,
                backAngle = backAngle,
                depthGood = false,
                skeleton = skeleton
            )
        }

        val secondaryDistance = when {
            isSquat() -> {
                val h = lh?.position ?: rh?.position
                val a = la?.position ?: ra?.position
                if (h != null && a != null) (h.y - a.y) / torsoScale else null
            }
            isPushUp() -> {
                val s = ls?.position ?: rs?.position
                val w = lw?.position ?: rw?.position
                if (s != null && w != null) distance(s, w) / torsoScale else null
            }
            isGluteBridge() -> {
                val h = lh?.position ?: rh?.position
                val s = ls?.position ?: rs?.position
                if (h != null && s != null) distance(h, s) / torsoScale else null
            }
            else -> null
        }

        val (downThreshold, upThreshold) = thresholds()

        // Calf raise's normalized metric decreases when the ankle rises.
        // Convert it into an angle-like increasing metric for the common state
        // machine so START -> RAISE -> START remains easy to understand.
        val counterMetric = if (isCalfRaise()) -rawMetric else rawMetric
        val counterDown = if (isCalfRaise()) -downThreshold else downThreshold
        val counterUp = if (isCalfRaise()) -upThreshold else upThreshold

        updateRepCounter(
            rawMetric = counterMetric,
            secondaryDistance = secondaryDistance,
            downThreshold = counterDown,
            upThreshold = counterUp
        )

        val displayedMetric = smooth(ArrayDeque(), rawMetric, 1)
        val inDownRange = if (isCalfRaise()) rawMetric <= downThreshold else rawMetric <= downThreshold
        val inUpRange = if (isCalfRaise()) rawMetric >= upThreshold else rawMetric >= upThreshold

        val formScore = when {
            isSquat() && backAngle > 35f -> 75
            inDownRange -> 96
            inUpRange -> 94
            phase == Phase.DOWN -> 88
            else -> 90
        }

        val feedback = when {
            isSquat() && backAngle > 35f -> "Keep your back more upright"
            isPushUp() && inDownRange -> "Good depth — press up"
            isPushUp() -> "Keep your body straight and continue"
            isBicep() && inDownRange -> "Good curl — control the return"
            isBicep() -> "Control the movement"
            isShoulderPress() && inUpRange -> "Great — arms overhead"
            isShoulderPress() -> "Press overhead with control"
            isGluteBridge() && inUpRange -> "Great bridge — squeeze and lower slowly"
            isGluteBridge() -> "Keep your hips controlled"
            isLunge() && inDownRange -> "Good lunge depth — drive up"
            isLunge() -> "Step and lower with control"
            isJumpingJack() && inUpRange -> "Great — arms up, now return"
            isJumpingJack() -> "Keep a steady rhythm"
            isCalfRaise() && inDownRange -> "Rise onto your toes"
            isCalfRaise() -> "Lower slowly and repeat"
            isSquat() && inDownRange -> "Good squat depth — drive up"
            phase == Phase.DOWN -> "Keep going and return to the start"
            else -> "Start the movement"
        }

        return PoseWorkoutResult(
            reps = repCount,
            kneeAngle = when {
                isGluteBridge() -> hipAngle
                isShoulderPress() || isJumpingJack() -> shoulderAngle
                isPushUp() || isBicep() -> elbowAngle
                else -> kneeAngle
            },
            feedback = feedback,
            personDetected = true,
            formScore = formScore.coerceIn(0, 100),
            backAngle = backAngle,
            depthGood = inDownRange || inUpRange || displayedMetric.isFinite(),
            skeleton = skeleton
        )
    }
}

private fun calculateJointAngle(first: PointF, middle: PointF, last: PointF): Float {
    val angle1 = Math.toDegrees(atan2((first.y - middle.y).toDouble(), (first.x - middle.x).toDouble()))
    val angle2 = Math.toDegrees(atan2((last.y - middle.y).toDouble(), (last.x - middle.x).toDouble()))
    var angle = abs(angle1 - angle2)
    if (angle > 180.0) angle = 360.0 - angle
    return angle.toFloat()
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PoseCameraPreview(
    modifier: Modifier = Modifier,
    mirrorEnabled: Boolean = true,
    exerciseName: String = "Bodyweight Squats",
    onPoseResult: (PoseWorkoutResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val exerciseAnalyzer = remember(exerciseName) { ExercisePoseAnalyzer(exerciseName) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var latestSkeleton by remember { mutableStateOf<List<SkeletonPoint>>(emptyList()) }
    AndroidView(
        modifier = modifier.graphicsLayer { scaleX = if (mirrorEnabled) -1f else 1f },
        factory = { previewView }
    )
    ComposeCanvas(modifier = modifier.graphicsLayer { scaleX = if (mirrorEnabled) -1f else 1f }) {
        val points = latestSkeleton.associateBy { it.landmarkType }
        val links = listOf(
            11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
            11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27,
            24 to 26, 26 to 28, 27 to 31, 28 to 32, 15 to 17,
            15 to 19, 16 to 18, 16 to 20
        )
        links.forEach { (a, b) ->
            val pa = points[a]; val pb = points[b]
            if (pa != null && pb != null) drawLine(
                color = Color(0xFF39F5A5),
                start = androidx.compose.ui.geometry.Offset(pa.x * size.width, pa.y * size.height),
                end = androidx.compose.ui.geometry.Offset(pb.x * size.width, pb.y * size.height),
                strokeWidth = 5f
            )
        }
        points.values.forEach { pt ->
            drawCircle(Color(0xFF55DFFF), radius = 7f,
                center = androidx.compose.ui.geometry.Offset(pt.x * size.width, pt.y * size.height))
            drawCircle(Color.White, radius = 7f, style = Stroke(width = 2f),
                center = androidx.compose.ui.geometry.Offset(pt.x * size.width, pt.y * size.height))
        }
    }

    DisposableEffect(previewView, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        val poseDetector = PoseDetection.getClient(options)
        val frameInFlight = AtomicBoolean(false)
        val active = AtomicBoolean(true)
        var cameraProvider: ProcessCameraProvider? = null

        exerciseAnalyzer.reset()

        cameraProviderFuture.addListener({
            if (!active.get()) return@addListener
            try {
                cameraProvider = cameraProviderFuture.get()
                if (!active.get()) return@addListener

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    // Process only one ML Kit frame at a time. Any skipped frame
                    // is closed immediately, preventing camera-buffer pressure.
                    if (!active.get() || !frameInFlight.compareAndSet(false, true)) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    analyzePoseFrame(
                        imageProxy = imageProxy,
                        poseDetector = poseDetector,
                        exerciseAnalyzer = exerciseAnalyzer,
                        mainHandler = mainHandler,
                        onResult = { result ->
                            latestSkeleton = result.skeleton
                            onPoseResult(result)
                        },
                        frameInFlight = frameInFlight,
                        active = active
                    )
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (_: Exception) {
                if (active.get()) {
                    mainHandler.post {
                        if (active.get()) {
                            onPoseResult(
                                PoseWorkoutResult(
                                    feedback = "Camera could not start",
                                    personDetected = false
                                )
                            )
                        }
                    }
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            active.set(false)
            try { cameraProvider?.unbindAll() } catch (_: Exception) {}
            try { poseDetector.close() } catch (_: Exception) {}
            cameraExecutor.shutdown()
            frameInFlight.set(false)
        }
    }
}

private fun analyzePoseFrame(
    imageProxy: ImageProxy,
    poseDetector: PoseDetector,
    exerciseAnalyzer: ExercisePoseAnalyzer,
    mainHandler: Handler,
    onResult: (PoseWorkoutResult) -> Unit,
    frameInFlight: AtomicBoolean,
    active: AtomicBoolean
) {
    try {
        if (!active.get()) {
            imageProxy.close()
            frameInFlight.set(false)
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            frameInFlight.set(false)
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        poseDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                if (active.get()) {
                    mainHandler.post {
                        if (active.get()) {
                            try {
                                val result = exerciseAnalyzer.analyze(pose)
                                onResult(result)
                            } catch (_: Exception) {
                                onResult(
                                    PoseWorkoutResult(
                                        feedback = "Keep your full body visible",
                                        personDetected = false
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                if (active.get()) {
                    mainHandler.post {
                        if (active.get()) {
                            onResult(
                                PoseWorkoutResult(
                                    feedback = "Move your full body into the camera",
                                    personDetected = false
                                )
                            )
                        }
                    }
                }
            }
            .addOnCompleteListener {
                try { imageProxy.close() } catch (_: Exception) {}
                frameInFlight.set(false)
            }
    } catch (_: Exception) {
        try { imageProxy.close() } catch (_: Exception) {}
        frameInFlight.set(false)
    }
}

// ============================================================
// STEP 9 - TODAY'S WORKOUT
// ============================================================

// ============================================================
// WORKOUT EXERCISE MODEL
// ============================================================

data class WorkoutExercise(
    val name: String,
    val durationSeconds: Int,
    val instruction: String,
    val emoji: String,
    val gifRes: Int
)

/*
 * Offline GIF player.
 * Uses Android's built-in Movie class, so no Coil/Glide dependency
 * is required. The GIF is loaded directly from res/drawable.
 */
@Suppress("DEPRECATION")
class OfflineGifView(context: Context) : View(context) {

    private var movie: Movie? = null
    private var gifRes: Int = 0
    private var playing = false
    private var startTime = 0L
    private var pausedTime = 0

    fun setGif(resourceId: Int) {
        if (resourceId == gifRes && movie != null) return

        gifRes = resourceId

        movie = try {
            resources.openRawResource(resourceId).use { input ->
                Movie.decodeStream(input)
            }
        } catch (_: Exception) {
            null
        }

        startTime = System.currentTimeMillis()
        pausedTime = 0
        invalidate()
    }

    fun setPlaying(value: Boolean) {
        if (playing == value) return

        if (value) {
            startTime = System.currentTimeMillis() - pausedTime
        } else {
            pausedTime = (System.currentTimeMillis() - startTime).toInt()
        }

        playing = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val m = movie ?: return

        val duration = if (m.duration() > 0) m.duration() else 1000

        val time = if (playing) {
            ((System.currentTimeMillis() - startTime) % duration).toInt()
        } else {
            pausedTime % duration
        }

        m.setTime(time)

        val movieWidth = m.width().coerceAtLeast(1)
        val movieHeight = m.height().coerceAtLeast(1)

        val scale = minOf(
            width.toFloat() / movieWidth,
            height.toFloat() / movieHeight
        )

        val left = (width - movieWidth * scale) / 2f
        val top = (height - movieHeight * scale) / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        m.draw(canvas, 0f, 0f)
        canvas.restore()

        if (playing) {
            postInvalidateDelayed(33L)
        }
    }
}

@Composable
fun ExerciseGif(
    gifRes: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    AndroidView(
        factory = { context ->
            OfflineGifView(context).apply {
                setGif(gifRes)
                setPlaying(isPlaying)
            }
        },
        update = { view ->
            view.setGif(gifRes)
            view.setPlaying(isPlaying)
        },
        modifier = modifier
    )
}

@Composable
fun TodaysWorkoutScreen(
    name: String,
    ageGroup: String,
    goal: String,
    primaryColor: Color,
    onRequestCameraPermission: () -> Unit,
    onComplete: () -> Unit
) {
    val exercises = remember(ageGroup, goal) {
        when {
            ageGroup == "Child" -> listOf(
                WorkoutExercise(
                    "Easy Walk",
                    30,
                    "Walk comfortably and keep a relaxed pace.",
                    "🚶",
                    R.drawable.easy_walk
                ),
                WorkoutExercise(
                    "Bodyweight Squats",
                    30,
                    "Use a comfortable range and keep your knees aligned.",
                    "🏋️",
                    R.drawable.bodyweight_squats
                ),
                WorkoutExercise(
                    "Wall Push-ups",
                    30,
                    "Keep your body straight and move slowly.",
                    "💪",
                    R.drawable.wall_pushups
                ),
                WorkoutExercise(
                    "Jumping Jacks",
                    30,
                    "Use a gentle pace and land comfortably.",
                    "⭐",
                    R.drawable.jumping_jacks
                ),
                WorkoutExercise(
                    "Stretching",
                    30,
                    "Move gently without forcing any stretch.",
                    "🧘",
                    R.drawable.stretching
                )
            )

            ageGroup == "Old" -> listOf(
                WorkoutExercise(
                    "Comfortable Walk",
                    30,
                    "Walk at a comfortable pace with good posture.",
                    "🚶",
                    R.drawable.comfortable_walk
                ),
                WorkoutExercise(
                    "Chair Squats",
                    30,
                    "Use a stable chair for support and control.",
                    "🪑",
                    R.drawable.chair_squats
                ),
                WorkoutExercise(
                    "Wall Push-ups",
                    30,
                    "Keep your body aligned and move slowly.",
                    "💪",
                    R.drawable.wall_pushups
                ),
                WorkoutExercise(
                    "Calf Raises",
                    30,
                    "Hold support if needed and rise gently.",
                    "🦵",
                    R.drawable.calf_raises
                ),
                WorkoutExercise(
                    "Gentle Mobility",
                    30,
                    "Move your shoulders, hips and ankles gently.",
                    "🧘",
                    R.drawable.gentle_mobility
                )
            )

            goal == "Build Strength" -> listOf(
                WorkoutExercise(
                    "Bodyweight Squats",
                    30,
                    "Keep your chest comfortable and lower with control.",
                    "🏋️",
                    R.drawable.bodyweight_squats
                ),
                WorkoutExercise(
                    "Wall Push-ups",
                    30,
                    "Keep a straight body line and move with control.",
                    "💪",
                    R.drawable.wall_pushups
                ),
                WorkoutExercise(
                    "Glute Bridges",
                    30,
                    "Lift gently and keep the movement controlled.",
                    "🦵",
                    R.drawable.glute_bridges
                ),
                WorkoutExercise(
                    "Reverse Lunges",
                    30,
                    "Use a comfortable step and keep your balance.",
                    "🏃",
                    R.drawable.reverse_lunges
                ),
                WorkoutExercise(
                    "Plank",
                    20,
                    "Keep a comfortable position and breathe normally.",
                    "🧱",
                    R.drawable.plank
                )
            )

            goal == "Improve Mobility" -> listOf(
                WorkoutExercise(
                    "Shoulder Mobility",
                    30,
                    "Move your shoulders slowly through a comfortable range.",
                    "🔄",
                    R.drawable.shoulder_mobility
                ),
                WorkoutExercise(
                    "Hip Mobility",
                    30,
                    "Use slow, comfortable hip movements.",
                    "🦵",
                    R.drawable.hip_mobility
                ),
                WorkoutExercise(
                    "Cat-Cow Stretch",
                    30,
                    "Move gently with your breathing.",
                    "🐈",
                    R.drawable.cat_cow_stretch
                ),
                WorkoutExercise(
                    "Bodyweight Squats",
                    30,
                    "Use a comfortable range of motion.",
                    "🏋️",
                    R.drawable.bodyweight_squats
                ),
                WorkoutExercise(
                    "Full-body Stretch",
                    30,
                    "Stretch gently without forcing the movement.",
                    "🧘",
                    R.drawable.full_body_stretch
                )
            )

            else -> listOf(
                WorkoutExercise(
                    "Brisk Walk",
                    30,
                    "Walk at a comfortable, steady pace.",
                    "🚶",
                    R.drawable.brisk_walk
                ),
                WorkoutExercise(
                    "Bodyweight Squats",
                    30,
                    "Keep the movement controlled and comfortable.",
                    "🏋️",
                    R.drawable.bodyweight_squats
                ),
                WorkoutExercise(
                    "Wall Push-ups",
                    30,
                    "Keep your body aligned and move slowly.",
                    "💪",
                    R.drawable.wall_pushups
                ),
                WorkoutExercise(
                    "Glute Bridges",
                    30,
                    "Lift gently and lower with control.",
                    "🦵",
                    R.drawable.glute_bridges
                ),
                WorkoutExercise(
                    "Light Stretching",
                    30,
                    "Finish with gentle full-body movements.",
                    "🧘",
                    R.drawable.light_stretching
                )
            )
        }
    }

    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraEnabled by remember { mutableStateOf(true) }
    var mirrorEnabled by remember { mutableStateOf(true) }
    var editExercise by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(true) }
    // Full-screen exercise GIF preview. Opening it pauses the workout timer.
    var isGifPreviewOpen by remember { mutableStateOf(false) }
    var poseResult by remember { mutableStateOf(PoseWorkoutResult()) }
    var isCooldown by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableStateOf(10) }

    // Rep-based exercises finish after 15 valid reps.
    // These exercises intentionally do NOT use rep counting and are time-based.
    fun isTimedExercise(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("walk") ||
                n.contains("stretch") ||
                n.contains("mobility") ||
                n.contains("cat-cow") ||
                n.contains("plank")
    }

    val currentExercise = exercises[currentIndex]
    val isSquatExercise = currentExercise.name.contains("squat", ignoreCase = true)
    val timedExercise = isTimedExercise(currentExercise.name)
    val useTimer = !cameraEnabled || timedExercise
    var timerRemaining by remember(currentIndex, cameraEnabled) {
        mutableStateOf(currentExercise.durationSeconds)
    }

    // Reset the timer only when the exercise/camera mode changes.
    // This prevents opening the GIF preview from resetting the paused timer.
    LaunchedEffect(currentIndex, cameraEnabled) {
        timerRemaining = currentExercise.durationSeconds
    }

    // Timer mode:
    // 1) Camera OFF -> EVERY exercise uses the timer.
    // 2) Camera ON  -> walking/stretching/mobility/plank also use the timer.
    // 3) Other camera-on exercises finish automatically after 15 reps.
    // Opening the exercise GIF pauses this coroutine and therefore pauses the timer.
    LaunchedEffect(
        currentIndex,
        cameraEnabled,
        editExercise,
        isCooldown,
        poseResult.personDetected,
        isGifPreviewOpen
    ) {
        if (isCooldown || editExercise || isGifPreviewOpen) return@LaunchedEffect

        if (useTimer) {
            // When camera is ON, wait until a person is detected before starting the timer.
            // When camera is OFF, timer starts normally because person detection is unavailable.
            if (cameraEnabled) {
                while (!poseResult.personDetected && !editExercise && !isCooldown && !isGifPreviewOpen) {
                    delay(200)
                }
            }

            while (
                timerRemaining > 0 &&
                !editExercise &&
                !isCooldown &&
                !isGifPreviewOpen
            ) {
                delay(1000)
                if (!editExercise && !isCooldown && !isGifPreviewOpen) {
                    timerRemaining--
                }
            }

            if (
                timerRemaining <= 0 &&
                !editExercise &&
                !isCooldown &&
                !isGifPreviewOpen
            ) {
                if (currentIndex < exercises.lastIndex) {
                    currentIndex++
                    poseResult = PoseWorkoutResult()
                    isRunning = true
                    isCooldown = true
                    cooldownRemaining = 10
                } else {
                    onComplete()
                }
            }
        }
    }

    // Rep mode: automatically move to the next exercise when 15 reps are completed.
    LaunchedEffect(currentIndex, cameraEnabled, poseResult.reps, isCooldown) {
        if (!isCooldown && !useTimer && poseResult.reps >= 15) {
            if (currentIndex < exercises.lastIndex) {
                currentIndex++
                poseResult = PoseWorkoutResult()
                isRunning = true
                isCooldown = true
                cooldownRemaining = 10
            } else {
                onComplete()
            }
        }
    }

    // 10-second cooldown between every exercise. During cooldown the NEXT exercise
    // is already displayed and its GIF keeps playing, while counting/timer is paused.
    LaunchedEffect(isCooldown, currentIndex) {
        if (isCooldown) {
            cooldownRemaining = 10
            while (cooldownRemaining > 0) {
                delay(1000)
                cooldownRemaining--
            }
            isCooldown = false
            poseResult = PoseWorkoutResult()
            isRunning = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            onRequestCameraPermission()
            delay(700)
            hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }
    }



    Box(modifier = Modifier.fillMaxSize().background(Black)) {

        // FULL-SCREEN CAMERA BACKGROUND
        if (hasCameraPermission && cameraEnabled && !isCooldown) {
            PoseCameraPreview(
                modifier = Modifier.fillMaxSize(),
                mirrorEnabled = mirrorEnabled,
                exerciseName = currentExercise.name,
                onPoseResult = { poseResult = it }
            )
        } else if (!hasCameraPermission) {
            Box(
                modifier = Modifier.fillMaxSize().background(DarkBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Camera permission is needed", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onRequestCameraPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Black),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Enable Camera", fontWeight = FontWeight.Bold) }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(DarkBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CAMERA OFF", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Timer mode", color = primaryColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("${timerRemaining}s", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Text("Next exercise starts automatically", color = LightGray, fontSize = 13.sp)
                }
            }
        }

        // A subtle dark glass layer keeps the controls readable while preserving the camera.
        Box(modifier = Modifier.fillMaxSize().background(Black.copy(alpha = 0.18f)))

        // TOP ACTIONS — the old "Today Workout / Stay Consistent" panel was removed.
        // Keep only the Edit and AI status controls at the top-right.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { editExercise = !editExercise },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkBlack.copy(alpha = 0.75f),
                    contentColor = White
                )
            ) {
                Text("✎  Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkGreen.copy(alpha = 0.75f))
                    .border(1.dp, primaryColor.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Text("● AI ON", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // SAMPLE EXERCISE / GIF PLACE
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 125.dp, start = 14.dp, end = 14.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkBlack.copy(alpha = 0.20f))
                .border(1.dp, White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Black.copy(alpha = 0.55f))
                    .clickable {
                        // Pause the workout and open the exercise GIF in a full-screen preview.
                        isRunning = false
                        isGifPreviewOpen = true
                    }
            ) {
                ExerciseGif(
                    gifRes = currentExercise.gifRes,
                    isPlaying = isRunning,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("SAMPLE EXERCISE", color = LightGray, fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(currentExercise.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (currentExercise.name.contains("squat", ignoreCase = true)) "Legs • Glutes • Core" else "Selected from your workout",
                    color = LightGray, fontSize = 12.sp
                )
            }

            Button(
                onClick = { editExercise = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkBlack.copy(alpha = 0.50f),
                    contentColor = primaryColor
                )
            ) { Text("✎  Change", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }

        // RESPONSIVE MIDDLE HUD + FORM CORRECTION
        // These two panels are kept inside one Row so they can never overlap
        // horizontally on narrow phones. Each side gets a proportional width.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 275.dp, start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // LEFT: live workout HUD
            Column(
                modifier = Modifier.weight(0.43f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkBlack.copy(alpha = 0.75f))
                        .border(1.dp, White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    if (useTimer) {
                        Text("WORKOUT TIMER", color = LightGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${timerRemaining}s",
                            color = primaryColor,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text("CURRENT REP", color = LightGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("${poseResult.reps} / 15", color = primaryColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkBlack.copy(alpha = 0.75f))
                        .border(1.dp, White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    if (useTimer) {
                        Text("MODE", color = LightGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("TIMER", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            if (isSquatExercise) "KNEE ANGLE" else "JOINT ANGLE",
                            color = LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (poseResult.kneeAngle > 0f) "${poseResult.kneeAngle.toInt()}°" else "--",
                            color = White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkBlack.copy(alpha = 0.75f))
                        .border(1.dp, White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text("PERSON", color = LightGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (poseResult.personDetected) "Detected" else "Searching…",
                        color = if (poseResult.personDetected) primaryColor else LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // RIGHT: form correction card. Weight keeps it inside the screen.
            Column(
                modifier = Modifier
                    .weight(0.57f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DarkBlack.copy(alpha = 0.20f))
                    .border(1.dp, White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 13.dp, vertical = 14.dp)
            ) {
                Text("✓  Form Correction", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = poseResult.feedback,
                    color = primaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = when {
                        currentExercise.name.contains("push", true) -> "• Keep your body aligned\n• Control the elbow bend\n• Move through a comfortable range"
                        currentExercise.name.contains("lunge", true) -> "• Keep balance\n• Lower with control\n• Drive through the front leg"
                        currentExercise.name.contains("plank", true) -> "• Keep shoulders and hips aligned\n• Keep your core engaged\n• Breathe normally"
                        isSquatExercise -> "• Keep your back controlled\n• Knees track with toes\n• Use comfortable depth"
                        else -> "• Keep your movement controlled\n• Keep your full body visible\n• Follow the exercise GIF"
                    },
                    color = White,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (poseResult.personDetected) "● Person detected" else "○ Move into camera view",
                    color = if (poseResult.personDetected) primaryColor else LightGray,
                    fontSize = 10.sp
                )
            }
        }

        // AUTO-ADVANCE STATUS
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 205.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DarkBlack.copy(alpha = 0.20f))
                .border(1.dp, primaryColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isCooldown)
                    "⏸ Rest ${cooldownRemaining}s • Get ready for next exercise"
                else if (useTimer)
                    "⏱ ${timerRemaining}s • Next exercise automatically"
                else
                    "✓ Complete 15 reps • Next exercise automatically",
                color = primaryColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // CAMERA + MIRROR CONTROLS
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 145.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { cameraEnabled = !cameraEnabled },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkBlack.copy(alpha = 0.20f),
                    contentColor = if (cameraEnabled) primaryColor else White
                )
            ) {
                Text("📷 Camera  ${if (cameraEnabled) "ON" else "OFF"}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = { mirrorEnabled = !mirrorEnabled },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkBlack.copy(alpha = 0.20f),
                    contentColor = if (mirrorEnabled) primaryColor else White
                )
            ) {
                Text("▣ Mirror  ${if (mirrorEnabled) "ON" else "OFF"}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // FINAL HOME UI: the old Diet / AI Partner / Camera action bar has been removed.

        // FULL-SCREEN EXERCISE GIF PREVIEW
        // This is the only Back arrow shown inside the workout module.
        if (isGifPreviewOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                isGifPreviewOpen = false
                                isRunning = true
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Text(
                                text = "←",
                                color = White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = currentExercise.name,
                            color = White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DarkBlack)
                            .border(1.dp, primaryColor.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                            .clickable {
                                // Tapping the large preview does not resume it; only Back resumes.
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Full-screen preview must keep the GIF animation running.
                        // The workout timer is paused separately via isRunning = false.
                        ExerciseGif(
                            gifRes = currentExercise.gifRes,
                            isPlaying = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Exercise preview",
                        color = LightGray,
                        fontSize = 13.sp
                    )

                    Text(
                        text = "Timer paused",
                        color = primaryColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // EXERCISE EDITOR OVERLAY
        if (editExercise) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(DarkBlack.copy(alpha = 0.96f))
                        .border(1.dp, primaryColor.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
                        .padding(18.dp)
                ) {
                    Text("Edit Workout", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Choose a sample exercise", color = LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))

                    exercises.forEachIndexed { index, exercise ->
                        Button(
                            onClick = {
                                currentIndex = index
                                editExercise = false
                                isGifPreviewOpen = false
                                isRunning = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (index == currentIndex) primaryColor.copy(alpha = 0.85f) else DarkGray.copy(alpha = 0.75f),
                                contentColor = if (index == currentIndex) Black else White
                            )
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(exercise.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(exercise.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${exercise.durationSeconds} sec", fontSize = 10.sp)
                                }
                                if (index == currentIndex) Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { editExercise = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = White)
                    ) { Text("Close") }
                }
            }
        }
    }
}

// ============================================================
// STEP 9 - WORKOUT COMPLETED
// ============================================================

@Composable
fun WorkoutCompletedScreen(
    name: String,
    goal: String,
    primaryColor: Color,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(125.dp)
                .clip(CircleShape)
                .background(DarkGray)
                .border(2.dp, primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = primaryColor,
                fontSize = 58.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Workout Completed!",
            color = primaryColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Well done, $name!",
            color = White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkBlack)
                .border(1.dp, DarkGray, RoundedCornerShape(18.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TODAY'S SESSION",
                color = primaryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "5 exercises completed",
                color = White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (goal.isNotEmpty()) "Goal: $goal" else "Keep building your consistency",
                color = LightGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Consistency matters more than perfection. Take a comfortable recovery break and stay hydrated.",
            color = LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {
            Text(
                text = "Continue to Diet Planner",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// STEP 14 - OFFLINE GYM PARTNER AI
// ============================================================

@Composable
fun OfflineAiChatScreen(
    name: String,
    age: Int,
    ageGroup: String,
    gender: String,
    heightCm: Float,
    weightKg: Float,
    bmi: Float,
    goal: String,
    vegetarian: Boolean,
    completedWorkouts: Int,
    workoutStreak: Int,
    lastWorkoutDate: String,
    workoutHistory: List<WorkoutHistoryEntry>,
    onVegetarianChanged: (Boolean) -> Unit,
    primaryColor: Color,
    messages: List<AiChatMessage>,
    onMessagesChanged: (List<AiChatMessage>) -> Unit,
    waitingForHydrationDuration: Boolean,
    onWaitingForHydrationDurationChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onStartWorkout: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }

    val userContext = UserFitnessContext(
        name = name,
        age = age,
        ageGroup = ageGroup,
        gender = gender,
        heightCm = heightCm,
        weightKg = weightKg,
        bmi = bmi,
        goal = goal,
        vegetarian = vegetarian,
        completedWorkouts = completedWorkouts,
        workoutStreak = workoutStreak,
        lastWorkoutDate = lastWorkoutDate,
        workoutHistory = workoutHistory
    )

    val context = LocalContext.current
    val voiceController = remember(context) { ChatVoiceController(context) }
    var voiceStatus by remember { mutableStateOf("") }
    var workoutPlanReady by remember { mutableStateOf(false) }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    fun submitQuestion(questionText: String, speakResponse: Boolean) {
        val question = questionText.trim()
        if (question.isEmpty()) return
        val workoutRequest = Regex("\\b(workout|exercise|training)\\b").containsMatchIn(question.lowercase(Locale.getDefault())) && Regex("\\b(plan|routine|minutes?|mins?|start|make|create)\\b").containsMatchIn(question.lowercase(Locale.getDefault()))
        val result = offlineAiReply(
            question = question,
            context = userContext,
            waitingForHydrationDuration = waitingForHydrationDuration
        )
        onMessagesChanged(messages + AiChatMessage(question, true) + AiChatMessage(result.text, false))
        workoutPlanReady = workoutRequest && result.text.contains("WORKOUT PLAN", ignoreCase = true)
        onWaitingForHydrationDurationChanged(result.needsHydrationFollowUp)
        input = ""
        voiceStatus = ""
        if (speakResponse) voiceController.speak(result.text)
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceStatus = "Listening… speak in English"
            voiceController.listenOnce(
                onTextRecognized = { recognizedText -> submitQuestion(recognizedText, true) },
                onFailure = { message -> voiceStatus = message }
            )
        } else {
            voiceStatus = "Microphone permission was denied."
        }
    }

    // Dedicated list state lets the chat jump to the newest message automatically.
    val chatListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            onMessagesChanged(
                listOf(
                    AiChatMessage(
                        "Hi $name! I'm GYM PARTNER AI 🤖\n\nI work completely offline. I can use the information you saved in this app to answer personal questions, plus general fitness, food, hydration, recovery and workout questions.",
                        false
                    )
                )
            )
        }
    }

    // Whenever a new user/AI message is added, move the chat to the newest message.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GYM PARTNER AI",
                    color = primaryColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Offline personal fitness assistant",
                    color = LightGray,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkBlack,
                    contentColor = primaryColor
                )
            ) {
                Text("Diet")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Try: Who am I? • My BMI? • My progress? • Water? • Workout? • Food?",
            color = LightGray,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkBlack)
                .border(1.dp, DarkGray, RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🌿 Food preference",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (vegetarian) {
                        "Vegetarian • remembered offline"
                    } else {
                        "Mixed • remembered offline"
                    },
                    color = LightGray,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { onVegetarianChanged(!vegetarian) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vegetarian) primaryColor else DarkGray,
                    contentColor = if (vegetarian) Black else White
                )
            ) {
                Text(
                    if (vegetarian) "VEG" else "MIXED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LazyColumn replaces the old manual verticalScroll.
        // This gives us reliable automatic scrolling to the latest AI response.
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(messages.size) { index ->
                AiMessageBubble(
                    message = messages[index],
                    primaryColor = primaryColor
                )
            }
        }

        if (workoutPlanReady) {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Black)
            ) {
                Text("▶ Start Exercise", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f),
                placeholder = {
                    Text("Ask GYM PARTNER AI...", color = LightGray)
                },
                minLines = 1,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = DarkGray,
                    cursorColor = primaryColor,
                    focusedPlaceholderColor = LightGray,
                    unfocusedPlaceholderColor = LightGray
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = {
                    voiceStatus = ""
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        voiceStatus = "Listening… speak in English"
                        voiceController.listenOnce(
                            onTextRecognized = { recognizedText -> submitQuestion(recognizedText, true) },
                            onFailure = { message -> voiceStatus = message }
                        )
                    } else {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkBlack, contentColor = primaryColor)
            ) {
                Text("🎙", fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = { submitQuestion(input, speakResponse = false) },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Black
                )
            ) {
                Text("Send", fontWeight = FontWeight.Bold)
            }
        }

        if (voiceStatus.isNotBlank()) {
            Text(
                text = voiceStatus,
                color = primaryColor,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 2.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "Offline assistant gives general guidance and does not diagnose injuries or medical conditions.",
            color = LightGray,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AiMessageBubble(
    message: AiChatMessage,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (message.isUser) 0.86f else 0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.isUser) primaryColor else DarkBlack)
                .border(
                    1.dp,
                    if (message.isUser) primaryColor else DarkGray,
                    RoundedCornerShape(16.dp)
                )
                .padding(13.dp)
        ) {
            Text(
                text = message.text,
                color = if (message.isUser) Black else White,
                fontSize = 14.sp
            )
        }
    }
}

data class OfflineAiResult(
    val text: String,
    val needsHydrationFollowUp: Boolean = false
)

fun offlineAiReply(
    question: String,
    context: UserFitnessContext,
    waitingForHydrationDuration: Boolean = false
): OfflineAiResult {
    val q = question.lowercase(Locale.getDefault()).trim()
    val cleanQ = q.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    val goalText = context.goal.ifBlank { "general fitness" }
    val displayName = context.name.ifBlank { "friend" }

    // ------------------------------------------------------------
    // FOLLOW-UP: hydration question
    // ------------------------------------------------------------
    if (waitingForHydrationDuration) {
        val minutes = Regex("(\\d{1,3})").find(cleanQ)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (minutes != null && minutes in 5..600) {
            val base = if (context.weightKg > 0f) context.weightKg * 35f else 2000f
            val activityAddition = when {
                minutes >= 120 -> 900f
                minutes >= 90 -> 700f
                minutes >= 60 -> 500f
                minutes >= 30 -> 300f
                else -> 150f
            }
            val estimateLitres = ((base + activityAddition).coerceIn(1500f, 5000f)) / 1000f
            return OfflineAiResult(
                "For your saved weight of ${formatNumber(context.weightKg)} kg and about $minutes minutes of exercise, a rough daily hydration starting estimate is around ${formatNumber(estimateLitres)} L/day. You may need more in hot weather or with heavy sweating. Drink regularly rather than forcing a large amount at once. This is general guidance, not a medical prescription."
            )
        }
        return OfflineAiResult(
            "Please tell me your usual workout duration, for example 30 minutes, 60 minutes or 90 minutes. Then I can make the hydration estimate more specific to you.",
            true
        )
    }

    return when {
        // --------------------------------------------------------
        // Voice/text workout plan requests: create a timed, gentle plan.
        // --------------------------------------------------------
        (cleanQ.contains("workout") || cleanQ.contains("exercise") || cleanQ.contains("training")) &&
                (cleanQ.contains("plan") || cleanQ.contains("routine") || cleanQ.contains("minutes") || cleanQ.contains("mins") || cleanQ.contains("start")) -> {
            val requestedMinutes = Regex("(\\d{1,3})\\s*(?:minutes?|mins?|min)\\b").find(cleanQ)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("\\b(10|15|20|30|45)\\b").find(cleanQ)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 10
            val minutes = requestedMinutes.coerceIn(5, 45)
            val warmup = maxOf(2, minutes / 5)
            val cooldown = maxOf(1, minutes / 6)
            val main = maxOf(2, minutes - warmup - cooldown)
            val focus = when {
                cleanQ.contains("upper") -> "upper-body"
                cleanQ.contains("lower") || cleanQ.contains("leg") -> "lower-body"
                cleanQ.contains("mobility") || cleanQ.contains("stretch") -> "mobility"
                else -> "full-body"
            }
            val mainMoves = when {
                focus == "upper-body" -> "Wall push-ups and gentle arm raises"
                focus == "lower-body" -> "Comfortable chair sit-to-stands and calf raises"
                focus == "mobility" -> "Gentle shoulder rolls, ankle circles and comfortable stretches"
                else -> "Easy marching, wall push-ups and comfortable chair sit-to-stands"
            }
            OfflineAiResult("""WORKOUT PLAN — $minutes minutes ($focus)
1. Warm-up — $warmup min: easy marching in place and gentle joint movements.
2. Main session — $main min: $mainMoves. Move at a comfortable pace and take breaks whenever needed.
3. Cool-down — $cooldown min: slow breathing and gentle, pain-free stretches.

Start gently; this is a general plan, not a medical assessment. Stop if you feel pain, dizziness or unwell. If you have pain today, avoid movements that affect that area and ask a trusted adult or qualified professional for help if it persists.

Tap “Start Exercise” below to open your workout screen.""")
        }

        // --------------------------------------------------------
        // Greetings / AI identity
        // --------------------------------------------------------
        cleanQ == "hi" || cleanQ == "hello" || cleanQ == "hey" || cleanQ.contains("how are you") || cleanQ.contains("how r you") ->
            OfflineAiResult("I'm doing great, $displayName! 🤖💪 I'm your offline Gym Partner AI. I work without internet and can use your saved fitness data to make answers more personal.")

        cleanQ.contains("who are you") || cleanQ.contains("what are you") || cleanQ.contains("what can you do") || cleanQ.contains("what do you do") || cleanQ.contains("purpose of you") || cleanQ.contains("what is your purpose") ->
            OfflineAiResult("I'm GYM PARTNER AI, an offline fitness assistant. I can answer questions about your saved profile and progress, workouts, exercises, food, vegetarian substitutions, protein, pre/post-workout meals, hydration, sleep, recovery and motivation. I use built-in rules and knowledge, so I don't need internet. I give general guidance and don't replace a qualified trainer or healthcare professional.")

        // --------------------------------------------------------
        // Personal / app data
        // --------------------------------------------------------
        cleanQ.contains("who am i") || cleanQ.contains("about me") || cleanQ.contains("tell me about myself") || cleanQ.contains("my profile") ->
            OfflineAiResult("You are $displayName. Your saved profile says ${if (context.age > 0) "you are ${context.age} years old" else "your age is not saved"}, ${if (context.gender.isNotBlank()) context.gender.lowercase(Locale.getDefault()) else "gender not specified"}, ${if (context.heightCm > 0f) "${formatNumber(context.heightCm)} cm tall" else "height not saved"}, and ${if (context.weightKg > 0f) "${formatNumber(context.weightKg)} kg" else "weight not saved"}. Your goal is $goalText. You've completed ${context.completedWorkouts} workout(s) and have a ${context.workoutStreak}-day streak. 💪")

        cleanQ.contains("my name") || cleanQ.contains("what is my name") ->
            OfflineAiResult("Your saved name in Gym Partner is ${context.name.ifBlank { "not set yet" }}.")

        cleanQ.contains("my age") || cleanQ.contains("how old am i") ->
            OfflineAiResult(if (context.age > 0) "Your saved age is ${context.age} years (${context.ageGroup})." else "Your age hasn't been saved yet.")

        cleanQ.contains("my height") || cleanQ.contains("how tall am i") ->
            OfflineAiResult(if (context.heightCm > 0f) "Your saved height is ${formatNumber(context.heightCm)} cm." else "Your height hasn't been saved yet.")

        cleanQ.contains("my weight") || cleanQ.contains("how much do i weigh") ->
            OfflineAiResult(if (context.weightKg > 0f) "Your saved weight is ${formatNumber(context.weightKg)} kg." else "Your weight hasn't been saved yet.")

        cleanQ.contains("my gender") ->
            OfflineAiResult(if (context.gender.isNotBlank()) "Your saved gender selection is ${context.gender}." else "Your gender selection hasn't been saved yet.")

        cleanQ.contains("my goal") || cleanQ.contains("what is my goal") ->
            OfflineAiResult("Your saved fitness goal is $goalText.")

        cleanQ.contains("my bmi") || cleanQ.contains("what is my bmi") ->
            OfflineAiResult(if (context.bmi > 0f) "Your saved BMI is ${formatNumber(context.bmi)}. BMI is only a screening measure and doesn't describe your complete health or fitness level." else "Your BMI hasn't been calculated and saved yet.")

        cleanQ.contains("how many workout") || cleanQ.contains("workouts have i") || cleanQ.contains("completed workout") ->
            OfflineAiResult("You've completed ${context.completedWorkouts} workout(s) according to Gym Partner's saved progress.")

        cleanQ.contains("streak") || cleanQ.contains("how long is my streak") ->
            OfflineAiResult("Your current saved workout streak is ${context.workoutStreak} day(s). Keep it comfortable and consistent. 🔥")

        cleanQ.contains("last workout") || cleanQ.contains("when did i workout") ->
            OfflineAiResult(if (context.lastWorkoutDate.isNotBlank()) "Your last saved workout date is ${context.lastWorkoutDate}." else "I don't have a saved workout date yet.")

        cleanQ.contains("progress") || cleanQ.contains("how am i doing") || cleanQ.contains("am i improving") ->
            OfflineAiResult("Your saved progress is ${context.completedWorkouts} completed workout(s) with a ${context.workoutStreak}-day streak. Progress is more than a number: consistency, comfortable training, recovery and how you feel all matter.")

        // --------------------------------------------------------
        // Food substitutions - specific cases FIRST
        // --------------------------------------------------------
        (cleanQ.contains("egg") && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("alternative") || cleanQ.contains("substitute") || cleanQ.contains("without egg") || cleanQ.contains("no egg")) && (context.vegetarian || cleanQ.contains("veg") || cleanQ.contains("vegetarian"))) ->
            OfflineAiResult("If you're vegetarian and want to replace eggs, good options are paneer, tofu, soy chunks, Greek yogurt/curd, milk, dal, chickpeas, rajma and other beans. For protein, soy chunks and tofu are especially useful; if you eat dairy, paneer/curd are easy choices. Try to include one protein-rich food in each main meal. 🌱💪")

        (cleanQ.contains("egg") && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("alternative") || cleanQ.contains("substitute") || cleanQ.contains("without egg") || cleanQ.contains("no egg"))) ->
            OfflineAiResult("If you want an egg replacement, choose based on the purpose. For protein: paneer, curd/Greek yogurt, tofu, soy chunks, dal, beans or chicken/fish if you eat meat. For baking, mashed banana, applesauce or suitable commercial egg replacer can work depending on the recipe.")

        (cleanQ.contains("chicken") && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("alternative") || cleanQ.contains("without chicken"))) ->
            OfflineAiResult("For a vegetarian replacement for chicken, try soy chunks, tofu, paneer, dal, chickpeas, rajma or other beans. For a high-protein meal, soy chunks or tofu are practical choices. 🌱")

        (cleanQ.contains("meat") && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("alternative") || cleanQ.contains("without meat"))) ->
            OfflineAiResult("Vegetarian alternatives to meat include soy chunks, tofu, paneer, lentils, chickpeas, rajma and other beans. Combine legumes with grains such as rice or roti across the day for a varied protein intake.")

        (cleanQ.contains("fish") && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("alternative"))) ->
            OfflineAiResult("If you want a vegetarian alternative to fish, try tofu, soy chunks, paneer, dal, chickpeas or beans. If your main aim is protein, tofu and soy are convenient options.")

        (cleanQ.contains("paneer") && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("alternative"))) ->
            OfflineAiResult("If you want to replace paneer, try tofu, soy chunks, lentils, chickpeas, beans or curd/Greek yogurt if dairy is okay for you. For a dairy-free option, tofu and soy are good choices.")

        ((cleanQ.contains("milk") || cleanQ.contains("dairy")) && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("alternative") || cleanQ.contains("without milk"))) ->
            OfflineAiResult("For a milk alternative, you can use fortified soy milk or another suitable fortified plant milk. For protein, soy milk is generally more useful than many low-protein plant drinks. If dairy is okay but you simply don't want milk, curd/yogurt is another option.")

        (cleanQ.contains("whey") && (cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("without whey"))) ->
            OfflineAiResult("If you don't want whey protein, you can get protein from regular foods such as milk, curd/Greek yogurt, paneer, tofu, soy chunks, dal, chickpeas and beans. If you avoid dairy, focus more on tofu, soy, lentils and beans.")

        cleanQ.contains("vegetarian protein") || cleanQ.contains("veg protein") || cleanQ.contains("protein without egg") || cleanQ.contains("protein without eggs") || cleanQ.contains("protein without meat") || cleanQ.contains("protein sources") ->
            OfflineAiResult("Good vegetarian protein sources include soy chunks, tofu, paneer, curd/Greek yogurt, milk, dal, chickpeas, rajma, beans, peas and nuts/seeds. For a simple gym meal, combine a protein source with rice/roti and vegetables. 🌱💪")

        // --------------------------------------------------------
        // Pre-workout food
        // --------------------------------------------------------
        cleanQ.contains("before workout") || cleanQ.contains("pre workout") || cleanQ.contains("preworkout") || cleanQ.contains("before gym") || cleanQ.contains("eat before exercise") ->
            OfflineAiResult("Before a workout, keep food comfortable and easy to digest. Good options include a banana, fruit + curd, oats, idli, toast or a small meal with rice/roti and a protein source. If you have only 20–30 minutes, keep the portion small. Avoid trying a very heavy meal immediately before training.")

        cleanQ.contains("empty stomach") || cleanQ.contains("workout on empty") || cleanQ.contains("gym on empty stomach") ->
            OfflineAiResult("You can exercise on an empty stomach if you feel comfortable, but it isn't required for fat loss. If you feel weak, dizzy or unusually tired, have a light snack such as fruit, banana + curd, or toast before training.")

        cleanQ.contains("banana before") || cleanQ.contains("banana before workout") ->
            OfflineAiResult("Yes, a banana can be a convenient pre-workout snack because it provides carbohydrates and is usually easy to digest. If you need more protein, pair it with curd or another protein-rich food when comfortable.")

        cleanQ.contains("coffee before") || cleanQ.contains("coffee pre workout") ->
            OfflineAiResult("Some people use coffee before exercise for alertness, but it isn't necessary for a good workout. Keep the amount comfortable, avoid it if caffeine causes problems for you, and don't use it as a substitute for sleep or food.")

        cleanQ.contains("30 min") || cleanQ.contains("30 minutes") ->
            OfflineAiResult("If you mean what to eat about 30 minutes before training, keep it light: banana, another easy fruit, toast, or a small serving of curd with fruit. Choose something you already know your stomach tolerates well.")

        cleanQ.contains("1 hour before") || cleanQ.contains("one hour before") || cleanQ.contains("60 minutes before") ->
            OfflineAiResult("About an hour before training, you can have a light balanced snack or small meal such as oats + milk/curd, banana + curd, idli + sambar, or rice/roti with a small protein portion. Keep the portion comfortable.")

        // --------------------------------------------------------
        // Post-workout food
        // --------------------------------------------------------
        cleanQ.contains("after workout") || cleanQ.contains("post workout") || cleanQ.contains("postworkout") || cleanQ.contains("after gym") || cleanQ.contains("eat after exercise") ->
            OfflineAiResult(if (context.vegetarian) {
                "After training, have a normal balanced meal or snack with protein + carbohydrates. Vegetarian examples: paneer/curd/tofu/soy + rice or roti + vegetables, or dal + rice + curd. Add fruit if you like. You don't need a special food immediately after every workout. 🌱"
            } else {
                "After training, have a balanced meal or snack with protein + carbohydrates. Examples: eggs + roti, chicken/fish + rice, or dal + rice + curd. Add vegetables or fruit for variety. You don't need a special food immediately after every workout."
            })

        cleanQ.contains("eggs after workout") || cleanQ.contains("egg after workout") ->
            OfflineAiResult(if (context.vegetarian) "Since your saved preference is vegetarian, use paneer, tofu, soy chunks, curd or dal instead of eggs after training. A simple option is tofu/soy + rice or roti + vegetables." else "Eggs can be part of a post-workout meal. Pair them with a carbohydrate such as rice, roti, oats or fruit and add vegetables or another food group for a balanced meal.")

        cleanQ.contains("muscle gain") || cleanQ.contains("gain muscle") || cleanQ.contains("build muscle") ->
            OfflineAiResult("For muscle gain, focus on progressive resistance training, enough overall food, regular protein-rich meals and adequate sleep. Vegetarian protein choices include soy, tofu, paneer, curd, dal and beans. Your exact calorie and protein needs depend on your body, activity and goal.")

        cleanQ.contains("weight loss") || cleanQ.contains("lose weight") || cleanQ.contains("fat loss") || cleanQ.contains("lose fat") ->
            OfflineAiResult("For gradual fat loss, focus on a sustainable eating pattern, regular activity, enough protein, vegetables and sensible portions rather than extreme restriction. Keep strength training if appropriate and prioritize sleep. Avoid crash diets.")

        cleanQ.contains("healthy snack") || cleanQ.contains("snacks") || cleanQ.contains("evening snack") ->
            OfflineAiResult("Simple snack ideas include fruit + curd, roasted chana, nuts in a sensible portion, milk/fortified soy milk, sprouts, or a small paneer/tofu snack. Pick based on your hunger and overall meal pattern.")

        cleanQ.contains("breakfast") || cleanQ.contains("morning food") || cleanQ.contains("what should i eat in morning") ->
            OfflineAiResult(if (context.vegetarian) "Vegetarian breakfast ideas: idli + sambar, dosa + sambar, oats + milk/curd + fruit, vegetable upma + curd, or roti + paneer/tofu. Try to include a protein-rich food along with your usual breakfast." else "Breakfast ideas: eggs + roti/toast + fruit, idli + sambar, oats + milk + fruit, dosa + sambar, or a balanced family breakfast. Try to include a protein-rich food.")

        cleanQ.contains("dinner") || cleanQ.contains("night food") || cleanQ.contains("what should i eat at night") ->
            OfflineAiResult(if (context.vegetarian) "A simple vegetarian dinner can be roti/rice + dal/paneer/tofu + vegetables + curd. Keep the portion comfortable and choose foods that suit your digestion and routine." else "A simple dinner can be roti/rice + dal/eggs/chicken/fish + vegetables + curd or another suitable side. Keep the portion comfortable and choose foods that suit your routine.")

        cleanQ.contains("rice") ->
            OfflineAiResult("Rice can fit into a fitness diet. Pair it with a protein-rich food such as dal, beans, paneer, tofu, eggs, chicken or fish plus vegetables. The right portion depends on your overall needs and goal; you don't have to completely avoid rice.")

        cleanQ.contains("roti") || cleanQ.contains("chapati") ->
            OfflineAiResult("Roti/chapati can be part of a balanced fitness meal. Pair it with dal, beans, paneer, tofu, eggs, chicken or fish and vegetables. Whole-grain options can add more fiber depending on what you choose.")

        cleanQ.contains("curd") || cleanQ.contains("yogurt") || cleanQ.contains("greek yogurt") ->
            OfflineAiResult("Curd/yogurt can be a convenient source of protein and calcium, depending on the type. You can pair it with fruit, oats or a meal. Greek yogurt generally provides more protein per serving than regular curd, but check the label if buying packaged products.")

        cleanQ.contains("oats") ->
            OfflineAiResult("Oats are a useful carbohydrate- and fiber-containing food. For a more balanced gym meal, combine oats with milk/curd or soy milk, fruit, and another protein source if needed. Savoury oats with vegetables and dal/soy can also work.")

        cleanQ.contains("peanut butter") || cleanQ.contains("peanut") ->
            OfflineAiResult("Peanut butter provides fats and some protein and can fit into a balanced diet. Try it with toast, oats or fruit. Because it is energy-dense, use a sensible portion rather than assuming more is always better.")

        cleanQ.contains("banana") || cleanQ.contains("fruit") ->
            OfflineAiResult("Fruit is useful for carbohydrates, fiber and micronutrients. Banana is especially convenient around exercise, but you can vary your choices with apples, oranges, papaya, watermelon, berries or other locally available fruit.")

        cleanQ.contains("sugar") || cleanQ.contains("junk food") || cleanQ.contains("fast food") || cleanQ.contains("cheat meal") ->
            OfflineAiResult("You don't need to label one food as completely forbidden. Keep highly sugary, highly processed or fast foods as occasional choices, while most meals can be based on varied whole or minimally processed foods. Your overall pattern matters more than one meal.")

        cleanQ.contains("how much protein") || cleanQ.contains("protein per day") || cleanQ.contains("daily protein") ->
            OfflineAiResult("Protein needs vary with body size, age, activity and goals. A practical approach is to include a protein-rich food at each main meal. Good options include eggs/dairy for mixed diets and tofu, soy, paneer, curd, dal and beans for vegetarian diets. For an exact target, a qualified dietitian or trainer can use your full context.")

        // --------------------------------------------------------
        // Hydration
        // --------------------------------------------------------
        cleanQ.contains("water") || cleanQ.contains("hydration") || cleanQ.contains("drink water") || cleanQ.contains("how much should i drink") ->
            OfflineAiResult("I can personalize a rough hydration estimate using your saved weight (${formatNumber(context.weightKg)} kg), but I need one more detail: how long do you usually work out? Tell me 30 minutes, 60 minutes or 90 minutes.", true)

        cleanQ.contains("during workout") && (cleanQ.contains("water") || cleanQ.contains("drink")) ->
            OfflineAiResult("During exercise, drink according to thirst and conditions. Longer or harder sessions, heat and heavy sweating can increase fluid needs. Avoid forcing a large amount quickly.")

        cleanQ.contains("electrolyte") || cleanQ.contains("ors") ->
            OfflineAiResult("Electrolytes can matter during prolonged or very sweaty exercise, especially in hot conditions. For ordinary shorter workouts, regular fluids and normal meals are often enough. If you have a medical condition affecting fluid or electrolyte balance, get professional advice.")

        // --------------------------------------------------------
        // Workout frequency / training
        // --------------------------------------------------------
        (cleanQ.contains("how many days") || cleanQ.contains("how often")) && (cleanQ.contains("workout") || cleanQ.contains("gym") || cleanQ.contains("exercise")) ->
            OfflineAiResult("For a general healthy routine, many people can start with around 3–5 workout days per week depending on intensity, experience and recovery. Rest or lighter days are useful. For your $goalText goal, increase gradually rather than doing too much too soon.")

        cleanQ.contains("what exercises") || cleanQ.contains("which exercises") || cleanQ.contains("suggest exercises") || cleanQ.contains("exercise will you suggest") ->
            OfflineAiResult("For your $goalText goal, Gym Partner can use comfortable exercises such as bodyweight squats, wall/incline or regular push-ups as appropriate, glute bridges, walking, lunges, calf raises, planks and mobility work. Your exact list also depends on your age group and ability. Focus on controlled technique.")

        cleanQ.contains("chest workout") || cleanQ.contains("chest exercise") ->
            OfflineAiResult("Push-ups are a simple chest exercise and also train the triceps and shoulders. Wall or incline push-ups are easier versions; regular push-ups are harder. Use a variation that lets you maintain controlled form.")

        cleanQ.contains("leg workout") || cleanQ.contains("legs exercise") ->
            OfflineAiResult("For a simple leg routine, try bodyweight squats, chair squats if needed, reverse lunges, glute bridges and calf raises. Start with comfortable repetitions and controlled movement.")

        cleanQ.contains("arm workout") || cleanQ.contains("biceps") || cleanQ.contains("triceps") ->
            OfflineAiResult("For arms, exercises can include incline/wall push-ups, close-grip push-up variations as appropriate, and resistance exercises if you have suitable equipment. Build gradually and prioritize technique over high repetitions.")

        cleanQ.contains("abs") || cleanQ.contains("six pack") || cleanQ.contains("core") ->
            OfflineAiResult("Core exercises can include planks, dead-bug style movements and controlled bodyweight exercises. A visible six-pack also depends on overall body composition and genetics, not only ab exercises. Train the core as part of a balanced routine.")

        cleanQ.contains("cardio") || cleanQ.contains("cardiovascular") || cleanQ.contains("endurance") ->
            OfflineAiResult("Walking, brisk walking, jogging, cycling and swimming can improve cardiovascular endurance. Start at a comfortable intensity and build duration gradually.")

        (cleanQ.contains("strength training") && cleanQ.contains("cardio")) || (cleanQ.contains("difference") && cleanQ.contains("cardio")) ->
            OfflineAiResult("Strength training mainly challenges muscles against resistance to improve strength and muscle capacity. Cardio mainly trains the heart and lungs to sustain activity. A balanced routine can include both.")

        cleanQ.contains("progressive overload") ->
            OfflineAiResult("Progressive overload means gradually increasing a training challenge over time—for example, adding repetitions, resistance, time or difficulty while keeping technique controlled. Increase gradually rather than making big jumps.")

        cleanQ.contains("home workout") || cleanQ.contains("workout at home") ->
            OfflineAiResult("A simple home workout can use squats, wall/incline push-ups, glute bridges, lunges, calf raises, planks and walking. Choose versions that match your ability and leave enough recovery between harder sessions.")

        cleanQ.contains("stop going") || cleanQ.contains("stop gym") || cleanQ.contains("quit gym") || cleanQ.contains("take a break") ->
            OfflineAiResult("Taking a break is not automatically a problem. If the break is long, return gradually instead of trying to make up missed sessions with very hard workouts. Consistency over time matters more than perfection.")

        // --------------------------------------------------------
        // Warm-up / stretching / recovery / soreness
        // --------------------------------------------------------
        cleanQ.contains("warm") || cleanQ.contains("warm up") || cleanQ.contains("warmup") ->
            OfflineAiResult("A simple 5–10 minute warm-up can include easy walking, gentle mobility and comfortable practice of the movements you'll train. Increase intensity gradually rather than starting suddenly at full effort.")

        cleanQ.contains("stretch") || cleanQ.contains("cool down") ->
            OfflineAiResult("Gentle stretching and easy movement can be useful as part of a cool-down. Don't force a stretch, bounce into it, or push through sharp pain.")

        cleanQ.contains("sore") || cleanQ.contains("soreness") || cleanQ.contains("muscle pain") || cleanQ.contains("body pain") || cleanQ.contains("body pains") || cleanQ.contains("ache") ->
            OfflineAiResult("Mild muscle soreness can happen after a new or harder workout and often improves over a few days. Use comfortable light activity and rest the muscles from movements that aggravate the soreness. Severe, worsening, unusual or persistent pain needs appropriate medical advice.")

        cleanQ.contains("how many days will the pain last") || cleanQ.contains("how long will the pain last") ->
            OfflineAiResult("Typical post-workout muscle soreness often improves over a few days, but the exact duration varies. If pain is severe, worsening, unusually long-lasting, or different from normal soreness, get appropriate medical advice.")

        cleanQ.contains("injur") || cleanQ.contains("prevent injury") || cleanQ.contains("avoid injury") ->
            OfflineAiResult("To reduce workout injury risk, warm up, learn the movement, use controlled technique, increase training gradually, use suitable equipment, recover adequately and stop if you develop sharp or worsening pain.")

        cleanQ.contains("recovery") || cleanQ.contains("recover") ->
            OfflineAiResult("Recovery includes sleep, enough food and fluids, rest days or lighter activity, and gradually increasing training. Protein-rich foods and carbohydrates can support normal recovery. If pain is unusual or severe, seek professional advice.")

        cleanQ.contains("sleep") || (cleanQ.contains("hours") && cleanQ.contains("sleep")) ->
            OfflineAiResult("Adequate sleep supports recovery, performance and general health. Many adults need around 7–9 hours per night, while teenagers generally need more. Your personal need can vary.")

        cleanQ.contains("tired after workout") || cleanQ.contains("feel tired after gym") || cleanQ.contains("fatigue after workout") ->
            OfflineAiResult("Some tiredness after exercise is normal. Check that you are eating enough, drinking fluids, warming up, recovering and not increasing training too quickly. If fatigue is severe, persistent or unusual, get appropriate medical advice.")

        // --------------------------------------------------------
        // Mental wellbeing / motivation
        // --------------------------------------------------------
        cleanQ.contains("motivat") || cleanQ.contains("lazy") || cleanQ.contains("skip workout") || cleanQ.contains("don't feel like") ->
            OfflineAiResult("Small consistent steps count 💪. On low-motivation days, aim for a short comfortable session or an easy walk rather than giving up completely. Missing one day doesn't erase your progress—return gradually.")

        cleanQ.contains("mental health") || cleanQ.contains("stress") || cleanQ.contains("mood") ->
            OfflineAiResult("Regular physical activity can support mood, stress management and general well-being. Even manageable activities such as walking can be useful when done consistently. Exercise isn't a replacement for professional mental-health care when that is needed.")

        cleanQ.contains("rest day") || cleanQ.contains("rest days") ->
            OfflineAiResult("Rest days give your body time to recover and help make training sustainable. You can use complete rest or comfortable light activity depending on how you feel and how hard you trained.")

        // --------------------------------------------------------
        // BMI / general fitness
        // --------------------------------------------------------
        cleanQ == "bmi" || cleanQ.contains("what does bmi stand for") || cleanQ.contains("what is bmi") ->
            OfflineAiResult("BMI stands for Body Mass Index. It is calculated from weight and height and is used as a screening measure. It doesn't directly measure body fat, fitness or overall health.")

        cleanQ.contains("healthy weight") || cleanQ.contains("ideal weight") ->
            OfflineAiResult("There isn't one perfect weight for everyone. Healthy weight depends on factors such as height, age, body composition, health and activity. BMI can be one screening measure, but it should not be treated as a complete fitness assessment.")

        cleanQ.contains("technology") || cleanQ.contains("stay consistent") || cleanQ.contains("consistency") ->
            OfflineAiResult("Technology can improve consistency through reminders, workout timers, progress tracking, streaks, exercise guidance and personalized feedback. Gym Partner keeps these core features on-device and offline.")

        cleanQ.contains("trainer") || cleanQ.contains("train you") || cleanQ.contains("train the trainers") ->
            OfflineAiResult("I can guide you like a basic offline digital fitness partner: explain exercises, suggest general routines, discuss food and hydration, and track saved progress. I don't physically watch your form like a human trainer, and I don't replace qualified supervision when it is needed.")

        // --------------------------------------------------------
        // Generic food / diet fallback comes late so specific cases win
        // --------------------------------------------------------
        cleanQ.contains("food") || cleanQ.contains("diet") || cleanQ.contains("eat") || cleanQ.contains("meal") || cleanQ.contains("nutrition") ->
            OfflineAiResult(
                if (context.vegetarian) {
                    "For your vegetarian preference, balanced choices can include dal, beans, paneer, curd, milk/fortified soy milk, tofu, soy chunks, oats, rice/roti, fruits and vegetables. Try to include a protein-rich food in your meals rather than relying on one food."
                } else {
                    "For your mixed preference, balanced meals can include rice/roti or other grains, vegetables, fruit and protein sources such as eggs, dal, beans, paneer, curd, chicken or fish. Choose portions that fit your routine and goal."
                }
            )

        cleanQ.contains("alternative") || cleanQ.contains("instead") || cleanQ.contains("replace") || cleanQ.contains("substitute") ->
            OfflineAiResult(
                if (context.vegetarian) {
                    "Vegetarian alternatives can include eggs/meat → tofu, soy chunks, paneer, curd, dal or beans; milk → fortified soy milk or curd; rice → roti or another grain. The best option depends on what food you are replacing and why."
                } else {
                    "Food substitutions depend on the purpose. For protein, options include eggs, dairy, tofu, soy, dal, beans, chicken or fish. For carbohydrates, rice, roti, oats and other grains can be exchanged based on your meal."
                }
            )

        // --------------------------------------------------------
        // Fallback
        // --------------------------------------------------------
        else ->
            OfflineAiResult("I can answer many offline questions about your profile, progress, workouts, exercises, vegetarian/non-vegetarian food, egg/meat replacements, protein, pre-workout and post-workout meals, rice, roti, oats, hydration, sleep, recovery, soreness, BMI and motivation. Try: “What do I eat instead of egg in veg?”, “What should I eat before gym?”, “What should I eat after workout?”, “How much water should I drink?”, or “Suggest exercises for me.”")
    }
}

private fun formatNumber(value: Float): String {
    return if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
}

// ============================================================
// STEP 13 - PROGRESS TRACKER UI
// ============================================================

data class AchievementBadge(
    val imageRes: Int,
    val title: String,
    val requirement: String,
    val unlocked: Boolean
)

private fun buildAchievementBadges(
    gender: String,
    completedWorkouts: Int,
    firstWorkoutDate: LocalDate?
): List<AchievementBadge> {
    val today = LocalDate.now()
    val male = gender != "Female"
    val images = if (male) {
        listOf(
            R.drawable.male_achievement_1,
            R.drawable.male_achievement_2,
            R.drawable.male_achievement_3,
            R.drawable.male_achievement_4,
            R.drawable.male_achievement_5,
            R.drawable.male_achievement_6
        )
    } else {
        listOf(
            R.drawable.female_achievement_1,
            R.drawable.female_achievement_2,
            R.drawable.female_achievement_3,
            R.drawable.female_achievement_4,
            R.drawable.female_achievement_5,
            R.drawable.female_achievement_6
        )
    }

    return listOf(
        AchievementBadge(images[0], "First Workout", "Day 1 • Start your journey", completedWorkouts >= 1),
        AchievementBadge(images[1], "Strength Builder", "2 months • Keep building", firstWorkoutDate != null && !today.isBefore(firstWorkoutDate.plusMonths(2))),
        AchievementBadge(images[2], "Consistency", "4 months • Stay consistent", firstWorkoutDate != null && !today.isBefore(firstWorkoutDate.plusMonths(4))),
        AchievementBadge(images[3], "Fitness Goal", "6 months • Keep progressing", firstWorkoutDate != null && !today.isBefore(firstWorkoutDate.plusMonths(6))),
        AchievementBadge(images[4], "Push Up Champ", "9 months • Keep pushing", firstWorkoutDate != null && !today.isBefore(firstWorkoutDate.plusMonths(9))),
        AchievementBadge(images[5], "No Excuses", "1 year • Long-term dedication", firstWorkoutDate != null && !today.isBefore(firstWorkoutDate.plusYears(1)))
    )
}

@Composable
fun ProgressTrackerScreen(
    name: String,
    goal: String,
    gender: String,
    completedWorkouts: Int,
    workoutStreak: Int,
    workoutHistory: List<WorkoutHistoryEntry>,
    primaryColor: Color,
    onContinue: () -> Unit
) {
    val firstWorkoutDate = workoutHistory
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        .minOrNull()
    val badges = buildAchievementBadges(
        gender = gender,
        completedWorkouts = completedWorkouts,
        firstWorkoutDate = firstWorkoutDate
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Text(
            text = "Progress Tracker",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Keep going, $name 💪",
            color = primaryColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        if (goal.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Goal: $goal", color = LightGray, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgressStatCard(
                modifier = Modifier.weight(1f),
                emoji = "🔥",
                value = workoutStreak.toString(),
                label = "Day Streak",
                primaryColor = primaryColor
            )
            ProgressStatCard(
                modifier = Modifier.weight(1f),
                emoji = "🏋️",
                value = completedWorkouts.toString(),
                label = "Workouts",
                primaryColor = primaryColor
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "🏆 Badges",
            color = White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        badges.forEach { badge ->
            BadgeCard(
                imageRes = badge.imageRes,
                title = badge.title,
                requirement = badge.requirement,
                unlocked = badge.unlocked,
                primaryColor = primaryColor
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "📅 Recent Workout History",
            color = White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (workoutHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkBlack)
                    .border(1.dp, DarkGray, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "Complete your first workout and it will appear here.",
                    color = LightGray,
                    fontSize = 13.sp
                )
            }
        } else {
            workoutHistory.take(7).forEach { entry ->
                HistoryCard(
                    entry = entry,
                    primaryColor = primaryColor
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkGreen.copy(alpha = 0.45f))
                .padding(16.dp)
        ) {
            Text(
                text = "GYM PARTNER AI: Consistency matters more than perfection. Follow a comfortable routine and take rest when needed.",
                color = LightGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Black
            )
        ) {
            Text(
                text = "Continue to Diet Planner",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun ProgressStatCard(
    modifier: Modifier,
    emoji: String,
    value: String,
    label: String,
    primaryColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(DarkBlack)
            .border(1.dp, primaryColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun BadgeCard(
    imageRes: Int,
    title: String,
    requirement: String,
    unlocked: Boolean,
    primaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (unlocked) DarkBlack else Color(0xFF111111))
            .border(
                1.dp,
                if (unlocked) primaryColor.copy(alpha = 0.65f) else DarkGray,
                RoundedCornerShape(16.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = title,
            modifier = Modifier
                .size(70.dp)
                .graphicsLayer(alpha = if (unlocked) 1f else 0.30f),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (unlocked) White else LightGray,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = requirement,
                color = if (unlocked) primaryColor else LightGray,
                fontSize = 11.sp
            )
            Text(
                text = if (unlocked) "Unlocked" else "Locked",
                color = if (unlocked) primaryColor else LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HistoryCard(
    entry: WorkoutHistoryEntry,
    primaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBlack)
            .border(1.dp, DarkGray, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "✅", fontSize = 25.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.workoutName, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "${entry.date}  •  ${entry.goal}", color = LightGray, fontSize = 12.sp)
        }
        Text(text = "Done", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================
// STEP 12 - DAILY DIET PLANNER
// ============================================================

data class DietMeal(
    val title: String,
    val foods: String,
    val tip: String
)

fun getDailyDietPlan(
    ageGroup: String,
    vegetarian: Boolean,
    dayIndex: Int
): List<DietMeal> {
    val cycle = dayIndex % 3

    return when (ageGroup) {
        "Child" -> {
            when (cycle) {
                0 -> listOf(
                    DietMeal("🌅 Breakfast", "Idli + sambar + fruit", "A balanced breakfast helps support school and daily activity."),
                    DietMeal("⚡ Pre-workout", "Banana + milk or curd", "Keep it light and comfortable before activity."),
                    DietMeal("💪 Post-workout", if (vegetarian) "Milk + banana + nuts" else "Milk + banana + 1–2 eggs", "Have a normal meal or snack after activity."),
                    DietMeal("🍛 Lunch", "Rice/roti + dal + vegetables + curd", "Include different food groups instead of focusing on calories."),
                    DietMeal("🍎 Evening Snack", "Fruit + roasted chana or nuts", "Choose a simple nourishing snack."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Roti + dal/paneer + vegetables + curd" else "Roti/rice + dal/egg/chicken + vegetables + curd", "Eat a varied family meal and stay hydrated.")
                )
                1 -> listOf(
                    DietMeal("🌅 Breakfast", "Oats + milk + fruit", "Add variety across the week."),
                    DietMeal("⚡ Pre-workout", "Fruit + curd", "Avoid exercising on an uncomfortably full stomach."),
                    DietMeal("💪 Post-workout", if (vegetarian) "Curd + fruit + nuts" else "Eggs + roti + fruit", "A regular balanced meal is enough."),
                    DietMeal("🍛 Lunch", "Rice/roti + beans/dal + vegetables + curd", "Try to include vegetables and a protein-rich food."),
                    DietMeal("🍎 Evening Snack", "Fruit + milk", "Keep snacks simple."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Rice/roti + paneer/dal + vegetables" else "Rice/roti + fish/chicken/egg + vegetables", "Hydrate through the day.")
                )
                else -> listOf(
                    DietMeal("🌅 Breakfast", "Dosa + sambar + fruit", "A varied breakfast supports daily energy needs."),
                    DietMeal("⚡ Pre-workout", "Banana + milk", "Keep the portion comfortable."),
                    DietMeal("💪 Post-workout", if (vegetarian) "Paneer/curd + roti + fruit" else "Eggs + roti + milk", "Choose a normal nourishing meal after activity."),
                    DietMeal("🍛 Lunch", "Rice/roti + dal/beans + vegetables + curd", "Aim for variety, not restriction."),
                    DietMeal("🍎 Evening Snack", "Fruit + roasted chana", "A simple snack can be enough."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Roti + dal/paneer + vegetables + curd" else "Roti + chicken/egg/dal + vegetables + curd", "Eat with your family when possible and stay hydrated.")
                )
            }
        }
        "Old" -> {
            when (cycle) {
                0 -> listOf(
                    DietMeal("🌅 Breakfast", if (vegetarian) "Idli + sambar + fruit" else "Idli + sambar + egg + fruit", "Choose foods that are comfortable to chew and digest."),
                    DietMeal("⚡ Before activity", "Banana or other soft fruit + curd", "Keep activity and food comfortable for your routine."),
                    DietMeal("💪 After activity", if (vegetarian) "Milk/curd + fruit + nuts" else "Milk/curd + fruit + egg", "A regular balanced snack is useful after activity."),
                    DietMeal("🍛 Lunch", "Rice/roti + dal + cooked vegetables + curd", "Include protein, vegetables and familiar staple foods."),
                    DietMeal("🍎 Evening Snack", "Fruit + roasted chana or nuts", "Choose a snack that suits your digestion."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Soft roti + dal/paneer + cooked vegetables" else "Soft rice/roti + dal/egg/fish + cooked vegetables", "Stay hydrated unless a clinician has advised a fluid restriction.")
                )
                1 -> listOf(
                    DietMeal("🌅 Breakfast", "Oats/porridge + milk + fruit", "A simple breakfast can be easy to prepare."),
                    DietMeal("⚡ Before activity", "Fruit + curd", "Keep the snack light."),
                    DietMeal("💪 After activity", if (vegetarian) "Curd + fruit + nuts" else "Egg + curd + fruit", "Focus on a balanced meal rather than supplements."),
                    DietMeal("🍛 Lunch", "Rice + dal/beans + cooked vegetables + curd", "Include a variety of food groups."),
                    DietMeal("🍎 Evening Snack", "Milk + fruit", "Choose foods that feel comfortable."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Rice/roti + dal/paneer + vegetables" else "Rice/roti + fish/chicken/egg + vegetables", "Keep hydration regular.")
                )
                else -> listOf(
                    DietMeal("🌅 Breakfast", "Dosa + sambar + fruit", "Choose a familiar, balanced breakfast."),
                    DietMeal("⚡ Before activity", "Banana + curd", "Keep it comfortable before exercise."),
                    DietMeal("💪 After activity", if (vegetarian) "Milk + banana + nuts" else "Milk + banana + egg", "A normal food-based snack is fine."),
                    DietMeal("🍛 Lunch", "Roti/rice + dal + vegetables + curd", "Include protein and vegetables regularly."),
                    DietMeal("🍎 Evening Snack", "Fruit + roasted chana", "Simple foods are a good everyday choice."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Roti + dal/paneer + cooked vegetables" else "Roti + chicken/fish/dal + cooked vegetables", "Stay hydrated and choose foods that suit you.")
                )
            }
        }
        else -> {
            // Adult plan: balanced everyday foods, without restrictive calorie targets.
            when (cycle) {
                0 -> listOf(
                    DietMeal("🌅 Breakfast", if (vegetarian) "Idli + sambar + fruit" else "2 eggs + dosa/roti + fruit", "Start with a balanced meal."),
                    DietMeal("⚡ Pre-workout", "Banana + milk or curd", "Have this 1–2 hours before exercise if it suits your routine."),
                    DietMeal("💪 Post-workout", if (vegetarian) "Paneer/curd + roti + fruit" else "Eggs + roti + milk", "Choose a protein-containing food and regular carbs."),
                    DietMeal("🍛 Lunch", "Rice/roti + dal/beans + vegetables + curd", "Build meals from several food groups."),
                    DietMeal("🍎 Evening Snack", "Fruit + roasted chana/nuts", "Keep a convenient nourishing option available."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Rice/roti + dal/paneer + vegetables + curd" else "Rice/roti + chicken/fish/egg + vegetables + curd", "Eat a balanced dinner and hydrate.")
                )
                1 -> listOf(
                    DietMeal("🌅 Breakfast", "Oats + milk + fruit + nuts", "A varied breakfast supports an active day."),
                    DietMeal("⚡ Pre-workout", "Apple/orange + curd", "Choose a light snack if you need one before exercise."),
                    DietMeal("💪 Post-workout", if (vegetarian) "Milk + banana + paneer/curd" else "Milk + banana + eggs", "Food-based recovery is a simple option."),
                    DietMeal("🍛 Lunch", "Roti + dal/beans + vegetables + curd", "Include a protein-rich food and vegetables."),
                    DietMeal("🍎 Evening Snack", "Sprouts/roasted chana + fruit", "Choose a snack that fits your routine."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Roti + paneer/dal + vegetables" else "Roti + chicken/fish/egg + vegetables", "Keep meals varied across the week.")
                )
                else -> listOf(
                    DietMeal("🌅 Breakfast", if (vegetarian) "Dosa + sambar + curd + fruit" else "Eggs + dosa/roti + fruit", "A balanced breakfast is a good start."),
                    DietMeal("⚡ Pre-workout", "Bread + peanut butter + fruit", "Use a comfortable portion before activity."),
                    DietMeal("💪 Post-workout", if (vegetarian) "Paneer + roti + curd" else "Eggs/chicken + roti + curd", "Choose a normal balanced meal after exercise."),
                    DietMeal("🍛 Lunch", "Rice/roti + dal/beans + vegetables + curd", "Include several food groups in the meal."),
                    DietMeal("🍎 Evening Snack", "Fruit + nuts or roasted chana", "A simple snack can work well."),
                    DietMeal("🌙 Dinner", if (vegetarian) "Rice/roti + dal/paneer + vegetables + curd" else "Rice/roti + fish/chicken/egg + vegetables + curd", "Stay hydrated and keep food choices varied.")
                )
            }
        }
    }
}



// ============================================================
// IMPROVED PROGRESS TRACKER
// Opened from More -> Progress & Streaks
// ============================================================

@Composable
fun ImprovedProgressTrackerScreen(
    name: String,
    goal: String,
    gender: String,
    completedWorkouts: Int,
    workoutStreak: Int,
    workoutHistory: List<WorkoutHistoryEntry>,
    primaryColor: Color,
    onBack: () -> Unit
) {
    val today = LocalDate.now()
    val last7Days = (6 downTo 0).map { today.minusDays(it.toLong()) }

    val weeklyCounts = last7Days.map { day ->
        workoutHistory.count { entry ->
            runCatching { LocalDate.parse(entry.date) }.getOrNull() == day
        }
    }

    val weeklyWorkouts = weeklyCounts.sum()
    val weeklyGoal = 5
    val weeklyProgress = (weeklyWorkouts.toFloat() / weeklyGoal.toFloat()).coerceIn(0f, 1f)
    val goalPercent = min(completedWorkouts * 10, 100)
    val estimatedCalories = completedWorkouts * 320
    val estimatedMinutes = completedWorkouts * 30
    val activeDays = weeklyCounts.count { it > 0 }
    val maxBar = maxOf(1, weeklyCounts.maxOrNull() ?: 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkGray,
                    contentColor = primaryColor
                )
            ) {
                Text("‹", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Progress Tracker",
                    color = White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your journey, measured",
                    color = LightGray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Weekly goal hero card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBlack)
                .border(
                    1.dp,
                    primaryColor.copy(alpha = 0.30f),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WEEKLY PROGRESS",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Keep going, ${name.ifBlank { "Champion" }} 💪",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "$weeklyWorkouts of $weeklyGoal workouts this week",
                        color = LightGray,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.10f))
                        .border(3.dp, primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(weeklyProgress * 100).toInt()}%",
                        color = primaryColor,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DarkGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(weeklyProgress)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(primaryColor)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (weeklyWorkouts >= weeklyGoal)
                    "Weekly goal completed! 🔥"
                else
                    "${weeklyGoal - weeklyWorkouts} more workout${if (weeklyGoal - weeklyWorkouts == 1) "" else "s"} to hit your goal.",
                color = if (weeklyWorkouts >= weeklyGoal) primaryColor else LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ImprovedProgressStat(
                modifier = Modifier.weight(1f),
                icon = "🔥",
                value = workoutStreak.toString(),
                label = "Day Streak",
                primaryColor = primaryColor
            )
            ImprovedProgressStat(
                modifier = Modifier.weight(1f),
                icon = "🏋️",
                value = completedWorkouts.toString(),
                label = "Workouts",
                primaryColor = primaryColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ImprovedProgressStat(
                modifier = Modifier.weight(1f),
                icon = "🔥",
                value = "$estimatedCalories",
                label = "Est. Calories",
                primaryColor = primaryColor
            )
            ImprovedProgressStat(
                modifier = Modifier.weight(1f),
                icon = "⏱️",
                value = "${estimatedMinutes}m",
                label = "Active Time",
                primaryColor = primaryColor
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Weekly chart
        Text(
            text = "Weekly Activity",
            color = White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$activeDays active day${if (activeDays == 1) "" else "s"} in the last 7 days",
            color = LightGray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkBlack)
                .border(
                    1.dp,
                    White.copy(alpha = 0.08f),
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            last7Days.forEachIndexed { index, day ->
                val count = weeklyCounts[index]
                val fraction = (count.toFloat() / maxBar.toFloat()).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (count > 0) count.toString() else "",
                        color = primaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkGray),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(if (count > 0) 0.08f else 0f))
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (count > 0) primaryColor else DarkGray)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = day.dayOfWeek.name.take(1),
                        color = LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Consistency card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(DarkBlack)
                .border(
                    1.dp,
                    primaryColor.copy(alpha = 0.22f),
                    RoundedCornerShape(22.dp)
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.12f))
                    .border(2.dp, primaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = primaryColor, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Consistency",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (workoutStreak > 0)
                        "$workoutStreak-day streak — keep it alive!"
                    else
                        "Start your first workout to build your streak.",
                    color = LightGray,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "$goalPercent%",
                color = primaryColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Achievement
        Text(
            text = "Achievements",
            color = White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        val firstWorkoutDate = workoutHistory
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .minOrNull()
        val achievements = buildAchievementBadges(
            gender = gender,
            completedWorkouts = completedWorkouts,
            firstWorkoutDate = firstWorkoutDate
        )

        achievements.forEach { badge ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (badge.unlocked) primaryColor.copy(alpha = 0.10f) else DarkBlack)
                    .border(
                        1.dp,
                        if (badge.unlocked) primaryColor.copy(alpha = 0.28f)
                        else White.copy(alpha = 0.07f),
                        RoundedCornerShape(17.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = badge.imageRes),
                    contentDescription = badge.title,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer(alpha = if (badge.unlocked) 1f else 0.30f),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = badge.title,
                        color = if (badge.unlocked) White else LightGray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = badge.requirement,
                        color = if (badge.unlocked) primaryColor else LightGray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (badge.unlocked) "Unlocked" else "Locked",
                        color = if (badge.unlocked) primaryColor else LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (goal.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkBlack)
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎯", fontSize = 23.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Current Goal", color = LightGray, fontSize = 11.sp)
                    Text(goal, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
    }
}

@Composable
private fun ImprovedProgressStat(
    modifier: Modifier,
    icon: String,
    value: String,
    label: String,
    primaryColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkBlack)
            .border(
                1.dp,
                White.copy(alpha = 0.08f),
                RoundedCornerShape(20.dp)
            )
            .padding(vertical = 15.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 23.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = primaryColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = LightGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ============================================================
// STEP 18 - MORE / PROGRESS / STREAKS / DAILY NOTIFICATIONS
// ============================================================

private const val MOTIVATION_CHANNEL_ID = "gym_partner_motivation"
private const val MOTIVATION_NOTIFICATION_ID = 1801
private const val MOTIVATION_REQUEST_BASE = 1900

private val motivationMessages = listOf(
    "🔥 Show up today. Your future self will thank you.",
    "💪 One workout at a time. Stay consistent, stay strong.",
    "⚡ Discipline beats motivation. Keep moving.",
    "🏋️ You do not need a perfect workout—just start.",
    "🎯 Small progress every day becomes a big transformation.",
    "🔥 Keep your streak alive. Today is another chance to win.",
    "💚 Train smart, recover well, and keep going."
)

private fun createMotivationNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        MOTIVATION_CHANNEL_ID,
        "Daily Motivation",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
}

private fun cancelMotivationNotifications(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
        val intent = Intent(context, MotivationNotificationReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, MOTIVATION_REQUEST_BASE + day, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pi)
        pi.cancel()
    }
}

private fun nextOccurrenceForWeekday(weekday: Int, hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.DAY_OF_WEEK, weekday)
    }
    if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.WEEK_OF_YEAR, 1)
    return next.timeInMillis
}

private fun canUseExactMotivationAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            })
        } catch (_: Exception) {
            // Fall back silently; the scheduler below will still use an inexact alarm.
        }
    }
}

private fun scheduleSingleMotivationAlarm(
    context: Context,
    alarmManager: AlarmManager,
    triggerAt: Long,
    pendingIntent: PendingIntent
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            // User-facing reminders: use exact delivery when the user has granted
            // the Alarms & reminders special access.
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            // Safe fallback when exact-alarm access is unavailable.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    } catch (_: SecurityException) {
        // If special access was revoked between the check and the call,
        // fall back to an inexact idle-aware alarm.
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Nothing more can be done from the background receiver.
        }
    }
}

private fun scheduleMotivationNotifications(context: Context, enabled: Boolean, hour: Int, minute: Int, repeatMode: String, selectedDays: Set<Int>) {
    cancelMotivationNotifications(context)
    if (!enabled) return
    createMotivationNotificationChannel(context)
    val days = if (repeatMode == "daily") (Calendar.SUNDAY..Calendar.SATURDAY).toSet() else selectedDays
    if (days.isEmpty()) return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    days.forEach { weekday ->
        val intent = Intent(context, MotivationNotificationReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            MOTIVATION_REQUEST_BASE + weekday,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = nextOccurrenceForWeekday(weekday, hour, minute)
        scheduleSingleMotivationAlarm(context, alarmManager, triggerAt, pi)
    }
}

class MotivationNotificationReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("gym_partner_preferences", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("motivation_notifications_enabled", false)) return
        if (!areGymPartnerNotificationsEnabled(context)) return
        createMotivationNotificationChannel(context)
        val index = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) % motivationMessages.size
        val notification = NotificationCompat.Builder(context, MOTIVATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GYM PARTNER AI 💪")
            .setContentText(motivationMessages[index])
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(MOTIVATION_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return
        }
        val hour = prefs.getInt("motivation_notification_hour", 7)
        val minute = prefs.getInt("motivation_notification_minute", 0)
        val mode = prefs.getString("motivation_repeat_mode", "daily") ?: "daily"
        val days = prefs.getStringSet("motivation_selected_days", (Calendar.SUNDAY..Calendar.SATURDAY).map { it.toString() }.toSet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        scheduleMotivationNotifications(context, true, hour, minute, mode, days)
    }
}

class MotivationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != "android.intent.action.MY_PACKAGE_REPLACED") return
        val prefs = context.getSharedPreferences("gym_partner_preferences", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("motivation_notifications_enabled", false)) return
        val hour = prefs.getInt("motivation_notification_hour", 7)
        val minute = prefs.getInt("motivation_notification_minute", 0)
        val mode = prefs.getString("motivation_repeat_mode", "daily") ?: "daily"
        val days = prefs.getStringSet("motivation_selected_days", (Calendar.SUNDAY..Calendar.SATURDAY).map { it.toString() }.toSet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        scheduleMotivationNotifications(context, true, hour, minute, mode, days)
    }
}

private fun loadProfileBitmapSafely(context: Context, uriString: String): Bitmap? {
    if (uriString.isBlank()) return null
    return runCatching {
        val uri = uriString.toUri()
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val maxDimension = 768
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

@Composable
private fun DemoModeScreen(
    primaryColor: Color,
    onExitDemo: () -> Unit,
    onOpenFeature: (String) -> Unit
) {
    val demoSteps = listOf(
        listOf("🏋️", "1. Workout & Exercise", "Browse workout options and open the workout screen.", "workout"),
        listOf("🤖", "2. AI Voice Assistant", "Ask questions by typing or using the microphone. Voice input requires microphone permission.", "ai"),
        listOf("🥗", "3. Diet Planner", "Explore meal ideas and food preferences.", "diet"),
        listOf("📈", "4. Progress & Streaks", "View workout progress, streaks and history.", "progress"),
        listOf("📊", "5. Improved Progress", "Explore the expanded progress tracker.", "improved_progress"),
        listOf("👤", "6. Profile & Settings", "Update profile information and explore reminders in More.", "more")
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Black).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("GymPartner Demo", color = primaryColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("A guided tour of your app", color = LightGray, fontSize = 14.sp)
            }
            TextButton(onClick = onExitDemo) {
                Text("✕ Exit Demo", color = primaryColor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        MoreCard(primaryColor) {
            Text("Welcome! 👋", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Follow the steps below. Tap ‘Open Feature’ to try that module. You can return to More and enter Demo Mode again anytime.", color = LightGray, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        demoSteps.forEach { step ->
            MoreCard(primaryColor) {
                Text("${step[0]}  ${step[1]}", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                Text(step[2], color = LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onOpenFeature(step[3]) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Black)
                ) { Text("Open Feature  →", fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Button(
            onClick = onExitDemo,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Black)
        ) { Text("✕ Exit Demo and Return to More", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun MoreScreen(
    name: String,
    profilePhotoUri: String,
    age: Int,
    heightCm: Float,
    weightKg: Float,
    completedWorkouts: Int,
    workoutStreak: Int,
    goal: String,
    primaryColor: Color,
    preferences: SharedPreferences,
    onRequestNotificationPermission: () -> Unit,
    onVoiceAssistantToggle: (Boolean) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onProfileUpdated: (String, Int, Float, Float, String) -> Unit,
    onProfilePhotoChanged: (String) -> Unit
) {
    val context = LocalContext.current

    var showProfileEditor by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(name) }
    var editAge by remember { mutableStateOf(age.toString()) }
    var editHeight by remember { mutableStateOf(if (heightCm > 0f) String.format(Locale.getDefault(), "%.1f", heightCm) else "") }
    var editWeight by remember { mutableStateOf(if (weightKg > 0f) String.format(Locale.getDefault(), "%.1f", weightKg) else "") }
    var editGoal by remember { mutableStateOf(goal) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val profilePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onProfilePhotoChanged(uri.toString())
        }
    }

    LaunchedEffect(profilePhotoUri) {
        profileBitmap = loadProfileBitmapSafely(context, profilePhotoUri)
    }

    var enabled by remember { mutableStateOf(preferences.getBoolean("motivation_notifications_enabled", false)) }
    var hour by remember { mutableStateOf(preferences.getInt("motivation_notification_hour", 7)) }
    var minute by remember { mutableStateOf(preferences.getInt("motivation_notification_minute", 0)) }
    var mode by remember { mutableStateOf(preferences.getString("motivation_repeat_mode", "daily") ?: "daily") }
    var days by remember { mutableStateOf(preferences.getStringSet("motivation_selected_days", (Calendar.SUNDAY..Calendar.SATURDAY).map { it.toString() }.toSet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()) }
    val dayItems = listOf(Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat", Calendar.SUNDAY to "Sun")
    val goalCompletion = min(completedWorkouts * 10, 100)
    val timeText = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    fun saveAndSchedule() {
        preferences.edit().putBoolean("motivation_notifications_enabled", enabled).putInt("motivation_notification_hour", hour).putInt("motivation_notification_minute", minute).putString("motivation_repeat_mode", mode).putStringSet("motivation_selected_days", days.map { it.toString() }.toSet()).apply()
        scheduleMotivationNotifications(context, enabled, hour, minute, mode, days)
    }

    Column(modifier = Modifier.fillMaxSize().background(Black).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("More", color = primaryColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Your journey, your rules 💪", color = LightGray, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(18.dp))

        MoreCard(primaryColor) {
            Text("🎬  Demo Mode", color = primaryColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Explore all GymPartner features step by step, with buttons to open each real module.", color = LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onNavigate("demo") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Black)
            ) {
                Text("Start App Demo  →", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        MoreCard(primaryColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProfileEditor = !showProfileEditor },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.12f))
                        .border(2.dp, primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = profileBitmap!!.asImageBitmap(),
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text("👤", fontSize = 30.sp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Tap to view / edit profile", color = LightGray, fontSize = 14.sp)
                }
                Text(if (showProfileEditor) "⌃" else "›", color = LightGray, fontSize = 28.sp)
            }

            if (showProfileEditor) {
                Spacer(modifier = Modifier.height(16.dp))
                MoreHorizontalDivider()
                Text("Profile", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { profilePhotoLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBlack,
                        contentColor = primaryColor
                    )
                ) {
                    Text("📷  Change Profile Picture", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = DarkGray,
                        focusedLabelColor = primaryColor,
                        unfocusedLabelColor = LightGray,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editAge,
                        onValueChange = { editAge = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Age") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor, unfocusedBorderColor = DarkGray,
                            focusedLabelColor = primaryColor, unfocusedLabelColor = LightGray,
                            focusedTextColor = White, unfocusedTextColor = White
                        )
                    )
                    OutlinedTextField(
                        value = editHeight,
                        onValueChange = { editHeight = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Height (cm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor, unfocusedBorderColor = DarkGray,
                            focusedLabelColor = primaryColor, unfocusedLabelColor = LightGray,
                            focusedTextColor = White, unfocusedTextColor = White
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editWeight,
                    onValueChange = { editWeight = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor, unfocusedBorderColor = DarkGray,
                        focusedLabelColor = primaryColor, unfocusedLabelColor = LightGray,
                        focusedTextColor = White, unfocusedTextColor = White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editGoal,
                    onValueChange = { editGoal = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Goal") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor, unfocusedBorderColor = DarkGray,
                        focusedLabelColor = primaryColor, unfocusedLabelColor = LightGray,
                        focusedTextColor = White, unfocusedTextColor = White
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val newAge = editAge.toIntOrNull()?.coerceAtLeast(0) ?: age
                        val newHeight = editHeight.toFloatOrNull()?.coerceAtLeast(0f) ?: heightCm
                        val newWeight = editWeight.toFloatOrNull()?.coerceAtLeast(0f) ?: weightKg
                        val newName = editName.trim().ifBlank { name }
                        val newGoal = editGoal.trim().ifBlank { goal }
                        onProfileUpdated(newName, newAge, newHeight, newWeight, newGoal)
                        showProfileEditor = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = Black
                    )
                ) {
                    Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        MoreCard(primaryColor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("progress") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📊  Progress & Streaks", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Track your journey, celebrate your wins!", color = LightGray, fontSize = 14.sp)
                    }
                    Text("›", color = primaryColor, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    MoreStat("🔥", workoutStreak.toString(), "Day Streak", primaryColor, Modifier.weight(1f)); MoreDivider()
                    MoreStat("📅", completedWorkouts.toString(), "Total Workouts", primaryColor, Modifier.weight(1f)); MoreDivider()
                    MoreStat("🎯", "$goalCompletion%", "Goal Completion", primaryColor, Modifier.weight(1f))
                }

                if (goal.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Goal: $goal", color = LightGray, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        MoreCard(primaryColor) {
            Text("🎙️  Chat Voice Input", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                "The app does not listen in the background. Open GYM PARTNER AI and tap the mic beside Send to ask a question in English; the chatbot will speak its reply.",
                color = LightGray,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        MoreCard(primaryColor) {
            Text("🔔  Notification Settings", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Get daily motivation & reminders", color = LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            MoreSettingRow("☀", "Daily Motivation Notifications", "Receive inspiring messages every day") {
                androidx.compose.material3.Switch(checked = enabled, onCheckedChange = {
                    if (it) {
                        val notificationsAlreadyEnabled = areGymPartnerNotificationsEnabled(context)
                        onRequestNotificationPermission()
                        enabled = true
                        saveAndSchedule()
                        if (notificationsAlreadyEnabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            !canUseExactMotivationAlarms(context)
                        ) {
                            openExactAlarmSettings(context)
                        }
                    } else {
                        enabled = false
                        saveAndSchedule()
                    }
                })
            }
            MoreHorizontalDivider()
            MoreSettingRow("🧪", "Test Notification", "Send a notification now to verify permissions") {
                Button(
                    onClick = {
                        if (!areGymPartnerNotificationsEnabled(context)) {
                            onRequestNotificationPermission()
                        } else {
                            sendTestMotivationNotification(context)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBlack,
                        contentColor = primaryColor
                    )
                ) {
                    Text("🔔 Test", fontWeight = FontWeight.Bold)
                }
            }
            MoreHorizontalDivider()
            MoreSettingRow("⏰", "Exact Reminder Time", "Allow Android to deliver the reminder as close as possible to your selected time") {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (!canUseExactMotivationAlarms(context)) {
                                openExactAlarmSettings(context)
                            } else {
                                saveAndSchedule()
                            }
                        } else {
                            saveAndSchedule()
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBlack,
                        contentColor = primaryColor
                    )
                ) {
                    Text(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canUseExactMotivationAlarms(context)) "✓ Allowed" else "Allow",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            MoreHorizontalDivider()
            MoreSettingRow("◷", "Notification Time", "Set when you want to receive notifications") {
                Button(onClick = {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m; saveAndSchedule() }, hour, minute, false).show()
                }, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = DarkBlack, contentColor = primaryColor)) { Text("◷  $timeText  ›", fontWeight = FontWeight.Bold) }
            }
            MoreHorizontalDivider()
            Text("📅  Repeat On", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Choose which days you want to receive notifications", color = LightGray, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(10.dp))
            MoreDayButton("Daily", mode == "daily", primaryColor) {
                mode = "daily"; days = (Calendar.SUNDAY..Calendar.SATURDAY).toSet(); saveAndSchedule()
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                dayItems.forEach { (day, label) ->
                    MoreDayButton(label, mode == "custom" && day in days, primaryColor, Modifier.weight(1f)) {
                        mode = "custom"
                        val updatedDays = if (day in days) days - day else days + day
                        days = if (updatedDays.isEmpty()) setOf(day) else updatedDays
                        saveAndSchedule()
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        MoreCard(primaryColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoreIconCircle("i", primaryColor); Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) { Text("App Information", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Version 1.0.0", color = LightGray, fontSize = 13.sp) }
                Text("›", color = LightGray, fontSize = 34.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun MoreCard(primaryColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DarkBlack).border(1.dp, primaryColor.copy(alpha = 0.28f), RoundedCornerShape(22.dp)).padding(18.dp), content = content)
}

@Composable
private fun MoreIconCircle(text: String, primaryColor: Color) {
    Box(modifier = Modifier.size(58.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.12f)).border(2.dp, primaryColor, CircleShape), contentAlignment = Alignment.Center) { Text(text, color = primaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun MoreStat(emoji: String, value: String, label: String, primaryColor: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(emoji, fontSize = 28.sp); Text(value, color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(label, color = LightGray, fontSize = 12.sp, textAlign = TextAlign.Center) }
}

@Composable
private fun MoreDivider() { Box(modifier = Modifier.width(1.dp).height(78.dp).background(DarkGray)) }

@Composable
private fun MoreHorizontalDivider() { Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(DarkGray)); Spacer(modifier = Modifier.height(10.dp)) }

@Composable
private fun MoreSettingRow(icon: String, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = GymGreen, fontSize = 24.sp, modifier = Modifier.width(40.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) { Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LightGray, fontSize = 12.sp) }
        trailing()
    }
}

@Composable
private fun MoreDayButton(label: String, selected: Boolean, primaryColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected) primaryColor else DarkGray, contentColor = if (selected) Black else White)) { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun DailyDietPlannerScreen(
    name: String,
    age: Int,
    ageGroup: String,
    goal: String,
    vegetarian: Boolean,
    onVegetarianChanged: (Boolean) -> Unit,
    primaryColor: Color
) {
    val context = LocalContext.current
    val dietPrefs = remember(context) {
        context.getSharedPreferences("gym_partner_diet_preferences", Context.MODE_PRIVATE)
    }
    var dietaryNotes by remember { mutableStateOf(dietPrefs.getString("dietary_notes", "") ?: "") }
    var workoutTime by remember { mutableStateOf(dietPrefs.getString("workout_time", "Evening") ?: "Evening") }
    var availableMinutes by remember { mutableStateOf(dietPrefs.getInt("available_minutes", 20)) }
    var painArea by remember { mutableStateOf(dietPrefs.getString("pain_area", "None") ?: "None") }
    var workoutFocus by remember { mutableStateOf(dietPrefs.getString("workout_focus", "Full body") ?: "Full body") }

    val dayNumber = LocalDate.now().dayOfYear
    val meals = getDailyDietPlan(
        ageGroup = ageGroup,
        vegetarian = vegetarian,
        dayIndex = dayNumber
    )
    val ageFriendlyNote = when {
        age in 5..17 -> "Growing body: eat regular balanced meals and snacks; avoid restrictive diets or calorie targets."
        age >= 65 -> "Choose comfortable, varied meals and adjust texture or fluids to your personal needs and clinician advice."
        else -> "Build meals around familiar carbohydrates, vegetables or fruit, and a protein-rich food."
    }
    val goalNote = when (goal.lowercase(Locale.getDefault())) {
        "muscle gain", "build muscle", "muscle building" -> "For strength goals, include protein-containing foods regularly and eat enough overall."
        "weight loss", "fat loss" -> if (age in 5..17) "For teens, focus on energy, strength, sleep and growth rather than weight-loss diets." else "Avoid crash diets; aim for balanced meals and sustainable habits."
        else -> "Adjust portions to hunger, activity and your usual routine; this guide does not set calorie limits."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Text(
            text = "Daily Diet Planner",
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Hi $name 👋  •  Age $age  •  $ageGroup",
            color = primaryColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        if (goal.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Goal: $goal",
                color = LightGray,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(DarkBlack).border(1.dp, primaryColor.copy(alpha = 0.65f), RoundedCornerShape(18.dp)).padding(16.dp)
        ) {
            Column {
                Text("✨ Personalized for you", color = primaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(ageFriendlyNote, color = White, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(goalNote, color = LightGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("When do you usually work out?", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Morning", "Afternoon", "Evening").forEach { option ->
                        Button(
                            onClick = { workoutTime = option; dietPrefs.edit().putString("workout_time", option).apply() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (workoutTime == option) primaryColor else DarkGray, contentColor = if (workoutTime == option) Black else White)
                        ) { Text(option, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (workoutTime) {
                        "Morning" -> "Timing idea: have breakfast after training, or a small familiar snack before exercise if you feel you need it."
                        "Afternoon" -> "Timing idea: eat your usual lunch around your workout and have a snack if you feel hungry afterward."
                        else -> "Timing idea: have your usual meals during the day and a comfortable snack before or after your evening session if needed."
                    }, color = LightGray, fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = dietaryNotes,
                    onValueChange = { dietaryNotes = it; dietPrefs.edit().putString("dietary_notes", it).apply() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Allergies / foods to avoid (optional)") },
                    placeholder = { Text("e.g. peanuts, lactose, foods I dislike") },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White, unfocusedTextColor = White,
                        focusedBorderColor = primaryColor, unfocusedBorderColor = DarkGray,
                        focusedLabelColor = primaryColor, unfocusedLabelColor = LightGray,
                        cursorColor = primaryColor
                    )
                )
                if (dietaryNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Reminder: use these notes to review the meal ideas; this guide cannot guarantee allergen-free food or prevent cross-contact. Check labels and ask a qualified health professional for serious allergies.", color = LightGray, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkBlack)
                .border(1.dp, primaryColor, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "🌿 Food Preference",
                    color = White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (vegetarian) "Vegetarian plan selected" else "Mixed plan selected",
                    color = LightGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onVegetarianChanged(false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!vegetarian) primaryColor else DarkGray,
                            contentColor = if (!vegetarian) Black else White
                        )
                    ) {
                        Text("Mixed", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onVegetarianChanged(true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vegetarian) primaryColor else DarkGray,
                            contentColor = if (vegetarian) Black else White
                        )
                    ) {
                        Text("Vegetarian", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Time-based workout planner with conservative pain-aware alternatives.
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(DarkBlack).border(1.dp, primaryColor.copy(alpha = 0.7f), RoundedCornerShape(18.dp)).padding(16.dp)
        ) {
            Column {
                Text("🏃 Smart Workout Planner", color = primaryColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("How much time do you have?", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(10, 15, 20, 30, 45).forEach { minutes ->
                        Button(onClick = { availableMinutes = minutes; dietPrefs.edit().putInt("available_minutes", minutes).apply() },
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 7.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (availableMinutes == minutes) primaryColor else DarkGray, contentColor = if (availableMinutes == minutes) Black else White)) {
                            Text("${minutes}m", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Focus", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Full body", "Upper body", "Lower body", "Mobility").forEach { focus ->
                        Button(onClick = { workoutFocus = focus; dietPrefs.edit().putString("workout_focus", focus).apply() },
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (workoutFocus == focus) primaryColor else DarkGray, contentColor = if (workoutFocus == focus) Black else White)) {
                            Text(focus, fontSize = 9.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Any pain or discomfort today?", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("None", "Knee", "Back", "Shoulder", "Other").forEach { area ->
                        Button(onClick = { painArea = area; dietPrefs.edit().putString("pain_area", area).apply() },
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 1.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (painArea == area) primaryColor else DarkGray, contentColor = if (painArea == area) Black else White)) {
                            Text(area, fontSize = 9.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val suggestions = when {
                    painArea != "None" -> listOf("Skip movements that cause pain", "Choose gentle, pain-free mobility only", "Stop if pain increases; tell a trusted adult/health professional if it persists")
                    workoutFocus == "Mobility" -> listOf("Gentle warm-up — 3 min", "Comfortable mobility and stretching — ${maxOf(3, availableMinutes - 5)} min", "Easy cool-down — 2 min")
                    workoutFocus == "Upper body" -> listOf("Warm-up — 3 min", "Wall push-ups and light arm raises — ${maxOf(3, availableMinutes - 5)} min", "Cool-down — 2 min")
                    workoutFocus == "Lower body" -> listOf("Warm-up — 3 min", "Chair sit-to-stands and calf raises, only if comfortable — ${maxOf(3, availableMinutes - 5)} min", "Cool-down — 2 min")
                    else -> listOf("Warm-up — 3 min", "Easy marching, wall push-ups and chair sit-to-stands — ${maxOf(3, availableMinutes - 5)} min", "Cool-down — 2 min")
                }
                Text("Your ${availableMinutes}-minute ${workoutFocus.lowercase(Locale.getDefault())} plan", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(5.dp))
                suggestions.forEach { item -> Text("• $item", color = LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                if (painArea != "None") {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text("Safety first: this is not a diagnosis. Do not train through pain; avoid the affected area and get help for severe, worsening or persistent pain.", color = Color(0xFFFFB4A8), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Use a comfortable pace, breathe normally, and stop if dizzy, unwell or in pain. This planner gives general suggestions, not medical advice.", color = LightGray, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Today's Plan",
            color = White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        meals.forEach { meal ->
            DietMealCard(
                meal = meal,
                primaryColor = primaryColor
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkGreen.copy(alpha = 0.45f))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "💧 Hydration",
                    color = primaryColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Drink water regularly through the day and adjust to your thirst, activity and local conditions.",
                    color = LightGray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "GYM PARTNER AI: This is a general offline meal guide, not a medical diet. For allergies, medical conditions, or special dietary needs, check with a qualified adult/health professional.",
            color = LightGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun DietMealCard(
    meal: DietMeal,
    primaryColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkBlack)
            .border(1.dp, DarkGray, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = meal.title,
                color = primaryColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = meal.foods,
                color = White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = meal.tip,
                color = LightGray,
                fontSize = 12.sp
            )
        }
    }
}
