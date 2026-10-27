package dev.dache.plugin.api

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ManifestJsonTest {
    private val manifest =
        """
        {
          "manifestVersion": 1,
          "id": "example",
          "name": "Example",
          "version": "1.0.0",
          "hosts": ["api.example.com"],
          "auth": { "type": "header", "name": "X-Api-Key" },
          "capabilities": ["SITES"],
          "validate": {
            "request": { "url": "https://api.example.com/me" },
            "displayName": "email"
          },
          "resources": [
            {
              "kind": "site",
              "request": {
                "url": "https://api.example.com/sites",
                "paging": { "type": "page", "sizeParam": "per_page", "size": 50 }
              },
              "fields": {
                "nativeId": "id",
                "customDomains": ["domain | lower", "aliases"],
                "deployments": {
                  "from": "last_deploy",
                  "fields": { "status": "state" },
                  "statusMap": { "ok": "SUCCESS" }
                }
              }
            }
          ]
        }
        """.trimIndent()

    @Test
    fun decodesEveryFieldShape() {
        val decoded = ManifestJson.decode(manifest)

        assertEquals(AuthSpec.Header(name = "X-Api-Key"), decoded.auth)
        val resource = decoded.resources.single()
        assertEquals(PagingSpec.Page(sizeParam = "per_page", size = 50), resource.request.paging)
        assertEquals(FieldSpec.Expr("id"), resource.fields["nativeId"])
        assertEquals(FieldSpec.ExprList(listOf("domain | lower", "aliases")), resource.fields["customDomains"])
        assertEquals(
            FieldSpec.Nested(
                from = "last_deploy",
                fields = mapOf("status" to FieldSpec.Expr("state")),
                statusMap = mapOf("ok" to "SUCCESS"),
            ),
            resource.fields["deployments"],
        )
    }

    @Test
    fun roundTrips() {
        val decoded = ManifestJson.decode(manifest)

        assertEquals(decoded, ManifestJson.decode(ManifestJson.encode(decoded)))
    }

    @Test
    fun bearerAuthIsJustATag() {
        val bearer = manifest.replace("""{ "type": "header", "name": "X-Api-Key" }""", """{ "type": "bearer" }""")

        val decoded = ManifestJson.decode(bearer)

        assertEquals(AuthSpec.Bearer, decoded.auth)
    }

    @Test
    fun unknownKeysAreRejected() {
        assertFailsWith<SerializationException> {
            ManifestJson.decode(manifest.replace("\"hosts\"", "\"hostz\": [], \"hosts\""))
        }
    }

    @Test
    fun nonStringExpressionIsRejected() {
        assertFailsWith<SerializationException> {
            ManifestJson.decode(manifest.replace("\"nativeId\": \"id\"", "\"nativeId\": 42"))
        }
    }
}
