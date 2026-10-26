# kotlinx.serialization: сохраняем сгенерированные сериализаторы
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ru.family.budget.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.family.budget.**$$serializer { *; }
