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
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import androidx.compose.runtime.collectAsState
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.ParchmentBG
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.OcrScannerPink
import java.util.concurrent.Executors

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
    val uiState by viewModel.ocrImportUiState.collectAsState()

    
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
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                errorMessage = null
                capturedBitmap = null
                
                try {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, it))
                    } else {
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    }
                    val bitmapCopy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    if (bitmapCopy != null) {
                        viewModel.processMenuImage(context, bitmapCopy)
                    } else {
                        errorMessage = "Failed to process image: Out of memory"
                    }
                } catch (t: Throwable) {
                    errorMessage = "Failed to load image: ${t.message}"
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
                            galleryLauncher.launch("image/*")
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

                if (capturedBitmap != null) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
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

@Composable
private fun CameraPreview(previewViewState: MutableState<PreviewView?>) {
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
private fun ScanControls(
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
        modifier = Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkBrown2.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                    fontSize = 13.sp
                )

                
                if (!hasCapturedPhoto && !returnBarcode) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tip: Ensure menu is fully in frame, well-lit, text in English.",
                        color = PrimaryGold.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }

                if (errorMessage != null) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = OcrScannerPink,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Capture Photo", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            Spacer(modifier = Modifier.size(6.dp))
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
                                    modifier = Modifier.size(18.dp),
                                    color = DarkBrown1,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.size(8.dp))
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
fun PermissionDeniedContent(onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Camera access is required to scan menus.",
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
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
