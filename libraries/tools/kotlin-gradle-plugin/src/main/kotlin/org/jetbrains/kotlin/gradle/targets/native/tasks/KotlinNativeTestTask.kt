/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.tasks

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.DefaultProcessForkOptions
import org.gradle.process.internal.ExecHandleFactory
import org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesClientSettings
import org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesTestExecutionSpec
import org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesTestExecutor
import org.jetbrains.kotlin.gradle.testing.IgnoredTestSuites
import org.jetbrains.kotlin.gradle.testing.TestsGrouping
import org.jetbrains.kotlin.gradle.utils.injected
import javax.inject.Inject

open class KotlinNativeTestTask : AbstractTestTask() {
    @Input
    var testsGrouping: TestsGrouping =
        TestsGrouping.root

    @Input
    @Optional
    var targetName: String? = null

    @Input
    var excludes = mutableSetOf<String>()

    @Suppress("UnstableApiUsage")
    private val filterExt: DefaultTestFilter
        get() = filter as DefaultTestFilter

    init {
        filterExt.isFailOnNoMatchingTests = false
    }

    @get:Inject
    open val fileResolver: FileResolver
        get() = injected

    @Suppress("LeakingThis")
    @Internal
    val processOptions: ProcessForkOptions = DefaultProcessForkOptions(fileResolver)

    fun processOptions(options: ProcessForkOptions.() -> Unit) {
        options(processOptions)
    }

    override fun createTestExecutionSpec(): TCServiceMessagesTestExecutionSpec {
        val extendedForkOptions = DefaultProcessForkOptions(fileResolver)
        processOptions.copyTo(extendedForkOptions)

        val clientSettings = when (testsGrouping) {
            TestsGrouping.none -> TCServiceMessagesClientSettings(rootNodeName = name)
            TestsGrouping.root -> TCServiceMessagesClientSettings(rootNodeName = name, nameOfRootSuiteToAppend = targetName)
            TestsGrouping.leaf -> TCServiceMessagesClientSettings(rootNodeName = name, nameOfLeafTestToAppend = targetName)
        }

        val cliArgs = CliArgs(
            "TEAMCITY",
            filterExt.includePatterns + filterExt.commandLineIncludePatterns,
            excludes
        )

        return TCServiceMessagesTestExecutionSpec(
            extendedForkOptions,
            cliArgs.toList(),
            clientSettings
        )
    }

    private class CliArgs(
        val testLogger: String? = null,
        val testGradleFilter: Set<String> = setOf(),
        val testNegativeGradleFilter: Set<String> = setOf()
    ) {
        fun toList() = mutableListOf<String>().also {
            if (testLogger != null) {
                it.add("--ktest_logger=$testLogger")
            }

            if (testGradleFilter.isNotEmpty()) {
                it.add("--ktest_gradle_filter=${testGradleFilter.joinToString(",")}")
            }

            if (testNegativeGradleFilter.isNotEmpty()) {
                it.add("--ktest_negative_gradle_filter=${testNegativeGradleFilter.joinToString(",")}")
            }
        }
    }

    @get:Inject
    open val execHandleFactory: ExecHandleFactory
        get() = injected

    override fun createTestExecuter() = TCServiceMessagesTestExecutor(
        execHandleFactory,
        buildOperationExecutor
    )
}
