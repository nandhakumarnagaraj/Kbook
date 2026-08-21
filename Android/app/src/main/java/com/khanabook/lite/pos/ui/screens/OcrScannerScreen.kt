package com.khanabook.lite.pos.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.ui.screens.ocr.CameraPreview
import com.khanabook.lite.pos.ui.screens.ocr.PermissionDeniedContent
import com.khanabook.lite.pos.ui.screens.ocr.ScanControls
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.ErrorPink
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.ParchmentBG
import com.khanabook.lite.pos.ui.theme.PrimaryGold

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(
    selectedCategoryName: String? = null,
    viewModel: com.khanabook.lite.pos.ui.viewmodel.MenuViewModel,
    onBack: () -> Unit,
    navController: androidx.navigation.NavController? = null,
    returnBarcode: Boolean = false
) {
    val context = LocalContext.current
    val uiState by viewModel.ocrImportUiState.collectAsStateWithLifecycle()

    // Clear any stale drafts from a previous scan so they don't immediately
    // trigger the back-navigation LaunchedEffect below before a new scan runs.
    LaunchedEffect(Unit) {
        if (!returnBarcode) viewModel.clearDrafts()
    }

    LaunchedEffect(uiState.drafts) {
        if (!returnBarcode && uiState.drafts.isNotEmpty() && !uiState.isProcessing) {
            onBack()
        }
    }

    // PDF Launcher restored
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.extractTextFromPdf(context, it)
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val isProcessing = uiState.isProcessing
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val displayError = errorMessage ?: uiState.error

    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val previewViewState = remember { mutableStateOf<PreviewView?>(null) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                errorMessage = null
                capturedBitmap = null
                
                try {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, it))
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    }
                    val bitmapCopy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    if (bitmapCopy != null) {
                        viewModel.processMenuImage(context, bitmapCopy)
                    } else {
                        errorMessage = "Failed to process image: Out of memory"
                    }
                } catch (t: Throwable) {
                    errorMessage = com.khanabook.lite.pos.domain.util.UserMessageSanitizer.sanitize(t, "Failed to load image. Please try again.")
                }
            }
        }

    DisposableEffect(context) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (e: Exception) {
                Log.e("OCR_SCREEN", "Error unbinding camera", e)
            }
            
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (returnBarcode) "Scan Barcode" else "Scan Menu",
                        color = PrimaryGold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                        )
                    }
                },
                actions = {
                    // Gallery icon
                    IconButton(
                        onClick = {
                            errorMessage = null
                            galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = PrimaryGold
                        )
                    }
                    
                    // PDF icon restored (available only in Menu Scan mode)
                    if (!returnBarcode) {
                        IconButton(
                            onClick = { 
                                errorMessage = null
                                pdfLauncher.launch("application/pdf") 
                            }
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "Upload PDF",
                                tint = PrimaryGold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { padding ->
        Box(
            modifier =
                Modifier.padding(padding).fillMaxSize().background(Color.Black)
        ) {
            if (hasCameraPermission) {
                CameraPreview(previewViewState = previewViewState)

                capturedBitmap?.let { bitmap ->
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Captured menu photo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                ScanControls(
                    selectedCategoryName = selectedCategoryName,
                    hasCapturedPhoto = capturedBitmap != null,
                    isProcessing = isProcessing,
                    processingLabel = uiState.processingLabel,
                    errorMessage = displayError,
                    returnBarcode = returnBarcode,
                    onCapturePhoto = {
                        val frozenBitmap = previewViewState.value?.bitmap
                        if (frozenBitmap == null) {
                            errorMessage = "Unable to capture preview. Try again."
                        } else {
                            capturedBitmap = frozenBitmap
                            errorMessage = null
                        }
                    },
                    onUsePhoto = {
                        capturedBitmap?.let { bitmap ->
                            if (returnBarcode) {
                                
                                val image = InputImage.fromBitmap(bitmap, 0)
                                com.google.mlkit.vision.text.TextRecognition
                                    .getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                                    .process(image)
                                    .addOnSuccessListener { visionText ->
                                        val result = visionText.text.trim()
                                        if (result.isNotEmpty()) {
                                            navController?.previousBackStackEntry
                                                ?.savedStateHandle
                                                ?.set("scanned_barcode", result)
                                        }
                                        navController?.popBackStack() ?: onBack()
                                    }
                                    .addOnFailureListener {
                                        navController?.popBackStack() ?: onBack()
                                    }
                            } else {
                                viewModel.processMenuImage(context, bitmap)
                            }
                        }
                    },
                    onRetake = {
                        capturedBitmap = null
                        errorMessage = null
                        viewModel.setProcessing(false)
                    }
                )
            } else {
                PermissionDeniedContent(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }
}

