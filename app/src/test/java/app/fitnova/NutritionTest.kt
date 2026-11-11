package app.fitnova

import app.fitnova.domain.Activity
import app.fitnova.domain.Body
import app.fitnova.domain.FoodArt
import app.fitnova.domain.Goal
import app.fitnova.domain.Recipe
import app.fitnova.domain.Sex
import app.fitnova.domain.bodyOrNull
import app.fitnova.domain.filterRecipes
import app.fitnova.domain.foodArt
import app.fitnova.domain.stillPossible
import app.fitnova.domain.tapGlass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {
    @Test fun tappingAGlassFillsUpToIt() {
        assertEquals(3, tapGlass(0, 3))
        assertEquals(5, tapGlass(2, 5))
        assertEquals(2, tapGlass(5, 2)) // tap to set lower, like a rating bar
    }

    @Test fun tappingTheLastFullGlassEmptiesIt() {
        assertEquals(2, tapGlass(3, 3))
        assertEquals(7, tapGlass(8, 8))
        assertEquals(0, tapGlass(1, 1))
    }

    @Test fun tapsNeverDropHiddenGlassesAboveEight() {
        // 10 logged, 8 glasses shown: tapping glass 8 used to set 8 and silently remove two.
        assertEquals(10, tapGlass(10, 8))
        assertEquals(10, tapGlass(10, 1))
        assertEquals(20, tapGlass(20, 5))
    }

    @Test fun bodyNeedsAllThreeNumbersInRange() {
        assertEquals(Body(Sex.FEMALE, 30, 165, 60, Activity.LIGHT, Goal.LOSE), bodyOrNull(Sex.FEMALE, "30", "165", "60", Activity.LIGHT, Goal.LOSE))
        assertTrue(bodyOrNull(Sex.MALE, "13", "120", "30", Activity.LOW, Goal.GAIN) != null) // lowest edges
        assertTrue(bodyOrNull(Sex.MALE, "100", "230", "250", Activity.LOW, Goal.GAIN) != null) // highest edges
        assertNull(bodyOrNull(Sex.MALE, "12", "170", "70", Activity.LOW, Goal.MAINTAIN))
        assertNull(bodyOrNull(Sex.MALE, "25", "231", "70", Activity.LOW, Goal.MAINTAIN))
        assertNull(bodyOrNull(Sex.MALE, "25", "170", "29", Activity.LOW, Goal.MAINTAIN))
        assertNull(bodyOrNull(Sex.MALE, "", "170", "70", Activity.LOW, Goal.MAINTAIN))
    }

    @Test fun fieldsFlagOnlyValuesThatCanNoLongerFit() {
        assertTrue(stillPossible("", 13..100, 3))
        assertTrue(stillPossible("1", 13..100, 3)) // on the way to 18
        assertTrue(stillPossible("5", 13..100, 3)) // on the way to 55
        assertTrue(stillPossible("100", 13..100, 3))
        assertFalse(stillPossible("12", 13..100, 3)) // 120+ is too old, 12 too young
        assertFalse(stillPossible("101", 13..100, 3))
        assertFalse(stillPossible("11", 120..230, 3)) // 110-119 cm is still too short
        assertTrue(stillPossible("2", 120..230, 3))
        assertFalse(stillPossible("24", 120..230, 3))
        assertTrue(stillPossible("9", 1..9999, 4))
    }

    private fun recipe(
        id: String,
        name: String,
        meal: String = "lunch",
        veg: Boolean = true,
        protein: Int = 10,
        tags: List<String> = emptyList(),
        ingredients: List<String> = emptyList(),
    ) = Recipe(id, name, meal, "Indian", veg, 20, 2, 300, protein, 30, 10, 4, emptyList(), tags, ingredients, emptyList(), "")

    private val all = listOf(
        recipe("dal", "Masoor Dal", ingredients = listOf("1 cup red lentils", "1 tsp turmeric")),
        recipe("tikka", "Chicken Tikka", meal = "dinner", veg = false, protein = 28, tags = listOf("high-protein")),
        recipe("oats", "Masala Oats", meal = "breakfast", tags = listOf("budget")),
        recipe("tofu", "Tofu Stir-Fry", meal = "dinner", protein = 22),
    )

    private fun ids(list: List<Recipe>) = list.map { it.id }

    @Test fun searchLooksAtNamesIngredientsAndTags() {
        assertEquals(listOf("oats"), ids(filterRecipes(all, "MASALA", null, false, false)))
        assertEquals(listOf("dal"), ids(filterRecipes(all, "lentil", null, false, false)))
        assertTrue(filterRecipes(all, "masala dal", null, false, false).isEmpty()) // every word has to match
        assertEquals(listOf("tikka"), ids(filterRecipes(all, "high protein", null, false, false)))
        assertEquals(listOf("tofu"), ids(filterRecipes(all, "stir fry", null, false, false)))
        assertEquals(listOf("dal"), ids(filterRecipes(all, " lentils,  turmeric ", null, false, false)))
        assertEquals(all, filterRecipes(all, "  ", null, false, false))
        assertTrue(filterRecipes(all, "pizza", null, false, false).isEmpty())
    }

    @Test fun chipsFilterByMealVegAndProtein() {
        assertEquals(listOf("tikka", "tofu"), ids(filterRecipes(all, "", "dinner", false, false)))
        assertEquals(listOf("tofu"), ids(filterRecipes(all, "", "dinner", true, false)))
        assertEquals(listOf("tikka", "tofu"), ids(filterRecipes(all, "", null, false, true)))
        assertEquals(listOf("tofu"), ids(filterRecipes(all, "", null, true, true)))
    }

    @Test fun foodArtPicksTheMostSpecificWord() {
        assertEquals(FoodArt.EGG, foodArt("Egg Curry"))
        assertEquals(FoodArt.CHICKEN, foodArt("Chicken Tikka"))
        assertEquals(FoodArt.CURRY, foodArt("Paneer Tikka")) // vegetarian: never meat art
        assertEquals(FoodArt.RICE, foodArt("Moong Dal Khichdi"))
        assertEquals(FoodArt.FLATBREAD, foodArt("Moong Dal Chilla"))
        assertEquals(FoodArt.FLATBREAD, foodArt("2 rotis"))
        assertEquals(FoodArt.BOWL, foodArt("Masala Oats"))
        assertEquals(FoodArt.SALAD, foodArt("Kala Chana Chaat"))
        assertEquals(FoodArt.BROCCOLI, foodArt("Tofu Stir-Fry"))
        assertEquals(FoodArt.BANANA, foodArt("Peanut Butter Banana Protein Smoothie"))
        assertEquals(FoodArt.MILK, foodArt("Masala chai"))
        assertEquals(FoodArt.APPLE, foodArt("apple"))
    }

    @Test fun foodArtMatchesWholeWordsOnly() {
        assertEquals(FoodArt.PLATE, foodArt("Eggplant bharta")) // not an egg
        assertEquals(FoodArt.PLATE, foodArt("Burger"))
        assertEquals(FoodArt.PLATE, foodArt(""))
    }
}
