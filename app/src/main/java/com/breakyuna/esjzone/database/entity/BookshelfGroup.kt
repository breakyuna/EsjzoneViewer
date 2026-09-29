package com.breakyuna.esjzone.database.entity
import androidx.room.Entity
@Entity(tableName = "bookshelf_groups", primaryKeys = ["scope", "name"])
data class BookshelfGroup(val scope: String, val name: String)
@Entity(tableName = "bookshelf_group_members", primaryKeys = ["scope", "bookKey"])
data class BookshelfGroupMember(val scope: String, val bookKey: String, val groupName: String)
