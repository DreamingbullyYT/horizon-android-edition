package com.movtery.zalithlauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor
import dev.horizon.launcher.game.zalith.FloatingBall as HorizonFloatingBall
import dev.horizon.launcher.game.zalith.DualMenuSubscreen as HorizonDualMenuSubscreen
import dev.horizon.launcher.game.zalith.MenuTextButton as HorizonMenuTextButton
import dev.horizon.launcher.game.zalith.MenuSwitchButton as HorizonMenuSwitchButton
import dev.horizon.launcher.game.zalith.MenuListLayout as HorizonMenuListLayout

@Composable fun MarqueeText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) { Text(text, modifier, maxLines=1, style=style) }
@Composable fun rememberDialogMaxHeight(): Dp = 520.dp
@Composable fun ImePanContainer(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.Center, content: @Composable BoxWithConstraintsScope.() -> Unit) { BoxWithConstraints(modifier, contentAlignment=contentAlignment, content=content) }
@Composable fun Modifier.verticalScrollWithBar(state: androidx.compose.foundation.ScrollState) = verticalScroll(state)
@Composable fun Modifier.lazyScrollWithBar(state: androidx.compose.foundation.lazy.LazyListState) = this
@Composable fun Modifier.fadeEdge(state: androidx.compose.foundation.ScrollState, direction: EdgeDirection = EdgeDirection.Vertical) = this
enum class EdgeDirection { Horizontal, Vertical }

@Composable fun MenuBox(modifier: Modifier=Modifier, position: androidx.compose.ui.geometry.Offset, onPositionChanged:(androidx.compose.ui.geometry.Offset)->Unit, opened:Boolean, onClick:()->Unit) {
 HorizonFloatingBall(position=position,onPositionChanged=onPositionChanged,modifier=modifier,onClick=onClick) { Text(if(opened) "☰" else "≡", modifier=Modifier.padding(8.dp)) }
}
@Composable fun DualMenuSubscreen(state:MenuState,closeScreen:()->Unit,leftMenuTitle:(@Composable BoxScope.()->Unit)?=null,leftMenuContent:@Composable ColumnScope.()->Unit={},rightMenuTitle:(@Composable BoxScope.()->Unit)?=null,rightMenuContent:@Composable ColumnScope.()->Unit={}) {
 HorizonDualMenuSubscreen(state=when(state){MenuState.SHOW->dev.horizon.launcher.game.zalith.MenuState.SHOW;MenuState.HIDE->dev.horizon.launcher.game.zalith.MenuState.HIDE;else->dev.horizon.launcher.game.zalith.MenuState.NONE},closeScreen=closeScreen,leftMenuContent=leftMenuContent,rightMenuTitle=rightMenuTitle,rightMenuContent=rightMenuContent)
}
@Composable fun MenuTextButton(modifier:Modifier=Modifier,text:String,enabled:Boolean=true,onClick:()->Unit,color:Color=MaterialTheme.colorScheme.surfaceVariant,contentColor:Color=MaterialTheme.colorScheme.onSurface) {
 HorizonMenuTextButton(modifier=modifier,text=text,enabled=enabled,onClick=onClick)
}
@Composable fun MenuSwitchButton(modifier:Modifier=Modifier,text:String,switch:Boolean,enabled:Boolean=true,onSwitch:(Boolean)->Unit,color:Color=MaterialTheme.colorScheme.surfaceVariant,contentColor:Color=MaterialTheme.colorScheme.onSurface) {
 HorizonMenuSwitchButton(modifier=modifier,text=text,switch=switch,enabled=enabled,onSwitch=onSwitch)
}
@Composable fun <E> MenuListLayout(modifier:Modifier=Modifier,title:String,items:List<E>,currentItem:E,onItemChange:(E)->Unit,getItemText:(E)->String,color:Color=MaterialTheme.colorScheme.surfaceVariant,contentColor:Color=MaterialTheme.colorScheme.onSurface) {
 HorizonMenuListLayout(modifier=modifier,title=title,items=items,currentItem=currentItem,onItemChange=onItemChange,getItemText=getItemText)
}
@Composable fun ScalingActionButton(modifier:Modifier=Modifier,onClick:()->Unit,content:@Composable RowScope.()->Unit) { Button(modifier=modifier,onClick=onClick,content=content) }

@Composable fun SimpleAlertDialog(title:String,text:String,confirmText:String=stringResource(R.string.generic_confirm),dismissText:String=stringResource(R.string.generic_cancel),dismissByDialog:Boolean=true,onConfirm:()->Unit,onDismiss:()->Unit) {
 AlertDialog(onDismissRequest={if(dismissByDialog)onDismiss()},title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=onConfirm){Text(confirmText)}},dismissButton={TextButton(onClick=onDismiss){Text(dismissText)}})
}
@Composable fun SimpleAlertDialog(title:String,text:String,confirmText:String=stringResource(R.string.generic_confirm),dismissByDialog:Boolean=true,onDismiss:()->Unit) {
 AlertDialog(onDismissRequest={if(dismissByDialog)onDismiss()},title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=onDismiss){Text(confirmText)}})
}
@Composable fun SimpleAlertDialog(title:String,text:@Composable ()->Unit={},confirmText:String=stringResource(R.string.generic_confirm),dismissText:String=stringResource(R.string.generic_cancel),onConfirm:()->Unit,onCancel:()->Unit,onDismissRequest:()->Unit={}) {
 AlertDialog(onDismissRequest=onDismissRequest,title={Text(title)},text=text,confirmButton={TextButton(onClick=onConfirm){Text(confirmText)}},dismissButton={TextButton(onClick=onCancel){Text(dismissText)}})
}
@Composable fun SimpleAlertDialog(title:String,text:@Composable ()->Unit={},confirmText:String=stringResource(R.string.generic_confirm),onDismiss:()->Unit) {
 AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text=text,confirmButton={TextButton(onClick=onDismiss){Text(confirmText)}})
}
@Composable fun SimpleEditDialog(title:String,value:String,onValueChange:(String)->Unit,label:(@Composable ()->Unit)?=null,isError:Boolean=false,supportingText:(@Composable ()->Unit)?=null,singleLine:Boolean=false,maxLines:Int=3,keyboardOptions:KeyboardOptions=KeyboardOptions.Default,extraBody:(@Composable ()->Unit)?=null,extraContent:(@Composable ()->Unit)?=null,onDismissRequest:()->Unit={},onCancel:()->Unit=onDismissRequest,onConfirm:()->Unit={}) {
 AlertDialog(onDismissRequest=onDismissRequest,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(value,onValueChange,label=label,isError=isError,supportingText=supportingText,singleLine=singleLine,maxLines=maxLines,keyboardOptions=keyboardOptions);extraBody?.invoke();extraContent?.invoke()}},confirmButton={TextButton(onClick=onConfirm){Text(stringResource(R.string.generic_confirm))}},dismissButton={TextButton(onClick=onCancel){Text(stringResource(R.string.generic_cancel))}})
}
@Composable fun SimpleEditDialog(title:String,value:String,onValueChange:(String)->Unit,label:(@Composable ()->Unit)?=null,isError:Boolean=false,supportingText:(@Composable ()->Unit)?=null,singleLine:Boolean=false,maxLines:Int=3,keyboardOptions:KeyboardOptions=KeyboardOptions.Default,extraBody:(@Composable ()->Unit)?=null,extraContent:(@Composable ()->Unit)?=null,onConfirm:()->Unit={}) {
 SimpleEditDialog(title,value,onValueChange,label,isError,supportingText,singleLine,maxLines,keyboardOptions,extraBody,extraContent,{}, {},onConfirm)
}
@Composable fun ProgressDialog(title:String,text:String) { AlertDialog(onDismissRequest={},title={Text(title)},text={Text(text)},confirmButton={}) }
@Composable fun OwnOutlinedTextField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,label:(@Composable ()->Unit)?=null,singleLine:Boolean=false,isError:Boolean=false,supportingText:(@Composable ()->Unit)?=null,keyboardOptions:KeyboardOptions=KeyboardOptions.Default,enabled:Boolean=true) { OutlinedTextField(value,onValueChange,modifier=modifier,label=label,singleLine=singleLine,isError=isError,supportingText=supportingText,keyboardOptions=keyboardOptions,enabled=enabled) }
@Composable fun SingleLineTextCheck(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,label:(@Composable ()->Unit)?=null,isError:Boolean=false) { OutlinedTextField(value,onValueChange,modifier=modifier,label=label,singleLine=true,isError=isError) }
@Composable fun DefaultSwitch(checked:Boolean,onCheckedChange:(Boolean)->Unit,enabled:Boolean=true) { Switch(checked,onCheckedChange,enabled=enabled) }
@Composable fun CheckChip(selected:Boolean,onClick:()->Unit,label:String) { FilterChip(selected,onClick,label={Text(label)}) }
@Composable fun WarningCard(title:String,text:String) { Card { Column(Modifier.padding(12.dp)){Text(title,style=MaterialTheme.typography.titleSmall);Text(text)} } }
@Composable fun SimpleTextSlider(value:Float,onValueChange:(Float)->Unit,valueRange:ClosedFloatingPointRange<Float>,modifier:Modifier=Modifier,enabled:Boolean=true,shorter:Boolean=false,decimalFormat:String="#0.00",suffix:String?=null,onValueChangeFinished:(()->Unit)?=null,onTextClick:(()->Unit)?=null,fineTuningControl:Boolean=true,fineTuningStep:Float=0.5f) { Slider(value.coerceIn(valueRange),onValueChange,valueRange=valueRange,enabled=enabled,onValueChangeFinished=onValueChangeFinished,modifier=modifier) }
@Composable fun SliderValueEditDialog(onDismissRequest:()->Unit,title:String,valueRange:ClosedFloatingPointRange<Float>,value:Float,onValueChange:(Float)->Unit,onValueChangeFinished:()->Unit={}) { var v by remember { mutableFloatStateOf(value) }; AlertDialog(onDismissRequest=onDismissRequest,title={Text(title)},text={Slider(v,{v=it},valueRange=valueRange)},confirmButton={TextButton(onClick={onValueChange(v);onValueChangeFinished();onDismissRequest()}){Text(stringResource(R.string.generic_confirm))}}) }
@Composable fun LittleTextLabel(text:String) { Text(text,style=MaterialTheme.typography.labelSmall) }
@Composable fun ButtonPosition() {}
@Composable fun PositionButton(onClick:()->Unit) { TextButton(onClick=onClick){Text("Position")} }
@Composable fun PositionFilledTonalButton(onClick:()->Unit) { FilledTonalButton(onClick=onClick){Text("Position")} }
