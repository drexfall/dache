package dev.dache.plugin.cli

import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = "usage: dache-plugin validate <manifest.json>..."

fun main(args: Array<String>) {
    val command = args.firstOrNull()
    if (command != "validate" || args.size < 2) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val reports = args.drop(1).map { validate(File(it)) }
    reports.forEach(::println)
    exitProcess(if (reports.all { it.valid }) 0 else 1)
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
