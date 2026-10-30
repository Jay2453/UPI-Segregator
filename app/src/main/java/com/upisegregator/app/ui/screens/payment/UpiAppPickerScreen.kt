package com.upisegregator.app.ui.screens.payment

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upisegregator.app.data.upi.UpiApp
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.components.SecondaryButton
import com.upisegregator.app.ui.theme.BackgroundWhite
import com.upisegregator.app.ui.theme.BorderLight
import com.upisegregator.app.ui.theme.CardShape
import com.upisegregator.app.ui.theme.DemoGreen
import com.upisegregator.app.ui.theme.DemoGreenLight
import com.upisegregator.app.ui.theme.ErrorRed
import com.upisegregator.app.ui.theme.ErrorRedLight
import com.upisegregator.app.ui.theme.PillShape
import com.upisegregator.app.ui.theme.PrimaryBlue
import com.upisegregator.app.ui.theme.PrimaryBlueLight
import com.upisegregator.app.ui.theme.SurfaceLight
import com.upisegregator.app.ui.theme.TextMuted
import com.upisegregator.app.ui.theme.TextPrimary
import com.upisegregator.app.ui.theme.TextSecondary

/**
 * Step 2 of 4 — UPI App Selection.
 *
 * Discovers all installed UPI-capable apps and lets the user pick one.
 * The user can also toggle whether to save their selection as the preferred app.
 * Handles gracefully: no apps found, preferred app uninstalled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiAppPickerScreen(
    amountPaise: Long,
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel = viewModel(factory = PaymentViewModel.Factory)
) {
    val context = LocalContext.current
    val state by viewModel.pickerState.collectAsState()
    var saveAsPreferred by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUpiApps(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Choose UPI App",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Step indicator
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(PrimaryBlueLight)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "STEP 2 OF 4 — SELECT UPI APP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Discovering UPI apps…",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                        }
                    }
                }

                state.noAppsFound -> {
                    NoUpiAppsCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    SecondaryButton(text = "Go Back", onClick = onNavigateBack)
                }

                else -> {
                    // Preferred app notice
                    if (state.preferredPackage != null) {
                        val preferred = state.availableApps.firstOrNull { it.packageName == state.preferredPackage }
                        if (preferred != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CardShape,
                                colors = CardDefaults.cardColors(containerColor = DemoGreenLight),
                                border = BorderStroke(1.dp, DemoGreen.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Star, null, tint = DemoGreen, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Preferred: ${preferred.displayName}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = DemoGreen.copy(alpha = 0.9f)
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    Text(
                        text = "COMPATIBLE UPI APPS ON THIS DEVICE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.8.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.availableApps) { app ->
                            UpiAppCard(
                                app = app,
                                isSelected = state.selectedApp?.packageName == app.packageName,
                                isPreferred = app.packageName == state.preferredPackage,
                                onSelect = { viewModel.onUpiAppSelected(app) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Save as preferred toggle
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Set as preferred app",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "UPI Segregator will open this app automatically next time.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = saveAsPreferred,
                                onCheckedChange = { saveAsPreferred = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue, checkedTrackColor = PrimaryBlueLight)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    PrimaryButton(
                        text = "Continue to Review →",
                        enabled = state.selectedApp != null,
                        onClick = {
                            val selected = state.selectedApp
                            if (selected != null) {
                                if (saveAsPreferred) viewModel.savePreferredApp(selected)
                                viewModel.prepareReview(amountPaise)
                                onContinue()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun UpiAppCard(
    app: UpiApp,
    isSelected: Boolean,
    isPreferred: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) PrimaryBlue else BorderLight
    val bgColor = if (isSelected) PrimaryBlueLight.copy(alpha = 0.5f) else BackgroundWhite

    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(app.packageName)
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(bgColor)
            .border(1.5.dp, borderColor, CardShape)
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App icon (native application icon loaded via PackageManager)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLight),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = app.displayName,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isPreferred) {
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(DemoGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PREFERRED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = DemoGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = PrimaryBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NoUpiAppsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No UPI Apps Found",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed
                )
            )
            Text(
                text = "UPI Segregator could not find any compatible UPI payment app on this device. " +
                        "Please install Google Pay, PhonePe, Paytm, or another NPCI-registered UPI app and try again.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ErrorRed.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            )
        }
    }
}
