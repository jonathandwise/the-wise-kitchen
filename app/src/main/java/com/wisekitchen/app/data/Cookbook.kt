package com.wisekitchen.app.data

import org.json.JSONArray
import org.json.JSONObject

/** The whole cookbook, as published in data/recipes.json. */
data class Cookbook(
    val schema: Int,
    val version: Long,
    val updated: String?,
    val title: String,
    val about: List<String>,
    val chapters: List<Chapter>,
) {
    val allItems: List<Item> by lazy { chapters.flatMap { it.items } }
    private val byId: Map<String, Item> by lazy { allItems.associateBy { it.id } }
    private val chapterOf: Map<String, Chapter> by lazy {
        chapters.flatMap { c -> c.items.map { it.id to c } }.toMap()
    }

    fun item(id: String): Item? = byId[id]
    fun chapterFor(id: String): Chapter? = chapterOf[id]
    fun chapter(id: String): Chapter? = chapters.firstOrNull { it.id == id }

    val recipeCount: Int get() = allItems.count { it is Recipe }
    val guideCount: Int get() = allItems.count { it is Guide }

    companion object {
        /** The newest data format this version of the app understands. */
        const val SUPPORTED_SCHEMA = 1

        fun parse(json: String): Cookbook {
            val o = JSONObject(json)
            val chapters = o.getJSONArray("chapters").objects().map { c ->
                Chapter(
                    id = c.getString("id"),
                    number = c.optInt("number", 0),
                    title = c.getString("title"),
                    blurb = c.optStr("blurb").orEmpty(),
                    items = c.getJSONArray("items").objects().map(::parseItem),
                )
            }
            require(chapters.isNotEmpty()) { "the cookbook has no chapters" }
            return Cookbook(
                schema = o.optInt("schema", 1),
                version = o.getLong("version"),
                updated = o.optStr("updated"),
                title = o.optStr("title") ?: "The Wise Kitchen",
                about = o.optJSONArray("about").strings(),
                chapters = chapters,
            )
        }

        private fun parseItem(o: JSONObject): Item {
            val id = o.getString("id")
            val title = o.getString("title")
            val source = o.optJSONObject("source")?.let { Source(it.getString("name"), it.optStr("site")) }
            val note = o.optStr("note")
            val added = o.optStr("added")
            return if (o.optStr("kind") == "guide") {
                Guide(
                    id = id, title = title, source = source, note = note, added = added,
                    sections = o.optJSONArray("sections").objects().map { s ->
                        Section(s.optStr("title"), s.optJSONArray("blocks").objects().mapNotNull(::parseBlock))
                    },
                )
            } else {
                Recipe(
                    id = id, title = title, source = source, note = note, added = added,
                    serves = o.optStr("serves"),
                    makes = o.optStr("makes"),
                    time = o.optStr("time"),
                    temp = o.optStr("temp"),
                    ingredients = o.optJSONArray("ingredients").objects().map { g ->
                        IngredientGroup(g.optStr("title"), g.optJSONArray("items").strings())
                    },
                    method = o.optJSONArray("method").objects().map { g ->
                        StepGroup(
                            g.optStr("title"),
                            g.optJSONArray("steps").objects().map { s -> Step(s.getString("text"), s.optJSONArray("sub").strings()) },
                        )
                    },
                    tips = o.optJSONArray("tips").strings(),
                )
            }
        }

        private fun parseBlock(b: JSONObject): Block? = when (b.optStr("type")) {
            "p" -> Block.Paragraph(b.getString("text"))
            "h" -> Block.Heading(b.getString("text"))
            "bullets" -> Block.Bullets(b.optJSONArray("items").strings())
            "steps" -> Block.Steps(b.optJSONArray("items").strings())
            "table" -> Block.Table(
                b.optJSONArray("header").strings(),
                b.optJSONArray("rows").let { rows ->
                    if (rows == null) emptyList() else (0 until rows.length()).map { rows.getJSONArray(it).strings() }
                },
            )
            else -> null // a block type from a newer app version: skip it
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }

        private fun JSONObject.optStr(key: String): String? =
            if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null
    }
}

data class Chapter(
    val id: String,
    val number: Int,
    val title: String,
    val blurb: String,
    val items: List<Item>,
)

data class Source(val name: String, val site: String?)

sealed interface Item {
    val id: String
    val title: String
    val source: Source?
    val note: String?
    val added: String?
}

data class Recipe(
    override val id: String,
    override val title: String,
    override val source: Source?,
    override val note: String?,
    override val added: String?,
    val serves: String?,
    val makes: String?,
    val time: String?,
    val temp: String?,
    val ingredients: List<IngredientGroup>,
    val method: List<StepGroup>,
    val tips: List<String>,
) : Item

data class IngredientGroup(val title: String?, val items: List<String>)
data class StepGroup(val title: String?, val steps: List<Step>)
data class Step(val text: String, val sub: List<String>)

data class Guide(
    override val id: String,
    override val title: String,
    override val source: Source?,
    override val note: String?,
    override val added: String?,
    val sections: List<Section>,
) : Item

data class Section(val title: String?, val blocks: List<Block>)

sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
    data class Steps(val items: List<String>) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

/** Plain text of everything in an item, lower-cased, for search. */
fun Item.searchText(): String = buildString {
    append(title).append('\n')
    source?.let { append(it.name).append('\n') }
    note?.let { append(it).append('\n') }
    when (this@searchText) {
        is Recipe -> {
            ingredients.forEach { g -> g.title?.let { append(it).append('\n') }; g.items.forEach { append(it).append('\n') } }
            tips.forEach { append(it).append('\n') }
        }
        is Guide -> sections.forEach { s ->
            s.title?.let { append(it).append('\n') }
            s.blocks.forEach { b ->
                when (b) {
                    is Block.Paragraph -> append(b.text)
                    is Block.Heading -> append(b.text)
                    is Block.Bullets -> b.items.forEach { append(it).append('\n') }
                    is Block.Steps -> b.items.forEach { append(it).append('\n') }
                    is Block.Table -> b.rows.forEach { append(it.joinToString(" ")).append('\n') }
                }
                append('\n')
            }
        }
    }
}.replace("**", "").lowercase()
