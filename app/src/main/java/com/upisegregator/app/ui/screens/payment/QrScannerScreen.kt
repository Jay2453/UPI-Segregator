@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.upisegregator.app.ui.screens.payment

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.upisegregator.app.data.upi.UpiQrParseResult
import com.upisegregator.app.data.upi.UpiQrParser
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.components.SecondaryButton
import com.upisegregator.app.ui.theme.CardShape
import com.upisegregator.app.ui.theme.ErrorRed
import com.upisegregator.app.ui.theme.ErrorRedLight
import com.upisegregator.app.ui.theme.PillShape
import com.upisegregator.app.ui.theme.PrimaryBlue
import com.upisegregator.app.ui.theme.PrimaryBlueLight
import com.upisegregator.app.ui.theme.SurfaceLight
import com.upisegregator.app.ui.theme.TextMuted
import com.upisegregator.app.ui.theme.TextPrimary
import com.upisegregator.app.ui.theme.TextSecondary
import java.util.concurrent.Executors

/**
 * Live camera-based QR code scanner for UPI payments.
 *
 * Uses CameraX and Google ML Kit Barcode Scanning for fast, accurate on-device scanning.
 * Conforms to NPCI UPI specifications.
 */
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit,
    onQrScanned: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel
) {
    val context = LocalContext.current
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan UPI QR Code",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                CameraScannerContent(
                    onQrScanned = onQrScanned,
                    viewModel = viewModel
                )
            } else {
                PermissionDeniedContent(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun CameraScannerContent(
    onQrScanned: () -> Unit,
    viewModel: PaymentViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSuccessfullyScanned by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val scanBoxSize = minOf(screenWidth * 0.78f, 290.dp)

        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !hasSuccessfullyScanned && !isProcessing) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            barcodeScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank() && !hasSuccessfullyScanned) {
                                            isProcessing = true
                                            val result = UpiQrParser.parse(rawValue)
                                            when (result) {
                                                is UpiQrParseResult.Success -> {
                                                    hasSuccessfullyScanned = true
                                                    viewModel.onQrCodeScanned(result.data)
                                                    onQrScanned()
                                                    break
                                                }
                                                is UpiQrParseResult.Invalid -> {
                                                    errorMessage = result.reason
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("UPI_QR_SCANNER", "Barcode processing error: ${e.message}")
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera: Camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (exc: Exception) {
                        Log.e("UPI_QR_SCANNER", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Dark Semi-Transparent Scrim Overlay with Cutout
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSizePx = scanBoxSize.toPx()
            val left = (canvasWidth - boxSizePx) / 2f
            val top = (canvasHeight - boxSizePx) / 2.3f

            val backgroundPath = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
            }
            val cutoutPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, left + boxSizePx, top + boxSizePx),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                )
            }

            drawPath(
                path = Path.combine(
                    operation = androidx.compose.ui.graphics.PathOperation.Difference,
                    path1 = backgroundPath,
                    path2 = cutoutPath
                ),
                color = Color(0xCC000000)
            )

            // Scanning reticle border corners
            val cornerLength = 36.dp.toPx()
            val strokeWidth = 4.5.dp.toPx()
            val primaryColor = Color(0xFF38BDF8) // Bright Cyan / Primary accent

            // Top-Left corner
            drawLine(primaryColor, Offset(left, top + cornerLength), Offset(left, top + 16.dp.toPx()), strokeWidth)
            drawArc(primaryColor, 180f, 90f, false, Offset(left, top), Size(32.dp.toPx(), 32.dp.toPx()), style = Stroke(strokeWidth))
            drawLine(primaryColor, Offset(left + 16.dp.toPx(), top), Offset(left + cornerLength, top), strokeWidth)

            // Top-Right corner
            drawLine(primaryColor, Offset(left + boxSizePx - cornerLength, top), Offset(left + boxSizePx - 16.dp.toPx(), top), strokeWidth)
            drawArc(primaryColor, 270f, 90f, false, Offset(left + boxSizePx - 32.dp.toPx(), top), Size(32.dp.toPx(), 32.dp.toPx()), style = Stroke(strokeWidth))
            drawLine(primaryColor, Offset(left + boxSizePx, top + 16.dp.toPx()), Offset(left + boxSizePx, top + cornerLength), strokeWidth)

            // Bottom-Left corner
            drawLine(primaryColor, Offset(left, top + boxSizePx - cornerLength), Offset(left, top + boxSizePx - 16.dp.toPx()), strokeWidth)
            drawArc(primaryColor, 90f, 90f, false, Offset(left, top + boxSizePx - 32.dp.toPx()), Size(32.dp.toPx(), 32.dp.toPx()), style = Stroke(strokeWidth))
            drawLine(primaryColor, Offset(left + 16.dp.toPx(), top + boxSizePx), Offset(left + cornerLength, top + boxSizePx), strokeWidth)

            // Bottom-Right corner
            drawLine(primaryColor, Offset(left + boxSizePx - cornerLength, top + boxSizePx), Offset(left + boxSizePx - 16.dp.toPx(), top + boxSizePx), strokeWidth)
            drawArc(primaryColor, 0f, 90f, false, Offset(left + boxSizePx - 32.dp.toPx(), top + boxSizePx - 32.dp.toPx()), Size(32.dp.toPx(), 32.dp.toPx()), style = Stroke(strokeWidth))
            drawLine(primaryColor, Offset(left + boxSizePx, top + boxSizePx - 16.dp.toPx()), Offset(left + boxSizePx, top + boxSizePx - cornerLength), strokeWidth)

            // Laser Beam Animation
            val laserY = top + (boxSizePx * laserPosition)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x0038BDF8),
                        Color(0xFF38BDF8),
                        Color(0xFF67E8F9),
                        Color(0xFF38BDF8),
                        Color(0x0038BDF8)
                    ),
                    startX = left + 10.dp.toPx(),
                    endX = left + boxSizePx - 10.dp.toPx()
                ),
                topLeft = Offset(left + 8.dp.toPx(), laserY - 1.5.dp.toPx()),
                size = Size(boxSizePx - 16.dp.toPx(), 3.dp.toPx())
            )
        }

        // Top Controls: Flashlight Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, end = 24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        val next = !isTorchOn
                        cameraControl?.enableTorch(next)
                        isTorchOn = next
                    }
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isTorchOn) "Turn Flash Off" else "Turn Flash On",
                        tint = if (isTorchOn) Color(0xFFFACC15) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Instructions and Feedback at Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isProcessing) {
                Card(
                    shape = PillShape,
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE0F172A)),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryBlue
                        )
                        Text(
                            text = "Reading UPI QR details…",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SecondaryButton(
                            text = "Scan Again",
                            onClick = {
                                errorMessage = null
                                isProcessing = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(Color(0x88000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Point camera at any UPI QR code to pay",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PrimaryBlueLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Camera Access Required",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "UPI Segregator needs camera permission to scan recipient QR codes like Google Pay and PhonePe. Your camera is only used while scanning.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.75f),
                lineHeight = 20.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        PrimaryButton(
            text = "Grant Camera Permission",
            onClick = onRequestPermission
        )

        Spacer(modifier = Modifier.height(12.dp))

        SecondaryButton(
            text = "Cancel",
            onClick = onNavigateBack
        )
    }
}
