package com.example.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.PaymentRun;
import com.example.application.domain.PaymentRun.PaymentRunStatus;

@Repository
public interface PaymentRunRepository extends JpaRepository<PaymentRun, Long> {

  @Query(
      "SELECT pr FROM PaymentRun pr "
          + "LEFT JOIN FETCH pr.createdBy "
          + "WHERE pr.company = :company "
          + "ORDER BY pr.createdAt DESC")
  List<PaymentRun> findByCompanyOrderByCreatedAtDesc(@Param("company") Company company);

  @Query(
      "SELECT pr FROM PaymentRun pr "
          + "LEFT JOIN FETCH pr.createdBy "
          + "WHERE pr.company = :company AND pr.status = :status "
          + "ORDER BY pr.createdAt DESC")
  List<PaymentRun> findByCompanyAndStatusOrderByCreatedAtDesc(
      @Param("company") Company company, @Param("status") PaymentRunStatus status);
}
