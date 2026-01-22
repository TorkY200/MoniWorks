package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.Contact.ContactType;
import com.example.application.repository.ContactNoteRepository;
import com.example.application.repository.ContactPersonRepository;
import com.example.application.repository.ContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing contacts (customers and suppliers).
 * Handles contact CRUD operations plus related people and notes.
 */
@Service
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;
    private final ContactPersonRepository contactPersonRepository;
    private final ContactNoteRepository contactNoteRepository;
    private final AuditService auditService;

    public ContactService(ContactRepository contactRepository,
                          ContactPersonRepository contactPersonRepository,
                          ContactNoteRepository contactNoteRepository,
                          AuditService auditService) {
        this.contactRepository = contactRepository;
        this.contactPersonRepository = contactPersonRepository;
        this.contactNoteRepository = contactNoteRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a new contact for the given company.
     * @throws IllegalArgumentException if code already exists
     */
    public Contact createContact(Company company, String code, String name, ContactType type) {
        if (contactRepository.existsByCompanyAndCode(company, code)) {
            throw new IllegalArgumentException("Contact code already exists: " + code);
        }
        Contact contact = new Contact(company, code, name, type);
        contact = contactRepository.save(contact);

        auditService.logEvent(company, null, "CONTACT_CREATED", "Contact", contact.getId(),
            "Created contact: " + code + " - " + name);

        return contact;
    }

    /**
     * Finds all contacts for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<Contact> findByCompany(Company company) {
        return contactRepository.findByCompanyOrderByCode(company);
    }

    /**
     * Finds active contacts for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<Contact> findActiveByCompany(Company company) {
        return contactRepository.findByCompanyAndActiveOrderByCode(company, true);
    }

    /**
     * Finds contacts by company and type.
     */
    @Transactional(readOnly = true)
    public List<Contact> findByCompanyAndType(Company company, ContactType type) {
        return contactRepository.findByCompanyAndTypeOrderByCode(company, type);
    }

    /**
     * Finds active contacts that can be used as customers (CUSTOMER or BOTH).
     */
    @Transactional(readOnly = true)
    public List<Contact> findActiveCustomers(Company company) {
        return contactRepository.findActiveByCompanyAndTypeOrBoth(company, ContactType.CUSTOMER);
    }

    /**
     * Finds active contacts that can be used as suppliers (SUPPLIER or BOTH).
     */
    @Transactional(readOnly = true)
    public List<Contact> findActiveSuppliers(Company company) {
        return contactRepository.findActiveByCompanyAndTypeOrBoth(company, ContactType.SUPPLIER);
    }

    /**
     * Searches contacts by code, name, or email.
     */
    @Transactional(readOnly = true)
    public List<Contact> searchByCompany(Company company, String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findByCompany(company);
        }
        return contactRepository.searchByCompany(company, searchTerm.trim());
    }

    /**
     * Finds a contact by company and code.
     */
    @Transactional(readOnly = true)
    public Optional<Contact> findByCompanyAndCode(Company company, String code) {
        return contactRepository.findByCompanyAndCode(company, code);
    }

    /**
     * Finds a contact by ID.
     */
    @Transactional(readOnly = true)
    public Optional<Contact> findById(Long id) {
        return contactRepository.findById(id);
    }

    /**
     * Saves a contact.
     */
    public Contact save(Contact contact) {
        return contactRepository.save(contact);
    }

    /**
     * Deactivates a contact (soft delete).
     */
    public void deactivate(Contact contact) {
        contact.setActive(false);
        contactRepository.save(contact);

        auditService.logEvent(contact.getCompany(), null, "CONTACT_DEACTIVATED", "Contact", contact.getId(),
            "Deactivated contact: " + contact.getCode());
    }

    /**
     * Gets distinct categories used by contacts in a company.
     */
    @Transactional(readOnly = true)
    public List<String> getCategories(Company company) {
        return contactRepository.findDistinctCategoriesByCompany(company);
    }

    // Contact Person methods

    /**
     * Finds all people for a contact.
     */
    @Transactional(readOnly = true)
    public List<ContactPerson> findPeopleByContact(Contact contact) {
        return contactPersonRepository.findByContactOrderByPrimaryDescNameAsc(contact);
    }

    /**
     * Creates a new contact person.
     */
    public ContactPerson createPerson(Contact contact, String name) {
        ContactPerson person = new ContactPerson(contact, name);
        return contactPersonRepository.save(person);
    }

    /**
     * Saves a contact person.
     */
    public ContactPerson savePerson(ContactPerson person) {
        return contactPersonRepository.save(person);
    }

    /**
     * Deletes a contact person.
     */
    public void deletePerson(ContactPerson person) {
        contactPersonRepository.delete(person);
    }

    // Contact Note methods

    /**
     * Finds all notes for a contact, newest first.
     */
    @Transactional(readOnly = true)
    public List<ContactNote> findNotesByContact(Contact contact) {
        return contactNoteRepository.findByContactOrderByCreatedAtDesc(contact);
    }

    /**
     * Creates a new contact note.
     */
    public ContactNote createNote(Contact contact, String noteText, User createdBy) {
        ContactNote note = new ContactNote(contact, noteText, createdBy);
        return contactNoteRepository.save(note);
    }

    /**
     * Saves a contact note.
     */
    public ContactNote saveNote(ContactNote note) {
        return contactNoteRepository.save(note);
    }

    /**
     * Deletes a contact note.
     */
    public void deleteNote(ContactNote note) {
        contactNoteRepository.delete(note);
    }
}
