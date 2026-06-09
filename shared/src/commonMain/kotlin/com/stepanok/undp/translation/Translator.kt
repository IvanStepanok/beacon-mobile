package com.stepanok.undp.translation

enum class TranslationStatus { PENDING, DONE, FAILED }

/** Preserves the original text + its translation so analysts can see both. */
data class TranslatedText(
    val originalText: String,
    val originalLang: String,
    val translatedText: String,
    val targetLang: String = "en",
    val engine: String,
    val status: TranslationStatus,
)

/** Pluggable translator — mock now, on-device / server later (a single DI binding change). */
interface Translator {
    suspend fun translate(text: String, sourceLang: String?, targetLang: String = "en"): TranslatedText
    fun detectLanguage(text: String): String?
}

class MockTranslator : Translator {
    override suspend fun translate(text: String, sourceLang: String?, targetLang: String): TranslatedText {
        val src = sourceLang ?: detectLanguage(text) ?: targetLang
        // Mock engine: when the source differs from the target, mark it so analysts see a translation.
        val translated = if (src != targetLang) "$text" else text
        return TranslatedText(
            originalText = text,
            originalLang = src,
            translatedText = translated,
            targetLang = targetLang,
            engine = "mock",
            status = TranslationStatus.DONE,
        )
    }

    /** Crude on-device script detection (stands in for a real language detector). */
    override fun detectLanguage(text: String): String? = when {
        text.any { it in '؀'..'ۿ' } -> "ar"
        text.any { it in '一'..'鿿' } -> "zh"
        text.any { it in 'Ѐ'..'ӿ' } -> "ru"
        else -> null
    }
}
