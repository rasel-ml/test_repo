package com.fontlens.data

import android.net.Uri

data class FontMeta(
    val family: String = "",
    val subfamily: String = "",
    val fullName: String = "",
    val version: String = "",
    val postscript: String = "",
    val manufacturer: String = "",
    val designer: String = "",
    val description: String = "",
    val trademark: String = "",
    val license: String = "",
    val licenseURL: String = "",
    val vendorURL: String = "",
    val designerURL: String = "",
    val sampleText: String = "",
    val numGlyphs: Int = 0,
    val weight: Int = 400,
    val weightName: String = "Regular",
    val unitsPerEm: Int = 1000,
    val italicAngle: Float = 0f,
    val isFixedPitch: Boolean = false,
    val boldSupport: Boolean = false,
    val italicSupport: Boolean = false,
    val condensedSupport: Boolean = false,
    val extendedSupport: Boolean = false,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isRegular: Boolean = true,
    val tables: List<String> = emptyList(),
    val supportedChars: List<Int> = emptyList(),
    val scriptCodes: List<String> = emptyList()
)

data class FontItem(
    val id: String,
    val displayName: String,
    val uri: Uri,
    val meta: FontMeta,
    var metaOverrides: Map<String, String> = emptyMap(),
    val addedAt: Long = System.currentTimeMillis(),
    val folderPath: String = ""
) {
    val effectiveMeta: FontMeta get() = meta.copy(
        family       = metaOverrides["family"]       ?: meta.family,
        subfamily    = metaOverrides["subfamily"]    ?: meta.subfamily,
        fullName     = metaOverrides["fullName"]     ?: meta.fullName,
        version      = metaOverrides["version"]      ?: meta.version,
        postscript   = metaOverrides["postscript"]   ?: meta.postscript,
        manufacturer = metaOverrides["manufacturer"] ?: meta.manufacturer,
        designer     = metaOverrides["designer"]     ?: meta.designer,
        description  = metaOverrides["description"]  ?: meta.description,
        trademark    = metaOverrides["trademark"]    ?: meta.trademark,
        license      = metaOverrides["license"]      ?: meta.license,
        licenseURL   = metaOverrides["licenseURL"]   ?: meta.licenseURL,
        vendorURL    = metaOverrides["vendorURL"]    ?: meta.vendorURL,
        designerURL  = metaOverrides["designerURL"]  ?: meta.designerURL,
        sampleText   = metaOverrides["sampleText"]   ?: meta.sampleText
    )
}

data class AppSettings(
    // langSamples: keyed by script code (e.g. "en", "hi"). Values are the sample texts.
    val langSamples: Map<String, String> = defaultLangSamples(),
    // User-defined priority order of script codes. Drives chip order and preview priority.
    val scriptOrder: List<String> = defaultLangSamples().keys.toList(),
    val samplePriority: SamplePriority = SamplePriority.METADATA_FIRST,
    val glyphShowAll: Boolean = false,
    // Legacy kept for Gson backward compat
    val defaultLangs: List<String> = listOf("en"),
    val defaultLang: String = "en",
    val folderRecursive: Boolean = true,
    val colorTheme: ColorTheme = ColorTheme.GREEN,
    val darkMode: Boolean = false,
    val followSystem: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DAY,
    val preferMetaSample: Boolean = true,
    val showFullFontName: Boolean = false,
    val langCoverageThreshold: Int = 40,
    // Legacy
    val theme: AppTheme = AppTheme.DAY
)

/** Built-in sample texts for all 29 scripts in ScriptCoverageAnalyzer */
fun defaultLangSamples(): Map<String, String> = linkedMapOf(
    "en"   to "The quick brown fox jumps over the lazy dog",
    "bn"   to "আমার সোনার বাংলা আমি তোমায় ভালোবাসি",
    "hi"   to "सभी मनुष्यों को गौरव और अधिकारों के मामले में जन्मजात स्वतन्त्रता",
    "ar"   to "الحروف العربية جميلة ومتنوعة في أشكالها",
    "he"   to "כָּל בְּנֵי הָאָדָם נוֹלְדוּ בְּנֵי חוֹרִין",
    "zh"   to "人人生而自由，在尊嚴和權利上一律平等",
    "ja"   to "いろはにほへとちりぬるをわかよたれそ",
    "ko"   to "나랏말싸미 듕귁에 달아 문자와로 서로 사맛디",
    "ta"   to "அனைத்து மனிதர்களும் சுதந்திரமாகவே பிறக்கின்றனர்",
    "te"   to "అన్ని మానవులు స్వేచ్ఛగా జన్మిస్తారు",
    "kn"   to "ಎಲ್ಲಾ ಮಾನವರೂ ಸ್ವತಂತ್ರರಾಗಿ ಜನಿಸಿದ್ದಾರೆ",
    "ml"   to "എല്ലാ മനുഷ്യരും സ്വതന്ത്രരായി ജനിക്കുന്നു",
    "gu"   to "બધા મનુષ્યો સ્વતંત્ર અને સમાન ગૌરવ સાથે જન્મ્યા છે",
    "pa"   to "ਸਾਰੇ ਮਨੁੱਖ ਆਜ਼ਾਦ ਅਤੇ ਬਰਾਬਰ ਪੈਦਾ ਹੁੰਦੇ ਹਨ",
    "or"   to "ସମସ୍ତ ମଣିଷ ସ୍ୱାଧୀନ ଭାବରେ ଜନ୍ମ ଲାଭ କରନ୍ତି",
    "si"   to "සියලු මනුෂ්‍යයෝ නිදහස් ව උපත ලබති",
    "th"   to "มนุษย์ทุกคนเกิดมามีอิสระและเท่าเทียมกัน",
    "km"   to "មនុស្សទាំងអស់កើតមកមានសេរីភាព",
    "my"   to "လူသားအားလုံးသည် လွတ်လပ်စွာမွေးဖွားလာကြသည်",
    "ka"   to "ყველა ადამიანი იბადება თავისუფალი",
    "hy"   to "Բոլոր մարդիկ ծնվում են ազատ",
    "am"   to "ሁሉም ሰዎች ነፃ ሆነው ይወለዳሉ",
    "ru"   to "Все люди рождаются свободными и равными",
    "el"   to "Όλοι οι άνθρωποι γεννιούνται ελεύθεροι",
    "bo"   to "མི་རིགས་ཀྱི་གོ་མཐོ་དང་ཐོབ་ཐང་གི་ཐད་ནས་རང་དབང་ཡོད།",
    "lo"   to "ມະນຸດທຸກຄົນເກີດມາມີສິດທິເທົ່າທຽມກັນ",
    "mn"   to "Хүн бүр төрөлхийн эрх чөлөөтэй",
    "pl"   to "Wszyscy ludzie rodzą się wolni i równi",
    "ipa"  to "ðə kwɪk braʊn fɒks dʒʌmps ˈoʊvər ðə ˈleɪzi dɒɡ"
)

/** Human-readable name for each script code */
fun scriptDisplayName(code: String): String = when (code) {
    "en"  -> "English"
    "bn"  -> "Bengali"
    "hi"  -> "Hindi"
    "ar"  -> "Arabic"
    "he"  -> "Hebrew"
    "zh"  -> "Chinese"
    "ja"  -> "Japanese"
    "ko"  -> "Korean"
    "ta"  -> "Tamil"
    "te"  -> "Telugu"
    "kn"  -> "Kannada"
    "ml"  -> "Malayalam"
    "gu"  -> "Gujarati"
    "pa"  -> "Punjabi"
    "or"  -> "Odia"
    "si"  -> "Sinhala"
    "th"  -> "Thai"
    "km"  -> "Khmer"
    "my"  -> "Burmese"
    "ka"  -> "Georgian"
    "hy"  -> "Armenian"
    "am"  -> "Amharic"
    "ru"  -> "Russian"
    "el"  -> "Greek"
    "bo"  -> "Tibetan"
    "lo"  -> "Lao"
    "mn"  -> "Mongolian"
    "pl"  -> "Polish"
    "ipa" -> "IPA"
    else  -> code.uppercase()
}

enum class SamplePriority { METADATA_FIRST, USER_FIRST, ALWAYS_USER, ALWAYS_META }
enum class AppTheme { SYSTEM, DAY, NIGHT }
enum class ColorTheme { RED, YELLOW, GREEN, BLUE }
enum class ThemeMode { SYSTEM, DAY, NIGHT }
enum class SortOrder { NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, FOLDER }

sealed class FontListItem {
    data class Font(val font: FontItem) : FontListItem()
    data class FolderHeader(val path: String) : FontListItem()
}
