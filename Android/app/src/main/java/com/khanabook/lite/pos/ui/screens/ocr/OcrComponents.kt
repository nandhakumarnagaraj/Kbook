package com.khanabook.lite.pos.ui.screens.ocr

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.ErrorPink
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.ParchmentBG
import com.khanabook.lite.pos.ui.theme.PrimaryGold

@Composable
internal fun CameraPreview(previewViewState: MutableState<PreviewView?>) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView =
                PreviewView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview =
                        Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview
                        )
                    } catch (e: Exception) {
                        Log.e("CAMERA", "Binding failed", e)
                    }
                },
                ContextCompat.getMainExecutor(ctx)
            )

            previewViewState.value = previewView
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            previewViewState.value = null
        }
    }
}

@Composable
internal fun ScanControls(
    selectedCategoryName: String?,
    hasCapturedPhoto: Boolean,
    isProcessing: Boolean,
    processingLabel: String,
    errorMessage: String?,
    returnBarcode: Boolean = false,
    onCapturePhoto: () -> Unit,
    onUsePhoto: () -> Unit,
    onRetake: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(KhanaBookTheme.spacing.medium).navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(KhanaBookTheme.spacing.medium)) {
                Text(
                    when {
                        !hasCapturedPhoto && !returnBarcode -> buildString {
                            append("AI will extract menu items for ")
                            if (!selectedCategoryName.isNullOrBlank()) {
                                append('"'); append(selectedCategoryName); append('"')
                            } else {
                                append("the selected category")
                            }
                        }
                        !hasCapturedPhoto && returnBarcode -> "Point at a barcode or text to scan."
                        isProcessing -> processingLabel
                        else -> "Photo captured. Use this or retake."
                    },
                    color = ParchmentBG,
                    style = MaterialTheme.typography.bodyMedium
                )

                
                if (!hasCapturedPhoto && !returnBarcode) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.extraSmall))
                    Text(
                        "Tip: Ensure menu is fully in frame, well-lit, text in English.",
                        color = PrimaryGold.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (errorMessage != null) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(com.khanabook.lite.pos.ui.theme.KhanaBookTheme.spacing.small))
                    Text(
                        text = errorMessage,
                        color = ErrorPink,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.smallMedium))

                if (!hasCapturedPhoto) {
                    Button(
                        onClick = onCapturePhoto,
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = PrimaryGold,
                                contentColor = DarkBrown1
                            )
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.size(KhanaBookTheme.spacing.small))
                        Text("Capture Photo", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.smallMedium)
                    ) {
                        OutlinedButton(
                            onClick = onRetake,
                            modifier = Modifier.weight(1f),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = PrimaryGold
                                )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.size(KhanaBookTheme.spacing.extraSmall))
                            Text("Retake")
                        }

                        Button(
                            onClick = onUsePhoto,
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = PrimaryGold,
                                    contentColor = DarkBrown1
                                )
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(KhanaBookTheme.iconSize.small),
                                    color = DarkBrown1,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.size(KhanaBookTheme.spacing.small))
                                Text("Analysing...", fontWeight = FontWeight.Bold)
                            } else {
                                Text("Use Photo", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PermissionDeniedContent(onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(KhanaBookTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Camera access is required to scan menus.",
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(com.khanabook.lite.pos.ui.theme.KhanaBookTheme.spacing.medium))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
        ) {
            Text("Grant Permission", color = DarkBrown1)
        }
        TextButton(
            onClick = {
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                context.startActivity(intent)
            }
        ) {
            Text("Open Settings", color = PrimaryGold)
        }
    }
}
