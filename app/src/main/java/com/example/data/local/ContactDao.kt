package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Contact
import com.example.data.model.ContactGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contact_groups ORDER BY createdAt DESC")
    fun observeGroups(): Flow<List<ContactGroup>>

    @Query("SELECT * FROM contact_groups ORDER BY createdAt DESC")
    suspend fun getAllGroupsDirect(): List<ContactGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ContactGroup): Long

    @Update
    suspend fun updateGroup(group: ContactGroup)

    @Delete
    suspend fun deleteGroup(group: ContactGroup)

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    fun observeContacts(): Flow<List<Contact>>

    @Query("SELECT COUNT(*) FROM contacts")
    fun observeContactCount(): Flow<Int>

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllContactsDirect(): List<Contact>

    @Query("SELECT * FROM contacts WHERE id IN (:ids)")
    suspend fun getContactsByIdsBatch(ids: List<Long>): List<Contact>

    @Query("SELECT * FROM contacts WHERE groupId IN (:groupIds) AND isOptedIn = 1")
    suspend fun getOptedInContactsByGroupIdsBatch(groupIds: List<Long>): List<Contact>

    @Query("SELECT * FROM contacts WHERE groupId = :groupId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getContactsByGroupDirect(groupId: Long): List<Contact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactsBatch(contacts: List<Contact>): List<Long>

    @Update
    suspend fun updateContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: Long)

    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun deleteContactsByIdsBatch(ids: List<Long>)

    @Query("UPDATE contacts SET groupId = 0 WHERE groupId = :groupId")
    suspend fun ungroupContacts(groupId: Long)
}
