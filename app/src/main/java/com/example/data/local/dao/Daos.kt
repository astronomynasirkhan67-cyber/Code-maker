package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.BoardEntity
import com.example.data.local.entity.LibraryEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

data class ProjectWithFiles(
    val project: ProjectEntity,
    val files: List<ProjectFileEntity>
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProjectById(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectByIdDirect(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY isMain DESC, name ASC")
    fun getFilesForProject(projectId: Long): Flow<List<ProjectFileEntity>>

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY isMain DESC, name ASC")
    suspend fun getFilesForProjectDirect(projectId: Long): List<ProjectFileEntity>

    @Query("SELECT * FROM project_files WHERE id = :fileId LIMIT 1")
    suspend fun getFileById(fileId: Long): ProjectFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ProjectFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<ProjectFileEntity>)

    @Update
    suspend fun updateFile(file: ProjectFileEntity)

    @Query("UPDATE project_files SET content = :content WHERE id = :fileId")
    suspend fun updateFileContent(fileId: Long, content: String)

    @Delete
    suspend fun deleteFile(file: ProjectFileEntity)

    @Query("DELETE FROM project_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)
}

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY name ASC")
    fun getAllBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE isInstalled = 1 ORDER BY name ASC")
    fun getInstalledBoards(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE id = :id LIMIT 1")
    fun getBoardById(id: String): Flow<BoardEntity?>

    @Query("SELECT * FROM boards WHERE id = :id LIMIT 1")
    suspend fun getBoardByIdDirect(id: String): BoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoards(boards: List<BoardEntity>)

    @Query("UPDATE boards SET isInstalled = :installed WHERE id = :id")
    suspend fun setBoardInstalled(id: String, installed: Boolean)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM libraries ORDER BY name ASC")
    fun getAllLibraries(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE isInstalled = 1 ORDER BY name ASC")
    fun getInstalledLibraries(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE id = :id LIMIT 1")
    fun getLibraryById(id: String): Flow<LibraryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraries(libraries: List<LibraryEntity>)

    @Query("UPDATE libraries SET isInstalled = :installed WHERE id = :id")
    suspend fun setLibraryInstalled(id: String, installed: Boolean)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM app_settings")
    fun getAllSettings(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    fun getSetting(key: String): Flow<String?>

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingDirect(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)
}
