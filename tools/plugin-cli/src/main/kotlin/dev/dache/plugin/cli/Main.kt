package dev.dache.plugin.cli

import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

private val USAGE =
    """
    usage: dache-plugin validate <manifest.json>...
           dache-plugin test <manifest.json> --fixtures <dir> [--setting key=value]...
    """.trimIndent()

/** The token `test` sends; fixtures don't check it. */
private const val FIXTURE_TOKEN = "fixture-token"

fun main(args: Array<String>) {
    val outcome =
        when (args.firstOrNull()) {
            "validate" -> args.drop(1).takeIf { it.isNotEmpty() }?.let(::validateAll)
            "test" -> Arguments.parse(args.drop(1), "--fixtures")?.let(::test)
            else -> null
        }
    if (outcome == null) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    println(outcome.text)
    exitProcess(if (outcome.ok) 0 else 1)
}

private fun validateAll(paths: List<String>): Outcome {
    val reports = paths.map { validate(File(it)) }
    return Outcome(reports.joinToString("\n"), reports.all { it.valid })
}

private fun test(arguments: Arguments): Outcome {
    val dir = File(arguments.option)
    if (!dir.isDirectory) return Outcome("${dir.path}: not a directory", ok = false)
    return runBlocking { refresh(arguments.manifest, Fixtures(dir).engine(), FIXTURE_TOKEN, arguments.settings) }
}

/** `<manifest> <flag> <value> [--setting key=value]...`, in any order after the manifest. */
class Arguments(
    val manifest: File,
    val option: String,
    val settings: Map<String, String>,
) {
    companion object {
        fun parse(
            args: List<String>,
            flag: String,
        ): Arguments? {
            val manifest = args.firstOrNull()?.takeUnless { it.startsWith("--") }
            val pairs = args.drop(1).chunked(2)
            val option = pairs.singleOrNull { it.first() == flag }?.getOrNull(1)
            val settings =
                pairs.filter { it.first() == "--setting" }.map {
                    it
                        .getOrNull(
                            1,
                        ).orEmpty()
                        .split('=', limit = 2)
                }
            val wellFormed =
                pairs.all { it.size == 2 && (it.first() == flag || it.first() == "--setting") } &&
                    settings.all { it.size == 2 }
            return if (manifest == null || option == null || !wellFormed) {
                null
            } else {
                Arguments(File(manifest), option, settings.associate { it[0] to it[1] })
            }
        }
    }
}

class Report(
    val file: String,
    val problems: List<String>,
) {
    val valid: Boolean get() = problems.isEmpty()

    override fun toString(): String =
        if (valid) {
            "$file: ok"
        } else {
            (
                listOf(
                    "$file: ${problems.size} problem(s)",
                ) + problems.map { "  $it" }
            ).joinToString("\n")
        }
}

fun validate(file: File): Report {
    if (!file.isFile) return Report(file.path, listOf("file not found"))
    val problems =
        when (val result = ManifestLoader.load(file.readText())) {
            is LoadResult.Loaded -> emptyList()
            is LoadResult.Invalid -> result.problems.map { it.toString() }
        }
    return Report(file.path, problems)
}
