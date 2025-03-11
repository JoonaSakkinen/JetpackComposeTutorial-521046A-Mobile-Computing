package com.substainable.jetpackcomposetutorial

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.substainable.jetpackcomposetutorial.ui.theme.JetpackComposeTutorialTheme
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider


class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var sensorEventListener: SensorEventListener
    private var lastShakeTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JetpackComposeTutorialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "conversation") {
                    composable("conversation") {
                        ConversationScreen(navController)
                    }
                    composable("profile") {
                        ProfileScreen(navController)
                    }
                }
            }
        }

        // Initialize sensor manager and accelerometer
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        // Define the sensor event listener for accelerometer
        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        val x = it.values[0]
                        val y = it.values[1]
                        val z = it.values[2]

                        // Calculate the acceleration
                        val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble())

                        // Threshold for shake detection
                        if (acceleration > 12) { // Example threshold value
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastShakeTime > 1000) {
                                // Trigger the shake action only once per second
                                lastShakeTime = currentTime
                                onShakeDetected()
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    override fun onResume() {
        super.onResume()
        // Register the sensor event listener
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        // Unregister the sensor event listener when the activity is paused
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun onShakeDetected() {
        // Handle the shake event (e.g., log or display a message)
        Log.d("ShakeEvent", "Device was shaken!")
    }
}

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val body: String,
    val photoPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<Message>>

    @Insert
    suspend fun insertMessage(message: Message)
}

@Database(entities = [Message::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Composable
fun ConversationScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val messageDao = db.messageDao()
    val messages by messageDao.getAllMessages().collectAsState(initial = emptyList())
    var newMessageText by remember { mutableStateOf("") }

    // State to track if camera permission is granted
    var isCameraPermissionGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )}

    // Track if Camera button was clicked before
    var hasCameraButtonBeenClicked by remember { mutableStateOf(false) }

    // Launchers for handling permissions and actions
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val photoFile = saveImageToInternalStorage(context, uri, "message_photo_${System.currentTimeMillis()}.jpg")
            photoFile?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    messageDao.insertMessage(Message(body = "", photoPath = it.absolutePath))
                }
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val photoFile = File(context.filesDir, "message_photo_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "com.substainable.jetpackcomposetutorial.provider",  // Package + .provider
                photoFile
            )
            CoroutineScope(Dispatchers.IO).launch {
                messageDao.insertMessage(Message(body = "", photoPath = photoFile.absolutePath))
            }
        }
    }

    // Request camera permission
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        isCameraPermissionGranted = isGranted
        if (isGranted) {
            val photoFile = File(context.filesDir, "message_photo_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "com.substainable.jetpackcomposetutorial.provider",  // Package + .provider
                photoFile
            )
            takePicture.launch(photoUri)
        } else {
            Log.d("Permission", "Camera permission denied")
        }
    }

    Column {
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true // Most recent at bottom
        ) {
            items(messages.reversed()) { message ->
                MessageCard(message, navController)
            }
        }
        Row(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = newMessageText,
                onValueChange = { newMessageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newMessageText.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        messageDao.insertMessage(Message(body = newMessageText))
                        newMessageText = ""
                    }
                }
            }) {
                Text("Send")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text("Photo")
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Camera Button
            Button(
                onClick = {
                    if (hasCameraButtonBeenClicked) {
                        // If clicked before, only launch if permission is granted
                        if (isCameraPermissionGranted) {
                            val photoFile = File(context.filesDir, "message_photo_${System.currentTimeMillis()}.jpg")
                            val photoUri = FileProvider.getUriForFile(
                                context,
                                "com.substainable.jetpackcomposetutorial.provider",
                                photoFile
                            )
                            takePicture.launch(photoUri)
                        } else {
                            // Disable the button if permission is denied
                            // Update the state to disable it for subsequent clicks
                        }
                    } else {
                        // First time clicked, ask for permission
                        hasCameraButtonBeenClicked = true
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !hasCameraButtonBeenClicked || isCameraPermissionGranted // First time enabled, after that only if permission granted
            ) {
                Text("Camera")
            }
        }
    }
}

@Composable
fun MessageCard(msg: Message, navController: NavController) {
    var profileImage by remember { mutableStateOf<String?>(null) }
    var userName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        profileImage = getSavedImagePathFromPreferences(context)
        userName = getSavedUserNameFromPreferences(context)
    }

    Row(modifier = Modifier.padding(all = 8.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(profileImage ?: R.drawable.kallio)
                .build(),
            contentDescription = "some photo",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable { navController.navigate("profile") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        var isExpanded by remember { mutableStateOf(false) }
        val surfaceColor by animateColorAsState(
            if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        )
        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            Text(
                text = userName ?: "Default Name",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 1.dp,
                color = surfaceColor,
                modifier = Modifier.animateContentSize().padding(1.dp)
            ) {
                Column {
                    if (msg.body.isNotEmpty()) {
                        Text(
                            text = msg.body,
                            modifier = Modifier.padding(all = 4.dp),
                            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    msg.photoPath?.let { path ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(File(path)).build(),
                            contentDescription = "photo",
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(200.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val profileImage = getSavedImagePathFromPreferences(context)
    val userName = getSavedUserNameFromPreferences(context)

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(profileImage ?: R.drawable.kallio)
                .build(),
            contentDescription = "profile photo",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Username: $userName",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

fun getSavedImagePathFromPreferences(context: Context): String? {
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    return prefs.getString("profile_image", null)
}

fun getSavedUserNameFromPreferences(context: Context): String? {
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    return prefs.getString("user_name", "Default Name")
}

fun saveImageToInternalStorage(context: Context, uri: Uri, fileName: String): File? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        outputStream.flush()
        return file
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
