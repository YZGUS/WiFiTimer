# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-dontwarn dagger.hilt.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**

# AndroidX
-dontwarn androidx.**
