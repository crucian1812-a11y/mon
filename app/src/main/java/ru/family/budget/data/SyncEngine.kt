package ru.family.budget.data

import ru.family.budget.model.FamilyData
import ru.family.budget.model.Merge

/** Скачать → объединить с локальными данными → загрузить, с повтором при одновременной записи. */
class SyncEngine(private val api: GitHubSync) {

    /**
     * @param current текущие локальные данные (читаются заново при каждой попытке)
     * @return данные, которые теперь лежат в GitHub
     */
    fun sync(current: () -> FamilyData, commitMessage: String): FamilyData {
        repeat(MAX_ATTEMPTS) {
            val snapshot = current()
            val remote = api.fetch()
            val merged = if (remote.data == null) snapshot else Merge.merge(remote.data, snapshot)
            if (merged == remote.data) return merged
            try {
                api.push(merged, remote.sha, commitMessage)
                return merged
            } catch (e: ConflictException) {
                // Другой телефон успел сохранить свои изменения — скачиваем заново и объединяем.
            }
        }
        throw SyncException("Не удалось сохранить: файл постоянно меняется. Попробуйте ещё раз.")
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
