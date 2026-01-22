package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SalesInvoiceRepository salesInvoiceRepository;
    @Mock
    private SupplierBillRepository supplierBillRepository;

    private GlobalSearchService globalSearchService;
    private Company company;

    @BeforeEach
    void setUp() {
        globalSearchService = new GlobalSearchService(
            transactionRepository,
            contactRepository,
            productRepository,
            accountRepository,
            salesInvoiceRepository,
            supplierBillRepository
        );
        company = new Company("Test Co", "NZ", "NZD", LocalDate.of(2024, 1, 1));
        company.setId(1L);
    }

    @Test
    void parseQuery_emptyQuery_returnsEmptyResult() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("");
        assertEquals("", result.freeText());
        assertTrue(result.filters().isEmpty());
    }

    @Test
    void parseQuery_freeTextOnly_extractsFreeText() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("acme corp");
        assertEquals("acme corp", result.freeText());
        assertTrue(result.filters().isEmpty());
    }

    @Test
    void parseQuery_typeFilter_extractsFilter() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("type:invoice");
        assertEquals("", result.freeText());
        assertEquals("invoice", result.filters().get("type:"));
    }

    @Test
    void parseQuery_statusFilter_extractsFilter() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("status:overdue");
        assertEquals("", result.freeText());
        assertEquals("overdue", result.filters().get("status:"));
    }

    @Test
    void parseQuery_amountGreaterThan_extractsFilter() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("amount>1000");
        assertEquals("", result.freeText());
        assertEquals("1000", result.filters().get("amount>"));
    }

    @Test
    void parseQuery_olderThan_extractsFilter() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("older_than:30d");
        assertEquals("", result.freeText());
        assertEquals("30d", result.filters().get("older_than:"));
    }

    @Test
    void parseQuery_mixedQuery_extractsBoth() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("type:invoice status:overdue acme");
        assertEquals("acme", result.freeText());
        assertEquals("invoice", result.filters().get("type:"));
        assertEquals("overdue", result.filters().get("status:"));
    }

    @Test
    void parseQuery_complexQuery_extractsAll() {
        GlobalSearchService.ParsedQuery result = globalSearchService.parseQuery("type:bill amount>500 older_than:60d supplier");
        assertEquals("supplier", result.freeText());
        assertEquals("bill", result.filters().get("type:"));
        assertEquals("500", result.filters().get("amount>"));
        assertEquals("60d", result.filters().get("older_than:"));
    }

    @Test
    void search_nullCompany_returnsEmptyList() {
        List<GlobalSearchResult> results = globalSearchService.search(null, "test");
        assertTrue(results.isEmpty());
    }

    @Test
    void search_withTypeFilter_searchesOnlyMatchingType() {
        Contact contact = new Contact(company, "ACME01", "Acme Corp", Contact.ContactType.CUSTOMER);
        contact.setId(1L);
        when(contactRepository.findByCompanyOrderByCode(company)).thenReturn(List.of(contact));

        List<GlobalSearchResult> results = globalSearchService.search(company, "type:contact");

        assertEquals(1, results.size());
        assertEquals(GlobalSearchResult.EntityType.CONTACT, results.get(0).entityType());
        verify(transactionRepository, never()).findByCompanyOrderByTransactionDateDesc(any());
        verify(salesInvoiceRepository, never()).findByCompanyOrderByIssueDateDescInvoiceNumberDesc(any());
    }

    @Test
    void search_withFreeText_filtersResults() {
        Contact contact1 = new Contact(company, "ACME01", "Acme Corp", Contact.ContactType.CUSTOMER);
        contact1.setId(1L);
        Contact contact2 = new Contact(company, "OTHER01", "Other Inc", Contact.ContactType.CUSTOMER);
        contact2.setId(2L);

        when(contactRepository.searchByCompany(company, "acme")).thenReturn(List.of(contact1));
        when(transactionRepository.findByCompanyOrderByTransactionDateDesc(company)).thenReturn(List.of());
        when(productRepository.searchByCompany(company, "acme")).thenReturn(List.of());
        when(accountRepository.findByCompanyOrderByCode(company)).thenReturn(List.of());
        when(salesInvoiceRepository.searchByCompany(company, "acme")).thenReturn(List.of());
        when(supplierBillRepository.searchByCompany(company, "acme")).thenReturn(List.of());

        List<GlobalSearchResult> results = globalSearchService.search(company, "acme");

        assertEquals(1, results.size());
        assertEquals("ACME01 - Acme Corp", results.get(0).title());
    }

    @Test
    void search_withStatusFilter_filtersContactsByActiveStatus() {
        Contact activeContact = new Contact(company, "ACT01", "Active Co", Contact.ContactType.CUSTOMER);
        activeContact.setId(1L);
        activeContact.setActive(true);

        Contact inactiveContact = new Contact(company, "INA01", "Inactive Co", Contact.ContactType.CUSTOMER);
        inactiveContact.setId(2L);
        inactiveContact.setActive(false);

        when(contactRepository.findByCompanyOrderByCode(company)).thenReturn(List.of(activeContact, inactiveContact));
        when(transactionRepository.findByCompanyOrderByTransactionDateDesc(company)).thenReturn(List.of());
        when(productRepository.findByCompanyOrderByCode(company)).thenReturn(List.of());
        when(accountRepository.findByCompanyOrderByCode(company)).thenReturn(List.of());
        when(salesInvoiceRepository.findByCompanyOrderByIssueDateDescInvoiceNumberDesc(company)).thenReturn(List.of());
        when(supplierBillRepository.findByCompanyOrderByBillDateDescBillNumberDesc(company)).thenReturn(List.of());

        List<GlobalSearchResult> results = globalSearchService.search(company, "status:active");

        // Filter to only contact results
        List<GlobalSearchResult> contactResults = results.stream()
            .filter(r -> r.entityType() == GlobalSearchResult.EntityType.CONTACT)
            .toList();
        assertEquals(1, contactResults.size());
        assertEquals("ACT01 - Active Co", contactResults.get(0).title());
    }
}
