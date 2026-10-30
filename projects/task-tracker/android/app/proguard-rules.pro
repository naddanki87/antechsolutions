# Framework-only app: default optimize rules are enough. Strip debug logs in release.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
