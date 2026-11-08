package app.fitnova.data

import android.content.ContentValues
import android.content.Context
import app.fitnova.domain.Meal
import app.fitnova.domain.Photo
import app.fitnova.domain.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Today's food log, kept on the phone. */
class MealStore(private val db: Db) {
    private val _today = MutableStateFlow<List<Meal>>(emptyList())
    val today: StateFlow<List<Meal>> = _today.asStateFlow()

    suspend fun load(day: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        _today.value = db.readableDatabase.rawQuery(
            "SELECT id, day, name, kcal, protein, carbs, fat, recipe_id, logged_at FROM meals WHERE day = ? ORDER BY logged_at",
            arrayOf(day.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Meal(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6), c.getString(7), c.getLong(8)))
                }
            }
        }
    }

    suspend fun add(meal: Meal) {
        withContext(Dispatchers.IO) {
            db.writableDatabase.insert(
                "meals",
                null,
                ContentValues().apply {
                    put("id", meal.id)
                    put("day", meal.day)
                    put("name", meal.name)
                    put("kcal", meal.kcal)
                    put("protein", meal.protein)
                    put("carbs", meal.carbs)
                    put("fat", meal.fat)
                    put("recipe_id", meal.recipeId)
                    put("logged_at", meal.loggedAt)
                },
            )
        }
        load()
    }

    suspend fun remove(id: String) {
        withContext(Dispatchers.IO) { db.writableDatabase.delete("meals", "id = ?", arrayOf(id)) }
        load()
    }
}

/** Bundled recipes (assets/recipes.json). Parsed once, off the main thread. */
suspend fun loadRecipes(context: Context): List<Recipe> = withContext(Dispatchers.IO) {
    runCatching {
        val rows = JSONArray(context.assets.open("recipes.json").bufferedReader().use { it.readText() })
        (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            fun list(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            Recipe(
                id = o.getString("id"),
                name = o.getString("name"),
                meal = o.optString("meal", "snack"),
                cuisine = o.optString("cuisine"),
                veg = o.optBoolean("veg"),
                minutes = o.optInt("minutes"),
                servings = o.optInt("servings", 1),
                kcal = o.optInt("kcal"),
                protein = o.optInt("protein"),
                carbs = o.optInt("carbs"),
                fat = o.optInt("fat"),
                fiber = o.optInt("fiber"),
                allergens = list("allergens"),
                tags = list("tags"),
                ingredients = list("ingredients"),
                steps = list("steps"),
                source = o.optString("source"),
                photo = o.optString("photo").ifBlank { null },
                photoCredit = o.optString("photoCredit").ifBlank { null },
                photoPage = o.optString("photoPage").ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())
}

/** Exercise photos (assets/exercise_photos.json), keyed by exercise id. Empty if not bundled. */
suspend fun loadExercisePhotos(context: Context): Map<String, Photo> = withContext(Dispatchers.IO) {
    runCatching {
        val root = JSONObject(context.assets.open("exercise_photos.json").bufferedReader().use { it.readText() })
        root.keys().asSequence().associateWith { id ->
            val o = root.getJSONObject(id)
            Photo(o.getString("photo"), o.optString("credit").ifBlank { null }, o.optString("page").ifBlank { null })
        }
    }.getOrDefault(emptyMap())
}
