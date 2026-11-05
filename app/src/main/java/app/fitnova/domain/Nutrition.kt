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
)

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

enum class Activity(val label: String, val factor: Double) {
    LOW("Low", 1.2),
    LIGHT("Light", 1.375),
    MODERATE("Moderate", 1.55),
    HIGH("High", 1.725),
    ATHLETE("Athlete", 1.9),
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
