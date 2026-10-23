-dontshrink
-dontoptimize
-dontpreverify
-dontnote **
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Main plugin class (referenced by plugin.yml / paper-plugin.yml)
-keep class net.rose.elementSMPRefined.ElementSMPRefined { *; }

# Paper bootstrap/loader classes, if you use them
-keep class * implements io.papermc.paper.plugin.bootstrap.PluginBootstrap { *; }
-keep class * implements io.papermc.paper.plugin.loader.PluginLoader { *; }

# Event handlers (names must survive for reflection-based registration)
-keepclassmembers class * {
    @org.bukkit.event.EventHandler *;
}

# Commands referenced by name in plugin.yml
-keep class * implements org.bukkit.command.CommandExecutor { *; }
-keep class * implements org.bukkit.command.TabCompleter { *; }

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Config serialization, if you use it
-keep class * implements org.bukkit.configuration.serialization.ConfigurationSerializable { *; }

# Narrow this once the build is stable
-dontwarn **