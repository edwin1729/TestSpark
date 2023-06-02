package org.jetbrains.research.testgenie.tools.llm.test

data class TestSuiteGeneratedByLLM(
     var imports: Set<String> = emptySet(),
     var testCases: MutableList<TestCaseGeneratedByLLM> = mutableListOf()
) {

    fun isEmpty(): Boolean {
        return testCases.isEmpty()
    }
}
