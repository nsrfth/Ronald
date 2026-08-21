package com.ronald.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    error: String?,
    busy: Boolean,
    onSubmit: (String, String, String) -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    val userFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { userFocus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ورود به حساب", style = MaterialTheme.typography.headlineMedium)

        error?.let {
            Text(it, color = Color(0xFFE57373), textAlign = TextAlign.Center)
        }

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { androidx.compose.material3.Text("نام کاربری یا ایمیل") },
            singleLine = true,
            modifier = Modifier.width(420.dp).focusRequester(userFocus),
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { androidx.compose.material3.Text("رمز عبور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(420.dp),
        )
        OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it },
            label = { androidx.compose.material3.Text("شماره موبایل حساب") },
            singleLine = true,
            modifier = Modifier.width(420.dp),
        )
        Text(
            "سایت برای تکمیل ورود، شماره موبایل حساب را می‌خواهد.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFAFC4D0),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { if (!busy) onSubmit(user.trim(), pass, mobile.trim()) },
        ) { Text(if (busy) "در حال ورود…" else "ورود") }
    }
}
