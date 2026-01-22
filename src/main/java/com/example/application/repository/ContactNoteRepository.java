package com.example.application.repository;

import com.example.application.domain.Contact;
import com.example.application.domain.ContactNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContactNoteRepository extends JpaRepository<ContactNote, Long> {

    List<ContactNote> findByContactOrderByCreatedAtDesc(Contact contact);

    @Query("SELECT n FROM ContactNote n WHERE n.contact.company.id = :companyId " +
           "AND n.followUpDate <= :date ORDER BY n.followUpDate, n.contact.name")
    List<ContactNote> findDueFollowUpsByCompany(@Param("companyId") Long companyId, @Param("date") LocalDate date);

    void deleteByContact(Contact contact);
}
