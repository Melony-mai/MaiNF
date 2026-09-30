package com.mai.nf.res

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationCompletenessTest {

    private fun parseStringKeys(file: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            keys.add(element.getAttribute("name"))
        }
        return keys
    }

    @Test
    fun testAllLanguagesHaveIdenticalKeys() {
        val resDir = listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.exists() } ?: File("src/main/res")
        val enFile = File(resDir, "values/strings.xml")
        val zhFile = File(resDir, "values-zh/strings.xml")
        val jaFile = File(resDir, "values-ja/strings.xml")

        assertTrue("EN strings file exists", enFile.exists())
        assertTrue("ZH strings file exists", zhFile.exists())
        assertTrue("JA strings file exists", jaFile.exists())

        val enKeys = parseStringKeys(enFile)
        val zhKeys = parseStringKeys(zhFile)
        val jaKeys = parseStringKeys(jaFile)

        assertFalse("EN strings should not be empty", enKeys.isEmpty())

        val missingInZh = enKeys - zhKeys
        val missingInJa = enKeys - jaKeys
        val extraInZh = zhKeys - enKeys
        val extraInJa = jaKeys - enKeys

        assertTrue("All EN keys must exist in ZH. Missing: $missingInZh", missingInZh.isEmpty())
        assertTrue("All EN keys must exist in JA. Missing: $missingInJa", missingInJa.isEmpty())
        assertTrue("No extra untranslated keys in ZH: $extraInZh", extraInZh.isEmpty())
        assertTrue("No extra untranslated keys in JA: $extraInJa", extraInJa.isEmpty())
    }
}
