package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Contact;
import com.example.application.domain.ContactPerson;

@Repository
public interface ContactPersonRepository extends JpaRepository<ContactPerson, Long> {

  List<ContactPerson> findByContactOrderByPrimaryDescNameAsc(Contact contact);

  Optional<ContactPerson> findByContactAndPrimaryTrue(Contact contact);

  void deleteByContact(Contact contact);
}
