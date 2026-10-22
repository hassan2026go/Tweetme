package com.tweetme.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tweetme.app.R
import com.tweetme.app.core.errorKind
import com.tweetme.app.core.errorRes
import com.tweetme.app.data.ChatBackend
import kotlinx.coroutines.launch

private val USERNAME_RE = Regex("^[a-z0-9_]{3,20}$")

@Composable
fun AuthScreen(backend: ChatBackend) {
    val scope = rememberCoroutineScope()
    var signup by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var info by remember { mutableStateOf<Int?>(null) }

    val ltr = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)

    fun submit() {
        error = null
        info = null
        if (signup) {
            if (name.trim().length < 2) { error = R.string.err_name; return }
            if (!USERNAME_RE.matches(username)) { error = R.string.err_username_format; return }
        }
        busy = true
        scope.launch {
            try {
                if (signup) backend.signUp(name.trim(), username, email.trim(), password)
                else backend.signIn(email.trim(), password)
            } catch (e: Exception) {
                error = errorRes(e.errorKind())
            } finally {
                busy = false
            }
        }
    }

    fun forgot() {
        error = null
        info = null
        if (email.isBlank()) { error = R.string.err_email_first; return }
        scope.launch {
            try {
                backend.resetPassword(email.trim())
                info = R.string.info_reset_sent
            } catch (e: Exception) {
                error = errorRes(e.errorKind())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(84.dp))
        Text(
            text = buildAnnotatedString {
                append("Tweet ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("me") }
            },
            fontSize = 30.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(if (signup) R.string.auth_lead_signup else R.string.auth_lead_login),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // تبديل دخول / حساب جديد
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(3.dp)) {
                listOf(false to R.string.tab_login, true to R.string.tab_signup).forEach { (isSignup, label) ->
                    val selected = signup == isSignup
                    Surface(
                        modifier = Modifier.weight(1f).clickable { signup = isSignup; error = null; info = null },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            stringResource(label),
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (signup) {
            OutlinedTextField(
                value = name, onValueChange = { if (it.length <= 40) name = it },
                label = { Text(stringResource(R.string.f_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { v ->
                    username = v.lowercase().filter { (it.isLetterOrDigit() && it.code < 128) || it == '_' }.take(20)
                },
                label = { Text(stringResource(R.string.f_username)) }, singleLine = true,
                textStyle = ltr, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.f_username_hint)) }
            )
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text(stringResource(R.string.f_email)) }, singleLine = true,
            textStyle = ltr, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.f_password)) }, singleLine = true,
            textStyle = ltr, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.toggle_password)
                    )
                }
            }
        )

        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        info?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }

        Button(
            onClick = { submit() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(
                    if (busy) R.string.please_wait else if (signup) R.string.btn_create_account else R.string.btn_login
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (!signup) {
            TextButton(onClick = { forgot() }) { Text(stringResource(R.string.forgot_password)) }
        }
        if (backend.isDemo) {
            Text(
                stringResource(R.string.demo_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp, textAlign = TextAlign.Center
            )
        }
    }
}
