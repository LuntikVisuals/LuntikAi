package com.luntik.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HumorWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("😂 Юморист — предупреждение", color = Danger) },
        text = {
            Column {
                Text(
                    text = HUMORIST_WARNING_FULL,
                    color = TextMain,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Пролистай текст выше до конца, затем нажми «Продолжить».",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = TextMain)
            ) { Text("Продолжить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
