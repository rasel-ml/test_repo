package com.fontlens.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Shared folder scanning utility — used by both FontListFragment and MainActivity.
 */
object FolderScanner {

    private val FONT_EXTS = setOf("ttf", "otf", "woff", "woff2", "ttc")

    /** Collect all font URIs under [folderUri], optionally recursive. */
    fun collectFontUris(context: Context, folderUri: Uri, recursive: Boolean): List<Uri> {
        val cr     = context.contentResolver
        val result = mutableListOf<Uri>()
        fun scan(treeUri: Uri, docId: String) {
            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            cr.query(childUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null)?.use { cursor ->
                val idCol   = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idCol)   ?: continue
                    val name    = cursor.getString(nameCol) ?: continue
                    val mime    = cursor.getString(mimeCol) ?: ""
                    val ext     = name.substringAfterLast(".").lowercase()
                    when {
                        ext in FONT_EXTS -> result.add(
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
                        recursive && mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                            scan(treeUri, childId)
                    }
                }
            }
        }
        scan(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
        return result
    }

    /**
     * Collect immediate child sub-folder names+docIds under [folderUri].
     * Returns list of Pair(displayName, childUri).
     */
    fun collectSubFolders(context: Context, folderUri: Uri): List<Pair<String, Uri>> {
        val cr     = context.contentResolver
        val result = mutableListOf<Pair<String, Uri>>()
        val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val childUri  = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, rootDocId)
        cr.query(childUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            val idCol   = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idCol)   ?: continue
                val name    = cursor.getString(nameCol) ?: continue
                val mime    = cursor.getString(mimeCol) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val subUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, childId)
                    // Only include if it actually contains fonts (recursively)
                    val hasFonts = collectFontUris(context, folderUri, true).isNotEmpty()
                    if (hasFonts) result.add(Pair(name, subUri))
                }
            }
        }
        return result
    }

    /**
     * Checks if [newUri] is a parent of any existing saved URI or vice versa.
     * Returns the conflicting URIs so the caller can decide what to remove.
     *
     * "Is parent" = the new path string is a prefix of an existing one,
     * or an existing one is a prefix of the new one.
     */
    fun findConflicts(newUri: Uri, existingUris: List<Uri>): List<Uri> {
        val newPath = uriToPath(newUri)
        return existingUris.filter { existing ->
            val existingPath = uriToPath(existing)
            // new is parent of existing, or existing is parent of new
            existingPath.startsWith(newPath + "/") ||
            existingPath.startsWith(newPath) && existingPath == newPath ||
            newPath.startsWith(existingPath + "/")
        }
    }

    fun uriToPath(uri: Uri): String =
        try { (uri.lastPathSegment ?: uri.toString()).substringAfter(":") }
        catch (_: Exception) { uri.toString() }
}
