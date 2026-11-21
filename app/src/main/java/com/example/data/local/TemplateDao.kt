package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MessageTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM message_templates ORDER BY usageCount DESC, id DESC")
    fun observeTemplates(): Flow<List<MessageTemplate>>

    @Query("SELECT * FROM message_templates")
    suspend fun getAllTemplatesDirect(): List<MessageTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MessageTemplate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<MessageTemplate>)

    @Update
    suspend fun updateTemplate(template: MessageTemplate)

    @Query("UPDATE message_templates SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Delete
    suspend fun deleteTemplate(template: MessageTemplate)
}
