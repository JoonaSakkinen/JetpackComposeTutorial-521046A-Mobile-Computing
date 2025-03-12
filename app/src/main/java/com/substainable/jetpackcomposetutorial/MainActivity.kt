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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JetpackComposeTutorialTheme {
                // Create NavController for navigation
                val navController = rememberNavController()

                // Set up navigation host
                NavHost(navController = navController, startDestination = "conversation") {
                    // Conversation screen
                    composable("conversation") {
                        ConversationScreen(navController)
                    }

                    // Profile screen
                    composable("profile") {
                        ProfileScreen(navController)  // Profile screen
                    }
                }
            }
        }
    }
}

@Composable
fun AccelerometerListener() {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    var accelerometerData by remember { mutableStateOf("X: 0, Y: 0, Z: 0") }

    val sensorEventListener = remember {
        object : SensorEventListener {
            var lastX = 0f
            var lastY = 0f
            var lastZ = 0f

            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    if (lastX == 0f && lastY == 0f && lastZ == 0f) {
                        lastX = x
                        lastY = y
                        lastZ = z
                    }
                    if (Math.abs(x - lastX) > 1 || Math.abs(y - lastY) > 1 || Math.abs(z - lastZ) > 1) {
                        accelerometerData = "X: $x, Y: $y, Z: $z"
                        showNotification(context, accelerometerData)
                        lastX = x
                        lastY = y
                        lastZ = z
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    // Use DisposableEffect to manage lifecycle
    DisposableEffect(Unit) {
        sensorManager.registerListener(
            sensorEventListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    Text(text = "Accelerometer Data: $accelerometerData")
}

fun showNotification(context: Context, data: String) {
    // Create a PendingIntent that will open the MainActivity
    val intent = Intent(context, MainActivity::class.java).apply {
        // Adding extra data to the intent
        putExtra("acceleration_data", data)
    }

    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context,
        0, // Request code
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Create the NotificationManager
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Create NotificationChannel if not created already
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "accelerometer_channel",
            "Accelerometer Data",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    // Build the notification
    val notification: Notification = NotificationCompat.Builder(context, "accelerometer_channel")
        .setContentTitle("Accelerometer detected a change: ")
        .setContentText(data)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent) // Set the PendingIntent for the notification click
        .setAutoCancel(true) // Automatically dismiss the notification when clicked
        .build()

    // Send the notification
    notificationManager.notify(1, notification)
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
    var isCameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraButtonBeenClicked by remember { mutableStateOf(false) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    // Define pickMedia for selecting photos from gallery
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val photoFile = saveImageToInternalStorage(context, uri)
            photoFile?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    messageDao.insertMessage(Message(body = "", photoPath = it.absolutePath))
                }
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoFile != null) {
            Log.d("Camera", "Photo saved at: ${photoFile!!.absolutePath}, exists: ${photoFile!!.exists()}, size: ${photoFile!!.length()}")
            if (photoFile!!.exists() && photoFile!!.length() > 0) {
                CoroutineScope(Dispatchers.IO).launch {
                    messageDao.insertMessage(Message(body = "", photoPath = photoFile!!.absolutePath))
                }
            } else {
                Log.e("Camera", "Photo file is empty or doesn't exist after capture")
            }
        } else {
            Log.d("Camera", "Photo capture failed or file is null")
        }
    }

    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        isCameraPermissionGranted = isGranted
        if (isGranted && hasCameraButtonBeenClicked) {
            photoFile = File(context.filesDir, "message_photo_${System.currentTimeMillis()}.jpg").apply {
                // So the problem was that the camera app for some reason required the file to already exist before
                // being able to write to it. Here we are making sure the file exists beforehand.
                if (!exists()) {
                    parentFile?.mkdirs()
                    createNewFile()
                }
                Log.d("Camera", "Created file at: $absolutePath")
            }
            val photoUri = FileProvider.getUriForFile(
                context,
                "com.substainable.jetpackcomposetutorial.provider",
                photoFile!!
            )
            takePicture.launch(photoUri)
        }
    }

    Column {
        LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
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
            Button(
                onClick = {
                    if (hasCameraButtonBeenClicked) {
                        if (isCameraPermissionGranted) {
                            photoFile = File(context.filesDir, "message_photo_${System.currentTimeMillis()}.jpg").apply {
                                if (!exists()) {
                                    parentFile?.mkdirs()
                                    createNewFile()
                                }
                                Log.d("Camera", "Created file at: $absolutePath")
                            }
                            val photoUri = FileProvider.getUriForFile(
                                context,
                                "com.substainable.jetpackcomposetutorial.provider",
                                photoFile!!
                            )
                            takePicture.launch(photoUri)
                        }
                    } else {
                        hasCameraButtonBeenClicked = true
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !hasCameraButtonBeenClicked || isCameraPermissionGranted
            ) {
                Text("Camera")
            }
        }
    }
}

@Composable
fun ProfileScreen(navController: NavController) {
    // Creating a mutable state to store the selected image
    var profileImage by remember { mutableStateOf<String?>(null) }
    // Creating a mutable state to store the username
    var userName by remember { mutableStateOf<String?>(null) }
    // Context value
    val context = LocalContext.current
    // Retrieve the saved image path from SharedPreferences when the screen is first loaded
    LaunchedEffect(Unit) {
        profileImage = getSavedImagePathFromPreferences(context)
        userName = getSavedUserNameFromPreferences(context)
    }
    // Create pickMedia for picking photos
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uri != null) {
                // Save the selected image to internal storage
                val savedImageFile = saveImageToInternalStorage(context, uri)

                savedImageFile?.let {
                    profileImage = it.absolutePath
                    saveImagePathToPreferences(context, it.absolutePath)  // Save the file path to SharedPreferences
                }
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

    JetpackComposeTutorialTheme {
        var name by remember {
            mutableStateOf("")
        }
        Column(modifier = Modifier.padding(16.dp)) {
            // launching Accelerometer listener
            AccelerometerListener()

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profileImage ?: R.drawable.kallio)
                    .build(),
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if(userName.isNullOrEmpty()){
                Text(text = "Default Name", style = MaterialTheme.typography.titleSmall)
            } else {
                Text(text = userName.toString(), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Somebody is somebody, biography anf stuff.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { text -> name = text })
            Button(
                onClick = { // Changing name
                    if (name.isNotBlank()) {
                        userName = name
                        saveUserNameToPreferences(context, name)
                    }
                }) {
                Text("Change name")
            }
            Button(
                onClick = { // Launch the photo picker and let the user choose only images.
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Text("Change profile picture")
            }
            Button(
                onClick = { navController.popBackStack() }
            ) {
                Text("Go Back")
            }
        }
    }
}

fun sendTestNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Create notification channel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "test_channel",
            "Test Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    // Build the notification
    val notification = NotificationCompat.Builder(context, "test_channel")
        .setContentTitle("Test Notification")
        .setContentText("This is a test notification.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    // Send the notification
    notificationManager.notify(1, notification)
}

fun saveImageToInternalStorage(context: Context, uri: Uri): File? {
    try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val oldFile = getSavedImagePathFromPreferences(context)?.let { File(it) }
        oldFile?.delete() // Delete the old file if it exists,
                    // this fixes the problem where GUI didn't refresh for the new profile photo
        val file = File(context.filesDir, "profile_picture_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return file
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun saveImagePathToPreferences(context: Context, imagePath: String) {
    val sharedPreferences = context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString("profile_image_path", imagePath)  // Save the path
    editor.apply()
}

fun saveUserNameToPreferences(context: Context, userName: String) {
    val sharedPreferences = context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString("user_name", userName)  // Save the path
    editor.apply()
}

fun getSavedImagePathFromPreferences(context: Context): String? {
    val sharedPreferences = context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)
    return sharedPreferences.getString("profile_image_path", null)  // Return the path if it exists, or null if not
}

fun getSavedUserNameFromPreferences(context: Context): String? {
    val sharedPreferences = context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)
    return sharedPreferences.getString("user_name", null)  // Return the path if it exists, or null if not
}

//object SampleData {
//    // Sample conversation data
//
//    val conversationSample = listOf(
//        Message(
//            "Test...Test...Test..."
//        ),
//        Message(
//
//            """List of Android versions:
//            |Android KitKat (API 19)
//            |Android Lollipop (API 21)
//            |Android Marshmallow (API 23)
//            |Android Nougat (API 24)
//            |Android Oreo (API 26)
//            |Android Pie (API 28)
//            |Android 10 (API 29)
//            |Android 11 (API 30)
//            |Android 12 (API 31)""".trim()
//        ),
//        Message(
//
//            """I think Kotlin is my favorite programming language.
//            |It's so much fun!""".trim()
//        ),
//        Message(
//
//            "Searching for alternatives to XML layouts..."
//        ),
//        Message(
//
//            """Hey, take a look at Jetpack Compose, it's great!
//            |It's the Android's modern toolkit for building native UI.
//            |It simplifies and accelerates UI development on Android.
//            |Less code, powerful tools, and intuitive Kotlin APIs :)""".trim()
//        ),
//        Message(
//
//            "It's available from API 21+ :)"
//        ),
//        Message(
//
//            "Writing Kotlin for UI seems so natural, Compose where have you been all my life?"
//        ),
//        Message(
//
//            "Android Studio next version's name is Arctic Fox"
//        ),
//        Message(
//
//            "Android Studio Arctic Fox tooling for Compose is top notch ^_^"
//        ),
//        Message(
//
//            "I didn't know you can now run the emulator directly from Android Studio"
//        ),
//        Message(
//
//            "Compose Previews are great to check quickly how a composable layout looks like"
//        ),
//        Message(
//
//            "Previews are also interactive after enabling the experimental setting"
//        ),
//        Message(
//
//            "Have you tried writing build.gradle with KTS?"
//        ),
//    )
//}
