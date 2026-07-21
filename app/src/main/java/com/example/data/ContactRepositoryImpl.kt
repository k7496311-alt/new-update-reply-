package com.example.data

import com.example.database.ContactDao
import com.example.database.ContactEntity
import com.example.model.Contact
import com.example.model.ContactStatus
import com.example.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactRepositoryImpl(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContactsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getContactsByStatus(status: ContactStatus): List<Contact> {
        return contactDao.getContactsByStatus(status.name).map { it.toDomainModel() }
    }

    override suspend fun getContactById(id: Long): Contact? {
        return contactDao.getContactById(id)?.toDomainModel()
    }

    override suspend fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        return contactDao.getContactByPhoneNumber(phoneNumber)?.toDomainModel()
    }

    override suspend fun saveContact(contact: Contact): Long {
        val entity = ContactEntity.fromDomainModel(contact)
        return if (entity.id == 0L) {
            contactDao.insertContact(entity)
        } else {
            contactDao.updateContact(entity)
            entity.id
        }
    }

    override suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(ContactEntity.fromDomainModel(contact))
    }
}
