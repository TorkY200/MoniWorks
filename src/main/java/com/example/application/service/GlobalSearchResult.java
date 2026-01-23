package com.example.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Unified search result DTO for global search across all entity types. Provides a consistent
 * structure for displaying search results regardless of entity type.
 */
public record GlobalSearchResult(
    EntityType entityType,
    Long entityId,
    String title,
    String subtitle,
    String description,
    LocalDate date,
    BigDecimal amount,
    String status,
    String icon) {
  /** Supported entity types in global search. */
  public enum EntityType {
    TRANSACTION("Transaction", "vaadin:exchange"),
    CONTACT("Contact", "vaadin:users"),
    PRODUCT("Product", "vaadin:package"),
    ACCOUNT("Account", "vaadin:book"),
    SALES_INVOICE("Sales Invoice", "vaadin:invoice"),
    SUPPLIER_BILL("Supplier Bill", "vaadin:records");

    private final String displayName;
    private final String iconName;

    EntityType(String displayName, String iconName) {
      this.displayName = displayName;
      this.iconName = iconName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getIconName() {
      return iconName;
    }
  }

  /** Creates a search result for a Transaction. */
  public static GlobalSearchResult fromTransaction(
      Long id,
      String type,
      String description,
      String reference,
      LocalDate date,
      BigDecimal amount,
      String status) {
    String title = type + (reference != null ? " - " + reference : "");
    return new GlobalSearchResult(
        EntityType.TRANSACTION,
        id,
        title,
        description,
        "Ref: " + (reference != null ? reference : "N/A"),
        date,
        amount,
        status,
        EntityType.TRANSACTION.getIconName());
  }

  /** Creates a search result for a Contact. */
  public static GlobalSearchResult fromContact(
      Long id, String code, String name, String type, String email, boolean active) {
    return new GlobalSearchResult(
        EntityType.CONTACT,
        id,
        code + " - " + name,
        type,
        email,
        null,
        null,
        active ? "Active" : "Inactive",
        EntityType.CONTACT.getIconName());
  }

  /** Creates a search result for a Product. */
  public static GlobalSearchResult fromProduct(
      Long id, String code, String name, String category, BigDecimal sellPrice, boolean active) {
    return new GlobalSearchResult(
        EntityType.PRODUCT,
        id,
        code + " - " + name,
        category,
        null,
        null,
        sellPrice,
        active ? "Active" : "Inactive",
        EntityType.PRODUCT.getIconName());
  }

  /** Creates a search result for an Account. */
  public static GlobalSearchResult fromAccount(
      Long id, String code, String name, String type, boolean active) {
    return new GlobalSearchResult(
        EntityType.ACCOUNT,
        id,
        code + " - " + name,
        type,
        null,
        null,
        null,
        active ? "Active" : "Inactive",
        EntityType.ACCOUNT.getIconName());
  }

  /** Creates a search result for a Sales Invoice. */
  public static GlobalSearchResult fromSalesInvoice(
      Long id,
      String invoiceNumber,
      String contactName,
      LocalDate issueDate,
      BigDecimal total,
      String status) {
    return new GlobalSearchResult(
        EntityType.SALES_INVOICE,
        id,
        "Invoice " + invoiceNumber,
        contactName,
        null,
        issueDate,
        total,
        status,
        EntityType.SALES_INVOICE.getIconName());
  }

  /** Creates a search result for a Supplier Bill. */
  public static GlobalSearchResult fromSupplierBill(
      Long id,
      String billNumber,
      String contactName,
      LocalDate billDate,
      BigDecimal total,
      String status) {
    return new GlobalSearchResult(
        EntityType.SUPPLIER_BILL,
        id,
        "Bill " + billNumber,
        contactName,
        null,
        billDate,
        total,
        status,
        EntityType.SUPPLIER_BILL.getIconName());
  }

  /** Returns the navigation route for this result. */
  public String getNavigationRoute() {
    return switch (entityType) {
      case TRANSACTION -> "transactions";
      case CONTACT -> "contacts";
      case PRODUCT -> "products";
      case ACCOUNT -> "accounts";
      case SALES_INVOICE -> "sales-invoices";
      case SUPPLIER_BILL -> "supplier-bills";
    };
  }
}
