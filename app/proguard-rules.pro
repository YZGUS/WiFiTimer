# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-dontwarn dagger.hilt.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Glance AppWidget
-dontwarn androidx.glance.**
-keep class androidx.glance.** { *; }
-keep class androidx.glance.appwidget.** { *; }**

# AndroidX
-dontwarn androidx.**
