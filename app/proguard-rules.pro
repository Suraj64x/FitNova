# ML Kit discovers its pose detector through ComponentRegistrar classes named in manifest
# meta-data and created by reflection; R8 full mode strips them without these rules.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.** { *; }

# WorkManager (pulled in transitively by ML Kit) builds its Room database by reflection
# (Class.forName("...WorkDatabase_Impl")), which R8 full mode would otherwise strip.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
