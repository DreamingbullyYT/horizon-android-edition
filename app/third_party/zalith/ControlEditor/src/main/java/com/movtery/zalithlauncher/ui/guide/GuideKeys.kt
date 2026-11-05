package com.movtery.zalithlauncher.ui.guide
import com.movtery.guide.GuideKey
private data class Key(val id:String):GuideKey
object GuideKeys {
 object Editor { object Step {
  val MenuBall=Key("editor-menu-ball")
  val LayerList=Key("editor-layer-list")
  val CreateLayer=Key("editor-create-layer")
  val Preview=Key("editor-preview")
  val Save=Key("editor-save")
 } }
}
