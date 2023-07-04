package org.jetbrains.research.testgenie.tools.llm

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiClass
import org.jetbrains.research.testgenie.TestGenieBundle
import org.jetbrains.research.testgenie.actions.getClassDisplayName
import org.jetbrains.research.testgenie.actions.getClassFullText
import org.jetbrains.research.testgenie.actions.getSignatureString
import org.jetbrains.research.testgenie.editor.Workspace
import org.jetbrains.research.testgenie.services.TestCaseDisplayService
import org.jetbrains.research.testgenie.tools.ProjectBuilder
import org.jetbrains.research.testgenie.tools.llm.generation.LLMProcessManager
import org.jetbrains.research.testgenie.tools.llm.generation.PromptManager
import java.io.File
import java.util.*

private var prompt = ""

class Pipeline(
    private val project: Project,
    projectClassPath: String,
    private val interestingPsiClasses: Set<PsiClass>,
    private val cut: PsiClass,
    private val packageName: String,
    private val polymorphismRelations: MutableMap<PsiClass, MutableList<PsiClass>>,
    modTs: Long,
    private val fileUrl: String,
    private val classFQN: String,
) {

    private val log = Logger.getInstance(this::class.java)

    private val sep = File.separatorChar

    private val id = UUID.randomUUID().toString()
    private val testResultDirectory = "${FileUtilRt.getTempDirectory()}${sep}testGenieResults$sep"
    private val testResultName = "test_gen_result_$id"

    private val resultPath = "$testResultDirectory$testResultName"

    // TODO move all interactions with Workspace to Manager
    var key = Workspace.TestJobInfo(fileUrl, classFQN, modTs, testResultName, projectClassPath)

    private val promptManager = PromptManager(cut, interestingPsiClasses,polymorphismRelations)

    private val processManager =
        LLMProcessManager(project, projectClassPath)

    // TODO("Removed unused input parameters. needs o be refactored after finalizing the implementation")

    fun forClass(): Pipeline {
        prompt = promptManager.generatePrompt()
        return this
    }

    fun runTestGeneration() {
        // TODO move all interactions with Workspace to Manager
        val workspace = project.service<Workspace>()
        workspace.addPendingResult(testResultName, key)

        // TODO move all interactions with TestCaseDisplayService to Manager
        project.service<TestCaseDisplayService>().resultName = testResultName

        val projectBuilder = ProjectBuilder(project)

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, TestGenieBundle.message("testGenerationMessage")) {
                override fun run(indicator: ProgressIndicator) {
                    if (indicator.isCanceled) {
                        indicator.stop()
                        return
                    }

                    if (projectBuilder.runBuild(indicator)) {
                        processManager.runLLMTestGenerator(indicator, prompt, resultPath, packageName, cut, classFQN)
                    }
                }
            })

        project.service<TestCaseDisplayService>().fileUrl = fileUrl
    }
}
