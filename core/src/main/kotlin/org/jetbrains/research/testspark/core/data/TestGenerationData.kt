package org.jetbrains.research.testspark.core.data

import org.jetbrains.research.testspark.core.test.data.TestCaseGeneratedByLLM

data class TestGenerationData(
    // Result processing
    // Report object for each test case
    var testGenerationResultList: MutableList<Report?> = mutableListOf(),
    // test file name?
    var resultName: String = "",
    var fileUrl: String = "",
    var resultPath: String = "",
    var testResultName: String = "",
    // The base directory of the project.
    var baseDir: String? = null,

    // Code required of imports and package for generated tests
    var importsCode: MutableSet<String> = mutableSetOf(),
    var packageName: String = "",
    var runWith: String = "",
    var otherInfo: String = "",

    // changing parameters with a large prompt
    var polyDepthReducing: Int = 0,
    var inputParamsDepthReducing: Int = 0,

    // list of correct test cases during the incorrect compilation
    val compilableTestCases: MutableSet<TestCaseGeneratedByLLM> = mutableSetOf(),

) {

    /**
     * Cleaning all old data before new test generation.
     */
    fun clear() {
        testGenerationResultList.clear()
        resultName = ""
        fileUrl = ""
        importsCode = mutableSetOf()
        packageName = ""
        runWith = ""
        otherInfo = ""
        polyDepthReducing = 0
        inputParamsDepthReducing = 0
        compilableTestCases.clear()
    }
}
