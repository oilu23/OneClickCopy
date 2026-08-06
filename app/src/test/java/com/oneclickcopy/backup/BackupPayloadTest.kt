package com.oneclickcopy.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Backup payload compatibility.
 *
 * These matter because a user's only copy of their data may be in an older
 * format; failing to parse it is indistinguishable from data loss.
 */
class BackupPayloadTest {

    private val transfer = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `entity round trips through backup form`() {
        val entity = com.oneclickcopy.data.DocumentEntity(
            id = 7,
            title = "Replies",
            content = "one\ntwo",
            copiedItems = """["0:one"]""",
            createdAt = 100L,
            updatedAt = 200L,
            uuid = "uuid-1",
        )

        val restored = entity.toBackup().toEntity()

        assertThat(restored.title).isEqualTo(entity.title)
        assertThat(restored.content).isEqualTo(entity.content)
        assertThat(restored.copiedItems).isEqualTo(entity.copiedItems)
        assertThat(restored.uuid).isEqualTo(entity.uuid)
        assertThat(restored.updatedAt).isEqualTo(entity.updatedAt)
        // Row id is device-local and must not survive a backup round trip.
        assertThat(restored.id).isEqualTo(0)
    }

    @Test
    fun `toEntity supplies timestamps when the backup omits them`() {
        val entity = BackupDocument(title = "No dates").toEntity()

        assertThat(entity.createdAt).isGreaterThan(0L)
        assertThat(entity.updatedAt).isGreaterThan(0L)
    }

    @Test
    fun `payload serializes with a version`() {
        val json = transfer.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(documents = listOf(BackupDocument(title = "x"))),
        )

        assertThat(json).contains("\"version\"")
        assertThat(json).contains("\"documents\"")
    }

    @Test
    fun `payload decodes despite unknown future fields`() {
        val futureJson = """
            {"version":99,"timestamp":1,"documents":[
              {"uuid":"a","title":"t","content":"c","copiedItems":"",
               "createdAt":1,"updatedAt":2,"unknownFutureField":true}
            ],"somethingNew":"ignored"}
        """.trimIndent()

        val payload = transfer.decodeFromString(BackupPayload.serializer(), futureJson)

        assertThat(payload.documents).hasSize(1)
        assertThat(payload.documents.single().title).isEqualTo("t")
    }

    @Test
    fun `legacy v1 payload without uuid still decodes`() {
        // Pre-refactor backups were written by Gson and had no uuid field.
        val legacy = """
            {"version":1,"timestamp":123,"documents":[
              {"title":"Old","content":"body","copiedItems":"",
               "createdAt":10,"updatedAt":20}
            ]}
        """.trimIndent()

        val payload = transfer.decodeFromString(BackupPayload.serializer(), legacy)

        assertThat(payload.documents).hasSize(1)
        assertThat(payload.documents.single().uuid).isEmpty()
        // An empty uuid must still produce a usable entity.
        assertThat(payload.documents.single().toEntity().title).isEqualTo("Old")
    }
}
