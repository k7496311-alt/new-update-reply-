package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Contact
import com.example.model.ContactStatus

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phoneNumber: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ContactStatus
) {
    fun toDomainModel(): Contact {
        return Contact(
            id = id,
            name = name,
            phoneNumber = phoneNumber,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status
        )
    }

    companion object {
        fun fromDomainModel(contact: Contact): ContactEntity {
            return ContactEntity(
                id = contact.id,
                name = contact.name,
                phoneNumber = contact.phoneNumber,
                createdAt = contact.createdAt,
                updatedAt = contact.updatedAt,
                status = contact.status
            )
        }
    }
}
