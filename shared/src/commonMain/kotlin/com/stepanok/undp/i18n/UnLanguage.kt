package com.stepanok.undp.i18n

/** The 6 UN languages. The app follows the system language, mapped to the nearest of these. */
enum class UnLanguage(val tag: String, val nativeName: String, val isRtl: Boolean) {
    ENGLISH("en", "English", false),
    ARABIC("ar", "العربية", true),
    CHINESE("zh", "中文", false),
    FRENCH("fr", "Français", false),
    RUSSIAN("ru", "Русский", false),
    SPANISH("es", "Español", false);

    companion object {
        /** Maps an arbitrary system locale tag to the nearest UN language (fallback English). */
        fun fromTag(tag: String): UnLanguage {
            val lang = tag.substringBefore('-').substringBefore('_').lowercase()
            return when (lang) {
                "ar", "fa", "ur", "ps", "ckb", "he" -> ARABIC      // RTL family → Arabic UI
                "zh", "yue" -> CHINESE
                "fr" -> FRENCH
                "ru", "uk", "be", "bg", "kk" -> RUSSIAN            // Cyrillic-script nearest
                "es", "pt", "ca", "gl" -> SPANISH                  // Iberian/Romance nearest
                "en" -> ENGLISH
                else -> ENGLISH
            }
        }
    }
}
