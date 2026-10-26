package ru.family.budget.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Распознавание текста на скриншоте прямо на телефоне (Tesseract, русский + английский). */
class ScreenshotOcr(private val context: Context) {

    fun recognize(uri: Uri): List<OcrLine> {
        val bitmap = loadBitmap(uri)
        val api = TessBaseAPI()
        try {
            check(api.init(dataDir().absolutePath, LANGUAGES)) { "Не удалось запустить распознавание текста" }
            // Режим «разреженный текст» лучше всего подходит для экранов приложений.
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
            api.setVariable("user_defined_dpi", "300")
            api.setImage(bitmap)
            api.getUTF8Text() // запускает распознавание

            val result = mutableListOf<OcrLine>()
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            val it = api.resultIterator ?: return result
            try {
                it.begin()
                do {
                    val text = it.getUTF8Text(level)?.trim().orEmpty()
                    val r = it.getBoundingRect(level)
                    if (text.isNotEmpty()) result += OcrLine(text, r.left, r.top, r.right, r.bottom)
                } while (it.next(level))
            } finally {
                it.delete()
            }
            return result
        } finally {
            api.recycle()
            bitmap.recycle()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Очень большие картинки уменьшаем — распознавание будет быстрее, а точность не пострадает.
                val w = info.size.width
                if (w > MAX_WIDTH) decoder.setTargetSize(MAX_WIDTH, info.size.height * MAX_WIDTH / w)
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Языковые данные лежат в assets; Tesseract нужен обычный файл, поэтому копируем их один раз. */
    private fun dataDir(): File {
        val root = File(context.noBackupFilesDir, "ocr")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        for (lang in LANGUAGES.split('+')) {
            val file = File(tessdata, "$lang.traineddata")
            if (file.length() > 0) continue
            val tmp = File(tessdata, "$lang.tmp")
            context.assets.open("tessdata/$lang.traineddata").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(file)
        }
        return root
    }

    private companion object {
        const val LANGUAGES = "rus+eng"
        const val MAX_WIDTH = 1440
    }
}
