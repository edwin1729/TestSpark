package org.jetbrains.research.testspark.tools

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.research.testspark.core.data.Report
import org.jetbrains.research.testspark.core.data.TestCase
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.generation.llm.getClassWithTestCaseName
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.utils.CommandLineRunner
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import org.jetbrains.research.testspark.data.IJTestCase
import org.jetbrains.research.testspark.helpers.java.JavaClassBuilderHelper
import org.jetbrains.research.testspark.helpers.kotlin.KotlinClassBuilderHelper
import org.jetbrains.research.testspark.services.TestsExecutionResultService
import java.io.File
import java.nio.file.Path

object ToolUtils {
    val sep = File.separatorChar
    val pathSep = File.pathSeparatorChar

    /**
     * Concatenate strings with OS specific path seperators
     */
    fun osJoin(vararg strings: String): String {
        return strings.joinToString(sep.toString())
    }

    /**
     * Retrieves the imports code from a given test suite code.
     *
     * @param testSuiteCode The test suite code from which to extract the imports code. If null, an empty string is returned.
     * @param classFQN The fully qualified name of the class to be excluded from the imports code. It will not be included in the result.
     * @return The imports code extracted from the test suite code. If no imports are found or the result is empty after filtering, an empty string is returned.
     */
    fun getImportsCodeFromTestSuiteCode(testSuiteCode: String?, classFQN: String): MutableSet<String> {
        testSuiteCode ?: return mutableSetOf()
        return testSuiteCode.replace("\r\n", "\n").split("\n").asSequence()
            .filter { it.contains("^import".toRegex()) }
            .filterNot { it.contains("evosuite".toRegex()) }
            .filterNot { it.contains("RunWith".toRegex()) }
            .filterNot { it.contains(classFQN.toRegex()) }.toMutableSet()
    }

    /**
     * Retrieves the package declaration from the given test suite code.
     *
     * @param testSuiteCode The generated code of the test suite.
     * @return The package declaration extracted from the test suite code, or an empty string if no package declaration was found.
     */
// get package from a generated code
    fun getPackageFromTestSuiteCode(testSuiteCode: String?): String {
        testSuiteCode ?: return ""
        if (!testSuiteCode.contains("package")) return ""
        val result = testSuiteCode.replace("\r\n", "\n").split("\n")
            .filter { it.contains("^package".toRegex()) }.joinToString("").split("package ")[1].split(";")[0]
        if (result.isBlank()) return ""
        return result
    }

    /**
     * Saves the data related to test generation in the specified project's workspace.
     *
     * @param project The project in which the test generation data will be saved.
     * @param report The report object to be added to the test generation result list.
     * @param packageName The package declaration line of the test generation data.
     * @param importsCode The import statements code of the test generation data.
     */
    fun saveData(
        project: Project,
        report: Report,
        packageName: String,
        importsCode: MutableSet<String>,
        fileUrl: String,
        generatedTestData: TestGenerationData,
        language: SupportedLanguage = SupportedLanguage.Java,
    ) {
        generatedTestData.fileUrl = fileUrl
        generatedTestData.packageName = packageName
        generatedTestData.importsCode.addAll(importsCode)

        project.service<TestsExecutionResultService>().initExecutionResult(report.testCaseList.values.map { it.id })

        for (testCase in report.testCaseList.values) {
            val code = testCase.testCode
            testCase.testCode = when (language) {
                SupportedLanguage.Java -> JavaClassBuilderHelper.generateCode(
                    project,
                    getClassWithTestCaseName(testCase.testName),
                    code,
                    generatedTestData.importsCode,
                    generatedTestData.packageName,
                    generatedTestData.runWith,
                    generatedTestData.otherInfo,
                    generatedTestData,
                )

                SupportedLanguage.Kotlin -> KotlinClassBuilderHelper.generateCode(
                    project,
                    getClassWithTestCaseName(testCase.testName),
                    code,
                    generatedTestData.importsCode,
                    generatedTestData.packageName,
                    generatedTestData.runWith,
                    generatedTestData.otherInfo,
                    generatedTestData,
                )
            }
        }

        generatedTestData.testGenerationResultList.add(report)
    }

    /**
     * Retrieves the build path for the given project.
     *
     * @param project the project for which to retrieve the build path
     * @return the build path as a string
     */
    fun getBuildPath(project: Project): String {
        var buildPath = ""

        for (module in ModuleManager.getInstance(project).modules) {
            val compilerOutputPath = CompilerModuleExtension.getInstance(module)?.compilerOutputPath

            compilerOutputPath?.let { buildPath += compilerOutputPath.path.plus(DataFilesUtil.classpathSeparator.toString()) }
            // Include extra libraries in classpath
            val librariesPaths = ModuleRootManager.getInstance(module).orderEntries().librariesOnly().pathsList.pathList
            for (lib in librariesPaths) {
                // exclude the invalid classpaths
                if (buildPath.contains(lib)) {
                    continue
                }
                if (lib.endsWith(".zip")) {
                    continue
                }

                // remove junit and hamcrest libraries, since we use our own libraries
                val pathArray = lib.split(File.separatorChar)
                val libFileName = pathArray[pathArray.size - 1]
                if (libFileName.startsWith("junit") ||
                    libFileName.startsWith("hamcrest")
                ) {
                    continue
                }

                buildPath += lib.plus(DataFilesUtil.classpathSeparator.toString())
            }
        }
        return buildPath
    }


    /**
     * @param path path of the java executable as String. Refactor to type Path eventually
     * @return version of java which is used in the project for which tests are generated by TestSpark
     */
    fun getJavaVersion(path: String): Int? {
        val regex = Regex("version \"(.*?)\"")
        val version = regex.find(CommandLineRunner.run(arrayListOf(path, "-version")))
            ?.groupValues
            ?.get(1)
            ?.split(".")
            ?.get(0)
            ?.toInt()
        return version
    }

    /**
     * Checks if the process has been stopped.
     *
     * @param errorMonitor the class responsible for monitoring the errors during the test generation process
     * @param indicator the progress indicator for tracking the progress of the process
     *
     * @return true if the process has been stopped, false otherwise
     */
    fun isProcessStopped(errorMonitor: ErrorMonitor, indicator: CustomProgressIndicator): Boolean {
        return errorMonitor.hasErrorOccurred() || isProcessCanceled(indicator)
    }

    fun isProcessCanceled(indicator: CustomProgressIndicator): Boolean {
        if (indicator.isCanceled()) {
            indicator.stop()
            return true
        }
        return false
    }

    fun getResultPath(id: String, testResultDirectory: String): String {
        val testResultName = "test_gen_result_$id"

        return "$testResultDirectory$testResultName"
    }

    fun transferToIJTestCases(report: Report) {
        val result: MutableMap<Int, TestCase> = mutableMapOf()
        report.testCaseList.keys.forEach { key ->
            val testcase = report.testCaseList[key]
            val ijTestCase = IJTestCase(testcase!!.id, testcase.testName, testcase.testCode, testcase.coveredLines)
            result[key] = ijTestCase
        }
        report.testCaseList = HashMap(result)
    }
}
