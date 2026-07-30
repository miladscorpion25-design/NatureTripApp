package com.example.naturetrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class ChecklistItem(
    val id: String = "",
    val title: String = "",
    val assignedTo: String = "", // اسم کسی که مسئولیتش رو برعهده گرفته
    val isDone: Boolean = false
)

class MainActivity : ComponentActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ورود ناشناس در صورت عدم ورود قبلی
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }

        setContent {
            MaterialTheme {
                MainAppScreen(db, auth)
            }
        }
    }
}

@Composable
fun MainAppScreen(db: FirebaseFirestore, auth: FirebaseAuth) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("چک‌لیست سفر", "مقصد سفر", "چت گروهی")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> SmartChecklistScreen(db, auth)
            1 -> DestinationScreen(db)
            2 -> GroupChatScreen(db, auth)
        }
    }
}

@Composable
fun SmartChecklistScreen(db: FirebaseFirestore, auth: FirebaseAuth) {
    var items by remember { mutableStateOf(listOf<ChecklistItem>()) }
    var newItemText by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("کاربر " + (auth.currentUser?.uid?.take(4) ?: "")) }

    // بارگیری و همگام‌سازی لحظه‌ای لیست از فایربیس
    LaunchedEffect(Unit) {
        db.collection("shared_checklist").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ChecklistItem::class.java)?.copy(id = doc.id)
                }
                items = list
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("نام شما جهت ثبت مسئولیت:", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = { Text("افزودن وسیله جدید...") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        val newItem = mapOf("title" to newItemText, "assignedTo" to "", "isDone" to false)
                        db.collection("shared_checklist").add(newItem)
                        newItemText = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("افزودن")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // افزودن آیتم‌های پیش‌فرض در صورت خالی بودن دیتابیس
        if (items.isEmpty()) {
            Button(
                onClick = {
                    val defaultItems = listOf("قوری و چای", "چادر مسافرتی", "زیرانداز", "منقل و زغال", "آب معدنی", "کنسرو و غذا", "کیت کمک‌های اولیه")
                    defaultItems.forEach { title ->
                        db.collection("shared_checklist").add(mapOf("title" to title, "assignedTo" to "", "isDone" to false))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("بارگیری اقلام پیش‌فرض سفر")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(items) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        val newAssignee = if (item.assignedTo.isEmpty()) userName else ""
                        db.collection("shared_checklist").document(item.id).update(
                            "assignedTo", newAssignee,
                            "isDone", newAssignee.isNotEmpty()
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (item.assignedTo.isNotEmpty()) "برعهده: ${item.assignedTo}" else "کسی برنداشته ➕",
                            color = if (item.assignedTo.isNotEmpty()) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationScreen(db: FirebaseFirestore) {
    var destination by remember { mutableStateOf("") }
    var locationUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("trip_info").document("destination").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                destination = snapshot.getString("name") ?: ""
                locationUrl = snapshot.getString("url") ?: ""
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("مقصد سفر گروه:", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("نام مکان (مثلاً: جنگل دالخانی)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = locationUrl,
            onValueChange = { locationUrl = it },
            label = { Text("لینک گوگل مپ یا نشان") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Button(
            onClick = {
                val data = mapOf("name" to destination, "url" to locationUrl)
                db.collection("trip_info").document("destination").set(data)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("ثبت و اشتراک‌گذاری با همه")
        }
    }
}

@Composable
fun GroupChatScreen(db: FirebaseFirestore, auth: FirebaseAuth) {
    Text("بخش چت آنلاین گروهی")
}
