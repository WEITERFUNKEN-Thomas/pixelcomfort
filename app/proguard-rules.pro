# Shizuku.newProcess ist privat und wird nur per Reflection aufgerufen
# (ShizukuShell.run). Ohne diese Regel entfernt R8 die Methode.
-keepclassmembers class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

# Quelloffene App: Namen nicht verschleiern, damit Stacktraces im Logcat
# lesbar bleiben und die Zeilennummern zum Quelltext passen.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
