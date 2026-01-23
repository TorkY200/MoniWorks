package com.example.application.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.Company;
import com.example.application.domain.SavedView;
import com.example.application.domain.SavedView.EntityType;
import com.example.application.domain.User;
import com.example.application.repository.SavedViewRepository;

/**
 * Service for managing saved grid views per user. Allows users to save, retrieve, and manage custom
 * grid configurations.
 */
@Service
@Transactional
public class SavedViewService {

  private static final Logger log = LoggerFactory.getLogger(SavedViewService.class);

  private final SavedViewRepository savedViewRepository;
  private final AuditService auditService;

  public SavedViewService(SavedViewRepository savedViewRepository, AuditService auditService) {
    this.savedViewRepository = savedViewRepository;
    this.auditService = auditService;
  }

  /** Finds all saved views for a user in a company. */
  @Transactional(readOnly = true)
  public List<SavedView> findByCompanyAndUser(Company company, User user) {
    return savedViewRepository.findByCompanyAndUserOrderByName(company, user);
  }

  /** Finds all saved views for a user for a specific entity type. */
  @Transactional(readOnly = true)
  public List<SavedView> findByEntityType(Company company, User user, EntityType entityType) {
    return savedViewRepository.findByCompanyAndUserAndEntityTypeOrderByName(
        company, user, entityType);
  }

  /** Finds a saved view by name. */
  @Transactional(readOnly = true)
  public Optional<SavedView> findByName(
      Company company, User user, EntityType entityType, String name) {
    return savedViewRepository.findByCompanyAndUserAndEntityTypeAndName(
        company, user, entityType, name);
  }

  /** Finds the default view for an entity type. */
  @Transactional(readOnly = true)
  public Optional<SavedView> findDefaultView(Company company, User user, EntityType entityType) {
    return savedViewRepository.findByCompanyAndUserAndEntityTypeAndIsDefaultTrue(
        company, user, entityType);
  }

  /** Creates a new saved view. */
  public SavedView create(
      Company company,
      User user,
      EntityType entityType,
      String name,
      String columnsJson,
      String filtersJson,
      String sortJson) {
    if (savedViewRepository.existsByCompanyAndUserAndEntityTypeAndName(
        company, user, entityType, name)) {
      throw new IllegalArgumentException("A view with this name already exists");
    }

    SavedView view = new SavedView(company, user, entityType, name);
    view.setColumnsJson(columnsJson);
    view.setFiltersJson(filtersJson);
    view.setSortJson(sortJson);

    SavedView saved = savedViewRepository.save(view);
    auditService.logEvent(
        company,
        user,
        "SAVED_VIEW_CREATED",
        "SAVED_VIEW",
        saved.getId(),
        "Created saved view: " + name + " for " + entityType);

    log.info(
        "Created saved view {} for user {} on entity type {}", name, user.getEmail(), entityType);
    return saved;
  }

  /** Updates an existing saved view. */
  public SavedView update(SavedView view, String columnsJson, String filtersJson, String sortJson) {
    view.setColumnsJson(columnsJson);
    view.setFiltersJson(filtersJson);
    view.setSortJson(sortJson);

    SavedView saved = savedViewRepository.save(view);
    auditService.logEvent(
        view.getCompany(),
        view.getUser(),
        "SAVED_VIEW_UPDATED",
        "SAVED_VIEW",
        saved.getId(),
        "Updated saved view: " + view.getName());

    return saved;
  }

  /** Sets a view as the default for its entity type. Clears any previous default. */
  public SavedView setAsDefault(SavedView view) {
    // Clear existing defaults for this entity type
    List<SavedView> existingViews =
        savedViewRepository.findByCompanyAndUserAndEntityTypeOrderByName(
            view.getCompany(), view.getUser(), view.getEntityType());
    for (SavedView existing : existingViews) {
      if (existing.isDefault() && !existing.getId().equals(view.getId())) {
        existing.setDefault(false);
        savedViewRepository.save(existing);
      }
    }

    // Set this view as default
    view.setDefault(true);
    SavedView saved = savedViewRepository.save(view);

    auditService.logEvent(
        view.getCompany(),
        view.getUser(),
        "SAVED_VIEW_SET_DEFAULT",
        "SAVED_VIEW",
        saved.getId(),
        "Set saved view as default: " + view.getName());

    return saved;
  }

  /** Removes default status from a view. */
  public SavedView clearDefault(SavedView view) {
    view.setDefault(false);
    return savedViewRepository.save(view);
  }

  /** Renames a saved view. */
  public SavedView rename(SavedView view, String newName) {
    if (!view.getName().equals(newName)
        && savedViewRepository.existsByCompanyAndUserAndEntityTypeAndName(
            view.getCompany(), view.getUser(), view.getEntityType(), newName)) {
      throw new IllegalArgumentException("A view with this name already exists");
    }

    String oldName = view.getName();
    view.setName(newName);
    SavedView saved = savedViewRepository.save(view);

    auditService.logEvent(
        view.getCompany(),
        view.getUser(),
        "SAVED_VIEW_RENAMED",
        "SAVED_VIEW",
        saved.getId(),
        "Renamed saved view from " + oldName + " to " + newName);

    return saved;
  }

  /** Deletes a saved view. */
  public void delete(SavedView view) {
    auditService.logEvent(
        view.getCompany(),
        view.getUser(),
        "SAVED_VIEW_DELETED",
        "SAVED_VIEW",
        view.getId(),
        "Deleted saved view: " + view.getName());
    savedViewRepository.delete(view);
    log.info("Deleted saved view {} for user {}", view.getName(), view.getUser().getEmail());
  }

  /** Creates or updates a saved view by name. */
  public SavedView saveOrUpdate(
      Company company,
      User user,
      EntityType entityType,
      String name,
      String columnsJson,
      String filtersJson,
      String sortJson) {
    Optional<SavedView> existing = findByName(company, user, entityType, name);
    if (existing.isPresent()) {
      return update(existing.get(), columnsJson, filtersJson, sortJson);
    } else {
      return create(company, user, entityType, name, columnsJson, filtersJson, sortJson);
    }
  }
}
