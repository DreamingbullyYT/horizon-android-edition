package com.movtery.zalithlauncher.utils.logging
import android.util.Log
object Logger {
 fun warning(tag:String,message:String,error:Throwable?=null) { Log.w(tag,message,error) }
}
