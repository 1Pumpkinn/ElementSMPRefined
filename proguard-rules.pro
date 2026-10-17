-dontshrink
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class net.rose.elementSMPRefined.ElementSMPRefined { *; }
-keepclassmembers class * { @org.bukkit.event.EventHandler *; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-dontwarn **
# Don't touch shaded libs (relocated PacketEvents, etc.)
-keep class com.github.retrooper.** { *; }
-keep class io.github.retrooper.** { *; }

