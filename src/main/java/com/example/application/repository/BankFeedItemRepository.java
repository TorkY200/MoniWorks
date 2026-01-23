package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.BankFeedItem;
import com.example.application.domain.BankFeedItem.FeedItemStatus;
import com.example.application.domain.BankStatementImport;

@Repository
public interface BankFeedItemRepository extends JpaRepository<BankFeedItem, Long> {

  List<BankFeedItem> findByBankStatementImportOrderByPostedDateDesc(
      BankStatementImport bankStatementImport);

  List<BankFeedItem> findByBankStatementImportAndStatusOrderByPostedDateDesc(
      BankStatementImport bankStatementImport, FeedItemStatus status);

  @Query(
      "SELECT b FROM BankFeedItem b WHERE b.bankStatementImport = :bsi "
          + "AND b.status = 'NEW' ORDER BY b.postedDate DESC")
  List<BankFeedItem> findUnmatchedByImport(@Param("bsi") BankStatementImport bankStatementImport);

  @Query(
      "SELECT b FROM BankFeedItem b WHERE b.bankStatementImport.account.id = :accountId "
          + "AND b.status = 'NEW' ORDER BY b.postedDate DESC")
  List<BankFeedItem> findUnmatchedByAccountId(@Param("accountId") Long accountId);

  Optional<BankFeedItem> findByBankStatementImportAndFitId(
      BankStatementImport bankStatementImport, String fitId);

  boolean existsByBankStatementImportAndFitId(
      BankStatementImport bankStatementImport, String fitId);

  @Query(
      "SELECT COUNT(b) FROM BankFeedItem b WHERE b.bankStatementImport = :bsi AND b.status = :status")
  long countByImportAndStatus(
      @Param("bsi") BankStatementImport bankStatementImport,
      @Param("status") FeedItemStatus status);
}
