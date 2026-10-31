package dev.dache.plugin.runtime

import dev.dache.core.model.Capability
import dev.dache.plugin.api.AuthSpec
import dev.dache.plugin.api.FieldSpec
import dev.dache.plugin.api.LimitsSpec
import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PagingSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.api.RequestSpec
import dev.dache.plugin.runtime.mapping.Expression
import dev.dache.plugin.runtime.mapping.ExpressionException
import dev.dache.plugin.runtime.mapping.FieldDef
import dev.dache.plugin.runtime.mapping.FieldType
import dev.dache.plugin.runtime.mapping.ManifestKind
import dev.dache.plugin.runtime.mapping.Pipe
import dev.dache.plugin.runtime.security.HostGuard

/** One thing wrong with a manifest. [path] points into the JSON, e.g. `resources[0].fields.name`. */
data class ManifestProblem(
    val path: String,
    val message: String,
) {
    override fun toString(): String = "$path: $message"
}

/**
 * Checks everything about a decoded manifest that the type system can't: ids, hosts, that every URL passes
 * [HostGuard], that the auth slot can't be overwritten by declared headers or query parameters, paging
 * limits, that placeholders only appear where there is an item to fill them, and that field mappings fit the
 * target model and parse.
 */
object ManifestValidator {
    const val MAX_PAGES = 50
    const val MAX_PAGE_SIZE = 1000

    private val idPattern = Regex("[a-z][a-z0-9-]{1,39}")
    private val versionPattern = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
    private val headerNamePattern = Regex("[A-Za-z0-9-]+")

    /** Headers a manifest may not set itself: secrets only travel through the auth slot. */
    private val reservedHeaders = setOf("authorization", "proxy-authorization", "cookie", "host", "content-length")

    fun validate(manifest: PluginManifest): List<ManifestProblem> = Checker(manifest).run()

    private val timePipes = setOf(Pipe.DATE, Pipe.MILLIS)

    private fun authProblem(auth: AuthSpec): ManifestProblem? =
        when (auth) {
            AuthSpec.Bearer -> null
            is AuthSpec.Header ->
                ManifestProblem("auth.name", "\"${auth.name}\" can't carry a token").takeIf {
                    !headerNamePattern.matches(auth.name) || auth.name.lowercase() in setOf("host", "cookie")
                }
            is AuthSpec.Query -> ManifestProblem("auth.name", "must not be blank").takeIf { auth.name.isBlank() }
        }

    private class Checker(
        private val manifest: PluginManifest,
    ) {
        private val problems = mutableListOf<ManifestProblem>()
        private val guard = HostGuard(manifest.hosts)
        private val placeholders = PlaceholderRules(guard, manifest.settings)

        private fun problem(
            path: String,
            message: String,
        ) {
            problems += ManifestProblem(path, message)
        }

        fun run(): List<ManifestProblem> {
            checkHeader()
            authProblem(manifest.auth)?.let { problems += it }
            placeholders.settings().forEach { (path, message) -> problem(path, message) }
            checkRequest("validate.request", manifest.validate.request, fannedOut = false)
            checkExpression("validate.displayName", manifest.validate.displayName)
            if (manifest.resources.isEmpty()) problem("resources", "declare at least one resource")
            manifest.resources.forEachIndexed { index, resource ->
                val path = "resources[$index]"
                val kind = ManifestKind.named(resource.kind)
                if (kind == null) {
                    problem(
                        "$path.kind",
                        "unknown kind \"${resource.kind}\"; supported: ${ManifestKind.entries.joinToString {
                            it.kindName
                        }}",
                    )
                } else {
                    if (kind.capability.name !in manifest.capabilities) {
                        problem("$path.kind", "kind ${kind.kindName} needs capability ${kind.capability.name}")
                    }
                    checkFields("$path.fields", resource.fields, kind.fields, topLevel = true)
                    checkStatusMap(path, resource.statusMap, resource.fields, kind.fields)
                }
                resource.forEach?.let { parents ->
                    checkRequest("$path.forEach.request", parents.request, fannedOut = false)
                    parents.items?.let { checkExpression("$path.forEach.items", it) }
                }
                checkRequest("$path.request", resource.request, fannedOut = resource.forEach != null)
                resource.items?.let { checkExpression("$path.items", it) }
            }
            problems += PageRules.check(manifest)
            problems += ActionRules(manifest, placeholders).check()
            return problems
        }

        private fun checkHeader() {
            if (manifest.manifestVersion != ManifestJson.CURRENT_VERSION) {
                problem(
                    "manifestVersion",
                    "unsupported version ${manifest.manifestVersion}; this build reads ${ManifestJson.CURRENT_VERSION}",
                )
            }
            if (!idPattern.matches(manifest.id)) problem("id", "must match ${idPattern.pattern}")
            if (manifest.name.isBlank()) problem("name", "must not be blank")
            if (!versionPattern.matches(manifest.version)) problem("version", "must be MAJOR.MINOR.PATCH")
            if (manifest.hosts.isEmpty()) problem("hosts", "declare at least one host")
            manifest.hosts.forEachIndexed { index, host ->
                if (!HostGuard.isValidHost(host)) {
                    problem("hosts[$index]", "\"$host\" is not a lowercase hostname (no scheme, port or wildcard)")
                }
            }
            if (manifest.dashboardUrl == null) problem("dashboardUrl", "name the provider's web dashboard")
            listOf("dashboardUrl" to manifest.dashboardUrl, "tokenUrl" to manifest.tokenUrl).forEach { (key, url) ->
                if (url != null && !isWebLink(url)) problem(key, "must be an https:// URL")
            }
            problems += limitProblems(manifest.limits)
            if (manifest.capabilities.isEmpty()) problem("capabilities", "declare at least one capability")
            manifest.capabilities.forEachIndexed { index, name ->
                if (Capability.entries.none { it.name == name }) {
                    problem("capabilities[$index]", "unknown capability \"$name\"")
                }
            }
        }

        /** [fannedOut]: the request is sent once per item of another list, so it may use placeholders. */
        private fun checkRequest(
            path: String,
            request: RequestSpec,
            fannedOut: Boolean,
        ) {
            if (request.method != "GET") problem("$path.method", "reads use GET; writes are declared as actions")
            placeholders.url(request.url, fannedOut).forEach { problem("$path.url", it) }
            request.query.forEach { (name, value) ->
                placeholders.query(name, value, fannedOut).forEach { problem("$path.query.$name", it) }
            }
            request.headers.keys.forEach { name ->
                val lower = name.lowercase()
                if (!headerNamePattern.matches(name) ||
                    lower in reservedHeaders ||
                    lower == manifest.auth.headerName()
                ) {
                    problem("$path.headers.$name", "header can't be set by a manifest")
                }
            }
            request.query.keys
                .filter { it == manifest.auth.queryName() }
                .forEach { problem("$path.query.$it", "collides with the auth parameter") }
            request.paging?.let { checkPaging("$path.paging", it, request) }
        }

        private fun checkPaging(
            path: String,
            paging: PagingSpec,
            request: RequestSpec,
        ) {
            if (paging.maxPages !in 1..MAX_PAGES) problem("$path.maxPages", "must be 1..$MAX_PAGES")
            if (paging.size !in 1..MAX_PAGE_SIZE) problem("$path.size", "must be 1..$MAX_PAGE_SIZE")
            val params =
                when (paging) {
                    is PagingSpec.Page -> {
                        if (paging.start < 0) problem("$path.start", "must not be negative")
                        listOfNotNull(paging.param, paging.sizeParam)
                    }
                    is PagingSpec.Cursor -> {
                        checkExpression("$path.next", paging.next)
                        listOfNotNull(paging.param, paging.sizeParam)
                    }
                    is PagingSpec.Link -> {
                        paging.next?.let { checkExpression("$path.next", it) }
                        listOfNotNull(paging.sizeParam)
                    }
                }
            if (params.any { it.isBlank() } || params.size != params.toSet().size) {
                problem(path, "param and sizeParam must be distinct and not blank")
            }
            params
                .filter { it in request.query || it == manifest.auth.queryName() }
                .forEach { problem(path, "\"$it\" is also set elsewhere") }
        }

        /** [topLevel]: fields of a resource, the only ones a nested request can be sent for. */
        private fun checkFields(
            path: String,
            fields: Map<String, FieldSpec>,
            schema: Map<String, FieldDef>,
            topLevel: Boolean,
        ) {
            schema
                .filter { (name, def) -> def.required && name !in fields }
                .forEach { (name, def) -> problem("$path.$name", "required ${def.type.name.lowercase()} field") }
            fields.forEach { (name, spec) ->
                val def = schema[name]
                if (def == null) {
                    problem("$path.$name", "unknown field; expected one of ${schema.keys.joinToString()}")
                } else {
                    checkField("$path.$name", spec, def, topLevel)
                }
            }
        }

        private fun checkField(
            path: String,
            spec: FieldSpec,
            def: FieldDef,
            topLevel: Boolean,
        ) {
            when (spec) {
                is FieldSpec.Expr -> checkSource(path, spec.expr, def.type)
                is FieldSpec.ExprList ->
                    if (def.type == FieldType.TEXT_LIST) {
                        spec.exprs.forEachIndexed { index, expr -> checkSource("$path[$index]", expr, FieldType.TEXT) }
                    } else {
                        problem(path, "only list fields take an array of expressions")
                    }
                is FieldSpec.Nested ->
                    if (def.type == FieldType.RECORDS) {
                        checkExpression("$path.from", spec.from)
                        checkFields("$path.fields", spec.fields, def.nested, topLevel = false)
                        checkStatusMap(path, spec.statusMap, spec.fields, def.nested)
                        if (spec.request != null && !topLevel) {
                            problem("$path.request", "only a resource's own fields can have a request")
                        }
                        spec.request?.takeIf { topLevel }?.let { checkRequest("$path.request", it, fannedOut = true) }
                    } else {
                        problem(path, "only record fields take a nested mapping")
                    }
            }
        }

        private fun checkStatusMap(
            path: String,
            statusMap: Map<String, String>,
            fields: Map<String, FieldSpec>,
            schema: Map<String, FieldDef>,
        ) {
            val statuses =
                fields.keys.firstNotNullOfOrNull {
                    schema[it]?.takeIf { def ->
                        def.type == FieldType.STATUS
                    }
                }
            if (statuses != null && statusMap.isEmpty()) problem("$path.statusMap", "map provider states to a status")
            if (statuses == null && statusMap.isNotEmpty()) problem("$path.statusMap", "no status field to apply it to")
            val allowed = statuses?.statuses.orEmpty()
            statusMap.forEach { (from, to) ->
                if (statuses != null && to !in allowed) {
                    problem("$path.statusMap.$from", "unknown status \"$to\"; use one of ${allowed.joinToString()}")
                }
            }
        }

        /** One field value written as a string: a template, or an expression whose result fits [type]. */
        private fun checkSource(
            path: String,
            source: String,
            type: FieldType,
        ) {
            when {
                type == FieldType.RECORDS -> problem(path, "expects a nested mapping object")
                '{' in source -> placeholders.field(source, type).forEach { problem(path, it) }
                else -> {
                    val pipes = checkExpression(path, source)?.pipes ?: return
                    if (type == FieldType.TIME && pipes.lastOrNull()?.pipe !in timePipes) {
                        problem(path, "a time field must end with the date pipe or millis")
                    }
                }
            }
        }

        private fun checkExpression(
            path: String,
            source: String,
        ): Expression? =
            try {
                Expression.parse(source)
            } catch (e: ExpressionException) {
                problem(path, e.message.orEmpty())
                null
            }
    }
}

/** The lowercase header that carries the token, if any. */
private fun AuthSpec.headerName(): String? =
    when (this) {
        AuthSpec.Bearer -> "authorization"
        is AuthSpec.Header -> name.lowercase()
        is AuthSpec.Query -> null
    }

private fun AuthSpec.queryName(): String? = (this as? AuthSpec.Query)?.name

/** A link the app may offer to open in the browser. The host need not be in `hosts`; the engine never calls it. */
private fun isWebLink(url: String): Boolean {
    val rest = url.removePrefix("https://")
    return rest != url &&
        rest
            .substringBefore('/')
            .substringBefore('#')
            .substringBefore('?')
            .let(HostGuard::isValidHost)
}

private fun limitProblems(limits: LimitsSpec): List<ManifestProblem> =
    listOfNotNull(
        limits.requestsPerMinute?.takeIf { it !in 1..LimitsSpec.MAX_REQUESTS_PER_MINUTE }?.let {
            ManifestProblem("limits.requestsPerMinute", "must be 1 to ${LimitsSpec.MAX_REQUESTS_PER_MINUTE}")
        },
        limits.maxRequestsPerRefresh.takeIf { it !in 1..LimitsSpec.MAX_REQUESTS_PER_REFRESH }?.let {
            ManifestProblem("limits.maxRequestsPerRefresh", "must be 1 to ${LimitsSpec.MAX_REQUESTS_PER_REFRESH}")
        },
    )
