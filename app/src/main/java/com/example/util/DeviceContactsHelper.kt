package com.example.util

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DevicePhonebookContact(
    val displayName: String,
    val rawPhone: String,
    val normalizedPhone: String
)

object DeviceContactsHelper {

    /**
     * Loads contacts directly from the Android device phonebook (`ContactsContract`),
     * normalizes all numbers through the Pan-India/NRI E.164 normalizer, and deduplicates by phone.
     */
    suspend fun loadDeviceContacts(context: Context): List<DevicePhonebookContact> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, DevicePhonebookContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val rawPhone = if (phoneIdx >= 0) cursor.getString(phoneIdx).orEmpty().trim() else ""
                    val normalized = MessageVariableParser.normalizeIndianOrNriPhone(rawPhone) ?: continue
                    if (!result.containsKey(normalized)) {
                        result[normalized] = DevicePhonebookContact(
                            displayName = name.ifBlank { MessageVariableParser.formatDisplayPhone(normalized) },
                            rawPhone = rawPhone,
                            normalizedPhone = normalized
                        )
                    }
                }
            }
        }
        result.values.toList()
    }
}
