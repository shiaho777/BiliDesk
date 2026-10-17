package app.bilidesk

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal actual fun rememberShellViewModel(): ShellViewModel =
    viewModel { ShellViewModel(SavedStateHandle()) }
