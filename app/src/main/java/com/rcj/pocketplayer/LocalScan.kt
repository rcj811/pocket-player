package com.rcj.pocketplayer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** 掃描使用者選的資料夾（含子資料夾）裡的音樂與影片。 */
object LocalScan {
    data class Found(val uri: Uri, val name: String, val isVideo: Boolean)
    data class Offer(val folderName: String, val files: List<Found>) {
        val audioCount get() = files.count { !it.isVideo }
        val videoCount get() = files.count { it.isVideo }
    }

    private val audioExt = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "amr", "mka")
    private val videoExt = setOf("mp4", "m4v", "mkv", "webm", "3gp", "mov")

    fun scan(context: Context, tree: Uri, maxFiles: Int = 3000): Offer {
        val found = ArrayList<Found>()
        val pending = ArrayDeque<String>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        pending.add(rootId)
        var visitedDirs = 0
        while (pending.isNotEmpty() && found.size < maxFiles && visitedDirs < 500) {
            val docId = pending.removeFirst()
            visitedDirs++
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            try {
                context.contentResolver.query(
                    children,
                    arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime == Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) pending.add(id)
                            continue
                        }
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val isVideo = mime.startsWith("video/") || (!mime.startsWith("audio/") && ext in videoExt)
                        val isAudio = !isVideo && (mime.startsWith("audio/") || ext in audioExt)
                        if (isAudio || isVideo) {
                            found += Found(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, isVideo)
                        }
                    }
                }
            } catch (_: Exception) {
                // 單一子資料夾讀不到就略過
            }
        }
        val folderName = rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "資料夾" }
        return Offer(folderName, found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
    }
}
