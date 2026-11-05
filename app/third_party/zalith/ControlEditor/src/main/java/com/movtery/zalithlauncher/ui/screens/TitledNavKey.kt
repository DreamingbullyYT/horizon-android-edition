package com.movtery.zalithlauncher.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

interface TitledNavKey : NavKey
@Composable fun rememberTitledNavBackStack(vararg elements: TitledNavKey): NavBackStack<TitledNavKey> = remember { NavBackStack(*elements) }
fun NavBackStack<TitledNavKey>.clearWith(key: TitledNavKey) { clear(); add(key) }
@Composable fun rememberTransitionSpec(): AnimatedContentTransitionScope<NavKey>.() -> ContentTransform = remember { { fadeIn() togetherWith fadeOut() } }
@Composable fun rememberSwapTween() = androidx.compose.animation.core.tween<Float>(220)
