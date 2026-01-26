package com.example.application.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Contact;
import com.example.application.domain.ContactNote;

@Repository
public interface ContactNoteRepository extends JpaRepository<ContactNote, Long> {

  @Query(
      "SELECT n FROM ContactNote n "
          + "LEFT JOIN FETCH n.createdBy "
          + "WHERE n.contact = :contact "
          + "ORDER BY n.createdAt DESC")
  List<ContactNote> findByContactOrderByCreatedAtDesc(@Param("contact") Contact contact);

  @Query(
      "SELECT n FROM ContactNote n "
          + "JOIN FETCH n.contact "
          + "LEFT JOIN FETCH n.createdBy "
          + "WHERE n.contact.company.id = :companyId "
          + "AND n.followUpDate <= :date ORDER BY n.followUpDate, n.contact.name")
  List<ContactNote> findDueFollowUpsByCompany(
      @Param("companyId") Long companyId, @Param("date") LocalDate date);

  void deleteByContact(Contact contact);
}
