package app.fitnova.domain

import kotlin.math.max
import kotlin.math.roundToInt

data class Recipe(
    val id: String,
    val name: String,
    val meal: String,
    val cuisine: String,
    val veg: Boolean,
    val minutes: Int,
    val servings: Int,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Int,
    val allergens: List<String>,
    val tags: List<String>,
    val ingredients: List<String>,
    val steps: List<String>,
    val source: String,
    /** Bundled photo path under assets/, with its licence credit and page (null if none). */
    val photo: String? = null,
    val photoCredit: String? = null,
    val photoPage: String? = null,
) {
    /** Everything search looks at, normalised once per recipe rather than on every keystroke. */
    internal val searchText by lazy { words("$name $cuisine $meal ${tags.joinToString(" ")} ${ingredients.joinToString(" ")}") }
}

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

enum class Activity(val label: String, val factor: Double, val hint: String) {
    LOW("Low", 1.2, "Desk job, little exercise"),
    LIGHT("Light", 1.375, "Workouts 1–3 days a week"),
    MODERATE("Moderate", 1.55, "Workouts 3–5 days a week"),
    HIGH("High", 1.725, "Workouts 6–7 days a week"),
    ATHLETE("Athlete", 1.9, "Hard training or a physical job"),
}

enum class Goal(val label: String) { LOSE("Lose fat"), MAINTAIN("Maintain"), GAIN("Build muscle") }

data class Body(val sex: Sex, val age: Int, val heightCm: Int, val weightKg: Int, val activity: Activity, val goal: Goal)

data class Targets(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)

/**
 * Mifflin–St Jeor BMR × activity factor, adjusted for the goal (−20% / +10%, with a safe floor).
 * Protein by body weight, fat at 25% of calories, carbs fill the rest. An estimate, not medical advice.
 */
fun targets(b: Body): Targets {
    val bmr = 10.0 * b.weightKg + 6.25 * b.heightCm - 5.0 * b.age + if (b.sex == Sex.MALE) 5 else -161
    val tdee = bmr * b.activity.factor
    val floor = if (b.sex == Sex.MALE) 1500.0 else 1200.0
    val kcal = when (b.goal) {
        Goal.LOSE -> max(tdee * 0.8, floor)
        Goal.MAINTAIN -> tdee
        Goal.GAIN -> tdee * 1.1
    }.roundToInt()
    val proteinPerKg = when (b.goal) {
        Goal.LOSE -> 2.0
        Goal.MAINTAIN -> 1.6
        Goal.GAIN -> 1.8
    }
    val protein = (b.weightKg * proteinPerKg).roundToInt()
    val fat = (kcal * 0.25 / 9).roundToInt()
    val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
    return Targets(kcal, protein, carbs, fat)
}

/** Allowed body numbers; the Body sheet shows them as the field ranges. */
val AGE_RANGE = 13..100
val HEIGHT_RANGE = 120..230
val WEIGHT_RANGE = 30..250

/** The [Body] typed into the Body sheet, or null while a number is missing or out of its range. */
fun bodyOrNull(sex: Sex, age: String, height: String, weight: String, activity: Activity, goal: Goal): Body? {
    val a = age.toIntOrNull()?.takeIf { it in AGE_RANGE } ?: return null
    val h = height.toIntOrNull()?.takeIf { it in HEIGHT_RANGE } ?: return null
    val w = weight.toIntOrNull()?.takeIf { it in WEIGHT_RANGE } ?: return null
    return Body(sex, a, h, w, activity, goal)
}

/**
 * False once the digits [typed] so far can no longer become a number in [range], however the user goes on (at most
 * [maxDigits] digits). A field can then flag "12" for an age of 13–100 at once, without flashing red on the "1" of 18.
 */
fun stillPossible(typed: String, range: IntRange, maxDigits: Int): Boolean {
    var low = typed.toLongOrNull() ?: return typed.isEmpty()
    var high = low
    repeat(maxDigits - typed.length + 1) {
        if (high >= range.first && low <= range.last) return true
        low *= 10
        high = high * 10 + 9
    }
    return false
}

data class Meal(
    val id: String,
    val day: String,
    val name: String,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val recipeId: String?,
    val loggedAt: Long,
)

data class Intake(val kcal: Int = 0, val protein: Int = 0, val carbs: Int = 0, val fat: Int = 0)

fun List<Meal>.intake() = Intake(sumOf { it.kcal }, sumOf { it.protein }, sumOf { it.carbs }, sumOf { it.fat })

// ---- Water ----

/** Daily goal in glasses of [GLASS_ML] ml (2 L). Up to [WATER_MAX] can be logged; AppViewModel.addWater caps there too. */
const val WATER_GOAL = 8
const val GLASS_ML = 250
const val WATER_MAX = 20

/**
 * The count after tapping glass [slot] (1-based) of [slots]: a tap fills up to that glass, and tapping the last full
 * glass empties it. Past [slots] every glass is already full and the extra ones are hidden, so a tap would remove
 * more than it shows: the count stays and only the −/+ buttons change it.
 */
fun tapGlass(current: Int, slot: Int, slots: Int = WATER_GOAL): Int = when {
    current > slots -> current
    slot == current -> slot - 1
    else -> slot
}

// ---- Recipes ----

/** Grams of protein per serving that make a recipe "high protein" (the 20g+ chip). */
const val HIGH_PROTEIN_G = 20

private val NonWord = Regex("[^\\p{L}\\p{N}]+")

/** Lower case with every run of punctuation or spaces turned into one space, so "Stir-Fry" matches "stir fry". */
private fun words(text: String) = text.lowercase().replace(NonWord, " ").trim()

/**
 * The recipes the Food tab shows: [meal] null means every meal, and each word of [query] has to appear in the
 * name, cuisine, meal, tags or ingredients.
 */
fun filterRecipes(all: List<Recipe>, query: String, meal: String?, vegOnly: Boolean, highProtein: Boolean): List<Recipe> {
    val terms = words(query).split(' ').filter { it.isNotEmpty() }
    return all.filter { r ->
        (meal == null || r.meal == meal) &&
            (!vegOnly || r.veg) &&
            (!highProtein || r.protein >= HIGH_PROTEIN_G) &&
            terms.all { it in r.searchText }
    }
}

// ---- Food artwork ----

/** Picture for a food, shown as emoji artwork. [PLATE] when nothing in the name is recognised. */
enum class FoodArt { EGG, CHICKEN, BANANA, AVOCADO, APPLE, FLATBREAD, RICE, BOWL, MILK, SALAD, CURRY, BROCCOLI, PLATE }

// Most specific first: "Egg Curry" is an egg, "Moong Dal Khichdi" rice, "Kala Chana Chaat" a salad.
// Meat words only; "tikka" alone stays a curry so Paneer Tikka never shows meat.
private val ArtWords = listOf(
    FoodArt.EGG to listOf("egg", "omelette", "omelet", "bhurji", "anda"),
    FoodArt.CHICKEN to listOf("chicken", "murgh", "mutton", "lamb", "meat", "keema", "kebab", "kabab", "turkey"),
    FoodArt.BANANA to listOf("banana", "kela"),
    FoodArt.AVOCADO to listOf("avocado", "guacamole"),
    FoodArt.APPLE to listOf("apple", "fruit"),
    FoodArt.FLATBREAD to listOf(
        "roti", "chapati", "chapatti", "phulka", "paratha", "parantha", "naan", "kulcha", "thepla", "puri", "wrap",
        "chilla", "cheela", "dosa", "uttapam", "pancake", "tortilla", "burrito", "toast", "bread", "sandwich", "pizza",
    ),
    FoodArt.RICE to listOf("rice", "pulao", "pilaf", "biryani", "khichdi", "idli", "poha"),
    FoodArt.BOWL to listOf("oats", "oatmeal", "porridge", "upma", "muesli", "cereal", "granola", "soup", "dalia", "quinoa"),
    FoodArt.MILK to listOf(
        "milk", "lassi", "shake", "smoothie", "yogurt", "yoghurt", "curd", "dahi", "raita", "chaas", "buttermilk",
        "whey", "drink", "tea", "chai", "coffee",
    ),
    FoodArt.SALAD to listOf("salad", "kachumber", "sprouts", "chaat", "hummus", "veggie", "vegetable", "cucumber"),
    FoodArt.CURRY to listOf(
        "curry", "dal", "daal", "dhal", "sambar", "rajma", "chole", "chana", "masala", "paneer", "korma", "sabzi",
        "sabji", "tikka", "makhani",
    ),
    FoodArt.BROCCOLI to listOf("broccoli", "stir fry", "greens", "spinach", "palak", "tofu"),
)

/** Artwork for a meal or recipe [name], matched on whole words (plurals too), so "eggplant" is not an egg. */
fun foodArt(name: String): FoodArt {
    val text = " ${words(name)} "
    return ArtWords.firstOrNull { (_, keys) -> keys.any { k -> " $k " in text || " ${k}s " in text || " ${k}es " in text } }?.first
        ?: FoodArt.PLATE
}
