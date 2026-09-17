package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val selectedBoardId: String = "arduino_uno",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class ProjectFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val name: String,
    val content: String,
    val isMain: Boolean = false
)

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val fqbn: String,
    val arch: String,
    val mcu: String,
    val clockSpeed: String,
    val flashSize: String,
    val ramSize: String,
    val isInstalled: Boolean = true,
    val description: String,
    val category: String,
    val defaultBaudRate: Int = 115200,
    val vidPidPairs: String = "" // comma separated VID:PID pairs for auto detection
)

@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val category: String,
    val isInstalled: Boolean = false,
    val headerToInclude: String,
    val dependencies: String = ""
)

@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
