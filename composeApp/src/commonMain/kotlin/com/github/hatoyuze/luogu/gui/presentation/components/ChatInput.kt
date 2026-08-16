package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .onKeyEvent { event ->
                    if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                        if (!event.isShiftPressed && text.isNotBlank()) {
                            onSendMessage(text.trim())
                            text = ""
                            true
                        } else false
                    } else false
                },
            placeholder = { Text("Type a message...") },
            enabled = enabled,
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSendMessage(text.trim())
                    text = ""
                }
            },
            enabled = enabled && text.isNotBlank(),
        ) {
            Text("➤", style = MaterialTheme.typography.titleMedium)  // ➤
        }
    }
}
