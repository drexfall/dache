package dev.dache.plugin.runtime

import dev.dache.plugin.api.SettingSpec
import dev.dache.plugin.runtime.mapping.ExpressionException
import dev.dache.plugin.runtime.mapping.FieldType
import dev.dache.plugin.runtime.mapping.Template
import dev.dache.plugin.runtime.security.HostGuard

/**
 * Where `{expression}` and `{@setting}` placeholders may appear in one manifest. Each check returns problem
 * messages; the caller adds the JSON path.
 */
internal class PlaceholderRules(
    private val guard: HostGuard,
    private val settings: List<SettingSpec>,
) {
    private val keys = settings.map { it.key }.toSet()
    private val optional = settings.filterNot { it.required }.map { it.key }.toSet()

    /** [fannedOut]: the request is sent once per item of another list, so item placeholders have a value. */
    fun url(
        url: String,
        fannedOut: Boolean,
    ): List<String> {
        val authorityEnd = url.indexOf('/', url.indexOf("://") + "://".length).takeIf { it >= 0 } ?: url.length
        return parsed(url) { template ->
            request(template, fannedOut, wholeQueryValue = false) +
                listOfNotNull(
                    guard.violation(template.sample()),
                    "put query parameters in \"query\"".takeIf { '?' in url },
                    "placeholders can only be in the path".takeIf { '{' in url.substring(0, authorityEnd) },
                )
        }
    }

    fun query(
        name: String,
        value: String,
        fannedOut: Boolean,
    ): List<String> =
        listOfNotNull("parameter names can't have placeholders".takeIf { '{' in name || '}' in name }) +
            parsed(value) { request(it, fannedOut, wholeQueryValue = true) }

    /** A field written as a template, which always produces text. */
    fun field(
        source: String,
        type: FieldType,
    ): List<String> =
        listOfNotNull("only text fields can be templates".takeIf { type != FieldType.TEXT }) +
            parsed(source, ::unknownSettings)

    fun settings(): List<Pair<String, String>> =
        settings.flatMapIndexed { index, setting ->
            listOfNotNull(
                ("settings[$index].key" to "must match ${settingKey.pattern}").takeIf {
                    !settingKey.matches(setting.key)
                },
                ("settings[$index].key" to "\"${setting.key}\" is declared twice").takeIf {
                    settings.count { it.key == setting.key } > 1
                },
                ("settings[$index].label" to "must not be blank").takeIf { setting.label.isBlank() },
            )
        }

    /** An optional setting can be blank, so it may only be a whole query value, which is then dropped. */
    private fun request(
        template: Template,
        fannedOut: Boolean,
        wholeQueryValue: Boolean,
    ): List<String> =
        listOfNotNull(
            "placeholders need an item to fill them; use forEach or a nested request".takeIf {
                !fannedOut && template.placeholders.isNotEmpty()
            },
        ) + unknownSettings(template) +
            template.settings
                .filter { it in optional && !(wholeQueryValue && template.soleSetting == it) }
                .map { "optional setting @$it can only be a whole query value" }

    private fun unknownSettings(template: Template): List<String> =
        template.settings.filter { it !in keys }.map { "unknown setting @$it" }

    /** Runs [check] on the parsed template, or reports why [source] doesn't parse. */
    private inline fun parsed(
        source: String,
        check: (Template) -> List<String>,
    ): List<String> {
        val template =
            try {
                Template.parse(source)
            } catch (e: ExpressionException) {
                return listOf(e.message.orEmpty())
            }
        return check(template)
    }

    private companion object {
        val settingKey = Regex("[a-z][A-Za-z0-9_]{0,39}")
    }
}
