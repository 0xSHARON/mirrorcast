package com.mirrorcast.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.mirrorcast.app.data.SettingsManager
import com.mirrorcast.app.R
import com.mirrorcast.app.discovery.DiscoveredReceiver
import com.mirrorcast.app.signaling.SignalingState
import com.mirrorcast.app.ui.theme.*
import com.mirrorcast.app.webrtc.StreamState
import kotlinx.coroutines.delay

enum class CurrentScreen {
    WELCOME,
    DISCOVERY,
    PAIRING,
    PERMISSION,
    MIRRORING,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorCastScreen(
    signalingState: SignalingState,
    streamState: StreamState,
    discoveredReceivers: List<DiscoveredReceiver>,
    themeMode: String = "system",
    onThemeChange: (String) -> Unit = {},
    onScanQrClick: () -> Unit,
    onConnectReceiver: (DiscoveredReceiver, String) -> Unit,
    onStopMirroring: () -> Unit,
    onRefreshDiscovery: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var screen by remember { mutableStateOf(CurrentScreen.WELCOME) }
    var selectedReceiver by remember { mutableStateOf<DiscoveredReceiver?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var selectedConnectionMode by remember { mutableStateOf("WiFi") } // WiFi, USB, Internet
    var showManualIpDialog by remember { mutableStateOf(false) }

    // Auto-advance screen state based on stream & signaling transitions
    LaunchedEffect(streamState) {
        if (streamState is StreamState.Streaming || streamState is StreamState.Capturing) {
            screen = CurrentScreen.MIRRORING
        } else if (screen == CurrentScreen.MIRRORING && streamState is StreamState.Idle) {
            screen = CurrentScreen.DISCOVERY
        }
    }

    LaunchedEffect(signalingState) {
        if (signalingState is SignalingState.Paired) {
            screen = CurrentScreen.PERMISSION
        }
    }

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            if (screen != CurrentScreen.WELCOME) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (screen == CurrentScreen.SETTINGS) "Settings" else "MirrorCast",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextSlatePrimary
                        )
                    },
                    navigationIcon = {
                        if (screen != CurrentScreen.DISCOVERY) {
                            IconButton(onClick = {
                                screen = when (screen) {
                                    CurrentScreen.PAIRING -> CurrentScreen.DISCOVERY
                                    CurrentScreen.PERMISSION -> CurrentScreen.PAIRING
                                    CurrentScreen.MIRRORING -> CurrentScreen.DISCOVERY
                                    CurrentScreen.SETTINGS -> CurrentScreen.DISCOVERY
                                    else -> CurrentScreen.DISCOVERY
                                }
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSlatePrimary)
                            }
                        }
                    },
                    actions = {
                        if (screen != CurrentScreen.SETTINGS) {
                            IconButton(onClick = { screen = CurrentScreen.SETTINGS }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSlateSecondary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBackground)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (screen) {
                CurrentScreen.WELCOME -> WelcomeScreen(
                    onGetStarted = { screen = CurrentScreen.DISCOVERY }
                )
                CurrentScreen.DISCOVERY -> DiscoveryScreen(
                    connectionMode = selectedConnectionMode,
                    onModeChange = { selectedConnectionMode = it },
                    receivers = discoveredReceivers,
                    onConnectClick = { receiver ->
                        selectedReceiver = receiver
                        enteredPin = ""
                        screen = CurrentScreen.PAIRING
                    },
                    onConnectUsb = {
                        val usbReceiver = DiscoveredReceiver(
                            name = "Windows PC (USB)",
                            host = "127.0.0.1",
                            port = 8090
                        )
                        selectedReceiver = usbReceiver
                        onConnectReceiver(usbReceiver, "0000")
                    },
                    onManualIpClick = { showManualIpDialog = true },
                    onScanQrClick = onScanQrClick,
                    onRefresh = onRefreshDiscovery
                )
                CurrentScreen.PAIRING -> PairingScreen(
                    receiver = selectedReceiver ?: DiscoveredReceiver(
                        name = if (selectedConnectionMode == "USB") "Windows PC (USB)" else "Windows Receiver",
                        host = if (selectedConnectionMode == "USB") "127.0.0.1" else (discoveredReceivers.firstOrNull()?.host ?: "127.0.0.1"),
                        port = 8090
                    ),
                    pin = enteredPin,
                    signalingState = signalingState,
                    onPinChange = { enteredPin = it },
                    onConnect = { receiver, pin ->
                        onConnectReceiver(receiver, pin)
                    },
                    onScanQrInstead = onScanQrClick
                )
                CurrentScreen.PERMISSION -> PermissionScreen(
                    onGrant = onRequestPermission
                )
                CurrentScreen.MIRRORING -> MirroringScreen(
                    receiverName = selectedReceiver?.name ?: "Windows PC",
                    streamState = streamState,
                    onStop = {
                        onStopMirroring()
                        screen = CurrentScreen.DISCOVERY
                    }
                )
                CurrentScreen.SETTINGS -> SettingsScreen(
                    currentTheme = themeMode,
                    onThemeChange = onThemeChange,
                    onBack = { screen = CurrentScreen.DISCOVERY }
                )
            }

            // Manual IP Dialog
            if (showManualIpDialog) {
                ManualConnectionDialog(
                    initialHost = discoveredReceivers.firstOrNull { it.host != "127.0.0.1" }?.host ?: "",
                    onDismiss = { showManualIpDialog = false },
                    onConnect = { host, port, pin ->
                        showManualIpDialog = false
                        val customReceiver = DiscoveredReceiver("Windows PC", host, port)
                        selectedReceiver = customReceiver
                        enteredPin = pin
                        onConnectReceiver(customReceiver, pin)
                    }
                )
            }
        }
    }
}

// =========================================================================
// SCREEN 1: WELCOME SCREEN
// =========================================================================
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Center Hero Illustration
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(PrimaryBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "MirrorCast Logo",
                    modifier = Modifier.size(76.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "MirrorCast",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextSlatePrimary
            )

            Text(
                text = "Mirror Your Android Screen to Windows PC",
                fontSize = 14.sp,
                color = TextSlateSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val context = LocalContext.current
            Text(
                text = "by 0xSHARON",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/0xSHARON"))
                            context.startActivity(intent)
                        } catch (e: Exception) { }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Feature Highlights
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    FeatureRow(Icons.Default.Hd, "High Quality Mirroring")
                    Spacer(modifier = Modifier.height(14.dp))
                    FeatureRow(Icons.Default.Security, "Secure & Private")
                    Spacer(modifier = Modifier.height(14.dp))
                    FeatureRow(Icons.Default.Link, "Easy Connection")
                    Spacer(modifier = Modifier.height(14.dp))
                    FeatureRow(Icons.Default.Speed, "Ultra Low Latency")
                }
            }
        }

        // Get Started Button
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Get Started  →", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryBlueLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSlatePrimary)
    }
}

// =========================================================================
// SCREEN 2: DEVICE DISCOVERY SCREEN
// =========================================================================
@Composable
fun DiscoveryScreen(
    connectionMode: String,
    onModeChange: (String) -> Unit,
    receivers: List<DiscoveredReceiver>,
    onConnectClick: (DiscoveredReceiver) -> Unit,
    onConnectUsb: () -> Unit,
    onManualIpClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Mode Selector (Wi-Fi, USB, Internet)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppTheme.colors.segmentBg)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ModeChip(Icons.Default.Wifi, "Wi-Fi", connectionMode == "WiFi") { onModeChange("WiFi") }
            ModeChip(Icons.Default.Cable, "USB", connectionMode == "USB") { onModeChange("USB") }
            ModeChip(Icons.Default.Language, "Internet", connectionMode == "Internet") { onModeChange("Internet") }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (connectionMode) {
            "WiFi" -> {
                // WI-FI DISCOVERY VIEW
                val wifiReceivers = receivers.filter { it.host != "127.0.0.1" }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Available Receivers",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSlatePrimary
                        )
                        Text(
                            text = if (wifiReceivers.isEmpty()) "Searching for Windows PCs on your Wi-Fi..." else "${wifiReceivers.size} receiver(s) found",
                            fontSize = 13.sp,
                            color = TextSlateSecondary
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = PrimaryBlue)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (wifiReceivers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = PrimaryBlue,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                "Looking for MirrorCast Receiver...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSlatePrimary
                            )
                            Text(
                                "Make sure MirrorCast is running on your PC\nand connected to the same Wi-Fi network.",
                                fontSize = 12.sp,
                                color = TextSlateSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(wifiReceivers) { receiver ->
                            ReceiverCard(receiver = receiver, onConnect = { onConnectClick(receiver) })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connect Manually Section
                Text(
                    text = "Not found? Connect manually",
                    fontSize = 13.sp,
                    color = TextSlateSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ActionCard(
                    icon = Icons.Default.Language,
                    title = "Connect via PC Address",
                    onClick = onManualIpClick
                )

                Spacer(modifier = Modifier.height(8.dp))

                ActionCard(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Scan QR Code",
                    onClick = onScanQrClick
                )
            }

            "USB" -> {
                // USB DIRECT CONNECTION VIEW
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(PrimaryBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cable,
                            contentDescription = "USB",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "USB Direct Mirroring",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSlatePrimary
                    )

                    Text(
                        text = "Ultra-low latency (~15ms) • 60 FPS • Zero Lag",
                        fontSize = 13.sp,
                        color = TextSlateSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("High-Speed USB Direct Connection", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSlatePrimary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Connected via USB cable. One tap connects directly to your PC receiver with instant pairing and zero configuration.",
                                fontSize = 12.sp,
                                color = TextSlateSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = onConnectUsb,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect via USB", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            "Internet" -> {
                // INTERNET / MANUAL VIEW
                val netReceivers = receivers.filter { it.host != "127.0.0.1" }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect Over Network / Internet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSlatePrimary
                            )
                            Text(
                                text = "Connect with your Windows PC across networks, hotspots, or direct connection.",
                                fontSize = 12.sp,
                                color = TextSlateSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = PrimaryBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (netReceivers.isNotEmpty()) {
                        Text(
                            text = "Available Receivers (${netReceivers.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlateSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(netReceivers) { receiver ->
                                ReceiverCard(receiver = receiver, onConnect = { onConnectClick(receiver) })
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    ActionCard(
                        icon = Icons.Default.QrCodeScanner,
                        title = "Scan QR Code on Windows Screen",
                        onClick = onScanQrClick
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ActionCard(
                        icon = Icons.Default.Edit,
                        title = "Connect via PC Name or Address",
                        onClick = onManualIpClick
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryBlueLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("How to Connect", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryBlue)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "1. Open MirrorCast Receiver on your Windows PC.\n" +
                                "2. Connect both devices to the same network, hotspot, or Wi-Fi.\n" +
                                "3. Tap your PC above, scan the QR code, or enter your PC name.",
                                fontSize = 12.sp,
                                color = TextSlatePrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun ReceiverCard(receiver: DiscoveredReceiver, onConnect: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (receiver.host == "127.0.0.1") Icons.Default.Cable else Icons.Default.Laptop,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(receiver.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextSlatePrimary)
                    Text(
                        text = if (receiver.host == "127.0.0.1") "USB Direct • Ultra-low Latency" else "Windows Receiver • Ready to mirror",
                        fontSize = 12.sp,
                        color = TextSlateSecondary
                    )
                }
            }

            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text("Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ActionCard(icon: ImageVector, title: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSlatePrimary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSlateSecondary)
        }
    }
}

@Composable
fun RowScope.ModeChip(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) LightSurface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PrimaryBlue else TextSlateSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) PrimaryBlue else TextSlateSecondary
            )
        }
    }
}

// =========================================================================
// SCREEN 3: PAIRING SCREEN (4-Digit PIN with interactive numpad)
// =========================================================================
@Composable
fun PairingScreen(
    receiver: DiscoveredReceiver,
    pin: String,
    signalingState: SignalingState,
    onPinChange: (String) -> Unit,
    onConnect: (DiscoveredReceiver, String) -> Unit,
    onScanQrInstead: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Pair with Receiver",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextSlatePrimary
            )
            Text(
                text = "Enter the 4-digit code shown on your Windows PC",
                fontSize = 13.sp,
                color = TextSlateSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Laptop Illustration with Receiver Info
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Laptop, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = receiver.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSlatePrimary
                    )
                    Text(
                        text = "Secure Direct Connection",
                        fontSize = 11.sp,
                        color = TextSlateSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4 Individual Digit Input Boxes
            val digit1 = if (pin.length > 0) pin[0].toString() else ""
            val digit2 = if (pin.length > 1) pin[1].toString() else ""
            val digit3 = if (pin.length > 2) pin[2].toString() else ""
            val digit4 = if (pin.length > 3) pin[3].toString() else ""

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PinDigitBox(digit1, isActive = pin.length == 0)
                PinDigitBox(digit2, isActive = pin.length == 1)
                PinDigitBox(digit3, isActive = pin.length == 2)
                PinDigitBox(digit4, isActive = pin.length == 3)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive On-Screen Numpad for 1-Tap Entry
            Numpad(
                onDigitClick = { digit ->
                    if (pin.length < 4) {
                        val nextPin = pin + digit
                        onPinChange(nextPin)
                        if (nextPin.length == 4) {
                            onConnect(receiver, nextPin)
                        }
                    }
                },
                onBackspace = {
                    if (pin.isNotEmpty()) {
                        onPinChange(pin.dropLast(1))
                    }
                },
                onClear = {
                    onPinChange("")
                }
            )
        }

        // Action Buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            val isConnecting = signalingState is SignalingState.Connecting || signalingState is SignalingState.Connected
            Button(
                onClick = { onConnect(receiver, pin) },
                enabled = pin.length == 4 && !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Waiting for PC approval...", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onScanQrInstead,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryBlue))
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code Instead", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PinDigitBox(digit: String, isActive: Boolean) {
    Box(
        modifier = Modifier
            .size(58.dp, 66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LightSurface)
            .border(
                width = if (isActive || digit.isNotEmpty()) 2.dp else 1.dp,
                color = if (digit.isNotEmpty()) PrimaryBlue else if (isActive) PrimaryBlue else LightCardBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (digit.isEmpty()) "•" else digit,
            fontSize = if (digit.isEmpty()) 24.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (digit.isEmpty()) Color(0xFFCBD5E1) else TextSlatePrimary
        )
    }
}

@Composable
fun Numpad(
    onDigitClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .size(62.dp, 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (key == "C" || key == "⌫") AppTheme.colors.segmentBg else LightSurface)
                            .border(1.dp, LightCardBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                when (key) {
                                    "C" -> onClear()
                                    "⌫" -> onBackspace()
                                    else -> onDigitClick(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSlatePrimary
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// SCREEN 4: PERMISSION SCREEN
// =========================================================================
@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(PrimaryBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ScreenShare,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Screen Capture Permission",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextSlatePrimary
            )

            Text(
                text = "We need your permission to capture your screen.\nThis is required to start mirroring.",
                fontSize = 14.sp,
                color = TextSlateSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onGrant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Grant Permission", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notice Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlueLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "You will see a system dialog to allow screen recording.",
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// =========================================================================
// SCREEN 5: MIRRORING IN PROGRESS
// =========================================================================
@Composable
fun MirroringScreen(
    receiverName: String,
    streamState: StreamState,
    onStop: () -> Unit
) {
    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (!isPaused) {
                elapsedSeconds++
            }
        }
    }

    val durationStr = remember(elapsedSeconds) {
        val m = elapsedSeconds / 60
        val s = elapsedSeconds % 60
        String.format("%02d:%02d", m, s)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Top Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connected to $receiverName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSlatePrimary
                    )
                }
                Text(
                    text = durationStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSlateSecondary
                )
            }
        }

        // Center Phone Mockup
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(380.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF0F172A))
                .border(3.dp, Color(0xFF334155), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(70.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isPaused) "PAUSED" else "STREAMING LIVE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused) Color(0xFFFBBF24) else Color(0xFF4ADE80)
                )
                Text(
                    text = "Screen Mirror Active",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Controls & Metrics
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Floating Control Bar (Pause, Stop, Mute)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(LightSurface)
                    .border(1.dp, LightCardBorder, RoundedCornerShape(28.dp))
                    .padding(vertical = 8.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause Button
                IconButton(onClick = { isPaused = !isPaused }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "Pause", tint = TextSlatePrimary)
                        Text(if (isPaused) "Resume" else "Pause", fontSize = 10.sp, color = TextSlateSecondary)
                    }
                }

                // Stop Button (Vibrant Red Circle)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(StopRed)
                        .clickable { onStop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(28.dp))
                }

                // Mute Button
                IconButton(onClick = { isMuted = !isMuted }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = "Mute", tint = TextSlatePrimary)
                        Text(if (isMuted) "Unmute" else "Mute", fontSize = 10.sp, color = TextSlateSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricItem("1080p", "Resolution")
                MetricItem("60 fps", "Frame Rate")
                MetricItem("~15 ms", "Latency")
            }
        }
    }
}

@Composable
fun MetricItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSlatePrimary)
        Text(label, fontSize = 11.sp, color = TextSlateSecondary)
    }
}

// =========================================================================
// SCREEN 6: SETTINGS SCREEN
// =========================================================================
@Composable
fun SettingsScreen(
    currentTheme: String = "system",
    onThemeChange: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var resolution by remember { mutableStateOf(SettingsManager.getResolution(context)) }
    var frameRate by remember { mutableStateOf(SettingsManager.getFrameRate(context)) }
    var bitrate by remember { mutableStateOf(SettingsManager.getBitrate(context)) }
    var codec by remember { mutableStateOf(SettingsManager.getCodec(context)) }
    var audioCapture by remember { mutableStateOf(SettingsManager.isAudioCapture(context)) }
    var autoDiscover by remember { mutableStateOf(SettingsManager.isAutoDiscover(context)) }
    var useQrCode by remember { mutableStateOf(SettingsManager.isUseQr(context)) }
    var rememberDevice by remember { mutableStateOf(SettingsManager.isRememberDevice(context)) }
    var lowLatencyMode by remember { mutableStateOf(SettingsManager.isLowLatency(context)) }

    var showResolutionDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showCodecDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Video & Streaming", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextSlatePrimary)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsRow(Icons.Default.Videocam, "Resolution", resolution) { showResolutionDialog = true }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsRow(Icons.Default.Speed, "Frame Rate", frameRate) { showFpsDialog = true }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsRow(Icons.Default.DataUsage, "Bitrate", bitrate) { showBitrateDialog = true }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsRow(Icons.Default.SettingsSuggest, "Video Codec", codec) { showCodecDialog = true }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsToggleRow("Capture System Audio", audioCapture) { 
                audioCapture = it
                SettingsManager.setAudioCapture(context, it)
            }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsToggleRow("Ultra-low Latency (~15ms)", lowLatencyMode) { 
                lowLatencyMode = it
                SettingsManager.setLowLatency(context, it)
            }
        }

        item {
            Text("Connection & Discovery", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextSlatePrimary)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggleRow("Auto discover receivers", autoDiscover) { 
                autoDiscover = it
                SettingsManager.setAutoDiscover(context, it)
            }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsToggleRow("Allow QR code pairing", useQrCode) { 
                useQrCode = it
                SettingsManager.setUseQr(context, it)
            }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsToggleRow("Remember last device", rememberDevice) { 
                rememberDevice = it
                SettingsManager.setRememberDevice(context, it)
            }
        }

        item {
            Text("Appearance & Theme", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextSlatePrimary)
            Spacer(modifier = Modifier.height(8.dp))
            val currentThemeName = when (currentTheme) {
                "dark" -> "Dark Mode (Sleek)"
                "light" -> "Light Mode (Modern)"
                else -> "System Default"
            }
            SettingsRow(Icons.Default.DarkMode, "Theme", currentThemeName) { showThemeDialog = true }
        }

        item {
            Text("About & Open Source", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextSlatePrimary)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsRow(Icons.Default.Info, "App Version", "v1.0.0 Production") {}
            Spacer(modifier = Modifier.height(6.dp))
            SettingsRow(Icons.Default.Person, "Developer", "0xSHARON") {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/0xSHARON"))
                    context.startActivity(intent)
                } catch (e: Exception) { }
            }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsRow(Icons.Default.Code, "GitHub Repository", "github.com/0xSHARON/mirrorcast") {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/0xSHARON/mirrorcast"))
                    context.startActivity(intent)
                } catch (e: Exception) { }
            }
        }
    }

    if (showResolutionDialog) {
        OptionsSelectionDialog(
            title = "Select Video Resolution",
            options = listOf("720p (HD 1280x720)", "1080p (Full HD 1920x1080)", "1440p (2K Quad HD)"),
            selected = resolution,
            onSelect = { 
                resolution = it
                SettingsManager.setResolution(context, it)
                showResolutionDialog = false 
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    if (showFpsDialog) {
        OptionsSelectionDialog(
            title = "Select Frame Rate",
            options = listOf("30 fps (Battery Saver)", "60 fps (Smooth & Recommended)", "90 fps (High Refresh)"),
            selected = frameRate,
            onSelect = { 
                frameRate = it
                SettingsManager.setFrameRate(context, it)
                showFpsDialog = false 
            },
            onDismiss = { showFpsDialog = false }
        )
    }

    if (showBitrateDialog) {
        OptionsSelectionDialog(
            title = "Select Bitrate",
            options = listOf("4 Mbps (Fast)", "6 Mbps (Standard)", "8 Mbps (High Quality)", "12 Mbps (Ultra)"),
            selected = bitrate,
            onSelect = { 
                bitrate = it
                SettingsManager.setBitrate(context, it)
                showBitrateDialog = false 
            },
            onDismiss = { showBitrateDialog = false }
        )
    }

    if (showCodecDialog) {
        OptionsSelectionDialog(
            title = "Select Video Codec",
            options = listOf("H.264 (Hardware Accelerated)", "VP8 (Software Fallback)"),
            selected = codec,
            onSelect = { 
                codec = it
                SettingsManager.setCodec(context, it)
                showCodecDialog = false 
            },
            onDismiss = { showCodecDialog = false }
        )
    }

    if (showThemeDialog) {
        OptionsSelectionDialog(
            title = "Select App Theme",
            options = listOf("System Default", "Dark Mode (Sleek)", "Light Mode (Modern)"),
            selected = when (currentTheme) {
                "dark" -> "Dark Mode (Sleek)"
                "light" -> "Light Mode (Modern)"
                else -> "System Default"
            },
            onSelect = { option ->
                val chosenTheme = when {
                    option.startsWith("Dark") -> "dark"
                    option.startsWith("Light") -> "light"
                    else -> "system"
                }
                SettingsManager.setTheme(context, chosenTheme)
                onThemeChange(chosenTheme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun OptionsSelectionDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextSlatePrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val isChosen = selected.startsWith(option.substringBefore(" "))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isChosen,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option, fontSize = 14.sp, color = TextSlatePrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PrimaryBlue)
            }
        }
    )
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSlatePrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, fontSize = 12.sp, color = TextSlateSecondary)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSlateSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SettingsToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSlatePrimary)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
            )
        }
    }
}

// =========================================================================
// MANUAL IP DIALOG
// =========================================================================
@Composable
fun ManualConnectionDialog(
    initialHost: String = "",
    onDismiss: () -> Unit,
    onConnect: (String, Int, String) -> Unit
) {
    var hostText by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf("8090") }
    var pinText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to Receiver", fontWeight = FontWeight.Bold, color = TextSlatePrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = hostText,
                    onValueChange = { hostText = it },
                    label = { Text("PC Name or Network Address") },
                    placeholder = { Text("e.g. My-PC or Network Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port (Default: 8090)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { pinText = it },
                    label = { Text("4-Digit PIN Code (from PC Screen)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 8090
                    onConnect(hostText.trim(), port, pinText.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSlateSecondary)
            }
        }
    )
}
