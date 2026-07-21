package com.example.repository

import com.example.model.Contact
import com.example.model.ContactStatus
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAllContacts(): Flow<List<Contact>>
    suspend fun getContactsByStatus(status: ContactStatus): List<Contact>
    suspend fun getContactById(id: Long): Contact?
    suspend fun getContactByPhoneNumber(phoneNumber: String): Contact?
    suspend fun saveContact(contact: Contact): Long
    suspend fun deleteContact(contact: Contact)
}
