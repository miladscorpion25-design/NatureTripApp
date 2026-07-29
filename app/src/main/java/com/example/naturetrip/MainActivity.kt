package com.example.naturetrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

@Composable
fun MainApp() {
    val auth = FirebaseAuth.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
    } else {
        HomeScreen(onSignOut = {
            auth.signOut()
            currentUser = null
        })
    }
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUp) "ثبت‌نام در سفر طبیعت" else "ورود به حساب",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("ایمیل") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("رمز عبور") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    if (isSignUp) {
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { onAuthSuccess() }
                            .addOnFailureListener { errorMessage = it.localizedMessage ?: "خطا" }
                    } else {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener { onAuthSuccess() }
                            .addOnFailureListener { errorMessage = it.localizedMessage ?: "خطا" }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSignUp) "ثبت‌نام" else "ورود")
        }
        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "حساب دارید؟ ورود" else "حساب ندارید؟ ثبت‌نام")
        }
        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun HomeScreen(onSignOut: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("چت گروهی", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("چک‌لیست سفر", modifier = Modifier.padding(16.dp))
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> GroupChatScreen()
                1 -> ChecklistScreen()
            }
        }
        Button(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("خروج از حساب")
        }
    }
}

data class ChatMessage(val sender: String = "", val text: String = "")

@Composable
fun GroupChatScreen() {
    val db = FirebaseFirestore.getInstance()
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    val currentUser = FirebaseAuth.getInstance().currentUser?.email ?: "ناشناس"

    LaunchedEffect(Unit) {
        db.collection("chats").get().addOnSuccessListener { snapshot ->
            if (snapshot != null) {
                val list = mutableListOf<ChatMessage>()
                for (doc in snapshot.documents) {
                    val sender = doc.getString("sender") ?: ""
                    val text = doc.getString("text") ?: ""
                    list.add(ChatMessage(sender = sender, text = text))
                }
                messages = list
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = msg.sender, style = MaterialTheme.typography.labelSmall)
                        Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیام شما...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (messageText.isNotBlank()) {
                    val msg = ChatMessage(sender = currentUser, text = messageText)
                    db.collection("chats").add(msg).addOnSuccessListener {
                        messages = messages + msg
                        messageText = ""
                    }
                }
            }) {
                Text("ارسال")
            }
        }
    }
}

data class ChecklistItem(val id: String = "", val title: String = "", val isChecked: Boolean = false)

@Composable
fun ChecklistScreen() {
    val db = FirebaseFirestore.getInstance()
    var itemTitle by remember { mutableStateOf("") }
    var itemsList by remember { mutableStateOf(listOf<ChecklistItem>()) }

    LaunchedEffect(Unit) {
        db.collection("checklist").get().addOnSuccessListener { snapshot ->
            if (snapshot != null) {
                val list = mutableListOf<ChecklistItem>()
                for (doc in snapshot.documents) {
                    val id = doc.id
                    val title = doc.getString("title") ?: ""
                    val isChecked = doc.getBoolean("isChecked") ?: false
                    list.add(ChecklistItem(id = id, title = title, isChecked = isChecked))
                }
                itemsList = list
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = itemTitle,
                onValueChange = { itemTitle = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("وسایل مورد نیاز سفر...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (itemTitle.isNotBlank()) {
                    val newItemMap = hashMapOf("title" to itemTitle, "isChecked" to false)
                    db.collection("checklist").add(newItemMap).addOnSuccessListener { docRef ->
                        val newItem = ChecklistItem(id = docRef.id, title = itemTitle, isChecked = false)
                        itemsList = itemsList + newItem
                        itemTitle = ""
                    }
                }
            }) {
                Text("افزودن")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(itemsList) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = { checked ->
                            db.collection("checklist").document(item.id).update("isChecked", checked)
                            itemsList = itemsList.map {
                                if (it.id == item.id) it.copy(isChecked = checked) else it
                            }
                        }
                    )
                    Text(text = item.title, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
