package com.movtery.zalithlauncher.ui.components
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
@Composable fun rememberBoxSize(): IntSize = LocalWindowInfo.current.containerSize
