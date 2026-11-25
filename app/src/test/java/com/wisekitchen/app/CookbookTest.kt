package com.wisekitchen.app

import com.wisekitchen.app.data.Block
import com.wisekitchen.app.data.Cookbook
import com.wisekitchen.app.data.Guide
import com.wisekitchen.app.data.Recipe
import com.wisekitchen.app.data.searchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the real data/recipes.json the app ships with, the way the app does. */
class CookbookTest {
    private val book: Cookbook by lazy {
        val f = listOf(File("../data/recipes.json"), File("data/recipes.json")).first { it.exists() }
        Cookbook.parse(f.readText())
    }

    @Test
    fun parsesTheWholeBook() {
        assertTrue(book.schema <= Cookbook.SUPPORTED_SCHEMA)
        assertEquals(12, book.chapters.size)
        assertTrue("at least 91 recipes", book.recipeCount >= 91)
        assertTrue("at least 9 guides", book.guideCount >= 9)
        assertEquals(book.allItems.size, book.allItems.map { it.id }.toSet().size)
    }

    @Test
    fun everyRecipeHasIngredientsAndSteps() {
        book.allItems.filterIsInstance<Recipe>().forEach { r ->
            assertTrue("${r.title} ingredients", r.ingredients.sumOf { it.items.size } > 0)
            assertTrue("${r.title} steps", r.method.sumOf { it.steps.size } > 0)
        }
    }

    @Test
    fun knownRecipesLookRight() {
        val tenders = book.item("air-fryer-chicken-tenders") as Recipe
        assertEquals("4 (3 tenders each)", tenders.serves)
        assertEquals("Skinnytaste", tenders.source?.name)
        assertEquals(8, tenders.ingredients.single().items.size)
        assertEquals(5, tenders.method.single().steps.size)

        val roast = book.item("garlic-herb-butter-roast-chicken") as Recipe
        assertEquals(listOf("Chicken", "Pan gravy (optional)"), roast.ingredients.map { it.title })
        assertEquals(listOf(null, "Pan gravy"), roast.method.map { it.title })

        val feet = book.item("dim-sum-chicken-feet-pressure-cooker") as Recipe
        assertEquals(3, feet.method.single().steps[1].sub.size)

        assertEquals("Poultry", book.chapterFor("peking-duck")?.title)
    }

    @Test
    fun guidesHaveTables() {
        val sheet = book.item("sous-vide-cheat-sheet") as Guide
        val tables = sheet.sections.flatMap { it.blocks }.filterIsInstance<Block.Table>()
        assertTrue(tables.size >= 8)
        tables.forEach { t -> t.rows.forEach { assertEquals(t.header.size, it.size) } }
    }

    @Test
    fun searchFindsIngredients() {
        val hits = book.allItems.filter { "gochujang" in it.searchText() }
        assertNotNull(hits.firstOrNull { it.id == "creamy-rose-tteokbokki" })
    }
}
