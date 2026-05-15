package demos.bpmdetector.infra.system

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

// SystemApi：只负责从系统文档能力拿文件名。
class AudioPickerSystemApi(
    private val contentResolver: ContentResolver,
) {
    // 尽量解析用户看得懂的显示名。
    fun resolveDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex) ?: uri.lastPathSegment.orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }
}
