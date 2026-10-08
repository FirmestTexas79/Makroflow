package cz.uhk.macroflow.nutrition

import java.text.Normalizer

/**
 * Kategorie ve špajzce (docs/adr/0073). Potravina se zařadí sama podle názvu – uživatel nic nevybírá.
 * Pořadí položek enumu = pořadí oddílů v seznamu.
 */
enum class FoodCategory(val label: String, val emoji: String, val color: Long) {
    MEAT("Maso a uzeniny", "🍗", 0xFFB5523B),
    FISH("Ryby a mořské plody", "🐟", 0xFF3E7CA6),
    DAIRY("Mléčné a vejce", "🧀", 0xFFD9A441),
    LEGUMES("Luštěniny a tofu", "🫘", 0xFF8A5A3B),
    GRAINS("Obiloviny a přílohy", "🍚", 0xFFC9A26B),
    BAKERY("Pečivo", "🥖", 0xFFB7793A),
    VEGETABLES("Zelenina", "🥦", 0xFF5E8C31),
    FRUIT("Ovoce", "🍎", 0xFFD0533F),
    NUTS("Ořechy a semínka", "🥜", 0xFF9C6B3C),
    FATS("Tuky a oleje", "🫒", 0xFF7D8B3A),
    SUPPLEMENTS("Doplňky a proteiny", "💪", 0xFF606C38),
    MEALS("Hotová jídla", "🍲", 0xFFBC6C25),
    SWEETS("Sladké a pochutiny", "🍫", 0xFF7A4A33),
    DRINKS("Nápoje", "🥤", 0xFF4F8FA0),
    OTHER("Ostatní", "🧂", 0xFF8C8C7A);

    companion object {
        /**
         * Zařazení podle názvu: slova bez diakritiky, malými písmeny; pravidlo platí, když některé slovo
         * názvu začíná daným kmenem („=slovo“ = celé slovo, „dvě slova“ = spojení v názvu). Výjimky jdou první (rybíz není ryba, arašídové máslo není tuk…),
         * pak kategorie v pořadí od nejjednoznačnějších. Nic nesedí → mililitry = nápoj, jinak Ostatní.
         */
        fun of(name: String, weight: String = ""): FoodCategory {
            val words = normalize(name).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            fun hit(stems: List<String>) = stems.any { st ->
                when {
                    ' ' in st -> normalize(name).contains(st)
                    st.startsWith("=") -> words.any { it == st.substring(1) }   // celé slovo (sýr ≠ syrová)
                    else -> words.any { it.startsWith(st) }
                }
            }
            RULES.firstOrNull { (_, stems) -> hit(stems) }?.let { return it.first }
            return if (Regex("""\d\s*(ml|l)\b""").containsMatchIn(weight.lowercase())) DRINKS else OTHER
        }

        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        private val RULES: List<Pair<FoodCategory, List<String>>> = listOf(
            // ── výjimky (jinak by spadly jinam) ──
            FATS to listOf("kokosovy olej", "kokosovy tuk"),
            SWEETS to listOf("brambur", "chips", "tycinka cokol"),
            GRAINS to listOf("knedlik", "bramborov knedl", "makaron"),
            FRUIT to listOf("rybiz", "kokos"),
            NUTS to listOf("arasidov", "mandlov", "kesu", "orechov maslo", "lieskov"),
            VEGETABLES to listOf("fazolky", "lusky", "hrasek", "cukrov hrach", "olivy", "olivky"),
            SUPPLEMENTS to listOf("protein", "izolat", "isolat", "whey", "syrovatkov", "kasein", "kreatin", "creatin", "bcaa", "gainer", "eaa"),
            DRINKS to listOf("napoj", "cokoladove mleko", "smoothie"),
            // ── hotová jídla ──
            MEALS to listOf("pizza", "burger", "hamburger", "kebab", "gulas", "polevk", "rizek", "rizot", "lasagne", "spaget",
                "sandwich", "sendvic", "wrap", "burrito", "tortilla s", "sushi", "omelet", "palacink", "livance", "knedlo",
                "svickova na", "segedin", "chilli con", "bowl", "salat s", "nudle s", "rizoto"),
            // ── živočišné ──
            FISH to listOf("losos", "pstruh", "makrel", "sled", "kapr", "tresk", "kreve", "sardin", "tunak", "ryb", "tilapi",
                "candat", "stika", "sumec", "halibut", "mors", "chobot", "kalamar", "slavky", "mussel", "surimi", "ancovi"),
            MEAT to listOf("hovez", "vepr", "kure", "kuri", "kruti", "kruta", "husi", "kachn", "jehne", "telec", "kralic", "srnc",
                "jelen", "divoc", "jatra", "sunk", "salam", "parek", "parky", "klobas", "debrecink", "slanin", "anglick slan", 
                "mlete maso", "maso", "steak", "svickov", "krkovic", "kyta", "panenk", "bucek", "zebir", "prsa", "stehn", "kridl",
                "uzene", "uzenin", "prosciutto", "chorizo", "pastrami", "kabanos", "tlacenk", "jitrnic", "sekana"),
            DAIRY to listOf("mlek", "mleko", "jogurt", "skyr", "tvaroh", "kefir", "acidof", "podmasl", "smetan", "=syr", "=syry", "=syru", "syrec", "eidam",
                "gouda", "emental", "parmez", "mozzar", "camembert", "hermelin", "niva", "tvaruzk", "ricott", "mascarpone", "cottage",
                "feta", "balkan", "halloumi", "cheddar", "brynz", "zervel", "vejce", "vajic", "bilek", "zloutek", "lucin", "philadelphia",
                "pribin", "termix", "puding", "mlecn"),
            // ── rostlinné ──
            LEGUMES to listOf("cocka", "cocky", "fazol", "hrach", "cizrn", "soja", "sojov", "tofu", "tempeh", "edamame", "hummus",
                "mungo", "lusten", "seitan"),
            NUTS to listOf("mandl", "kesu", "pistaci", "liskov", "orech", "arasid", "semin", "slunecnic", "dynov", "chia", "lnen",
                "sezam", "=mak", "makov", "pekan", "makadam", "para orech", "pinie", "konopn"),
            BAKERY to listOf("chleb", "chlebic", "rohlik", "housk", "bage", "toast", "knackebrot", "kaiserk", "veka", "pecivo",
                "tortill", "pita", "croissant", "loupak", "baget", "bulk", "dalamank", "kolac", "buchta", "muffin", "lavas", "bun"),
            GRAINS to listOf("ryze", "ryzov", "testovin", "spaget", "penne", "fusill", "kuskus", "bulgur", "quinoa", "kinoa", "pohank",
                "jahl", "vlock", "ovesn", "musli", "granol", "cornflak", "kase", "mouk", "kroupy", "knedlik", "noky", "gnocchi",
                "bramb", "batat", "hranolk", "krupic", "pseni", "kukurice", "popcorn", "amarant", "nudle", "klicky", "otruby"),
            VEGETABLES to listOf("zelen", "brokol", "kvetak", "mrkev", "mrkv", "okurk", "rajc", "paprik", "cibul", "cesnek", "porek",
                "salat", "spenat", "kapust", "zeli", "kedluben", "celer", "petrzel", "redkv", "rukol", "cuket", "lilek", "dyne",
                "houb", "zampion", "hrib", "hliv", "chrest", "repa", "cervena repa", "kukurice cukrova", "fenykl", "pak choi", "klicky z",
                "zeleninov", "pazitk", "bylink", "kopr", "kimchi", "kysane zeli"),
            FRUIT to listOf("jablk", "jablek", "hrusk", "banan", "pomeranc", "mandarin", "citron", "limet", "grep", "kiwi", "mango",
                "ananas", "jahod", "malin", "boruvk", "ostruzin", "brusink", "visn", "tresn", "merunk", "broskv", "nektarin",
                "svestk", "slivk", "hrozn", "hrozink", "fik", "dat", "melou", "avokad", "granatov", "papaj", "lici", "ovoc",
                "rakytn", "angrest", "kdoul", "kaki", "goji", "brusin"),
            FATS to listOf("olej", "maslo", "sadlo", "margarin", "ghi", "ghee", "tuk", "rama", "flora", "majonez", "lucina tuk"),
            SWEETS to listOf("cokol", "susenk", "oplatk", "bonbon", "gumov", "medvidk", "dort", "zakusek", "zmrzlin", "nutell", "med",
                "dzem", "marmelad", "cukr", "sirup", "chips", "brambur", "krekr", "tycink", "keks", "pernik", "lizatk",
                "marcipan", "karamel", "sladkost", "dezert", "tiramisu", "brownie", "donut", "kobliha", "kolac"),
            DRINKS to listOf("dzus", "limonad", "cola", "kola", "pivo", "vino", "kava", "caj", "voda", "most",
                "energy", "energet", "napoj", "nektar", "sirup do", "kombucha", "tonik", "frappe", "latte", "cappuccino"),
            OTHER to listOf("korein", "koren", "sul", "pepr", "kecup", "horcice", "omack", "dresink", "ocet", "drozd", "prasek do")
        )
    }
}
