package io.sentry

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.exception.SentryEnvelopeException
import io.sentry.protocol.Device
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.core.Option
import org.junit.Assert.assertThat

class GsonSerializerTest {

    private class Fixture {
        val logger: ILogger = mock()
        val serializer: ISerializer
        val hub = mock<IHub>()

        init {
            val options = SentryOptions()
            options.setLogger(logger)
            options.setDebug(true)
            options.setEnvelopeReader(EnvelopeReader())
            whenever(hub.options).thenReturn(options)
            serializer = GsonSerializer(options)
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun before() {
        fixture = Fixture()
    }

    private fun serializeToString(ev: SentryEvent): String {
        return this.serializeToString { wrt -> fixture.serializer.serialize(ev, wrt) }
    }

    private fun serializeToString(session: Session): String {
        return this.serializeToString { wrt -> fixture.serializer.serialize(session, wrt) }
    }

    private fun serializeToString(userFeedback: UserFeedback): String {
        return this.serializeToString { wrt -> fixture.serializer.serialize(userFeedback, wrt) }
    }

    private fun serializeToString(serialize: (StringWriter) -> Unit): String {
        val wrt = StringWriter()
        serialize(wrt)
        return wrt.toString()
    }

    private fun serializeToString(envelope: SentryEnvelope): String {
        val outputStream = ByteArrayOutputStream()
        BufferedWriter(OutputStreamWriter(outputStream))
        fixture.serializer.serialize(envelope, outputStream)
        return outputStream.toString()
    }

    @Test
    fun `when serializing SentryEvent-SentryId object, it should become a event_id json without dashes`() {
        val sentryEvent = generateEmptySentryEvent()

        val actual = serializeToString(sentryEvent)

        val expected = "{\"event_id\":\"${sentryEvent.eventId}\",\"contexts\":{}}"

        assertJsonContains(actual, expected)
    }

    @Test
    fun `when deserializing event_id, it should become a SentryEvent-SentryId uuid`() {
        val expected = UUID.randomUUID().toString().replace("-", "")
        val jsonEvent = "{\"event_id\":\"$expected\"}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals(expected, actual!!.eventId.toString())
    }

    @Test
    fun `when serializing SentryEvent-Date, it should become a timestamp json ISO format`() {
        val dateIsoFormat = "2000-12-31T23:59:58.000Z"
        val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
        sentryEvent.eventId = null

        val expected = "{\"timestamp\":\"$dateIsoFormat\",\"contexts\":{}}"

        val actual = serializeToString(sentryEvent)

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing timestamp, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "2000-12-31T23:59:58.000Z"
        val expected = DateUtils.getDateTime(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals(expected, actual!!.timestamp)
    }

    @Test
    fun `when deserializing millis timestamp, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "1581410911"
        val expected = DateUtils.getDateTimeWithMillisPrecision(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals(expected, actual!!.timestamp)
    }

    @Test
    fun `when deserializing millis timestamp with mills precision, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "1581410911.988"
        val expected = DateUtils.getDateTimeWithMillisPrecision(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals(expected, actual!!.timestamp)
    }

    @Test
    fun `when deserializing unknown properties, it should be added to unknown field`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"string\":\"test\",\"int\":1,\"boolean\":true}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals("test", (actual!!.unknown!!["string"] as JsonPrimitive).asString)
        assertEquals(1, (actual.unknown!!["int"] as JsonPrimitive).asInt)
        assertEquals(true, (actual.unknown!!["boolean"] as JsonPrimitive).asBoolean)
    }

    @Test
    fun `when deserializing unknown properties with nested objects, it should be added to unknown field`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val objects = hashMapOf<String, Any>()
        objects["int"] = 1
        objects["boolean"] = true

        val unknown = hashMapOf<String, Any>()
        unknown["object"] = objects
        sentryEvent.acceptUnknownProperties(unknown)

        val jsonEvent = "{\"object\":{\"int\":1,\"boolean\":true}}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        val hashMapActual = actual!!.unknown!!["object"] as JsonObject // gson creates it as JsonObject

        assertEquals(true, hashMapActual.get("boolean").asBoolean)
        assertEquals(1, (hashMapActual.get("int")).asInt)
    }

    @Test
    fun `when serializing unknown field, it should become unknown as json format`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val objects = hashMapOf<String, Any>()
        objects["int"] = 1
        objects["boolean"] = true

        val unknown = hashMapOf<String, Any>()
        unknown["object"] = objects

        sentryEvent.acceptUnknownProperties(unknown)

        val actual = serializeToString(sentryEvent)

        val expected = "{\"unknown\":{\"object\":{\"boolean\":true,\"int\":1}},\"contexts\":{}}"

        assertJsonContains(actual, expected)
    }

    @Test
    fun `when serializing a TimeZone, it should become a timezone ID string`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null
        val device = Device()
        device.timezone = TimeZone.getTimeZone("Europe/Vienna")
        sentryEvent.contexts.setDevice(device)

        val expected = "{\"contexts\":{\"device\":{\"timezone\":\"Europe/Vienna\"}}}"

        val actual = serializeToString(sentryEvent)

        assertJsonContains(actual, expected)
    }

    @Test
    fun `when deserializing a timezone ID string, it should become a Device-TimeZone`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"contexts\":{\"device\":{\"timezone\":\"Europe/Vienna\"}}}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals("Europe/Vienna", actual!!.contexts.device!!.timezone!!.id)
    }

    @Test
    fun `when serializing a DeviceOrientation, it should become an orientation string`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null
        val device = Device()
        device.orientation = Device.DeviceOrientation.LANDSCAPE
        sentryEvent.contexts.setDevice(device)

        val expected = "{\"contexts\":{\"device\":{\"orientation\":\"landscape\"}}}"
        val actual = serializeToString(sentryEvent)
        assertJsonContains(actual, expected)
    }

    private fun assertJsonContains(actual: String, expected: String) {
        assertThat(actual, jsonEquals<String>(expected).`when`(Option.IGNORING_EXTRA_FIELDS))
    }

    @Test
    fun `when deserializing an orientation string, it should become a DeviceOrientation`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"contexts\":{\"device\":{\"orientation\":\"landscape\"}}}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals(Device.DeviceOrientation.LANDSCAPE, actual!!.contexts.device!!.orientation)
    }

    @Test
    fun `when serializing a SentryLevel, it should become a sentry level string`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null
        sentryEvent.level = SentryLevel.DEBUG

        val expected = "{\"level\":\"debug\",\"contexts\":{}}"

        val actual = serializeToString(sentryEvent)

        assertJsonContains(actual, expected)
    }

    @Test
    fun `when deserializing a sentry level string, it should become a SentryLevel`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"level\":\"debug\"}"

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertEquals(SentryLevel.DEBUG, actual!!.level)
    }

    @Test
    fun `when deserializing a event with breadcrumbs containing data, it should become have breadcrumbs`() {
        val jsonEvent = FileFromResources.invoke("event_breadcrumb_data.json")

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

        assertNotNull(actual) { event ->
            assertNotNull(event.breadcrumbs) {
                assertEquals(2, it.size)
            }
        }
    }

    @Test
    fun `when deserializing a event with custom contexts, they should be set in the event contexts`() {
        val jsonEvent = FileFromResources.invoke("event_with_contexts.json")

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)
        val obj = actual!!.contexts["object"] as Map<*, *>
        val number = actual.contexts["number"] as Double
        val list = actual.contexts["list"] as List<*>
        val listObjects = actual.contexts["list_objects"] as List<*>

        assertTrue(obj["boolean"] as Boolean)
        assertEquals("hi", obj["string"] as String)
        assertEquals(9.0, obj["number"] as Double)

        assertEquals(50.0, number)

        assertEquals(1.0, list[0])
        assertEquals(2.0, list[1])

        val listObjectsFirst = listObjects[0] as Map<*, *>
        assertTrue(listObjectsFirst["boolean"] as Boolean)
        assertEquals("hi", listObjectsFirst["string"] as String)
        assertEquals(9.0, listObjectsFirst["number"] as Double)

        val listObjectsSecond = listObjects[1] as Map<*, *>
        assertFalse(listObjectsSecond["boolean"] as Boolean)
        assertEquals("ciao", listObjectsSecond["string"] as String)
        assertEquals(10.0, listObjectsSecond["number"] as Double)
    }

    @Test
    fun `when theres a null value, gson wont blow up`() {
        val json = FileFromResources.invoke("event.json")
        val event = fixture.serializer.deserialize(StringReader(json), SentryEvent::class.java)
        assertNotNull(event)
        assertNull(event.user)
    }

    @Test
    fun `When deserializing a Session all the values should be set to the Session object`() {
        val jsonEvent = FileFromResources.invoke("session.json")

        val actual = fixture.serializer.deserialize(StringReader(jsonEvent), Session::class.java)

        assertSessionData(actual)
    }

    @Test
    fun `When deserializing an Envelope and reader throws IOException it should return null `() {
        val inputStream = mock<InputStream>()
        whenever(inputStream.read(any())).thenThrow(IOException())

        val envelope = fixture.serializer.deserializeEnvelope(inputStream)
        assertNull(envelope)
    }

    @Test
    fun `When serializing a Session all the values should be set to the JSON string`() {
        val session = createSessionMockData()
        val jsonSession = serializeToString(session)
        // reversing, so we can assert values and not a json string
        val expectedSession = fixture.serializer.deserialize(StringReader(jsonSession), Session::class.java)

        assertSessionData(expectedSession)
    }

    @Test
    fun `When deserializing an Envelope, all the values should be set to the SentryEnvelope object`() {
        val jsonEnvelope = FileFromResources.invoke("envelope_session.txt")
        val envelope = fixture.serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))
        assertEnvelopeData(envelope)
    }

    @Test
    fun `When deserializing an Envelope, SdkVersion should be set`() {
        val jsonEnvelope = FileFromResources.invoke("envelope_session_sdkversion.txt")
        val envelope = fixture.serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))!!
        assertNotNull(envelope.header.sdkVersion)
        val sdkInfo = envelope.header.sdkVersion!!

        assertEquals("test", sdkInfo.name)
        assertEquals("1.2.3", sdkInfo.version)

        assertNotNull(sdkInfo.integrations)
        assertTrue(sdkInfo.integrations!!.any { it == "NdkIntegration" })

        assertNotNull(sdkInfo.packages)

        assertTrue(sdkInfo.packages!!.any {
            it.name == "maven:io.sentry:sentry-android-core"
            it.version == "4.5.6"
        })
    }

    @Test
    fun `When serializing an envelope, all the values should be set`() {
        val session = createSessionMockData()
        val sentryEnvelope = SentryEnvelope.from(fixture.serializer, session, null)

        val jsonEnvelope = serializeToString(sentryEnvelope)
        // reversing it so we can assert the values
        val envelope = fixture.serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))
        assertEnvelopeData(envelope)
    }

    @Test
    fun `When serializing an envelope, SdkVersion should be set`() {
        val session = createSessionMockData()
        val version = SdkVersion().apply {
            name = "test"
            version = "1.2.3"
            addIntegration("TestIntegration")
            addPackage("abc", "4.5.6")
        }
        val sentryEnvelope = SentryEnvelope.from(fixture.serializer, session, version)

        val jsonEnvelope = serializeToString(sentryEnvelope)
        // reversing it so we can assert the values
        val envelope = fixture.serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))!!
        assertNotNull(envelope.header.sdkVersion)

        val sdkVersion = envelope.header.sdkVersion!!
        assertEquals(version.name, sdkVersion.name)
        assertEquals(version.version, sdkVersion.version)

        assertNotNull(sdkVersion.integrations)
        assertTrue(sdkVersion.integrations!!.any { it == "TestIntegration" })

        assertNotNull(sdkVersion.packages)
        assertTrue(sdkVersion.packages!!.any {
            it.name == "abc"
            it.version == "4.5.6"
        })
    }

    @Test
    fun `when serializing a data map, data should be stringfied`() {
        val data = mapOf("a" to "b")
        val expected = "{\"a\":\"b\"}"

        val dataJson = fixture.serializer.serialize(data)

        assertEquals(expected, dataJson)
    }

    @Test
    fun `serializes transaction`() {
        val trace = TransactionContext("transaction-name", "http")
        trace.description = "some request"
        trace.status = SpanStatus.OK
        trace.setTag("myTag", "myValue")
        val tracer = SentryTracer(trace, fixture.hub)
        val span = tracer.startChild("child")
        span.finish(SpanStatus.OK)
        tracer.finish()

        val stringWriter = StringWriter()
        fixture.serializer.serialize(SentryTransaction(tracer), stringWriter)

        val element = JsonParser().parse(stringWriter.toString()).asJsonObject
        assertEquals("transaction-name", element["transaction"].asString)
        assertEquals("transaction", element["type"].asString)
        assertNotNull(element["start_timestamp"].asString)
        assertNotNull(element["event_id"].asString)
        assertNotNull(element["spans"].asJsonArray)

        val jsonSpan = element["spans"].asJsonArray[0].asJsonObject
        assertNotNull(jsonSpan["trace_id"])
        assertNotNull(jsonSpan["span_id"])
        assertNotNull(jsonSpan["parent_span_id"])
        assertEquals("child", jsonSpan["op"].asString)
        assertNotNull("ok", jsonSpan["status"].asString)
        assertNotNull(jsonSpan["timestamp"])
        assertNotNull(jsonSpan["start_timestamp"])

        val jsonTrace = element["contexts"].asJsonObject["trace"].asJsonObject
        assertNotNull(jsonTrace["trace_id"].asString)
        assertNotNull(jsonTrace["span_id"].asString)
        assertEquals("http", jsonTrace["op"].asString)
        assertEquals("some request", jsonTrace["description"].asString)
        assertEquals("ok", jsonTrace["status"].asString)
        assertEquals("myValue", jsonTrace["tags"].asJsonObject["myTag"].asString)
    }

    @Test
    fun `deserializes transaction`() {
        val json = """{
                          "transaction": "a-transaction",
                          "type": "transaction",
                          "start_timestamp": "2020-10-23T10:24:01.791Z",
                          "timestamp": "2020-10-23T10:24:02.791Z",
                          "event_id": "3367f5196c494acaae85bbbd535379ac",
                          "contexts": {
                            "trace": {
                              "trace_id": "b156a475de54423d9c1571df97ec7eb6",
                              "span_id": "0a53026963414893",
                              "op": "http",
                              "status": "ok"
                            },
                            "custom": {
                              "some-key": "some-value"
                            }
                          },
                          "spans": [
                            {
                              "start_timestamp": "2021-03-05T08:51:12.838Z",
                              "timestamp": "2021-03-05T08:51:12.949Z",
                              "trace_id": "2b099185293344a5bfdd7ad89ebf9416",
                              "span_id": "5b95c29a5ded4281",
                              "parent_span_id": "a3b2d1d58b344b07",
                              "op": "PersonService.create",
                              "description": "desc",
                              "status": "aborted",
                              "tags": {
                                "name": "value"
                              }
                            }
                          ]
                        }"""
        val transaction = fixture.serializer.deserialize(StringReader(json), SentryTransaction::class.java)
        assertNotNull(transaction)
        assertEquals("a-transaction", transaction.transaction)
        assertNotNull(transaction.startTimestamp)
        assertNotNull(transaction.timestamp)
        assertNotNull(transaction.contexts)
        assertNotNull(transaction.contexts.trace)
        assertEquals(SpanStatus.OK, transaction.status)
        assertEquals("transaction", transaction.type)
        assertEquals("b156a475de54423d9c1571df97ec7eb6", transaction.contexts.trace!!.traceId.toString())
        assertEquals("0a53026963414893", transaction.contexts.trace!!.spanId.toString())
        assertEquals("http", transaction.contexts.trace!!.operation)
        assertNotNull(transaction.contexts["custom"])
        assertEquals("some-value", (transaction.contexts["custom"] as Map<*, *>)["some-key"])

        assertNotNull(transaction.spans)
        assertEquals(1, transaction.spans.size)
        val span = transaction.spans[0]
        assertNotNull(span.startTimestamp)
        assertNotNull(span.timestamp)
        assertEquals("2b099185293344a5bfdd7ad89ebf9416", span.traceId.toString())
        assertEquals("5b95c29a5ded4281", span.spanId.toString())
        assertEquals("a3b2d1d58b344b07", span.parentSpanId.toString())
        assertEquals("PersonService.create", span.op)
        assertEquals(SpanStatus.ABORTED, span.status)
        assertEquals("desc", span.description)
        assertEquals(mapOf("name" to "value"), span.tags)
    }

    @Test
    fun `serializing user feedback`() {
        val actual = serializeToString(userFeedback)

        val expected = "{\"event_id\":\"${userFeedback.eventId}\",\"name\":\"${userFeedback.name}\"," +
            "\"email\":\"${userFeedback.email}\",\"comments\":\"${userFeedback.comments}\"}"

        assertEquals(expected, actual)
    }

    @Test
    fun `deserializing user feedback`() {
        val jsonUserFeedback = "{\"event_id\":\"c2fb8fee2e2b49758bcb67cda0f713c7\"," +
            "\"name\":\"John\",\"email\":\"john@me.com\",\"comments\":\"comment\"}"
        val actual = fixture.serializer.deserialize(StringReader(jsonUserFeedback), UserFeedback::class.java)
        assertNotNull(actual)
        assertEquals(userFeedback.eventId, actual.eventId)
        assertEquals(userFeedback.name, actual.name)
        assertEquals(userFeedback.email, actual.email)
        assertEquals(userFeedback.comments, actual.comments)
    }

    @Test
    fun `serialize envelope with item throwing`() {
        val eventID = SentryId()
        val header = SentryEnvelopeHeader(eventID)

        val message = "hello"
        val attachment = Attachment(message.toByteArray(), "bytes.txt")
        val validAttachmentItem = SentryEnvelopeItem.fromAttachment(attachment, 5)

        val invalidAttachmentItem = SentryEnvelopeItem.fromAttachment(Attachment("no"), 5)
        val envelope = SentryEnvelope(header, listOf(invalidAttachmentItem, validAttachmentItem))

        val actualJson = serializeToString(envelope)

        val expectedJson = "{\"event_id\":\"${eventID}\"}\n" +
                "{\"content_type\":\"${attachment.contentType}\"," +
                "\"filename\":\"${attachment.filename}\"," +
                "\"type\":\"attachment\"," +
                "\"attachment_type\":\"event.attachment\"," +
                "\"length\":${attachment.bytes?.size}}\n" +
                "$message\n"

        assertEquals(expectedJson, actualJson)

        verify(fixture.logger)
                .log(eq(SentryLevel.ERROR),
                    eq("Failed to create envelope item. Dropping it."),
                    any<SentryEnvelopeException>())
    }

    @Test
    fun `empty maps are serialized to null`() {
        val event = SentryEvent()
        event.tags = emptyMap()
        val element = JsonParser().parse(serializeToString(event)).asJsonObject
        assertNull(element.asJsonObject["tags"])
    }

    @Test
    fun `empty lists are serialized to null`() {
        val transaction = SentryTransaction(SentryTracer(TransactionContext("tx", "op"), fixture.hub))
        val stringWriter = StringWriter()
        fixture.serializer.serialize(transaction, stringWriter)
        val element = JsonParser().parse(stringWriter.toString()).asJsonObject
        assertNull(element.asJsonObject["spans"])
    }

    @Test
    fun `gson serializer uses logger set on SentryOptions`() {
        val logger = mock<ILogger>()
        val options = SentryOptions()
        options.setLogger(logger)
        options.setDebug(true)
        whenever(logger.isEnabled(any())).thenReturn(true)

        (options.serializer as GsonSerializer).serialize(mapOf("key" to "val"), mock())
        verify(logger).log(any(), check {
            assertTrue(it.startsWith("Serializing object:"))
        }, any<Any>())
    }

    private fun assertSessionData(expectedSession: Session?) {
        assertNotNull(expectedSession)
        assertEquals(UUID.fromString("c81d4e2e-bcf2-11e6-869b-7df92533d2db"), expectedSession.sessionId)
        assertEquals("123", expectedSession.distinctId)
        assertTrue(expectedSession.init!!)
        assertEquals("2020-02-07T14:16:00.000Z", DateUtils.getTimestamp(expectedSession.started!!))
        assertEquals("2020-02-07T14:16:00.000Z", DateUtils.getTimestamp(expectedSession.timestamp!!))
        assertEquals(6000.toDouble(), expectedSession.duration)
        assertEquals(Session.State.Ok, expectedSession.status)
        assertEquals(2, expectedSession.errorCount())
        assertEquals(123456.toLong(), expectedSession.sequence)
        assertEquals("io.sentry@1.0+123", expectedSession.release)
        assertEquals("debug", expectedSession.environment)
        assertEquals("127.0.0.1", expectedSession.ipAddress)
        assertEquals("jamesBond", expectedSession.userAgent)
    }

    private fun assertEnvelopeData(expectedEnveope: SentryEnvelope?) {
        assertNotNull(expectedEnveope)
        assertEquals(1, expectedEnveope.items.count())
        expectedEnveope.items.forEach {
            assertEquals(SentryItemType.Session, it.header.type)
            val reader =
                InputStreamReader(ByteArrayInputStream(it.data), Charsets.UTF_8)
            val actualSession = fixture.serializer.deserialize(reader, Session::class.java)
            assertSessionData(actualSession)
        }
    }

    private fun generateEmptySentryEvent(date: Date = Date()): SentryEvent =
        SentryEvent(date)

    private fun createSessionMockData(): Session =
        Session(
            Session.State.Ok,
            DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
            DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
            2,
            "123",
            UUID.fromString("c81d4e2e-bcf2-11e6-869b-7df92533d2db"),
            true,
            123456.toLong(),
            6000.toDouble(),
            "127.0.0.1",
            "jamesBond",
            "debug",
            "io.sentry@1.0+123"
        )

    private val userFeedback: UserFeedback get() {
        val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
        return UserFeedback(eventId).apply {
            name = "John"
            email = "john@me.com"
            comments = "comment"
        }
    }
}
