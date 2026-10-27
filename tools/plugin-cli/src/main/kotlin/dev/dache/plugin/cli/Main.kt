package dev.dache.plugin.cli

import kotlin.system.exitProcess

private const val USAGE = "usage: dache-plugin <command> [args]"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    System.err.println("unknown command: ${args.first()}\n$USAGE")
    exitProcess(2)
}
