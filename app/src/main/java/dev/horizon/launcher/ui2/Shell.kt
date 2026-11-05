package dev.horizon.launcher.ui2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Rail on the left, the chosen screen on the right, messages at the bottom. */
@Composable
fun Shell(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val layout = remember { Layout.load(context) }
    val state = remember { AppState(scope, layout) { m -> scope.launch { snack.showSnackbar(m) } } }
    BackHandler { if (state.screen != layout.start) state.screen = layout.start else onExit() }

    CompositionLocalProvider(LocalAccent provides layout.accent) {
        Box(Modifier.fillMaxSize().background(layout.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(16.dp)) {
                Rail(state, layout)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ScreenRegistry.Show(state.screen, state)
                }
            }
            if (state.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = layout.accent)
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Rail(state: AppState, layout: Layout) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.width(64.dp).fillMaxHeight().background(Glass, shape).border(1.dp, GlassLine, shape).padding(vertical = 8.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterHorizontally,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            layout.nav.forEach { RailButton(it, state) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            layout.footer.forEach { RailButton(it, state) }
        }
    }
}

@Composable
private fun RailButton(item: NavItem, state: AppState) {
    val on = state.screen == item.id
    Surface(
        onClick = { state.screen = item.id },
        shape = RoundedCornerShape(16.dp),
        color = if (on) LocalAccent.current else Glass,
        modifier = Modifier.size(48.dp),
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Glyph(item.icon) }
    }
}
