package com.shoaib.tgforward.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shoaib.tgforward.engine.AuthStep
import com.shoaib.tgforward.engine.ForwardEngine

@Composable
fun AuthScreen(engine: ForwardEngine, step: AuthStep, busy: Boolean, error: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Auto Post", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        when (step) {
            AuthStep.NeedApi -> ApiForm(engine, busy)
            AuthStep.Connecting -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Telegram se connect ho raha hai...")
            }
            AuthStep.Phone -> PhoneForm(engine, busy)
            is AuthStep.Code -> CodeForm(engine, step.hint, busy)
            is AuthStep.Password -> PasswordForm(engine, step.hint, busy)
            AuthStep.Email -> EmailForm(engine, busy)
            AuthStep.EmailCode -> EmailCodeForm(engine, busy)
            AuthStep.Registration -> RegisterForm(engine, busy)
            is AuthStep.OtherDevice -> Text("Doosre device par confirm karo:\n${step.link}")
            AuthStep.Ready -> Unit
        }
        if (step !is AuthStep.NeedApi && step !is AuthStep.Connecting) {
            TextButton(onClick = engine::resetApi) { Text("API ID badlo") }
        }
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ApiForm(engine: ForwardEngine, busy: Boolean) {
    var apiId by rememberSaveable { mutableStateOf("") }
    var apiHash by rememberSaveable { mutableStateOf("") }
    Text(
        "my.telegram.org se api_id aur api_hash",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = apiId,
        onValueChange = { apiId = it.filter { ch -> ch.isDigit() } },
        label = { Text("API ID") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = apiHash,
        onValueChange = { apiHash = it.trim() },
        label = { Text("API hash") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { engine.saveApi(apiId, apiHash) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Aage") }
}

@Composable
private fun PhoneForm(engine: ForwardEngine, busy: Boolean) {
    var phone by rememberSaveable { mutableStateOf("+91") }
    Text("Jis number par Telegram chalta hai, woh country code ke saath likho.")
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Phone") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { engine.submitPhone(phone) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (busy) "Bhej rahe hain..." else "Code bhejo") }
}

@Composable
private fun CodeForm(engine: ForwardEngine, hint: String, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    Text(hint)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter { ch -> ch.isDigit() } },
        label = { Text("Code") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { engine.submitCode(code) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (busy) "Check ho raha hai..." else "Login") }
    TextButton(onClick = engine::resendCode, enabled = !busy) { Text("Code dobara bhejo") }
}

@Composable
private fun PasswordForm(engine: ForwardEngine, hint: String, busy: Boolean) {
    var password by rememberSaveable { mutableStateOf("") }
    Text("Is account par 2FA password laga hai.")
    if (hint.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text("Hint: $hint", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { engine.submitPassword(password) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Confirm") }
}

@Composable
private fun EmailForm(engine: ForwardEngine, busy: Boolean) {
    var email by rememberSaveable { mutableStateOf("") }
    Text("Telegram email maang raha hai.")
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { engine.submitEmail(email) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Bhejo") }
}

@Composable
private fun EmailCodeForm(engine: ForwardEngine, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    Text("Email par aaya code dalo.")
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.trim() },
        label = { Text("Email code") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { engine.submitEmailCode(code) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Confirm") }
}

@Composable
private fun RegisterForm(engine: ForwardEngine, busy: Boolean) {
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    Text("Yeh number Telegram par naya hai. Naam likho.")
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(first, { first = it }, label = { Text("First name") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(last, { last = it }, label = { Text("Last name") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = { engine.submitRegistration(first, last) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Account banao") }
}
