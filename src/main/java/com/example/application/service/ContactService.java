package com.example.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.domain.Contact.ContactType;
import com.example.application.repository.ContactNoteRepository;
import com.example.application.repository.ContactPersonRepository;
import com.example.application.repository.ContactRepository;

/**
 * Service for managing contacts (customers and suppliers). Handles contact CRUD operations plus
 * related people and notes.
 */
@Service
@Transactional
public class ContactService {

  private final ContactRepository contactRepository;
  private final ContactPersonRepository contactPersonRepository;
  private final ContactNoteRepository contactNoteRepository;
  private final AuditService auditService;

  public ContactService(
      ContactRepository contactRepository,
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
   *
   * @throws IllegalArgumentException if code already exists
   */
  public Contact createContact(Company company, String code, String name, ContactType type) {
    if (contactRepository.existsByCompanyAndCode(company, code)) {
      throw new IllegalArgumentException("Contact code already exists: " + code);
    }
    Contact contact = new Contact(company, code, name, type);
    contact = contactRepository.save(contact);

    auditService.logEvent(
        company,
        null,
        "CONTACT_CREATED",
        "Contact",
        contact.getId(),
        "Created contact: " + code + " - " + name);

    return contact;
  }

  /** Finds all contacts for a company, ordered by code. */
  @Transactional(readOnly = true)
  public List<Contact> findByCompany(Company company) {
    return contactRepository.findByCompanyOrderByCode(company);
  }

  /** Finds active contacts for a company, ordered by code. */
  @Transactional(readOnly = true)
  public List<Contact> findActiveByCompany(Company company) {
    return contactRepository.findByCompanyAndActiveOrderByCode(company, true);
  }

  /** Finds contacts by company and type. */
  @Transactional(readOnly = true)
  public List<Contact> findByCompanyAndType(Company company, ContactType type) {
    return contactRepository.findByCompanyAndTypeOrderByCode(company, type);
  }

  /** Finds active contacts that can be used as customers (CUSTOMER or BOTH). */
  @Transactional(readOnly = true)
  public List<Contact> findActiveCustomers(Company company) {
    return contactRepository.findActiveByCompanyAndTypeOrBoth(company, ContactType.CUSTOMER);
  }

  /** Finds active contacts that can be used as suppliers (SUPPLIER or BOTH). */
  @Transactional(readOnly = true)
  public List<Contact> findActiveSuppliers(Company company) {
    return contactRepository.findActiveByCompanyAndTypeOrBoth(company, ContactType.SUPPLIER);
  }

  /** Searches contacts by code, name, or email. */
  @Transactional(readOnly = true)
  public List<Contact> searchByCompany(Company company, String searchTerm) {
    if (searchTerm == null || searchTerm.isBlank()) {
      return findByCompany(company);
    }
    return contactRepository.searchByCompany(company, searchTerm.trim());
  }

  /** Finds a contact by company and code. */
  @Transactional(readOnly = true)
  public Optional<Contact> findByCompanyAndCode(Company company, String code) {
    return contactRepository.findByCompanyAndCode(company, code);
  }

  /** Finds a contact by ID. */
  @Transactional(readOnly = true)
  public Optional<Contact> findById(Long id) {
    return contactRepository.findById(id);
  }

  /** Saves a contact. */
  public Contact save(Contact contact) {
    return save(contact, null);
  }

  /**
   * Saves a contact with audit logging for edits. Captures before/after state for key fields.
   *
   * @param contact the contact to save
   * @param actor the user making the change
   * @return the saved contact
   */
  public Contact save(Contact contact, User actor) {
    boolean isNew = contact.getId() == null;

    if (!isNew) {
      // Capture before state for existing contact
      Contact before = contactRepository.findById(contact.getId()).orElse(null);
      if (before != null) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!before.getCode().equals(contact.getCode())) {
          changes.put("code", Map.of("from", before.getCode(), "to", contact.getCode()));
        }
        if (!before.getName().equals(contact.getName())) {
          changes.put("name", Map.of("from", before.getName(), "to", contact.getName()));
        }
        if (before.getType() != contact.getType()) {
          changes.put(
              "type", Map.of("from", before.getType().name(), "to", contact.getType().name()));
        }
        if (!Objects.equals(before.getEmail(), contact.getEmail())) {
          changes.put(
              "email",
              Map.of(
                  "from", before.getEmail() != null ? before.getEmail() : "",
                  "to", contact.getEmail() != null ? contact.getEmail() : ""));
        }
        if (!Objects.equals(before.getPhone(), contact.getPhone())) {
          changes.put(
              "phone",
              Map.of(
                  "from", before.getPhone() != null ? before.getPhone() : "",
                  "to", contact.getPhone() != null ? contact.getPhone() : ""));
        }
        if (!Objects.equals(before.getCategory(), contact.getCategory())) {
          changes.put(
              "category",
              Map.of(
                  "from", before.getCategory() != null ? before.getCategory() : "",
                  "to", contact.getCategory() != null ? contact.getCategory() : ""));
        }
        if (before.isActive() != contact.isActive()) {
          changes.put("active", Map.of("from", before.isActive(), "to", contact.isActive()));
        }
        if (!Objects.equals(before.getTaxOverrideCode(), contact.getTaxOverrideCode())) {
          changes.put(
              "taxOverrideCode",
              Map.of(
                  "from", before.getTaxOverrideCode() != null ? before.getTaxOverrideCode() : "",
                  "to", contact.getTaxOverrideCode() != null ? contact.getTaxOverrideCode() : ""));
        }

        if (!changes.isEmpty()) {
          Contact saved = contactRepository.save(contact);
          auditService.logEvent(
              contact.getCompany(),
              actor,
              "CONTACT_UPDATED",
              "Contact",
              contact.getId(),
              "Updated contact: " + contact.getCode(),
              changes);
          return saved;
        }
      }
    }

    return contactRepository.save(contact);
  }

  /** Deactivates a contact (soft delete). */
  public void deactivate(Contact contact) {
    contact.setActive(false);
    contactRepository.save(contact);

    auditService.logEvent(
        contact.getCompany(),
        null,
        "CONTACT_DEACTIVATED",
        "Contact",
        contact.getId(),
        "Deactivated contact: " + contact.getCode());
  }

  /** Gets distinct categories used by contacts in a company. */
  @Transactional(readOnly = true)
  public List<String> getCategories(Company company) {
    return contactRepository.findDistinctCategoriesByCompany(company);
  }

  // Contact Person methods

  /** Finds all people for a contact. */
  @Transactional(readOnly = true)
  public List<ContactPerson> findPeopleByContact(Contact contact) {
    return contactPersonRepository.findByContactOrderByPrimaryDescNameAsc(contact);
  }

  /** Creates a new contact person. */
  public ContactPerson createPerson(Contact contact, String name) {
    ContactPerson person = new ContactPerson(contact, name);
    return contactPersonRepository.save(person);
  }

  /** Saves a contact person. */
  public ContactPerson savePerson(ContactPerson person) {
    return contactPersonRepository.save(person);
  }

  /** Deletes a contact person. */
  public void deletePerson(ContactPerson person) {
    contactPersonRepository.delete(person);
  }

  // Contact Note methods

  /** Finds all notes for a contact, newest first. */
  @Transactional(readOnly = true)
  public List<ContactNote> findNotesByContact(Contact contact) {
    return contactNoteRepository.findByContactOrderByCreatedAtDesc(contact);
  }

  /** Creates a new contact note. */
  public ContactNote createNote(Contact contact, String noteText, User createdBy) {
    ContactNote note = new ContactNote(contact, noteText, createdBy);
    return contactNoteRepository.save(note);
  }

  /** Saves a contact note. */
  public ContactNote saveNote(ContactNote note) {
    return contactNoteRepository.save(note);
  }

  /** Deletes a contact note. */
  public void deleteNote(ContactNote note) {
    contactNoteRepository.delete(note);
  }
}
