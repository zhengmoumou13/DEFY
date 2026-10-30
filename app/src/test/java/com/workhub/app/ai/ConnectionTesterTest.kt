package com.workhub.app.ai

import com.workhub.app.data.BackupSettings
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ConnectionTesterTest {
    private fun body(request: Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    private fun response(request: Request, code: Int, value: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(value.toResponseBody()).build()
    @Test fun probesUseTheConfiguredEndpointAndModel() {
        val tester = ConnectionTester()
        AiServiceKind.entries.forEach { kind ->
            val request = tester.request(kind,"https://service.example/v1","test-key","custom-model")
            assertEquals("Bearer test-key",request.header("Authorization"))
            assertTrue(body(request).contains("custom-model"))
            assertTrue(request.url.toString().endsWith(if(kind==AiServiceKind.SPEECH) "/audio/transcriptions" else "/chat/completions"))
        }
        val image = body(tester.request(AiServiceKind.VISION,"https://image.example/v1","key","vision"))
        assertTrue(image.contains("data:image/png;base64,"))
        assertFalse(image.contains("训练记录"))
    }
    @Test fun speechFixtureIsValidOneSecondPcmWav() {
        val wav = ConnectionTester.silentWav()
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF",wav.copyOfRange(0,4).toString(Charsets.US_ASCII))
        assertEquals(16_000,b.getInt(24));assertEquals(32_000,b.getInt(40));assertEquals(32_044,wav.size)
        assertTrue(wav.drop(44).all { it == 0.toByte() })
    }
    @Test fun successRequiresCorrectProtocolAndVisionResult() {
        assertTrue(ConnectionTester.validateResponse(AiServiceKind.TEXT,"""{"choices":[{"message":{"content":"OK"}}]}""").contains("文字"))
        assertTrue(ConnectionTester.validateResponse(AiServiceKind.VISION,"""{"choices":[{"message":{"content":"红色"}}]}""").contains("图片"))
        assertTrue(ConnectionTester.validateResponse(AiServiceKind.SPEECH,"""{"text":""}""").contains("实际识别效果"))
        listOf("<html>Welcome</html>","{}","""{"choices":[]}""").forEach { body ->
            assertThrows(Exception::class.java) { ConnectionTester.validateResponse(AiServiceKind.TEXT,body) }
        }
        assertThrows(Exception::class.java) { ConnectionTester.validateResponse(AiServiceKind.VISION,"""{"choices":[{"message":{"content":"I cannot view images"}}]}""") }
        assertThrows(Exception::class.java) { ConnectionTester.validateResponse(AiServiceKind.SPEECH,"""{"text":null}""") }
    }
    @Test fun asyncSuccessReportsElapsedTime() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { response(it.request(),200,"""{"choices":[{"message":{"content":"OK"}}]}""") }.build()
        try {
            val result=ConnectionTester(client).test(AiServiceKind.TEXT,"https://example.invalid/v1","key","model")
            assertTrue(result.elapsedMs >= 0);assertTrue(result.description.contains("文字接口可用"))
        } finally { client.dispatcher.executorService.shutdown() }
    }
    @Test fun httpErrorsDoNotExposeRawResponsesOrCredentials() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { response(it.request(),401,"test-key private-server-message") }.build()
        try {
            val error=runCatching { ConnectionTester(client).test(AiServiceKind.TEXT,"https://example.invalid/v1","test-key","model") }.exceptionOrNull()!!
            assertTrue(error.message!!.contains("401"));assertFalse(error.message!!.contains("test-key"));assertFalse(error.message!!.contains("private-server-message"))
        } finally { client.dispatcher.executorService.shutdown() }
    }
    @Test fun timeoutsAreReportedAsTimeouts() = runBlocking {
        val client=OkHttpClient.Builder().addInterceptor { throw SocketTimeoutException("private-host") }.build()
        try {
            val error=runCatching { ConnectionTester(client).test(AiServiceKind.TEXT,"https://example.invalid/v1","key","model") }.exceptionOrNull()!!
            assertTrue(error.message!!.contains("超时"));assertFalse(error.message!!.contains("private-host"))
        } finally { client.dispatcher.executorService.shutdown() }
    }
    @Test fun cancellationCancelsUnderlyingRequest() = runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val call=AtomicReference<Call>()
        val client=OkHttpClient.Builder().addInterceptor {
            call.set(it.call());entered.countDown();release.await(3,TimeUnit.SECONDS)
            response(it.request(),200,"""{"choices":[{"message":{"content":"OK"}}]}""")
        }.build()
        try {
            val job=launch(Dispatchers.Default) { ConnectionTester(client).test(AiServiceKind.TEXT,"https://example.invalid/v1","key","model") }
            assertTrue(entered.await(3,TimeUnit.SECONDS));job.cancelAndJoin();assertTrue(call.get().isCanceled())
        } finally { release.countDown();client.dispatcher.executorService.shutdown() }
    }
    @Test fun invalidUrlsAndMissingCredentialsNeverStartRequests() {
        val tester=ConnectionTester()
        listOf("http://example.com","https://user:pass@example.com","https://example.com/?key=secret").forEach { base ->
            assertThrows(IllegalArgumentException::class.java) { tester.request(AiServiceKind.TEXT,base,"key","model") }
        }
        assertThrows(IllegalArgumentException::class.java) { tester.request(AiServiceKind.TEXT,"https://example.com","","model") }
        assertThrows(IllegalArgumentException::class.java) { tester.request(AiServiceKind.TEXT,"https://example.com","key","") }
    }
    @Test fun themeSurvivesBackupAndOldBackupsRemainCompatible() {
        assertEquals("dark",Json.decodeFromString<BackupSettings>("{}").themeMode)
        val settings=BackupSettings(themeMode="light")
        assertEquals("light",Json.decodeFromString<BackupSettings>(Json.encodeToString(settings)).themeMode)
    }
}
