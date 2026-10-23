-dontshrink
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class net.rose.elementSMPRefined.ElementSMPRefined { *; }
-keepclassmembers class * { @org.bukkit.event.EventHandler *; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# Paper API is compileOnly, so ProGuard cannot see its classes; silence the resulting warnings.
-dontwarn **
