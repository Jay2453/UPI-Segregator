package com.upisegregator.app.ui.screens.importer

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.importer.DetectedFileFormat
import com.upisegregator.app.data.importer.StatementImportRepository
import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.ImportableTransaction
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.importer.model.StatementImportResult
import com.upisegregator.app.data.importer.model.TransactionDirection
import com.upisegregator.app.data.importer.pdf.PdfDocumentType
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

enum class ImportFilterTab {
    ALL,
    VALID,
    DUPLICATES,
    CREDITS
}

data class StatementImportUiState(
    val session: UserSession = UserSession(),
    val fileName: String? = null,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val candidates: List<ImportableTransaction> = emptyList(),
    val activeFilter: ImportFilterTab = ImportFilterTab.ALL,
    val importResult: StatementImportResult? = null,
    val detectedFormat: DetectedFileFormat = DetectedFileFormat.UNKNOWN,
    val detectedBank: String? = null,
    val extractionWarnings: List<String> = emptyList(),
    val requiresPassword: Boolean = false,
    val pdfDocumentType: PdfDocumentType? = null
) {
    val totalDetected: Int get() = candidates.size
    val validCount: Int get() = candidates.count { it.validationStatus == RowValidationStatus.VALID && it.duplicateStatus == DuplicateStatus.NONE }
    val duplicateCount: Int get() = candidates.count { it.duplicateStatus != DuplicateStatus.NONE }
    val creditCount: Int get() = candidates.count { it.direction == TransactionDirection.CREDIT }
    val selectedCount: Int get() = candidates.count { it.isSelected }

    val filteredCandidates: List<ImportableTransaction>
        get() = when (activeFilter) {
            ImportFilterTab.ALL -> candidates
            ImportFilterTab.VALID -> candidates.filter { it.validationStatus == RowValidationStatus.VALID && it.duplicateStatus == DuplicateStatus.NONE }
            ImportFilterTab.DUPLICATES -> candidates.filter { it.duplicateStatus != DuplicateStatus.NONE }
            ImportFilterTab.CREDITS -> candidates.filter { it.direction == TransactionDirection.CREDIT }
        }
}

class StatementImportViewModel(
    private val sessionManager: SessionManager,
    private val statementImportRepository: StatementImportRepository
) : ViewModel() {

    private var currentFileBytes: ByteArray? = null
    private var currentFileName: String? = null
    private var currentMimeType: String? = null

    val sessionState: StateFlow<UserSession> = sessionManager.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSession()
        )

    private val _uiState = MutableStateFlow(StatementImportUiState())
    val uiState: StateFlow<StatementImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionManager.sessionFlow.first()
            _uiState.update { it.copy(session = session) }
        }
    }

    /**
     * Reads and parses a CSV or PDF statement file from the selected [Uri] using [ContentResolver].
     */
    fun onFileSelected(uri: Uri, contentResolver: ContentResolver, fileName: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                fileName = fileName,
                candidates = emptyList(),
                importResult = null,
                requiresPassword = false,
                detectedFormat = DetectedFileFormat.UNKNOWN,
                detectedBank = null,
                extractionWarnings = emptyList(),
                pdfDocumentType = null
            )
        }

        viewModelScope.launch {
            try {
                val session = sessionManager.sessionFlow.first()
                val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")
                val mimeType = contentResolver.getType(uri)

                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unable to read the selected file or file is empty."
                        )
                    }
                    return@launch
                }

                currentFileBytes = bytes
                currentFileName = fileName
                currentMimeType = mimeType

                processBytes(bytes, fileName, mimeType, userId, password = null)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error reading statement: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    /**
     * Retries unlocking and parsing an encrypted PDF with the user-provided password.
     * The password is treated ephemerally: never logged or persisted.
     */
    fun unlockWithPassword(password: String) {
        val bytes = currentFileBytes ?: return
        val fileName = currentFileName ?: "statement.pdf"
        val mimeType = currentMimeType

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                requiresPassword = false
            )
        }

        viewModelScope.launch {
            try {
                val session = sessionManager.sessionFlow.first()
                val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")
                processBytes(bytes, fileName, mimeType, userId, password = password)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to unlock PDF: ${e.localizedMessage ?: e.message}",
                        requiresPassword = true
                    )
                }
            }
        }
    }

    private suspend fun processBytes(
        bytes: ByteArray,
        fileName: String,
        mimeType: String?,
        userId: String,
        password: String?
    ) {
        val result = statementImportRepository.parseFileAndDetectDuplicates(
            fileBytes = bytes,
            fileName = fileName,
            mimeType = mimeType,
            userId = userId,
            password = password
        )

        if (result.requiresPassword) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.errorMessage,
                    requiresPassword = true,
                    detectedFormat = result.detectedFormat,
                    detectedBank = result.detectedBank,
                    pdfDocumentType = result.pdfDocumentType
                )
            }
        } else if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    candidates = result.transactions,
                    detectedFormat = result.detectedFormat,
                    detectedBank = result.detectedBank,
                    extractionWarnings = result.extractionWarnings,
                    pdfDocumentType = result.pdfDocumentType,
                    errorMessage = null,
                    requiresPassword = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.errorMessage ?: "Failed to parse bank statement.",
                    detectedFormat = result.detectedFormat,
                    detectedBank = result.detectedBank,
                    extractionWarnings = result.extractionWarnings,
                    pdfDocumentType = result.pdfDocumentType,
                    requiresPassword = false
                )
            }
        }
    }

    fun toggleSelection(candidateId: String) {
        _uiState.update { state ->
            val updated = state.candidates.map {
                if (it.id == candidateId) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(candidates = updated)
        }
    }

    fun selectAllValid() {
        _uiState.update { state ->
            val updated = state.candidates.map {
                val canSelect = it.validationStatus == RowValidationStatus.VALID && it.duplicateStatus == DuplicateStatus.NONE
                it.copy(isSelected = canSelect)
            }
            state.copy(candidates = updated)
        }
    }

    fun deselectAll() {
        _uiState.update { state ->
            val updated = state.candidates.map { it.copy(isSelected = false) }
            state.copy(candidates = updated)
        }
    }

    fun setFilter(filter: ImportFilterTab) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(importResult = null) }
    }

    fun resetState() {
        currentFileBytes = null
        currentFileName = null
        currentMimeType = null
        _uiState.update {
            StatementImportUiState(session = it.session)
        }
    }

    /**
     * Commits selected valid transactions to the database.
     */
    fun commitImport(onSuccess: (StatementImportResult) -> Unit) {
        val currentState = _uiState.value
        if (currentState.isImporting || currentState.selectedCount == 0) return

        _uiState.update { it.copy(isImporting = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val session = sessionManager.sessionFlow.first()
                val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")

                val result = statementImportRepository.saveImportedTransactions(
                    allCandidates = currentState.candidates,
                    userId = userId,
                    fileName = currentState.fileName ?: "Bank_Statement.csv"
                )

                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = result
                    )
                }
                onSuccess(result)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = "Import failed: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UpiSegregatorApp
                StatementImportViewModel(
                    sessionManager = app.container.sessionManager,
                    statementImportRepository = app.container.statementImportRepository
                )
            }
        }
    }
}
