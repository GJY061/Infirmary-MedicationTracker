package com.example.infirmary.viewmodel

/** Shared save-form state; a new instance resets old progress and feedback values. */
data class FormUiState(
    val isSaving: Boolean = false,
    val errorMessage: String = "",
    val infoMessage: String = ""
)
