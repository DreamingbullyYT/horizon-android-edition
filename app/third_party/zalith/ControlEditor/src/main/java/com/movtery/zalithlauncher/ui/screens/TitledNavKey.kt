package com.movtery.zalithlauncher.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import kotlinx.collections.immutable.persistentListOf

interface TitledNavKey : NavKey

@Composable
fun rememberTitledNavBackStack(vararg elements: TitledNavKey): MutableState<NavBackStack> =
    remember { mutableStateOf(persistentListOf<NavKey>(*elements)) }

fun MutableState<NavBackStack>.clearWith(key: TitledNavKey) {
    value = persistentListOf<NavKey>(key)
}

@Composable
fun rememberTransitionSpec(): AnimatedContentTransitionScope<*>.() -> ContentTransform =
    remember { { fadeIn() togetherWith fadeOut() } }

@Composable
fun rememberSwapTween(): FiniteAnimationSpec<Float> = remember { tween(220) }
