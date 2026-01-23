package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Account;
import com.example.application.domain.BankFeedItem;
import com.example.application.domain.BankFeedItem.FeedItemStatus;
import com.example.application.domain.BankStatementImport;
import com.example.application.domain.Company;

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

  // Reconciliation status aggregate queries
  @Query(
      "SELECT COUNT(b) FROM BankFeedItem b "
          + "WHERE b.bankStatementImport.account = :account AND b.status = :status")
  long countByAccountAndStatus(
      @Param("account") Account account, @Param("status") FeedItemStatus status);

  @Query(
      "SELECT COALESCE(SUM(b.amount), 0) FROM BankFeedItem b "
          + "WHERE b.bankStatementImport.account = :account AND b.status = :status")
  java.math.BigDecimal sumAmountByAccountAndStatus(
      @Param("account") Account account, @Param("status") FeedItemStatus status);

  @Query(
      "SELECT b FROM BankFeedItem b "
          + "WHERE b.bankStatementImport.account = :account AND b.status = 'NEW' "
          + "ORDER BY b.postedDate ASC")
  List<BankFeedItem> findOldestUnmatchedByAccount(
      @Param("account") Account account, org.springframework.data.domain.Pageable pageable);

  @Query(
      "SELECT MIN(b.postedDate) FROM BankFeedItem b "
          + "WHERE b.bankStatementImport.account = :account AND b.status = 'NEW'")
  java.time.LocalDate findOldestUnmatchedDateByAccount(@Param("account") Account account);

  @Query(
      "SELECT COUNT(b) FROM BankFeedItem b "
          + "WHERE b.bankStatementImport.company = :company AND b.status = :status")
  long countByCompanyAndStatus(
      @Param("company") Company company, @Param("status") FeedItemStatus status);

  @Query(
      "SELECT COALESCE(SUM(b.amount), 0) FROM BankFeedItem b "
          + "WHERE b.bankStatementImport.company = :company AND b.status = :status")
  java.math.BigDecimal sumAmountByCompanyAndStatus(
      @Param("company") Company company, @Param("status") FeedItemStatus status);
}
