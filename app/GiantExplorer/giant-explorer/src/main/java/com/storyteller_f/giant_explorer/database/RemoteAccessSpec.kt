package com.storyteller_f.giant_explorer.database

import android.net.Uri
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.storyteller_f.file_system_remote.RemoteSchemes
import com.storyteller_f.file_system_remote.RemoteSpec
import com.storyteller_f.file_system_remote.ShareSpec
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "remote-access",
)
data class RemoteAccessSpec(
    @PrimaryKey(true)
    val id: Int,
    val server: String,
    val port: Int,
    val user: String,
    val password: String,
    val share: String, // smb 专用
    val type: String,
    val name: String
) {
    fun toRemoteSpec(): RemoteSpec {
        return RemoteSpec(server, port, user, password, type)
    }

    fun toShareSpec(): ShareSpec {
        return ShareSpec(server, port, user, password, type, share)
    }

    fun toUri(): Uri {
        return if (type == RemoteSchemes.SMB || type == RemoteSchemes.WEB_DAV) {
            toShareSpec().toUri()
        } else {
            toRemoteSpec().toUri()
        }
    }
}

@Dao
interface RemoteAccessDao {
    @Query("select * from `remote-access`")
    fun list(): Flow<List<RemoteAccessSpec>>

    @Query("select * from `remote-access`")
    suspend fun listAsync(): List<RemoteAccessSpec>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(spec: RemoteAccessSpec)

    @Delete
    fun remove(spec: RemoteAccessSpec)

    @Query("select * from `remote-access` where id = :id")
    fun find(id: Int): RemoteAccessSpec
}
