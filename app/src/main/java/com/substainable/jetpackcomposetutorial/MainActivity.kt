package com.substainable.jetpackcomposetutorial

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.substainable.jetpackcomposetutorial.ui.theme.JetpackComposeTutorialTheme

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File
import java.io.FileOutputStream

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
                        ConversationScreen(
                            SampleData.conversationSample,
                            navController
                        )  // Pass NavController to ConversationScreen
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

data class Message(val body: String)

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
                // Set image size to 40 dp
                .size(40.dp)
                // Clip image to be shaped as a circle
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable {
                    navController.navigate("profile")
                }
        )

        Spacer(modifier = Modifier.width(8.dp))

        var isExpanded by remember { mutableStateOf(false) }
        // surfaceColor will be updated gradually from one color to the other
        val surfaceColor by animateColorAsState(
            if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        )

        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            var user by remember { mutableStateOf<String?>(null) }
            if(userName.isNullOrEmpty()){
                user = "Default Name"
            } else {
                user = userName
            }
            Text(
                text = user.toString(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 1.dp,
                // surfaceColor color will be changing gradually from primary to surface
                color = surfaceColor,
                // animateContentSize will change the Surface size gradually
                modifier = Modifier
                    .animateContentSize()
                    .padding(1.dp)
            ) {
                Text(
                    text = msg.body,
                    modifier = Modifier.padding(all = 4.dp),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ConversationScreen(messages: List<Message>, navController: NavController) {
    LazyColumn {
        items(messages) { message ->
            MessageCard(message, navController)
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
                onClick = { // Launch the photo picker and let the user choose only images.
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

fun saveImageToInternalStorage(context: Context, uri: Uri): File? {
    try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "profile_picture.jpg")  // Path to save the image
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

object SampleData {
    // Sample conversation data

    val conversationSample = listOf(
        Message(
            "Test...Test...Test..."
        ),
        Message(

            """List of Android versions:
            |Android KitKat (API 19)
            |Android Lollipop (API 21)
            |Android Marshmallow (API 23)
            |Android Nougat (API 24)
            |Android Oreo (API 26)
            |Android Pie (API 28)
            |Android 10 (API 29)
            |Android 11 (API 30)
            |Android 12 (API 31)""".trim()
        ),
        Message(

            """I think Kotlin is my favorite programming language.
            |It's so much fun!""".trim()
        ),
        Message(

            "Searching for alternatives to XML layouts..."
        ),
        Message(

            """Hey, take a look at Jetpack Compose, it's great!
            |It's the Android's modern toolkit for building native UI.
            |It simplifies and accelerates UI development on Android.
            |Less code, powerful tools, and intuitive Kotlin APIs :)""".trim()
        ),
        Message(

            "It's available from API 21+ :)"
        ),
        Message(

            "Writing Kotlin for UI seems so natural, Compose where have you been all my life?"
        ),
        Message(

            "Android Studio next version's name is Arctic Fox"
        ),
        Message(

            "Android Studio Arctic Fox tooling for Compose is top notch ^_^"
        ),
        Message(

            "I didn't know you can now run the emulator directly from Android Studio"
        ),
        Message(

            "Compose Previews are great to check quickly how a composable layout looks like"
        ),
        Message(

            "Previews are also interactive after enabling the experimental setting"
        ),
        Message(

            "Have you tried writing build.gradle with KTS?"
        ),
    )
}