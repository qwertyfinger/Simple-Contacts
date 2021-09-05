package com.simplemobiletools.contacts.pro.helpers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.simplemobiletools.commons.extensions.normalizePhoneNumber
import com.simplemobiletools.commons.helpers.SORT_BY_FIRST_NAME
import com.simplemobiletools.commons.helpers.SORT_BY_FULL_NAME
import com.simplemobiletools.commons.helpers.SORT_BY_MIDDLE_NAME
import com.simplemobiletools.commons.helpers.SORT_BY_SURNAME
import com.simplemobiletools.contacts.pro.extensions.config
import com.simplemobiletools.contacts.pro.extensions.getEmptyContact
import com.simplemobiletools.contacts.pro.models.Contact
import com.simplemobiletools.contacts.pro.models.PhoneNumber

private const val ID_COLUMN = "_id"
private const val NAME_COLUMN_GET = "name"
private const val NAME_COLUMN_INSERT = "tag"
private const val NAME_COLUMN_UPDATE = "newTag"
private const val PHONE_NUMBER_COLUMN = "number"
private const val PHONE_NUMBER_COLUMN_UPDATE = "newNumber"

class SimContactsHelper(val context: Context) {
    private val simCardContactsUri = Uri.parse("content://icc/adn")

    fun getAllContacts(): List<Contact> {
        val contactList = mutableListOf<Contact>()

        val cursor = context.contentResolver.query(simCardContactsUri, null, null, null, getSortString()) ?: return contactList

        cursor.use {
            try {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndex(NAME_COLUMN_GET))
                    val id = cursor.getInt(cursor.getColumnIndex(ID_COLUMN))
                    val phoneNumber = cursor.getString(cursor.getColumnIndex(PHONE_NUMBER_COLUMN))

                    contactList.add(SimContact(id, name, phoneNumber).toContact(context))
                    Log.d("Contact", "id: $id, name: $name phone: $phoneNumber")
                }
            } catch (ignored: Exception) {
            }
        }

        return contactList
    }

    fun insertContact(contact: Contact): Boolean {
        val simContact = ContentValues().apply {
            put(NAME_COLUMN_INSERT, contact.getNameToDisplay())
            put(PHONE_NUMBER_COLUMN, contact.firstPhoneNumber ?: "")
        }

        return context.contentResolver.insert(simCardContactsUri, simContact) != null
    }

    fun updateContact(contact: Contact): Boolean {
        val newName = contact.getNameToDisplay()
        val newPhoneNumber = contact.firstPhoneNumber ?: ""

        // TODO: check if it's possible to find contact by id with where clause
        val oldContact = getAllContacts().find { it.id == contact.id }
        val oldName = oldContact?.getNameToDisplay() ?: ""
        val oldPhoneNumber = oldContact?.firstPhoneNumber ?: ""

        val simContact = ContentValues().apply {
            put(NAME_COLUMN_INSERT, oldName)
            put(PHONE_NUMBER_COLUMN, oldPhoneNumber)
            put(NAME_COLUMN_UPDATE, newName)
            put(PHONE_NUMBER_COLUMN_UPDATE, newPhoneNumber)
        }
        return context.contentResolver.update(simCardContactsUri, simContact, null, null) != 0
    }

    fun deleteContacts(contactList: List<Contact>) {
        contactList.forEach { contact ->
            val name = contact.getNameToDisplay()
            val phoneNumber = contact.phoneNumbers.firstOrNull()?.value ?: ""
            val selection = "$NAME_COLUMN_INSERT = ? AND $PHONE_NUMBER_COLUMN = ?"
            val selectionArgs = arrayOf(name, phoneNumber)
            context.contentResolver.delete(simCardContactsUri, selection, selectionArgs)
        }
    }

    private fun getSortString(): String {
        val sorting = context.config.sorting
        val nameSort = "$NAME_COLUMN_GET COLLATE NOCASE"
        return when {
            sorting and SORT_BY_FIRST_NAME != 0 -> nameSort
            sorting and SORT_BY_MIDDLE_NAME != 0 -> nameSort
            sorting and SORT_BY_SURNAME != 0 -> nameSort
            sorting and SORT_BY_FULL_NAME != 0 -> nameSort
            else -> ID_COLUMN
        }
    }
}

data class SimContact(
    private val id: Int,
    private val name: String,
    private val phoneNumber: String
) {
    fun toContact(context: Context): Contact {
        return context.getEmptyContact().also { emptyContact ->
            emptyContact.id = id
            emptyContact.contactId = id
            emptyContact.firstName = name
            emptyContact.phoneNumbers = arrayListOf(
                PhoneNumber(
                    value = phoneNumber,
                    type = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    label = "",
                    normalizedNumber = phoneNumber.normalizePhoneNumber()
                )
            )
            // TODO: how to handle SIM2?
            emptyContact.source = SIM_1_CUSTOM
        }
    }
}
