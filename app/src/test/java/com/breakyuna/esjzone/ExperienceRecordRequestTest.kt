package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.features.ExperienceRecordException
import com.breakyuna.esjzone.network.features.getExperienceRecords
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExperienceRecordRequestTest {
    private val authorization = Authorization(domain = "www.esjzone.cc")

    private fun response(request: Request, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody())
        .build()

    private fun withClient(client: OkHttpClient, block: () -> Unit) {
        val field = EsjzoneClient::class.java.getDeclaredField("sharedHttpClient").apply { isAccessible = true }
        val original = field.get(EsjzoneClient) as OkHttpClient
        field.set(EsjzoneClient, client)
        try {
            block()
        } finally {
            field.set(EsjzoneClient, original)
        }
    }

    @Test fun eachPageObtainsAndSendsItsOwnToken() {
        val requests = mutableListOf<Request>()
        var tokenCount = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = when (request.url.encodedPath) {
                "/my/record" -> "<JinJing>fixture-token-${++tokenCount}</JinJing>"
                "/inc/mem_record_data.php" -> {
                    if (request.header("Authorization") == "fixture-token-$tokenCount") {
                        """{"status":200,"total":45,"rows":[{"cdate":"2026-11-11","exp":"+5"}]}"""
                    } else {
                        """{"status":301,"total":0,"rows":[]}"""
                    }
                }
                else -> error("Unexpected request path")
            }
            response(request, body)
        }.build()

        withClient(client) {
            val first = EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.COMMENT, 0)
            val next = EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.COMMENT, 30)
            assertEquals(45, first.total)
            assertEquals("+5", first.rows.single().exp)
            assertEquals("+5", next.rows.single().exp)
        }

        assertEquals(listOf("POST", "GET", "POST", "GET"), requests.map { it.method })
        for (index in listOf(0, 2)) {
            val tokenRequest = requests[index]
            val form = tokenRequest.body as FormBody
            assertEquals("/my/record", tokenRequest.url.encodedPath)
            assertEquals("plxf", form.name(0))
            assertEquals("getAuthToken", form.value(0))

            val recordsRequest = requests[index + 1]
            assertEquals("https://www.esjzone.cc/my/record", recordsRequest.header("Referer"))
            assertEquals("XMLHttpRequest", recordsRequest.header("X-Requested-With"))
            assertEquals("fixture-token-${index / 2 + 1}", recordsRequest.header("Authorization"))
            assertEquals("type:3", recordsRequest.url.queryParameter("search"))
            assertEquals(if (index == 0) "0" else "30", recordsRequest.url.queryParameter("offset"))
        }
    }

    @Test fun missingTokenStopsBeforeRequestingRecords() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            response(chain.request(), "<html>login</html>")
        }.build()

        withClient(client) {
            assertThrows(ExperienceRecordException::class.java) {
                EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.POST, 0)
            }
        }
        assertEquals(listOf("/my/record"), requests.map { it.url.encodedPath })
    }

    @Test fun rejectedTokenDoesNotBecomeAnEmptyRecordList() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = if (request.url.encodedPath == "/my/record") {
                "<JinJing>fixture-token</JinJing>"
            } else {
                """{"status":301,"total":0,"rows":[]}"""
            }
            response(request, body)
        }.build()

        withClient(client) {
            assertThrows(ExperienceRecordException::class.java) {
                EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.POST, 0)
            }
        }
        // One fresh token and one repeat after the first status 301, then failure.
        assertEquals(listOf("POST", "GET", "POST", "GET"), requests.map { it.method })
    }
}
