package com.timemachine.keyboard

import com.timemachine.keyboard.emoji.EmojiCatalog
import com.timemachine.keyboard.predict.WordDictionary
import com.timemachine.keyboard.predict.WordPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionTest {
    private val dict = WordDictionary(
        listOf("não" to 1000L, "nada" to 500L, "nadar" to 50L, "nunca" to 300L, "casa" to 400L, "casamento" to 80L, "dia" to 900L, "ação" to 200L),
    )

    @Test fun completesByPrefixIgnoringAccents() {
        val p = WordPredictor(dict)
        assertEquals(listOf("não", "nada", "nunca"), p.suggest("n", null))
        assertEquals("não", p.suggest("nao", null).first())
        assertEquals("ação", p.suggest("aca", null).first())
    }

    @Test fun dropsRareSpellingsThatOnlyDifferByAccent() {
        val d = WordDictionary(listOf("não" to 1000L, "nao" to 30L, "nâo" to 5L, "e" to 900L, "é" to 800L))
        val p = WordPredictor(d)
        assertEquals(listOf("não"), p.suggest("nao", null))
        assertEquals(setOf("e", "é"), p.suggest("e", null).toSet())
    }

    @Test fun keepsTheCaseTheUserTyped() {
        val p = WordPredictor(dict)
        assertEquals("Não", p.suggest("Na", null).first())
        assertEquals("NÃO", p.suggest("NA", null).first())
    }

    @Test fun learnedWordsAndSequencesRankHigher() {
        val p = WordPredictor(dict)
        repeat(3) { p.learn("nadar", "vou") }
        assertEquals("nadar", p.suggest("na", "vou").first())
        // palavra que não está no dicionário também passa a ser sugerida
        p.learn("timemachine", null)
        assertTrue("timemachine" in p.suggest("time", null))
    }

    @Test fun nextWordComesOnlyFromWhatWasLearned() {
        val p = WordPredictor(dict)
        assertTrue(p.suggest("", "bom").isEmpty())
        p.learn("dia", "bom")
        assertEquals(listOf("dia"), p.suggest("", "bom"))
    }

    @Test fun clearForgetsEverythingLearned() {
        val p = WordPredictor(dict)
        p.learn("dia", "bom")
        p.learn("xyzw", null)
        p.clear()
        assertTrue(p.suggest("", "bom").isEmpty())
        assertTrue(p.suggest("xyz", null).isEmpty())
    }

    @Test fun ignoresNonWords() {
        val p = WordPredictor(dict)
        p.learn("a1b2", null)
        p.learn("x", null)
        assertTrue(p.suggest("a1", null).isEmpty())
    }

    @Test fun splitsCurrentAndPreviousWord() {
        assertEquals("cas" to "minha", WordPredictor.splitContext("na minha cas"))
        assertEquals("" to "bom", WordPredictor.splitContext("bom "))
        assertEquals("ol" to null, WordPredictor.splitContext("ol"))
        // depois de pontuação não há palavra anterior na mesma frase
        assertEquals("" to null, WordPredictor.splitContext("fim."))
        assertEquals("" to null, WordPredictor.splitContext(""))
    }

    @Test fun normalizesAccentsAndCase() {
        assertEquals("acao", WordDictionary.normalize("Ação"))
        assertNull(WordDictionary(emptyList()).completions("a", 3).firstOrNull())
    }
}

class EmojiCatalogTest {
    private val sample = """
        @smileys
        😀
        🙂
        @people
        👋 👋🏻 👋🏼
        @symbols
        #️⃣
        @flags
        🇧🇷
    """.trimIndent()

    @Test fun parsesCategoriesAndSkinTones() {
        val cats = EmojiCatalog.parse(sample.byteInputStream())
        assertEquals(listOf("smileys", "people", "symbols", "flags"), cats.map { it.id })
        // "#️⃣" é um emoji, não um cabeçalho
        assertEquals(listOf(listOf("#️⃣")), cats[2].items)
        assertEquals(listOf("👋", "👋🏻", "👋🏼"), cats[1].items.single())
        assertEquals("Carinhas", cats[0].label)
    }

    @Test fun dropsWhatTheDeviceCannotDraw() {
        val cats = EmojiCatalog.parse(sample.byteInputStream()) { it != "🙂" && it != "👋🏼" }
        assertEquals(listOf(listOf("😀")), cats[0].items)
        assertEquals(listOf("👋", "👋🏻"), cats[1].items.single())
    }

    @Test fun bundledAssetHasEveryCategory() {
        val asset = java.io.File("src/main/assets/emoji.txt")
        val cats = EmojiCatalog.parse(asset.inputStream())
        assertEquals(9, cats.size)
        assertTrue(cats.sumOf { it.items.size } > 1800)
    }
}
