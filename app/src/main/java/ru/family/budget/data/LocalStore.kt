package ru.family.budget.data

import android.content.Context
import android.util.AtomicFile
import ru.family.budget.model.FamilyData
import java.io.File

/** Локальная копия данных на телефоне — приложение работает и без интернета. */
class LocalStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "family-data.json"))

    fun load(): FamilyData = try {
        AppJson.decodeFromString(FamilyData.serializer(), String(file.readFully(), Charsets.UTF_8))
    } catch (e: Exception) {
        FamilyData()
    }

    fun save(data: FamilyData) {
        val bytes = AppJson.encodeToString(FamilyData.serializer(), data).toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
