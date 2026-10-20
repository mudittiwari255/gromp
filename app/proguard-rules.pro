# Room and WorkManager ship their own consumer rules.
# Keep the accessibility and notification services referenced from the manifest.
-keep class app.gromp.lock.GrompAccessibilityService { *; }
-keep class app.gromp.data.NotificationCounterService { *; }
