package com.m57.hermescontrol.ui.model.components

import com.m57.hermescontrol.data.model.ModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerPresentationTest {
    private val providers =
        listOf(
            ModelProvider(
                slug = "openai",
                name = "OpenAI",
                models = listOf("gpt-4o", "o3"),
            ),
            ModelProvider(
                slug = "anthropic",
                name = "Anthropic",
                models = listOf("claude-sonnet"),
            ),
        )

    @Test
    fun blankQueryKeepsProviderModelsCollapsed() {
        val sections = modelPickerSections(providers, query = "", expandedProvider = null)

        assertEquals(listOf("openai", "anthropic"), sections.map { it.provider.slug })
        assertTrue(sections.all { it.visibleModels.isEmpty() })
    }

    @Test
    fun expandedProviderShowsOnlyItsModels() {
        val sections = modelPickerSections(providers, query = "", expandedProvider = "openai")

        assertEquals(listOf("gpt-4o", "o3"), sections.first().visibleModels)
        assertTrue(sections.last().visibleModels.isEmpty())
    }

    @Test
    fun searchShowsMatchingModelsWithoutManualExpansion() {
        val sections = modelPickerSections(providers, query = "sonnet", expandedProvider = null)

        assertEquals(listOf("anthropic"), sections.map { it.provider.slug })
        assertEquals(listOf("claude-sonnet"), sections.single().visibleModels)
    }

    @Test
    fun providerSearchShowsEveryModelForMatchingProvider() {
        val sections = modelPickerSections(providers, query = "openai", expandedProvider = null)

        assertEquals(listOf("gpt-4o", "o3"), sections.single().visibleModels)
    }
}
