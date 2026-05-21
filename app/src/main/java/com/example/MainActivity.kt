package com.example

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.CardTransaction
import com.example.data.entity.NfcCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.NfcScanState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private val viewModel: FinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize NFC Adapter safely
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        } catch (e: Exception) {
            nfcAdapter = null
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    DompetNFCScreen(
                        viewModel = viewModel,
                        nfcSupported = nfcAdapter != null,
                        nfcEnabled = nfcAdapter?.isEnabled == true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcTestingReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableNfcTestingReaderMode()
    }

    private fun enableNfcTestingReaderMode() {
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                try {
                    adapter.enableReaderMode(
                        this,
                        { tag: Tag ->
                            val idBytes = tag.id
                            val hexId = idBytes.joinToString("") { String.format("%02X", it) }
                            
                            // Audio feedback
                            beepFeedback()
                            
                            // Haptic feedback
                            vibrateFeedback()

                            // Guess card details
                            val guessedType = guessNfcCardType(tag)

                            // Process card scan in state
                            viewModel.handlePhysicalNfcTag(hexId, guessedType)
                        },
                        NfcAdapter.FLAG_READER_NFC_A or 
                                NfcAdapter.FLAG_READER_NFC_B or 
                                NfcAdapter.FLAG_READER_NFC_F or 
                                NfcAdapter.FLAG_READER_NFC_V or 
                                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                        null
                    )
                } catch (e: Exception) {
                    // Fail silently or log
                }
            }
        }
    }

    private fun disableNfcTestingReaderMode() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (e: Exception) {
            // Fail silently
        }
    }

    private fun guessNfcCardType(tag: Tag): String {
        val techList = tag.techList.toList()
        return when {
            techList.contains("android.nfc.tech.IsoDep") -> {
                // Emoney cards in Indonesia are general ISO-DEP (IsoDep / ISO 14443-4)
                // We're unsure of specific bank until parsed further, default as Mandiri/Flazz
                "BCA Flazz"
            }
            techList.contains("android.nfc.tech.MifareClassic") -> "BRI Brizzi"
            techList.contains("android.nfc.tech.MifareUltralight") -> "BNI TapCash"
            else -> "Kartu NFC"
        }
    }

    private fun beepFeedback() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
        } catch (e: Exception) {
            // ToneGenerator not supported or audio bound exception
        }
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (e: Exception) {
            // Vibration permission or hardware missing
        }
    }
}

// FORMAT INDONESIAN RUPIAH UTIL
fun formatRupiah(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(amount).replace("Rp", "Rp ").replace(",00", "")
}

// CATEGORY DEFINITION
data class ExpenseCategory(
    val name: String,
    val icon: String,
    val color: Color
)

val transactionCategories = listOf(
    ExpenseCategory("Tol & Parkir", "🚗", Color(0xFFFBBF24)),       // Amber
    ExpenseCategory("Transportasi", "🚇", Color(0xFF3B82F6)),       // Blue
    ExpenseCategory("Makanan", "☕", Color(0xFF10B981)),            // Emerald
    ExpenseCategory("Supermarket", "🛒", Color(0xFF8B5CF6)),        // Violet
    ExpenseCategory("Belanja", "🛍️", Color(0xFFEC4899)),            // Pink
    ExpenseCategory("Lainnya", "💸", Color(0xFF6B7280))             // Gray
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DompetNFCScreen(
    viewModel: FinanceViewModel,
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val totalExpenditureOverall by viewModel.totalExpenditureOverall.collectAsStateWithLifecycle()
    val selectedCardId by viewModel.selectedCardId.collectAsStateWithLifecycle()
    val selectedCard by viewModel.selectedCard.collectAsStateWithLifecycle()
    val selectedCardTransactions by viewModel.selectedCardTransactions.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0 = Kartu Saya, 1 = Analisis

    // Dialog sheets states
    var showSimulateTapDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showRenameCardDialog by remember { mutableStateOf(false) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var textCardNameInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Observe Scan State for Toast alerts
    LaunchedEffect(scanState) {
        when (scanState) {
            is NfcScanState.Success -> {
                val state = scanState as NfcScanState.Success
                if (state.isNew) {
                    Toast.makeText(context, "Berhasil Menghubungkan Kartu Baru: ${state.card.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Kartu Terbaca: ${state.card.name}", Toast.LENGTH_SHORT).show()
                }
                viewModel.clearScanState()
            }
            is NfcScanState.Error -> {
                Toast.makeText(context, (scanState as NfcScanState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.clearScanState()
            }
            else -> {}
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFFF3F4F9))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // HEADER BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SELAMAT DATANG",
                        color = Color(0xFF6366F1), // indigo-500
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Duempet",
                        color = Color(0xFF1E293B), // slate-800
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // NFC STATUS PILL
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                when {
                                    !nfcSupported -> Color.LightGray.copy(alpha = 0.5f)
                                    nfcEnabled -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    else -> Color(0xFFFBBF24).copy(alpha = 0.15f)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    !nfcSupported -> Color.Gray.copy(alpha = 0.3f)
                                    nfcEnabled -> Color(0xFF10B981)
                                    else -> Color(0xFFFBBF24)
                                },
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    !nfcSupported -> Color.Gray
                                    nfcEnabled -> Color(0xFF10B981)
                                    else -> Color(0xFFFBBF24)
                                },
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Text(
                                text = when {
                                    !nfcSupported -> "NO NFC"
                                    nfcEnabled -> "NFC SIAP"
                                    else -> "NFC OFF"
                                },
                                color = when {
                                    !nfcSupported -> Color.DarkGray
                                    nfcEnabled -> Color(0xFF10B981)
                                    else -> Color(0xFFFBBF24)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Avatar badge
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E7FF)) // indigo-100
                            .border(1.dp, Color(0xFFC7D2FE), CircleShape), // indigo-200
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JD",
                            color = Color(0xFF4F46E5), // indigo-600
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // NAVIGATION TAB ROW (M3 Style Rounded Buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                listOf("Kartu Saya", "Analisis Pengeluaran").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (activeTab == index) Color(0xFFEEF2FF) else Color.Transparent)
                            .clickable { activeTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (activeTab == index) Color(0xFF4F46E5) else Color(0xFF64748B),
                            fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // TAB CONTENT AREA
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    slideInHorizontally { width -> if (targetState > initialState) width else -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> if (targetState > initialState) -width else width } + fadeOut()
                },
                label = "TabTransition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { targetTab ->
                when (targetTab) {
                    0 -> CardsDashboardScreen(
                        cards = cards,
                        selectedCard = selectedCard,
                        transactions = selectedCardTransactions,
                        scanState = scanState,
                        onCardSelect = { cardId -> viewModel.selectCard(cardId) },
                        onSimulateTapClick = { showSimulateTapDialog = true },
                        onAddExpenseClick = { showAddExpenseDialog = true },
                        onRenameCardClick = {
                            selectedCard?.let { textCardNameInput = it.name }
                            showRenameCardDialog = true
                        },
                        onTopUpClick = { showTopUpDialog = true },
                        onDeleteCardClick = { card -> viewModel.deleteCard(card) },
                        onDeleteTransaction = { tx -> viewModel.deleteTransaction(tx) }
                    )

                    1 -> SpendingAnalysisTab(
                        overallTransactions = transactions,
                        cards = cards,
                        totalExpenditureOverall = totalExpenditureOverall
                    )
                }
            }
        }

        // SIMULASI TAP BACKEND / EMULATOR DIALOG
        if (showSimulateTapDialog) {
            SimulateTapNfcDialog(
                onDismiss = { showSimulateTapDialog = false },
                onSimulatedTap = { cardType, balance, customName ->
                    viewModel.handleSimulatedTap(cardType, balance, customName)
                    showSimulateTapDialog = false
                }
            )
        }

        // REGISTER TRANSACTION DIALOG
        if (showAddExpenseDialog && selectedCard != null) {
            AddExpenseDialog(
                cardName = selectedCard?.name ?: "",
                availableBalance = selectedCard?.balance ?: 0.0,
                onDismiss = { showAddExpenseDialog = false },
                onConfirm = { amount, category, note ->
                    viewModel.addManualTransaction(selectedCard!!.id, amount, category, note)
                    showAddExpenseDialog = false
                }
            )
        }

        // RENAME CARD DIALOG
        if (showRenameCardDialog && selectedCard != null) {
            AlertDialog(
                onDismissRequest = { showRenameCardDialog = false },
                containerColor = Color(0xFF1E293B),
                title = { Text("Edit Nama Kartu", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Masukkan nama panggil khusus kartu NFC ini:", color = Color.LightGray, fontSize = 14.sp)
                        OutlinedTextField(
                            value = textCardNameInput,
                            onValueChange = { textCardNameInput = it },
                            placeholder = { Text("cth: Flazz Belanja") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color.Gray
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateCardName(selectedCard!!.id, textCardNameInput)
                            showRenameCardDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameCardDialog = false }) {
                        Text("Batal", color = Color.Gray)
                    }
                }
            )
        }

        // SIMULASI TOP UP DIALOG
        if (showTopUpDialog && selectedCard != null) {
            var topUpAmountText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showTopUpDialog = false },
                containerColor = Color(0xFF1E293B),
                title = { Text("Simulasi Pengisian Saldo (Top-Up)", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Berdasarkan teknologi NFC e-money, Anda bisa mensimulasikan penambahan saldo baru langsung ke cip kartu ini.", color = Color.LightGray, fontSize = 13.sp)
                        OutlinedTextField(
                            value = topUpAmountText,
                            onValueChange = { topUpAmountText = it.filter { char -> char.isDigit() } },
                            label = { Text("Jumlah Top Up (Rp)", color = Color.LightGray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color.Gray
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = topUpAmountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                val currentBalance = selectedCard?.balance ?: 0.0
                                viewModel.updateCardBalanceManual(selectedCard!!.id, currentBalance + amount)
                                Toast.makeText(context, "Sandi cip NFC terisi: +${formatRupiah(amount)}", Toast.LENGTH_SHORT).show()
                            }
                            showTopUpDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text("Top-Up")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTopUpDialog = false }) {
                        Text("Batal", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun CardsDashboardScreen(
    cards: List<NfcCard>,
    selectedCard: NfcCard?,
    transactions: List<CardTransaction>,
    scanState: NfcScanState,
    onCardSelect: (String) -> Unit,
    onSimulateTapClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onRenameCardClick: () -> Unit,
    onTopUpClick: () -> Unit,
    onDeleteCardClick: (NfcCard) -> Unit,
    onDeleteTransaction: (CardTransaction) -> Unit
) {
    var showDeleteConfirmCard by remember { mutableStateOf<NfcCard?>(null) }
    var showDeleteConfirmTx by remember { mutableStateOf<CardTransaction?>(null) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        // TOP NFC SCANNING GUIDELINES BANNER
        item {
            NfcStatusScanBanner(
                scanState = scanState,
                onSimulateClick = onSimulateTapClick
            )
        }

        // CARDS HORIZONTAL SLIDER
        if (cards.isNotEmpty()) {
            item {
                Text(
                    text = "Daftar Kartu Terdaftar (${cards.size})",
                    color = Color(0xFF1E293B), // slate-800
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Row scrollable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    cards.forEach { card ->
                        Box(
                            modifier = Modifier
                                .width(135.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(
                                    1.5.dp,
                                    if (selectedCard?.id == card.id) {
                                        parseColorStringGradient(card.cardColorHex).last()
                                    } else {
                                        Color(0xFFE2E8F0)
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onCardSelect(card.id) }
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = card.type,
                                        color = parseColorStringGradient(card.cardColorHex).last(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    if (selectedCard?.id == card.id) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Terpilih",
                                            tint = parseColorStringGradient(card.cardColorHex).last(),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = card.name,
                                    color = Color(0xFF1E293B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = formatRupiah(card.balance),
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // SELECTED CARD GRAPHIC DISPLAY
        if (selectedCard != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Physical Plastic Card design
                    NfcCardGraphic(card = selectedCard)

                    // Quick Stats Row widget based on Sleek Theme Layouts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val totalSpentSelected = transactions.sumOf { it.amount }
                        val countTransactions = transactions.size

                        // Box 1: Pengeluaran
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "PENGELUARAN",
                                    color = Color(0xFF94A3B8), // slate-400
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "- ${formatRupiah(totalSpentSelected)}",
                                    color = Color(0xFFF43F5E), // rose-500
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Total kartu ini",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Box 2: Frekuensi
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "FREKUENSI",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "$countTransactions Transaksi",
                                    color = Color(0xFF1E293B), // slate-800
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Bulan ini",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Card Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Belanja
                        Button(
                            onClick = onAddExpenseClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Belanja", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bayar/Belanja", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Top Up
                        Button(
                            onClick = onTopUpClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)), // Indigo-600
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "TopUp", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Top up Saldo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Card Settings Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onRenameCardClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF64748B))
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ubah Nama", fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = { showDeleteConfirmCard = selectedCard },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus Kartu", fontSize = 12.sp)
                        }
                    }
                }
            }

            // TRANSACTIONS LIST CONTAINER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Riwayat Pengeluaran Kartu Ini",
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    val sumSpent = transactions.sumOf { it.amount }
                    Text(
                        text = "Total Spent: ${formatRupiah(sumSpent)}",
                        color = Color(0xFFF43F5E), // rose-500
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(
                                1.dp,
                                Color(0xFFE2E8F0),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🛒", fontSize = 34.sp)
                            Text(
                                "Belum Ada Pengeluaran",
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Klik tombol 'Bayar/Belanja' di atas untuk mencatat pengeluaran tol, makanan, atau belanja Anda.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(transactions) { tx ->
                    val catObj = transactionCategories.find { it.name == tx.category } ?: transactionCategories.last()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {
                                    Toast.makeText(context, "Tekan lama transaksi untuk menghapus item", Toast.LENGTH_SHORT).show()
                                },
                                onLongClick = {
                                    showDeleteConfirmTx = tx
                                }
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Category Badge Rounded
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(catObj.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                              ) {
                                Text(text = catObj.icon, fontSize = 20.sp)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tx.note,
                                    color = Color(0xFF1E293B),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${tx.category} · ${formatTimestamp(tx.timestamp)}",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = "- ${formatRupiah(tx.amount)}",
                            color = Color(0xFFF43F5E),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        } else {
            // EMPTY STATE WHEN NO CARDS LINKED
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "💳", fontSize = 48.sp)
                        Text(
                            text = "Belum Ada Kartu Tertaut",
                            color = Color(0xFF1E293B),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tempelkan kartu Mandiri e-Money / BCA Flazz Anda langsung ke pembaca NFC ponsel, atau klik tombol di bawah untuk simulasi di browser.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onSimulateTapClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simulasikan Tap Kartu")
                        }
                    }
                }
            }
        }
    }

    // CONFIRM DELETE KARTU
    if (showDeleteConfirmCard != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmCard = null },
            containerColor = Color.White,
            title = { Text("Hapus Kartu Dari Dompet?", color = Color(0xFF1E293B)) },
            text = { Text("Menghapus kartu '${showDeleteConfirmCard!!.name}' akan menghapus permanen semua riwayat transaksi pengeluaran kartu tersebut dari penyimpanan lokal.", color = Color(0xFF64748B)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCardClick(showDeleteConfirmCard!!)
                        showDeleteConfirmCard = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmCard = null }) {
                    Text("Batal", color = Color(0xFF64748B))
                }
            }
        )
    }

    // CONFIRM DELETE TRANSAKSI / EXPENSE REFUND
    if (showDeleteConfirmTx != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmTx = null },
            containerColor = Color.White,
            title = { Text("Hapus Transaksi Ini?", color = Color(0xFF1E293B)) },
            text = { Text("Transaksi ini sejumlah ${formatRupiah(showDeleteConfirmTx!!.amount)} akan dihapus. Saldo pada kartu '${selectedCard?.name}' akan diisi ulang otomatis.", color = Color(0xFF64748B)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction(showDeleteConfirmTx!!)
                        showDeleteConfirmTx = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Kembalikan Saldo & Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmTx = null }) {
                    Text("Batal", color = Color(0xFF64748B))
                }
            }
        )
    }
}

@Composable
fun NfcStatusScanBanner(
    scanState: NfcScanState,
    onSimulateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseEffect"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(
                1.dp,
                Color(0xFFE2E8F0),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pulsing Radar scan
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF6366F1).copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = (size.minDimension / 1.5f) * pulseScale
                                )
                                drawCircle(
                                    color = Color(0xFF6366F1),
                                    radius = size.minDimension / 5.5f
                                )
                            }
                    )

                    Column {
                        Text(
                            text = if (scanState is NfcScanState.Scanning) "Sedang mendeteksi cip..." else "Ketuk Kartu NFC Anda",
                            color = Color(0xFF1E293B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tempelkan kartu di belakang body HP",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = onSimulateClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF), contentColor = Color(0xFF4F46E5)),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("📶", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simulasi Tap", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = scanState is NfcScanState.Scanning) {
                LinearProgressIndicator(
                    trackColor = Color(0xFFE2E8F0),
                    color = Color(0xFF4F46E5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun NfcCardGraphic(card: NfcCard) {
    val gradientColors = parseColorStringGradient(card.cardColorHex)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f) // Card ratio 85.60 x 53.98 mm
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Card Brand Row & Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gold smartcard contactless chip
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                            )
                        )
                ) {
                    // Lines to look like chip contacts
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(Color.Gray.copy(alpha = 0.5f), size = size, style = Stroke(width = 1f))
                        drawLine(Color.Gray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(size.width / 2, 0f), end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height))
                        drawLine(Color.Gray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, size.height / 2), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2))
                    }
                }

                // Brand Type Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = card.type,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Balance display
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SALDO AKTIF",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = formatRupiah(card.balance),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Card Holder ID and Tapped timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = card.name.uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: " + if(card.id.length > 8) card.id.take(4) + " ···· " + card.id.takeLast(4) else card.id,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // NFC logo icon
                Column(horizontalAlignment = Alignment.End) {
                    Text("📶", fontSize = 18.sp)
                    Text(
                        text = formatRelativeTime(card.lastTappedTime),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 8.sp,
                        textAlign = TextAlign.Right
                    )
                }
            }
        }
    }
}

@Composable
fun SpendingAnalysisTab(
    overallTransactions: List<CardTransaction>,
    cards: List<NfcCard>,
    totalExpenditureOverall: Double
) {
    if (overallTransactions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "📊", fontSize = 54.sp)
                Text(
                    text = "Data Transaksi Tidak Cukup",
                    color = Color(0xFF1E293B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Buat beberapa simulasi transaksi belanja untuk melihat grafik analisis yang interaktif di sini.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Group amounts spent by category
        val categoryTotals = remember(overallTransactions) {
            val map = mutableMapOf<String, Double>()
            overallTransactions.forEach { tx ->
                val current = map[tx.category] ?: 0.0
                map[tx.category] = current + tx.amount
            }
            map.toList().sortedByDescending { it.second }
        }

        val highestCategory = categoryTotals.firstOrNull()?.first ?: "Lainnya"

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            // TOTAL OUTFLOW SPECS CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "TOTAL SEMUA PENGELUARAN KARTU",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = formatRupiah(totalExpenditureOverall),
                            color = Color(0xFFF43F5E), // rose-500
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kartu Aktif Terkait",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${cards.size} Kartu",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Jumlah Transaksi Belanja",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${overallTransactions.size} Transaksi",
                                color = Color(0xFF1E293B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // VISUAL HORIZONTAL COMPARISON CHARTS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Analisis Pengeluaran Kategori",
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        categoryTotals.forEach { (categoryName, totalAmount) ->
                            val catObj = transactionCategories.find { it.name == categoryName } ?: transactionCategories.last()
                            val ratio = if (totalExpenditureOverall > 0) totalAmount / totalExpenditureOverall else 0.0
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = catObj.icon, fontSize = 14.sp)
                                        Text(text = categoryName, color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = formatRupiah(totalAmount), color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "(${(ratio * 100).toInt()}%)", color = Color(0xFF64748B), fontSize = 11.sp)
                                    }
                                }

                                // Neon Horizontal Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(ratio.toFloat())
                                            .clip(CircleShape)
                                            .background(catObj.color)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // INTELLIGENT COMPANION RECOMMENDATION
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Saran Asisten Keuangan",
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEEF2FF))
                            .border(
                                1.dp,
                                Color(0xFFC7D2FE),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "💡", fontSize = 24.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Rekomendasi Pemakaian Kartu",
                                    color = Color(0xFF4F46E5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = generateIndonesianAdviceText(highestCategory),
                                    color = Color(0xFF374151),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateIndonesianAdviceText(category: String): String {
    return when (category) {
        "Tol & Parkir" -> "Pengeluaran kartu Anda didominasi oleh biaya Jalan Tol atau Perparkiran. Usahakan untuk menjaga saldo minimal Rp 100.000 agar tidak terkendala tidak cukup saldo saat tapping di gerbang tol otomatis."
        "Transportasi" -> "Kartu Anda sering ditap pada armada Transportasi massal (MRT/LRT/KRL/TJ). Ini sangat luar biasa untuk hemat pengeluaran bulanan dibanding mobil pribadi! Isi saldo harian Rp 30.000 sudah sangat aman."
        "Makanan" -> "Cukup banyak pengeluaran dialokasikan untuk jajan kuliner & kopi via kartu. Periksa kembali struk toko Anda, kurangi frekuensi beli kopi kafe untuk menghemat pos pengeluaran bulanan Anda."
        "Supermarket", "Belanja" -> "Anda gemar menggunakan kartu prabayar non-tunai di minimarket/supermarket. Ingat untuk membandingkan total belanja bulanan Anda agar kartu e-money ini fokus sebagai instrumen perjalanan transit."
        else -> "Analisis kartu prabayar Anda berjalan dengan baik. Tetap biasakan cek saldo secara berkala menggunakan sensor NFC sebelum bepergian agar transit atau pembayaran Anda lancar!"
    }
}

// SIMULATOR TAP BOTTOM-DIALOG
@Composable
fun SimulateTapNfcDialog(
    onDismiss: () -> Unit,
    onSimulatedTap: (String, Double, String) -> Unit
) {
    var selectedType by remember { mutableStateOf("BCA Flazz") }
    var startingBalance by remember { mutableStateOf(100000f) }
    var customCardNameInput by remember { mutableStateOf("") }

    val types = listOf("BCA Flazz", "Mandiri e-Money", "BRI Brizzi", "BNI TapCash", "Custom Tag")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Simulasikan Tapping NFC",
                    color = Color(0xFF1E293B),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Karena keterbatasan sensor NFC pada browser emulator, gunakan fitur ini untuk merekayasa sensor tap fisik.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )

                // Select card type
                Text(
                    text = "Pilih Bank / Jenis Kartu",
                    color = Color(0xFF1E293B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.chunked(3).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chunk.forEach { type ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selectedType == type) Color(0xFF4F46E5) else Color(0xFFF1F5F9)
                                        )
                                        .clickable { selectedType = type }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type,
                                        color = if (selectedType == type) Color.White else Color(0xFF64748B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Custom Card Name
                Text(
                    text = "Nama Kustom (Opsional)",
                    color = Color(0xFF1E293B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = customCardNameInput,
                    onValueChange = { customCardNameInput = it },
                    placeholder = { Text("cth: Kartu Operasional MRT", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF4F46E5),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color(0xFF4F46E5),
                        unfocusedLabelColor = Color(0xFF64748B)
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Slider starting balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Masukan Saldo Cip", color = Color(0xFF1E293B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = formatRupiah(startingBalance.toDouble()), color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = startingBalance,
                    onValueChange = { startingBalance = it },
                    valueRange = 5000f..2000000f,
                    steps = 199, // increments approx 10k
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF10B981),
                        activeTrackColor = Color(0xFF10B981),
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSimulatedTap(selectedType, startingBalance.toDouble(), customCardNameInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Simulasikan Tap", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// LOG TRANSAKSI DIALOG
@Composable
fun AddExpenseDialog(
    cardName: String,
    availableBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tol & Parkir") }
    var noteInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Catat Transaksi Belanja",
                    color = Color(0xFF1E293B),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Kartu: $cardName · Saldo: ${formatRupiah(availableBalance)}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        amountText = digits
                    },
                    label = { Text("Jumlah (Rp)", color = Color(0xFF64748B)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color(0xFFEF4444),
                        unfocusedLabelColor = Color(0xFF64748B)
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Grid Selection
                Text(
                    text = "Pilih Kategori Pengeluaran",
                    color = Color(0xFF1E293B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    transactionCategories.chunked(3).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chunk.forEach { cat ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selectedCategory == cat.name) cat.color.copy(alpha = 0.25f) else Color(0xFFF1F5F9)
                                        )
                                        .border(
                                            1.dp,
                                            if (selectedCategory == cat.name) cat.color else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedCategory = cat.name }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = cat.icon, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = cat.name, color = Color(0xFF1E293B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Catatan / Keterangan Belanja", color = Color(0xFF64748B)) },
                    placeholder = { Text("cth: Tap KRL Manggarai", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = Color(0xFFEF4444),
                        unfocusedLabelColor = Color(0xFF64748B)
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount <= 0) {
                                // Do nothing, amount invalid
                            } else if (amount > availableBalance) {
                                // Prevent saving due to empty chip balance
                            } else {
                                onConfirm(amount, selectedCategory, noteInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        enabled = (amountText.toDoubleOrNull() ?: 0.0) in 1.0..availableBalance
                    ) {
                        Text("Catat Transaksi", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// UTILITIES
fun parseColorStringGradient(gradientStr: String): List<Color> {
    return try {
        val splitColors = gradientStr.split(",")
        splitColors.map { Color(android.graphics.Color.parseColor(it.trim())) }
    } catch (e: Exception) {
        listOf(Color(0xFF0F2027), Color(0xFF2C5364)) // Fallback slate gradient
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    return sdf.format(Date(timestamp))
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Baru saja"
        diff < 3600000 -> "${diff / 60000} mnt yang lalu"
        diff < 86400000 -> "${diff / 3600000} jam yang lalu"
        else -> {
            val sdf = SimpleDateFormat("dd MMM", Locale("id", "ID"))
            sdf.format(Date(timestamp))
        }
    }
}

// Custom experimental combined clickable for long clicks
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = composed {
    this.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = LocalIndication.current,
        onClick = onClick,
        onLongClick = onLongClick
    )
}
