-dontshrink
-dontoptimize
-dontwarn **
-dontusemixedcaseclassnames

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

-repackageclasses 'a'
-overloadaggressively
-useuniqueclassmembernames

# Main class (matches plugin.yml: net.rose.elementSMPRefined.ElementSMPRefined)
-keep public class * extends org.bukkit.plugin.java.JavaPlugin { *; }

# Bukkit finds listeners via reflection and the annotation
-keepclassmembers class * {
    @org.bukkit.event.EventHandler <methods>;
}

# Enums: your Element names get used with valueOf / saved data / PDC
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements org.bukkit.configuration.serialization.ConfigurationSerializable { *; }

# Obfuscation diagnostics used by coverage, retrace, and shrink analysis.
-printmapping build/outputs/proguard/mapping.txt
-printseeds build/outputs/proguard/seeds.txt
-printusage build/outputs/proguard/usage.txt
