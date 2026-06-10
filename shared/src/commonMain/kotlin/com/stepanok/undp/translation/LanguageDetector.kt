package com.stepanok.undp.translation

/**
 * On-device language DETECTION only — the app never translates. Reports go up with the
 * original text + detected language; the real translation runs server-side (LibreTranslate)
 * after submit, so analysts see both the original and the machine translation.
 */
interface LanguageDetector {
    fun detectLanguage(text: String): String?
}

/** Crude script-range detection (stands in for a real language detector). */
class ScriptLanguageDetector : LanguageDetector {
    override fun detectLanguage(text: String): String? = when {
        text.any { it in '؀'..'ۿ' } -> "ar"
        text.any { it in '一'..'鿿' } -> "zh"
        text.any { it in 'Ѐ'..'ӿ' } -> "ru"
        else -> null
    }
}
