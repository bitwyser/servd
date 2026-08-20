package dev.servd.core.files

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileStoreTest {

    @Test
    fun save_list_get_roundtrip_preserves_bytes() {
        val dir = Files.createTempDirectory("servd-files").toFile()
        try {
            val store = FileStore(dir)
            val bytes = "hello servd file".toByteArray()
            val meta = store.save("note.txt", "text/plain", "alice", bytes.inputStream())

            assertEquals("note.txt", meta.name)
            assertEquals(bytes.size.toLong(), meta.size)
            assertEquals("alice", meta.fromName)
            assertEquals(1, store.list().size)

            val entry = store.get(meta.id)
            assertContentEquals(bytes, entry!!.second.readBytes())
            assertNull(store.get("does-not-exist"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun strips_path_components_from_upload_name() {
        val dir = Files.createTempDirectory("servd-files").toFile()
        try {
            val store = FileStore(dir)
            val meta = store.save("../../etc/passwd", null, "x", "d".byteInputStream())
            assertEquals("passwd", meta.name)
        } finally {
            dir.deleteRecursively()
        }
    }
}
