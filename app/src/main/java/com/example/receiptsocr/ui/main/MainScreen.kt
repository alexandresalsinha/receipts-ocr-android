package com.example.receiptsocr.ui.main

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(data) { item ->
            Text(text = "Hello $item!")
        }
    }
}
