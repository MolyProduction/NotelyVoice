package com.module.notelycompose.export.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.resources.Res
import com.module.notelycompose.resources.close
import com.module.notelycompose.resources.error
import org.jetbrains.compose.resources.stringResource

@Composable
fun NoSelectionErrorDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(Res.string.error),
                    color = LocalCustomColors.current.bodyContentColor
                )
            },
            text = {
                Text(
                    text = "No selection made, please select an item to export",
                    color = LocalCustomColors.current.bodyContentColor
                )
            },
            confirmButton = {
                androidx.compose.material.TextButton(
                    onClick = onDismiss,
                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
                        backgroundColor = LocalCustomColors.current.shareDialogBackgroundColor,
                        contentColor = LocalCustomColors.current.bodyContentColor
                    ),
                    modifier = Modifier.padding(8.dp)
                ) {
                    androidx.compose.material.Text(text = "Cancel")
                }
            },
            containerColor = LocalCustomColors.current.shareDialogBackgroundColor,
            titleContentColor = LocalCustomColors.current.bodyContentColor,
            shape = RectangleShape
        )
    }
}
