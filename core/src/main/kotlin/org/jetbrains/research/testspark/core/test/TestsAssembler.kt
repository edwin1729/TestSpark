package org.jetbrains.research.testspark.core.test


abstract class TestsAssembler {
    private var rawText = ""

    /**
     * Receives a text chunk of the response of an LLM.
     *
     * @param text part of the LLM response
     */
    fun consume(text: String) {
        rawText = rawText.plus(text)
    }

    /**
     * Returns the content of the LLM response collected so far.
     *
     * @return The content of the LLM response.
     */
    fun getContent(): String {
        return rawText
    }

    /**
     * Extracts test cases from raw text and generates a TestSuite using the given package name.
     *
     * @param packageName The package name to be set in the generated TestSuite.
     * @return A TestSuiteGeneratedByLLM object containing the extracted test cases and package name.
     */
    abstract fun assembleTestSuite(packageName: String): TestSuiteGeneratedByLLM?
}