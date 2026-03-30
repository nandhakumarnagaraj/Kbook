package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE email = :email AND is_deleted = 0 LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE login_id = :loginId AND is_deleted = 0 LIMIT 1")
    suspend fun getUserByLoginId(loginId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE is_deleted = 0 LIMIT 1")
    suspend fun getAnyUser(): UserEntity?

    @Query(
        "UPDATE users SET whatsapp_number = :newPhone, is_synced = 0, updated_at = :updatedAt WHERE id = :userId"
    )
    suspend fun updateWhatsappNumber(userId: Long, newPhone: String, updatedAt: Long)

    @Query(
        "UPDATE users SET email = :newEmail, whatsapp_number = :newPhone, is_synced = 0, updated_at = :updatedAt WHERE id = :userId"
    )
    suspend fun updateAccountDetails(userId: Long, newEmail: String, newPhone: String, updatedAt: Long)

    @Query(
        "UPDATE users SET login_id = :newLoginId, email = :newEmail, whatsapp_number = :newPhone, is_synced = :isSynced, updated_at = :updatedAt, server_updated_at = :serverUpdatedAt WHERE id = :userId"
    )
    suspend fun updateIdentityAndWhatsappNumber(
        userId: Long,
        newLoginId: String,
        newEmail: String,
        newPhone: String,
        isSynced: Boolean,
        updatedAt: Long,
        serverUpdatedAt: Long
    )

    @Query("SELECT * FROM users WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query(
        "UPDATE users SET is_active = :isActive, is_synced = 0, updated_at = :updatedAt WHERE id = :userId"
    )
    suspend fun setActivationStatus(userId: Long, isActive: Boolean, updatedAt: Long)

    @Query(
        "UPDATE users SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE id = :userId"
    )
    suspend fun markDeleted(userId: Long, updatedAt: Long)

    @Query("SELECT * FROM users WHERE is_synced = 0")
    suspend fun getUnsyncedUsers(): List<UserEntity>

    @Query("UPDATE users SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markUsersAsSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedUsers(items: List<UserEntity>)

    @Query("SELECT * FROM users")
    suspend fun getAllUsersOnce(): List<UserEntity>
}
