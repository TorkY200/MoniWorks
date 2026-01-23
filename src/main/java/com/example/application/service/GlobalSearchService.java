package com.example.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.repository.*;

/**
 * Service for global search across all major entities. Supports free text search and query
 * expressions like: - type:invoice - status:overdue - amount>10000 - older_than:60d
 *
 * <p>Search is always company-scoped for tenant isolation.
 */
@Service
@Transactional(readOnly = true)
public class GlobalSearchService {

  private static final Logger log = LoggerFactory.getLogger(GlobalSearchService.class);
  private static final int MAX_RESULTS_PER_TYPE = 10;
  private static final int MAX_TOTAL_RESULTS = 50;

  private final TransactionRepository transactionRepository;
  private final ContactRepository contactRepository;
  private final ProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final SalesInvoiceRepository salesInvoiceRepository;
  private final SupplierBillRepository supplierBillRepository;

  public GlobalSearchService(
      TransactionRepository transactionRepository,
      ContactRepository contactRepository,
      ProductRepository productRepository,
      AccountRepository accountRepository,
      SalesInvoiceRepository salesInvoiceRepository,
      SupplierBillRepository supplierBillRepository) {
    this.transactionRepository = transactionRepository;
    this.contactRepository = contactRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.supplierBillRepository = supplierBillRepository;
  }

  /**
   * Parses a query string and extracts filter expressions and free text. Supported expressions: -
   * type:invoice, type:bill, type:transaction, type:contact, type:product, type:account -
   * status:draft, status:posted, status:issued, status:overdue, status:paid - amount>N, amount<N,
   * amount>=N, amount<=N - older_than:Nd (e.g., older_than:30d for older than 30 days) -
   * newer_than:Nd
   */
  public ParsedQuery parseQuery(String query) {
    if (query == null || query.isBlank()) {
      return new ParsedQuery("", Map.of());
    }

    Map<String, String> filters = new HashMap<>();
    StringBuilder freeText = new StringBuilder();

    // Pattern for field:value or field>value or field<value expressions
    Pattern filterPattern = Pattern.compile("(\\w+)([:<>=]+)(\\S+)");

    String[] tokens = query.trim().split("\\s+");
    for (String token : tokens) {
      Matcher matcher = filterPattern.matcher(token);
      if (matcher.matches()) {
        String field = matcher.group(1).toLowerCase();
        String operator = matcher.group(2);
        String value = matcher.group(3);
        filters.put(field + operator, value);
      } else {
        if (freeText.length() > 0) {
          freeText.append(" ");
        }
        freeText.append(token);
      }
    }

    return new ParsedQuery(freeText.toString(), filters);
  }

  /** Performs a global search across all entity types. */
  public List<GlobalSearchResult> search(Company company, String query) {
    if (company == null) {
      return List.of();
    }

    ParsedQuery parsed = parseQuery(query);
    log.debug("Global search - freeText: '{}', filters: {}", parsed.freeText(), parsed.filters());

    // Determine which entity types to search based on type: filter
    Set<GlobalSearchResult.EntityType> typesToSearch = determineTypesToSearch(parsed.filters());

    List<GlobalSearchResult> results = new ArrayList<>();

    // Search each entity type
    if (typesToSearch.contains(GlobalSearchResult.EntityType.TRANSACTION)) {
      results.addAll(searchTransactions(company, parsed));
    }
    if (typesToSearch.contains(GlobalSearchResult.EntityType.CONTACT)) {
      results.addAll(searchContacts(company, parsed));
    }
    if (typesToSearch.contains(GlobalSearchResult.EntityType.PRODUCT)) {
      results.addAll(searchProducts(company, parsed));
    }
    if (typesToSearch.contains(GlobalSearchResult.EntityType.ACCOUNT)) {
      results.addAll(searchAccounts(company, parsed));
    }
    if (typesToSearch.contains(GlobalSearchResult.EntityType.SALES_INVOICE)) {
      results.addAll(searchSalesInvoices(company, parsed));
    }
    if (typesToSearch.contains(GlobalSearchResult.EntityType.SUPPLIER_BILL)) {
      results.addAll(searchSupplierBills(company, parsed));
    }

    // Apply global amount filter if specified
    results = applyAmountFilter(results, parsed.filters());

    // Sort by relevance (date desc for dated items, then by title)
    results.sort(
        (a, b) -> {
          if (a.date() != null && b.date() != null) {
            return b.date().compareTo(a.date());
          } else if (a.date() != null) {
            return -1;
          } else if (b.date() != null) {
            return 1;
          }
          return a.title().compareToIgnoreCase(b.title());
        });

    // Limit total results
    if (results.size() > MAX_TOTAL_RESULTS) {
      results = results.subList(0, MAX_TOTAL_RESULTS);
    }

    return results;
  }

  private Set<GlobalSearchResult.EntityType> determineTypesToSearch(Map<String, String> filters) {
    String typeFilter = filters.get("type:");
    if (typeFilter == null) {
      // Search all types
      return EnumSet.allOf(GlobalSearchResult.EntityType.class);
    }

    Set<GlobalSearchResult.EntityType> types = EnumSet.noneOf(GlobalSearchResult.EntityType.class);
    switch (typeFilter.toLowerCase()) {
      case "transaction", "transactions", "txn" ->
          types.add(GlobalSearchResult.EntityType.TRANSACTION);
      case "contact", "contacts", "customer", "supplier" ->
          types.add(GlobalSearchResult.EntityType.CONTACT);
      case "product", "products", "item", "items" ->
          types.add(GlobalSearchResult.EntityType.PRODUCT);
      case "account", "accounts", "gl" -> types.add(GlobalSearchResult.EntityType.ACCOUNT);
      case "invoice", "invoices", "sales" -> types.add(GlobalSearchResult.EntityType.SALES_INVOICE);
      case "bill", "bills", "purchase" -> types.add(GlobalSearchResult.EntityType.SUPPLIER_BILL);
      default -> {
        // Unknown type filter - search all
        return EnumSet.allOf(GlobalSearchResult.EntityType.class);
      }
    }
    return types;
  }

  private List<GlobalSearchResult> searchTransactions(Company company, ParsedQuery parsed) {
    List<Transaction> transactions;
    String freeText = parsed.freeText();

    if (freeText.isBlank()) {
      transactions = transactionRepository.findByCompanyOrderByTransactionDateDesc(company);
    } else {
      // Search by description or reference
      transactions =
          transactionRepository.findByCompanyOrderByTransactionDateDesc(company).stream()
              .filter(
                  t ->
                      matchesFreeText(t.getDescription(), freeText)
                          || matchesFreeText(t.getReference(), freeText))
              .toList();
    }

    // Apply status filter
    String statusFilter = parsed.filters().get("status:");
    if (statusFilter != null) {
      transactions =
          transactions.stream().filter(t -> matchesTransactionStatus(t, statusFilter)).toList();
    }

    // Apply date filters
    transactions =
        applyDateFilters(transactions, parsed.filters(), Transaction::getTransactionDate);

    return transactions.stream()
        .limit(MAX_RESULTS_PER_TYPE)
        .map(
            t -> {
              BigDecimal amount = calculateTransactionAmount(t);
              return GlobalSearchResult.fromTransaction(
                  t.getId(),
                  t.getType().name(),
                  t.getDescription(),
                  t.getReference(),
                  t.getTransactionDate(),
                  amount,
                  t.getStatus().name());
            })
        .toList();
  }

  private BigDecimal calculateTransactionAmount(Transaction t) {
    if (t.getLines() == null || t.getLines().isEmpty()) {
      return BigDecimal.ZERO;
    }
    // Sum up debit amounts only (debits show the "size" of the transaction)
    return t.getLines().stream()
        .filter(TransactionLine::isDebit)
        .map(line -> line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<GlobalSearchResult> searchContacts(Company company, ParsedQuery parsed) {
    List<Contact> contacts;
    String freeText = parsed.freeText();

    if (freeText.isBlank()) {
      contacts = contactRepository.findByCompanyOrderByCode(company);
    } else {
      contacts = contactRepository.searchByCompany(company, freeText);
    }

    // Apply status filter (active/inactive)
    String statusFilter = parsed.filters().get("status:");
    if (statusFilter != null) {
      boolean active = "active".equalsIgnoreCase(statusFilter);
      contacts = contacts.stream().filter(c -> c.isActive() == active).toList();
    }

    return contacts.stream()
        .limit(MAX_RESULTS_PER_TYPE)
        .map(
            c ->
                GlobalSearchResult.fromContact(
                    c.getId(),
                    c.getCode(),
                    c.getName(),
                    c.getType().name(),
                    c.getEmail(),
                    c.isActive()))
        .toList();
  }

  private List<GlobalSearchResult> searchProducts(Company company, ParsedQuery parsed) {
    List<Product> products;
    String freeText = parsed.freeText();

    if (freeText.isBlank()) {
      products = productRepository.findByCompanyOrderByCode(company);
    } else {
      products = productRepository.searchByCompany(company, freeText);
    }

    // Apply status filter (active/inactive)
    String statusFilter = parsed.filters().get("status:");
    if (statusFilter != null) {
      boolean active = "active".equalsIgnoreCase(statusFilter);
      products = products.stream().filter(p -> p.isActive() == active).toList();
    }

    return products.stream()
        .limit(MAX_RESULTS_PER_TYPE)
        .map(
            p ->
                GlobalSearchResult.fromProduct(
                    p.getId(),
                    p.getCode(),
                    p.getName(),
                    p.getCategory(),
                    p.getSellPrice(),
                    p.isActive()))
        .toList();
  }

  private List<GlobalSearchResult> searchAccounts(Company company, ParsedQuery parsed) {
    List<Account> accounts;
    String freeText = parsed.freeText();

    if (freeText.isBlank()) {
      accounts = accountRepository.findByCompanyOrderByCode(company);
    } else {
      // Search by code or name
      accounts =
          accountRepository.findByCompanyOrderByCode(company).stream()
              .filter(
                  a ->
                      matchesFreeText(a.getCode(), freeText)
                          || matchesFreeText(a.getName(), freeText))
              .toList();
    }

    // Apply status filter (active/inactive)
    String statusFilter = parsed.filters().get("status:");
    if (statusFilter != null) {
      boolean active = "active".equalsIgnoreCase(statusFilter);
      accounts = accounts.stream().filter(a -> a.isActive() == active).toList();
    }

    return accounts.stream()
        .limit(MAX_RESULTS_PER_TYPE)
        .map(
            a ->
                GlobalSearchResult.fromAccount(
                    a.getId(), a.getCode(), a.getName(), a.getType().name(), a.isActive()))
        .toList();
  }

  private List<GlobalSearchResult> searchSalesInvoices(Company company, ParsedQuery parsed) {
    List<SalesInvoice> invoices;
    String freeText = parsed.freeText();

    if (freeText.isBlank()) {
      invoices = salesInvoiceRepository.findByCompanyOrderByIssueDateDescInvoiceNumberDesc(company);
    } else {
      invoices = salesInvoiceRepository.searchByCompany(company, freeText);
    }

    // Apply status filter
    String statusFilter = parsed.filters().get("status:");
    if (statusFilter != null) {
      invoices = invoices.stream().filter(i -> matchesInvoiceStatus(i, statusFilter)).toList();
    }

    // Apply date filters
    invoices = applyDateFilters(invoices, parsed.filters(), SalesInvoice::getIssueDate);

    return invoices.stream()
        .limit(MAX_RESULTS_PER_TYPE)
        .map(
            i ->
                GlobalSearchResult.fromSalesInvoice(
                    i.getId(),
                    i.getInvoiceNumber(),
                    i.getContact() != null ? i.getContact().getName() : "Unknown",
                    i.getIssueDate(),
                    i.getTotal(),
                    getInvoiceStatusDisplay(i)))
        .toList();
  }

  private List<GlobalSearchResult> searchSupplierBills(Company company, ParsedQuery parsed) {
    List<SupplierBill> bills;
    String freeText = parsed.freeText();

    if (freeText.isBlank()) {
      bills = supplierBillRepository.findByCompanyOrderByBillDateDescBillNumberDesc(company);
    } else {
      bills = supplierBillRepository.searchByCompany(company, freeText);
    }

    // Apply status filter
    String statusFilter = parsed.filters().get("status:");
    if (statusFilter != null) {
      bills = bills.stream().filter(b -> matchesBillStatus(b, statusFilter)).toList();
    }

    // Apply date filters
    bills = applyDateFilters(bills, parsed.filters(), SupplierBill::getBillDate);

    return bills.stream()
        .limit(MAX_RESULTS_PER_TYPE)
        .map(
            b ->
                GlobalSearchResult.fromSupplierBill(
                    b.getId(),
                    b.getBillNumber(),
                    b.getContact() != null ? b.getContact().getName() : "Unknown",
                    b.getBillDate(),
                    b.getTotal(),
                    getBillStatusDisplay(b)))
        .toList();
  }

  private boolean matchesFreeText(String field, String searchText) {
    if (field == null || searchText == null || searchText.isBlank()) {
      return searchText == null || searchText.isBlank();
    }
    return field.toLowerCase().contains(searchText.toLowerCase());
  }

  private boolean matchesTransactionStatus(Transaction t, String statusFilter) {
    return switch (statusFilter.toLowerCase()) {
      case "draft" -> t.isDraft();
      case "posted" -> t.isPosted();
      default -> true;
    };
  }

  private boolean matchesInvoiceStatus(SalesInvoice i, String statusFilter) {
    return switch (statusFilter.toLowerCase()) {
      case "draft" -> i.isDraft();
      case "issued" -> i.isIssued();
      case "void" -> i.isVoid();
      case "overdue" -> i.isOverdue();
      case "paid" -> i.isPaid();
      case "unpaid" -> !i.isPaid() && !i.isVoid();
      default -> true;
    };
  }

  private boolean matchesBillStatus(SupplierBill b, String statusFilter) {
    return switch (statusFilter.toLowerCase()) {
      case "draft" -> b.isDraft();
      case "posted" -> b.isPosted();
      case "void" -> b.isVoid();
      case "overdue" -> b.isOverdue();
      case "paid" -> b.isPaid();
      case "unpaid" -> !b.isPaid() && !b.isVoid();
      default -> true;
    };
  }

  private String getInvoiceStatusDisplay(SalesInvoice i) {
    if (i.isVoid()) return "VOID";
    if (i.isDraft()) return "DRAFT";
    if (i.isPaid()) return "PAID";
    if (i.isOverdue()) return "OVERDUE";
    return "ISSUED";
  }

  private String getBillStatusDisplay(SupplierBill b) {
    if (b.isVoid()) return "VOID";
    if (b.isDraft()) return "DRAFT";
    if (b.isPaid()) return "PAID";
    if (b.isOverdue()) return "OVERDUE";
    return "POSTED";
  }

  private <T> List<T> applyDateFilters(
      List<T> items,
      Map<String, String> filters,
      java.util.function.Function<T, LocalDate> dateExtractor) {
    LocalDate now = LocalDate.now();

    // older_than:Nd
    String olderThan = filters.get("older_than:");
    if (olderThan != null && olderThan.endsWith("d")) {
      try {
        int days = Integer.parseInt(olderThan.substring(0, olderThan.length() - 1));
        LocalDate cutoff = now.minusDays(days);
        items =
            items.stream()
                .filter(
                    item -> {
                      LocalDate date = dateExtractor.apply(item);
                      return date != null && date.isBefore(cutoff);
                    })
                .toList();
      } catch (NumberFormatException e) {
        log.debug("Invalid older_than format: {}", olderThan);
      }
    }

    // newer_than:Nd
    String newerThan = filters.get("newer_than:");
    if (newerThan != null && newerThan.endsWith("d")) {
      try {
        int days = Integer.parseInt(newerThan.substring(0, newerThan.length() - 1));
        LocalDate cutoff = now.minusDays(days);
        items =
            items.stream()
                .filter(
                    item -> {
                      LocalDate date = dateExtractor.apply(item);
                      return date != null && date.isAfter(cutoff);
                    })
                .toList();
      } catch (NumberFormatException e) {
        log.debug("Invalid newer_than format: {}", newerThan);
      }
    }

    return items;
  }

  private List<GlobalSearchResult> applyAmountFilter(
      List<GlobalSearchResult> results, Map<String, String> filters) {
    // Check for amount comparisons
    for (Map.Entry<String, String> entry : filters.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("amount")) {
        String operator = key.substring(6); // ">" or "<" or ">=" or "<="
        try {
          BigDecimal threshold = new BigDecimal(entry.getValue());
          results =
              results.stream()
                  .filter(
                      r -> {
                        if (r.amount() == null) return false;
                        return switch (operator) {
                          case ">" -> r.amount().compareTo(threshold) > 0;
                          case "<" -> r.amount().compareTo(threshold) < 0;
                          case ">=" -> r.amount().compareTo(threshold) >= 0;
                          case "<=" -> r.amount().compareTo(threshold) <= 0;
                          case ":" -> r.amount().compareTo(threshold) == 0;
                          default -> true;
                        };
                      })
                  .toList();
        } catch (NumberFormatException e) {
          log.debug("Invalid amount format: {}", entry.getValue());
        }
      }
    }
    return results;
  }

  /** Parsed query containing free text and field filters. */
  public record ParsedQuery(String freeText, Map<String, String> filters) {}
}
