package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.SavedView;
import com.example.application.domain.SavedView.EntityType;
import com.example.application.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedViewRepository extends JpaRepository<SavedView, Long> {

    List<SavedView> findByCompanyAndUserOrderByName(Company company, User user);

    List<SavedView> findByCompanyAndUserAndEntityTypeOrderByName(Company company, User user, EntityType entityType);

    Optional<SavedView> findByCompanyAndUserAndEntityTypeAndName(Company company, User user, EntityType entityType, String name);

    Optional<SavedView> findByCompanyAndUserAndEntityTypeAndIsDefaultTrue(Company company, User user, EntityType entityType);

    boolean existsByCompanyAndUserAndEntityTypeAndName(Company company, User user, EntityType entityType, String name);

    @Query("SELECT sv FROM SavedView sv WHERE sv.company = :company AND sv.user = :user AND sv.entityType = :entityType AND sv.isDefault = true")
    Optional<SavedView> findDefaultView(@Param("company") Company company, @Param("user") User user, @Param("entityType") EntityType entityType);

    @Query("UPDATE SavedView sv SET sv.isDefault = false WHERE sv.company = :company AND sv.user = :user AND sv.entityType = :entityType")
    void clearDefaultViews(@Param("company") Company company, @Param("user") User user, @Param("entityType") EntityType entityType);
}
