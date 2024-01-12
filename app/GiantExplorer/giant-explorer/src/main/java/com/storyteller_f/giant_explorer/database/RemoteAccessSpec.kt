package com.storyteller_f.giant_explorer.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.storyteller_f.file_system_remote.RemoteAccessType
import com.storyteller_f.file_system_remote.RemoteSpec
import com.storyteller_f.file_system_remote.ShareSpec
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "remote-access",
    primaryKeys = ["server", "port", "user", "password", "share", "type"]
)
data class RemoteAccessSpec(
    val server: String = "",
    val port: Int = 0,
    val user: String = "",
    val password: String = "",
    @ColumnInfo(defaultValue = "") val share: String = "", // smb 专用
    @ColumnInfo(defaultValue = RemoteAccessType.FTP) val type: String
) {
    fun toFtpSpec(): RemoteSpec {
        return RemoteSpec(server, port, user, password, type)
    }

    fun toShareSpec(): ShareSpec {
        return ShareSpec(server, port, user, password, type, share)
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
}

fun ShareSpec.toRemote() =
    RemoteAccessSpec(server, port, user, password, share, type)

fun RemoteSpec.toRemote() =
    RemoteAccessSpec(server, port, user, password, type = type)