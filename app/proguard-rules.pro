# kotlinx.serialization: сохраняем сгенерированные сериализаторы
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ru.family.budget.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.family.budget.**$$serializer { *; }

# Tesseract OCR: нативный код обращается к полям и методам Java-классов по имени
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
