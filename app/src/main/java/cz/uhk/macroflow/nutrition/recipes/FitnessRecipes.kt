package cz.uhk.macroflow.nutrition.recipes

/**
 * Fitness recepty (docs/adr/0068): 15 trendových jídel z TikToku / Instagramu 2025–2026.
 * Ingredience v gramech na celý recept, výživové hodnoty na 100 g (tabulkové, USDA).
 * Fotky: res/drawable-nodpi/recipe_<id>.jpg (Unsplash License, autor v [Recipe.photoCredit]).
 */
object FitnessRecipes {

    enum class Category(val label: String) { BREAKFAST("Snídaně"), MAIN("Hlavní jídlo"), SNACK("Svačina"), DESSERT("Dezert") }

    /** Ingredience: [grams] na celý recept, hodnoty na 100 g. */
    data class Ing(val name: String, val grams: Float, val kcal100: Float, val p100: Float, val s100: Float, val t100: Float, val fiber100: Float)

    data class Macros(val kcal: Int, val p: Float, val s: Float, val t: Float, val fiber: Float)

    data class Recipe(
        val id: String,
        val name: String,
        /** Známý (často anglický) název trendu. */
        val alias: String,
        val trend: String,
        val category: Category,
        val servings: Int,
        val minutes: Int,
        val ingredients: List<Ing>,
        /** Alergeny podle EU 14 (česky). */
        val allergens: List<String>,
        val steps: List<String>,
        val photoCredit: String
    ) {
        val photo: String get() = "recipe_$id"

        /** Makra pro [portions] porcí (porce = recept / servings). */
        fun macros(portions: Float = 1f): Macros {
            val k = portions / servings
            fun sum(sel: (Ing) -> Float) = ingredients.sumOf { (sel(it) * it.grams / 100f * k).toDouble() }.toFloat()
            return Macros(sum { it.kcal100 }.toInt(), sum { it.p100 }, sum { it.s100 }, sum { it.t100 }, sum { it.fiber100 })
        }

        /** Gramy ingrediencí pro [portions] porcí. */
        fun scaled(portions: Float): List<Ing> = ingredients.map { it.copy(grams = it.grams * portions / servings) }
    }

    val ALL: List<Recipe> = listOf(
        Recipe(
            id = "protein_overnight_oats", name = "Proteinové ovesné vločky přes noc", alias = "Protein Overnight Oats",
            trend = "Stálice TikToku a Instagramu – meal prep snídaně ve sklenici, kterou si fitness creatoři připravují na celý týden.",
            category = Category.BREAKFAST, servings = 1, minutes = 5,
            ingredients = listOf(
                Ing("Ovesné vločky", 60f, 379f, 13.2f, 67.7f, 6.5f, 10.1f),
                Ing("Syrovátkový protein (vanilka)", 30f, 380f, 78f, 8f, 6f, 0f),
                Ing("Polotučné mléko 1,5 %", 150f, 46f, 3.3f, 4.8f, 1.5f, 0f),
                Ing("Řecký jogurt 0 %", 100f, 59f, 10.2f, 3.6f, 0.4f, 0f),
                Ing("Chia semínka", 10f, 486f, 16.5f, 42.1f, 30.7f, 34.4f),
                Ing("Borůvky", 80f, 57f, 0.7f, 14.5f, 0.3f, 2.4f)
            ),
            allergens = listOf("lepek", "mléko"),
            steps = listOf(
                "Ve sklenici s víčkem promíchej vločky, protein a chia semínka.",
                "Přilij mléko a vmíchej řecký jogurt, aby nezůstaly hrudky.",
                "Uzavři a nech v lednici aspoň 6 hodin, ideálně přes noc.",
                "Ráno promíchej, případně dolij trochu mléka a navrch dej borůvky."
            ),
            photoCredit = "Cosmin Ursea / Unsplash"
        ),
        Recipe(
            id = "protein_pancakes", name = "Proteinové lívance s cottage", alias = "Protein Pancakes",
            trend = "Mixované lívance z vloček, vajec a cottage sýra patří k nejsdílenějším high-protein snídaním na TikToku.",
            category = Category.BREAKFAST, servings = 1, minutes = 15,
            ingredients = listOf(
                Ing("Ovesné vločky", 50f, 379f, 13.2f, 67.7f, 6.5f, 10.1f),
                Ing("Vejce", 110f, 143f, 12.6f, 0.7f, 9.5f, 0f),
                Ing("Cottage sýr", 100f, 98f, 11.1f, 3.4f, 4.3f, 0f),
                Ing("Banán", 60f, 89f, 1.1f, 22.8f, 0.3f, 2.6f),
                Ing("Prášek do pečiva", 4f, 53f, 0f, 28f, 0f, 0.2f),
                Ing("Borůvky", 60f, 57f, 0.7f, 14.5f, 0.3f, 2.4f)
            ),
            allergens = listOf("lepek", "vejce", "mléko"),
            steps = listOf(
                "Vločky, vejce, cottage, banán a prášek do pečiva rozmixuj dohladka.",
                "Nech těsto 5 minut odpočinout, aby vločky nasákly.",
                "Na nepřilnavé pánvi bez tuku (nebo s kapkou oleje) peč malé lívance cca 2 minuty z každé strany.",
                "Podávej s borůvkami, případně s lžící řeckého jogurtu."
            ),
            photoCredit = "nikldn / Unsplash"
        ),
        Recipe(
            id = "egg_muffins", name = "Vaječné muffiny se šunkou a zeleninou", alias = "Egg Muffins",
            trend = "Klasika meal prep videí – jedna dávka z pekáče vydrží v lednici na 4 dny a ráno se jen ohřeje.",
            category = Category.BREAKFAST, servings = 3, minutes = 30,
            ingredients = listOf(
                Ing("Vejce", 330f, 143f, 12.6f, 0.7f, 9.5f, 0f),
                Ing("Vaječné bílky", 150f, 52f, 10.9f, 0.7f, 0.2f, 0f),
                Ing("Baby špenát", 60f, 23f, 2.9f, 3.6f, 0.4f, 2.2f),
                Ing("Červená paprika", 100f, 31f, 1f, 6f, 0.3f, 2.1f),
                Ing("Cibule", 50f, 40f, 1.1f, 9.3f, 0.1f, 1.7f),
                Ing("Šunka výběrová", 100f, 110f, 19f, 1.5f, 3f, 0f),
                Ing("Eidam 30 %", 40f, 260f, 28f, 0f, 16.5f, 0f)
            ),
            allergens = listOf("vejce", "mléko"),
            steps = listOf(
                "Předehřej troubu na 180 °C a vymaž formu na 12 muffinů (nebo použij silikonovou).",
                "Nakrájej papriku, cibuli, šunku a špenát a rozděl je do důlků formy.",
                "Vejce a bílky rozšlehej se solí a pepřem a zalij jimi zeleninu.",
                "Posyp nastrouhaným eidamem a peč 18–20 minut, dokud vajíčka neztuhnou.",
                "Nech vychladnout a skladuj v krabičce v lednici max. 4 dny."
            ),
            photoCredit = "Mihály Vereb / Unsplash"
        ),
        Recipe(
            id = "greek_yogurt_bagels", name = "Proteinové bagely z řeckého jogurtu", alias = "2-Ingredient Bagels",
            trend = "Virální „dvousložkové“ těsto z mouky a řeckého jogurtu – rychlé pečivo s vyšším obsahem bílkovin bez kynutí.",
            category = Category.BREAKFAST, servings = 4, minutes = 35,
            ingredients = listOf(
                Ing("Pšeničná mouka hladká", 150f, 364f, 10.3f, 76.3f, 1f, 2.7f),
                Ing("Prášek do pečiva", 6f, 53f, 0f, 28f, 0f, 0.2f),
                Ing("Řecký jogurt 0 %", 200f, 59f, 10.2f, 3.6f, 0.4f, 0f),
                Ing("Vejce (na potření)", 25f, 143f, 12.6f, 0.7f, 9.5f, 0f),
                Ing("Sezam", 8f, 573f, 17.7f, 23.4f, 49.7f, 11.8f)
            ),
            allergens = listOf("lepek", "mléko", "vejce", "sezam"),
            steps = listOf(
                "Předehřej troubu na 190 °C a plech vylož pečicím papírem.",
                "Smíchej mouku, prášek do pečiva a špetku soli, přidej jogurt a zpracuj hladké těsto.",
                "Rozděl na 4 díly, vyválej válečky a spoj je do kroužků.",
                "Potři rozšlehaným vejcem a posyp sezamem.",
                "Peč 22–25 minut dozlatova a nech vychladnout na mřížce."
            ),
            photoCredit = "Claudio Schwarz / Unsplash"
        ),
        Recipe(
            id = "cottage_cheese_flatbread_wrap", name = "Wrap z cottage placky s kuřetem", alias = "Cottage Cheese Flatbread",
            trend = "Placka jen z cottage sýra a vajec je jeden z největších virálních hitů TikToku – low-carb a high-protein náhrada tortilly.",
            category = Category.MAIN, servings = 2, minutes = 45,
            ingredients = listOf(
                Ing("Cottage sýr", 230f, 98f, 11.1f, 3.4f, 4.3f, 0f),
                Ing("Vejce", 110f, 143f, 12.6f, 0.7f, 9.5f, 0f),
                Ing("Kuřecí prsa", 150f, 120f, 22.5f, 0f, 2.6f, 0f),
                Ing("Olivový olej", 5f, 884f, 0f, 0f, 100f, 0f),
                Ing("Ledový salát", 30f, 14f, 0.9f, 3f, 0.1f, 1.2f),
                Ing("Rajčata", 60f, 18f, 0.9f, 3.9f, 0.2f, 1.2f)
            ),
            allergens = listOf("mléko", "vejce"),
            steps = listOf(
                "Předehřej troubu na 180 °C a plech vylož pečicím papírem.",
                "Cottage sýr a vejce rozmixuj dohladka, osol a okořeň (např. česnek, oregano).",
                "Směs rozetři v tenké vrstvě na plech a peč 35–40 minut, dokud placka nezezlátne a nepůjde sloupnout.",
                "Mezitím osol kuřecí prsa a opeč je na oleji, pak nakrájej na nudličky.",
                "Placku rozkroj na dvě části, naplň kuřetem, salátem a rajčaty a zaroluj."
            ),
            photoCredit = "Giorgio Trovato / Unsplash"
        ),
        Recipe(
            id = "chicken_burrito_bowl", name = "Kuřecí burrito bowl na meal prep", alias = "Chicken Burrito Bowl",
            trend = "Nejoblíbenější meal prep krabička fitness creatorů – kopie Chipotle bowlu s vysokým obsahem bílkovin.",
            category = Category.MAIN, servings = 4, minutes = 40,
            ingredients = listOf(
                Ing("Kuřecí prsa", 600f, 120f, 22.5f, 0f, 2.6f, 0f),
                Ing("Rýže basmati (syrová)", 240f, 360f, 7.5f, 79f, 0.6f, 1.3f),
                Ing("Černé fazole sterilované (scezené)", 240f, 91f, 6f, 16.6f, 0.3f, 6.9f),
                Ing("Kukuřice sterilovaná (scezená)", 200f, 67f, 2.3f, 13f, 1f, 2f),
                Ing("Rajčata", 200f, 18f, 0.9f, 3.9f, 0.2f, 1.2f),
                Ing("Červená cibule", 60f, 40f, 1.1f, 9.3f, 0.1f, 1.7f),
                Ing("Avokádo", 140f, 160f, 2f, 8.5f, 14.7f, 6.7f),
                Ing("Limetková šťáva", 30f, 25f, 0.4f, 8.4f, 0.1f, 0.4f),
                Ing("Řecký jogurt 0 % (místo zakysané smetany)", 120f, 59f, 10.2f, 3.6f, 0.4f, 0f),
                Ing("Olivový olej", 10f, 884f, 0f, 0f, 100f, 0f),
                Ing("Mletá chilli směs koření", 10f, 282f, 13.5f, 49.7f, 14.3f, 34.8f)
            ),
            allergens = listOf("mléko"),
            steps = listOf(
                "Uvař rýži podle návodu a promíchej ji s trochou limetkové šťávy.",
                "Kuřecí prsa obal v oleji, chilli koření a soli a opeč na pánvi nebo grilu, pak nakrájej.",
                "Rajčata a cibuli nakrájej nadrobno a smíchej se zbytkem limetky (salsa).",
                "Fazole a kukuřici propláchni a krátce prohřej.",
                "Do 4 krabiček rozděl rýži, kuře, fazole, kukuřici a salsu; avokádo a jogurt přidej až před jídlem."
            ),
            photoCredit = "AnaCristina Smith / Unsplash"
        ),
        Recipe(
            id = "salmon_rice_bowl", name = "Lososový rýžový bowl", alias = "Salmon Rice Bowl",
            trend = "Odstartoval ho virální TikTok Emily Mariko a dodnes patří k nejkopírovanějším rychlým obědům s omega-3.",
            category = Category.MAIN, servings = 2, minutes = 30,
            ingredients = listOf(
                Ing("Losos (filet, syrový)", 250f, 208f, 20.4f, 0f, 13.4f, 0f),
                Ing("Rýže jasmínová (syrová)", 150f, 365f, 7.1f, 80f, 0.7f, 1.3f),
                Ing("Sójová omáčka", 20f, 53f, 8.1f, 4.9f, 0.6f, 0.8f),
                Ing("Edamame (mražené, loupané)", 100f, 121f, 11.9f, 8.9f, 5.2f, 5.2f),
                Ing("Okurka salátová", 150f, 15f, 0.7f, 3.6f, 0.1f, 0.5f),
                Ing("Avokádo", 100f, 160f, 2f, 8.5f, 14.7f, 6.7f),
                Ing("Sriracha", 10f, 93f, 1.9f, 19.2f, 0.9f, 2.2f),
                Ing("Sezam", 6f, 573f, 17.7f, 23.4f, 49.7f, 11.8f)
            ),
            allergens = listOf("ryby", "sója", "lepek", "sezam"),
            steps = listOf(
                "Uvař rýži.",
                "Lososa potři částí sójové omáčky a peč 12–15 minut na 200 °C (nebo v air fryeru).",
                "Edamame spař horkou vodou, okurku a avokádo nakrájej.",
                "Lososa rozdrob vidličkou do rýže, zakápni zbytkem sójové omáčky a srirachou.",
                "Přidej zeleninu, edamame, posyp sezamem a podávej (klidně s plátky nori)."
            ),
            photoCredit = "You Le / Unsplash"
        ),
        Recipe(
            id = "cottage_cheese_pasta", name = "Krémové těstoviny s cottage omáčkou a kuřetem", alias = "High-Protein Pasta",
            trend = "Omáčka z rozmixovaného cottage sýra a pečených rajčat je virální „fit alfredo“ s dvojnásobkem bílkovin.",
            category = Category.MAIN, servings = 3, minutes = 40,
            ingredients = listOf(
                Ing("Těstoviny semolinové (suché)", 200f, 371f, 13f, 75f, 1.5f, 3.2f),
                Ing("Kuřecí prsa", 300f, 120f, 22.5f, 0f, 2.6f, 0f),
                Ing("Cottage sýr", 250f, 98f, 11.1f, 3.4f, 4.3f, 0f),
                Ing("Cherry rajčata", 300f, 18f, 0.9f, 3.9f, 0.2f, 1.2f),
                Ing("Červená paprika", 150f, 31f, 1f, 6f, 0.3f, 2.1f),
                Ing("Česnek", 10f, 149f, 6.4f, 33f, 0.5f, 2.1f),
                Ing("Olivový olej", 10f, 884f, 0f, 0f, 100f, 0f),
                Ing("Parmazán", 20f, 392f, 35.8f, 3.2f, 25.8f, 0f)
            ),
            allergens = listOf("lepek", "mléko"),
            steps = listOf(
                "Rajčata, papriku a česnek pokapej olejem, osol a peč 20 minut na 200 °C.",
                "Mezitím uvař těstoviny al dente a opeč osolené kuřecí prsa nakrájená na kostky.",
                "Upečenou zeleninu rozmixuj s cottage sýrem a parmazánem do hladké omáčky.",
                "Omáčku smíchej s těstovinami a kuřetem, případně zřeď trochou vody z těstovin."
            ),
            photoCredit = "Pixzolo Photography / Unsplash"
        ),
        Recipe(
            id = "smash_burger_tacos", name = "Smash burger tacos z libového hovězího", alias = "Smash Burger Tacos",
            trend = "Hamburger přitlačený rovnou na tortillu – virální „Big Mac tacos“ s fit omáčkou z jogurtu.",
            category = Category.MAIN, servings = 2, minutes = 20,
            ingredients = listOf(
                Ing("Hovězí mleté maso 5 % tuku", 400f, 137f, 21.4f, 0f, 5f, 0f),
                Ing("Pšeničné tortilly malé", 160f, 306f, 8.2f, 50f, 7.5f, 3.5f),
                Ing("Eidam 30 % (plátky)", 60f, 260f, 28f, 0f, 16.5f, 0f),
                Ing("Ledový salát", 60f, 14f, 0.9f, 3f, 0.1f, 1.2f),
                Ing("Sterilované okurky", 60f, 25f, 0.5f, 5.5f, 0.1f, 1f),
                Ing("Cibule", 40f, 40f, 1.1f, 9.3f, 0.1f, 1.7f),
                Ing("Řecký jogurt 0 %", 80f, 59f, 10.2f, 3.6f, 0.4f, 0f),
                Ing("Hořčice", 10f, 60f, 3.7f, 5.8f, 3.3f, 4f),
                Ing("Kečup", 15f, 101f, 1f, 25f, 0.1f, 0.3f)
            ),
            allergens = listOf("lepek", "mléko", "hořčice"),
            steps = listOf(
                "Maso osol, rozděl na 4 kuličky a každou rozplácni na jednu stranu tortilly.",
                "Tortillu pokládej masem dolů na rozpálenou pánev a peč 3 minuty, až je maso propečené.",
                "Otoč, na maso polož plátek eidamu a ohřej tortillu ještě 1 minutu.",
                "Smíchej jogurt, hořčici, kečup a pár nasekaných okurek na „Big Mac“ omáčku.",
                "Taco naplň salátem, cibulí a okurkami, přelij omáčkou a přelož napůl."
            ),
            photoCredit = "Jeswin Thomas / Unsplash"
        ),
        Recipe(
            id = "turkey_chili", name = "Krůtí chilli s fazolemi", alias = "Turkey Chili",
            trend = "Velký hrnec na celý týden – oblíbený „bulk“ i „cut“ meal prep amerických fitness creatorů.",
            category = Category.MAIN, servings = 4, minutes = 50,
            ingredients = listOf(
                Ing("Krůtí mleté maso", 500f, 150f, 18.7f, 0f, 8.3f, 0f),
                Ing("Červené fazole sterilované (scezené)", 400f, 100f, 6.9f, 13.5f, 0.5f, 6.4f),
                Ing("Krájená rajčata v konzervě", 800f, 21f, 1f, 4f, 0.2f, 1f),
                Ing("Cibule", 150f, 40f, 1.1f, 9.3f, 0.1f, 1.7f),
                Ing("Červená paprika", 150f, 31f, 1f, 6f, 0.3f, 2.1f),
                Ing("Česnek", 10f, 149f, 6.4f, 33f, 0.5f, 2.1f),
                Ing("Mletá chilli směs koření", 10f, 282f, 13.5f, 49.7f, 14.3f, 34.8f),
                Ing("Olivový olej", 10f, 884f, 0f, 0f, 100f, 0f)
            ),
            allergens = listOf(),
            steps = listOf(
                "V hrnci na oleji osmahni nakrájenou cibuli, papriku a česnek.",
                "Přidej krůtí maso a opékej, dokud nezbělá; rozdrob ho vařečkou.",
                "Vmíchej chilli koření, rajčata a scezené fazole, osol.",
                "Duš pod pokličkou 25–30 minut, občas zamíchej.",
                "Podávej samotné, s rýží nebo s lžící řeckého jogurtu."
            ),
            photoCredit = "Maria Klichik / Unsplash"
        ),
        Recipe(
            id = "protein_chia_pudding", name = "Proteinový chia puding s malinami", alias = "Protein Chia Pudding",
            trend = "Chia puding se na TikToku vrátil v proteinové verzi s jogurtem – sytá svačina s vlákninou připravená předem.",
            category = Category.SNACK, servings = 1, minutes = 5,
            ingredients = listOf(
                Ing("Chia semínka", 30f, 486f, 16.5f, 42.1f, 30.7f, 34.4f),
                Ing("Polotučné mléko 1,5 %", 150f, 46f, 3.3f, 4.8f, 1.5f, 0f),
                Ing("Řecký jogurt 0 %", 100f, 59f, 10.2f, 3.6f, 0.4f, 0f),
                Ing("Syrovátkový protein (vanilka)", 15f, 380f, 78f, 8f, 6f, 0f),
                Ing("Maliny", 80f, 52f, 1.2f, 11.9f, 0.7f, 6.5f),
                Ing("Med", 5f, 304f, 0.3f, 82.4f, 0f, 0.2f)
            ),
            allergens = listOf("mléko"),
            steps = listOf(
                "Protein rozmíchej v mléce, aby nevznikly hrudky.",
                "Přidej chia semínka a jogurt, důkladně promíchej.",
                "Po 10 minutách znovu zamíchej a nech v lednici aspoň 3 hodiny.",
                "Navrch dej maliny a pokapej medem."
            ),
            photoCredit = "Maryam Sicard / Unsplash"
        ),
        Recipe(
            id = "greek_yogurt_bark", name = "Mražená jogurtová kůra s ovocem", alias = "Greek Yogurt Bark",
            trend = "Zmrazený plát řeckého jogurtu s ovocem a čokoládou je virální „zdravá“ sladkost z TikToku a Pinterestu.",
            category = Category.SNACK, servings = 4, minutes = 10,
            ingredients = listOf(
                Ing("Řecký jogurt 0 %", 500f, 59f, 10.2f, 3.6f, 0.4f, 0f),
                Ing("Syrovátkový protein (vanilka)", 30f, 380f, 78f, 8f, 6f, 0f),
                Ing("Med", 20f, 304f, 0.3f, 82.4f, 0f, 0.2f),
                Ing("Jahody", 150f, 32f, 0.7f, 7.7f, 0.3f, 2f),
                Ing("Borůvky", 100f, 57f, 0.7f, 14.5f, 0.3f, 2.4f),
                Ing("Hořká čokoláda 70 %", 30f, 598f, 7.8f, 45.9f, 42.6f, 10.9f)
            ),
            allergens = listOf("mléko", "sója"),
            steps = listOf(
                "Jogurt smíchej s proteinem a medem dohladka.",
                "Rozetři ho na plech s pečicím papírem do vrstvy asi 1 cm.",
                "Posyp nakrájenými jahodami a borůvkami a pokapej rozpuštěnou čokoládou.",
                "Zmraz aspoň na 3 hodiny, pak nalámej na kousky a skladuj v mrazáku."
            ),
            photoCredit = "Hannah Busing / Unsplash"
        ),
        Recipe(
            id = "cottage_cheese_ice_cream", name = "Jahodová zmrzlina z cottage sýra", alias = "Cottage Cheese Ice Cream",
            trend = "Rozmixovaný a zmražený cottage sýr (často v Ninja Creami) je jedním z nejvirálnějších high-protein dezertů let 2024–2026.",
            category = Category.DESSERT, servings = 2, minutes = 10,
            ingredients = listOf(
                Ing("Cottage sýr", 300f, 98f, 11.1f, 3.4f, 4.3f, 0f),
                Ing("Jahody (čerstvé nebo mražené)", 150f, 32f, 0.7f, 7.7f, 0.3f, 2f),
                Ing("Med", 25f, 304f, 0.3f, 82.4f, 0f, 0.2f)
            ),
            allergens = listOf("mléko"),
            steps = listOf(
                "Cottage sýr, jahody a med rozmixuj úplně dohladka.",
                "Nalij do nádoby (nebo kelímku Ninja Creami) a zmraz na 4–6 hodin.",
                "Před podáváním nech 10 minut povolit a prošlehej, nebo zpracuj v Ninja Creami.",
                "Podávej s čerstvým ovocem."
            ),
            photoCredit = "Julia Vivcharyk / Unsplash"
        ),
        Recipe(
            id = "protein_mug_cake", name = "Čokoládový proteinový hrnkový dort", alias = "Protein Mug Cake",
            trend = "Dezert z mikrovlnky za 90 sekund – stálý hit „high-protein dessert“ videí na TikToku i Instagramu.",
            category = Category.DESSERT, servings = 1, minutes = 5,
            ingredients = listOf(
                Ing("Syrovátkový protein (čokoláda)", 30f, 380f, 78f, 8f, 6f, 0f),
                Ing("Ovesné vločky (mleté)", 20f, 379f, 13.2f, 67.7f, 6.5f, 10.1f),
                Ing("Kakao holandské", 10f, 228f, 19.6f, 57.9f, 13.7f, 37f),
                Ing("Vejce", 55f, 143f, 12.6f, 0.7f, 9.5f, 0f),
                Ing("Polotučné mléko 1,5 %", 40f, 46f, 3.3f, 4.8f, 1.5f, 0f),
                Ing("Prášek do pečiva", 2f, 53f, 0f, 28f, 0f, 0.2f),
                Ing("Hořká čokoláda 70 %", 10f, 598f, 7.8f, 45.9f, 42.6f, 10.9f)
            ),
            allergens = listOf("lepek", "vejce", "mléko", "sója"),
            steps = listOf(
                "Ve větším hrnku smíchej protein, mleté vločky, kakao a prášek do pečiva.",
                "Přidej vejce a mléko a vidličkou prošlehej hladké těsto.",
                "Doprostřed zatlač kousky čokolády.",
                "Peč v mikrovlnce 60–90 s na plný výkon, nepřepékej, ať zůstane vláčný."
            ),
            photoCredit = "Dan DeAlmeida / Unsplash"
        ),
        Recipe(
            id = "big_back_yogurt_bowl", name = "Proteinový jogurtový bowl s arašídovým máslem", alias = "„Big Back“ Protein Bowl",
            trend = "Nadupaný jogurtový bowl s granolou a arašídovým máslem – trend „big back“ svačin, které vypadají jako dezert, ale mají přes 30 g bílkovin.",
            category = Category.SNACK, servings = 1, minutes = 5,
            ingredients = listOf(
                Ing("Řecký jogurt 0 %", 250f, 59f, 10.2f, 3.6f, 0.4f, 0f),
                Ing("Granola", 40f, 450f, 10f, 62f, 17f, 7f),
                Ing("Arašídové máslo", 15f, 588f, 25f, 20f, 50f, 6f),
                Ing("Borůvky", 80f, 57f, 0.7f, 14.5f, 0.3f, 2.4f),
                Ing("Banán", 60f, 89f, 1.1f, 22.8f, 0.3f, 2.6f),
                Ing("Med", 5f, 304f, 0.3f, 82.4f, 0f, 0.2f)
            ),
            allergens = listOf("mléko", "lepek", "arašídy"),
            steps = listOf(
                "Jogurt dej do misky a krátce ho prošlehej, ať je krémový.",
                "Přidej nakrájený banán a borůvky.",
                "Posyp granolou a přelij rozehřátým arašídovým máslem a medem."
            ),
            photoCredit = "Shayna Douglas / Unsplash"
        )
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }
}
