package com.example.application.ui.views;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.application.domain.Company;
import com.example.application.service.CompanyContextService;
import com.example.application.service.GlobalSearchResult;
import com.example.application.service.GlobalSearchService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.PermitAll;

/** Global search results view. Displays search results across all entity types with navigation. */
@Route(value = "search", layout = MainLayout.class)
@PageTitle("Search | MoniWorks")
@PermitAll
public class GlobalSearchView extends VerticalLayout implements BeforeEnterObserver {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd MMM yyyy");
  private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

  private final GlobalSearchService globalSearchService;
  private final CompanyContextService companyContextService;

  private final TextField searchField;
  private final Grid<GlobalSearchResult> resultsGrid;
  private final Div helpSection;
  private final H2 resultsHeader;
  private final Span resultsCount;

  private String currentQuery = "";

  public GlobalSearchView(
      GlobalSearchService globalSearchService, CompanyContextService companyContextService) {
    this.globalSearchService = globalSearchService;
    this.companyContextService = companyContextService;

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    // Results header - create first so searchField listener can reference it
    HorizontalLayout headerLayout = new HorizontalLayout();
    headerLayout.setWidthFull();
    headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);

    resultsHeader = new H2("Search Results");
    resultsHeader.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
    resultsHeader.setVisible(false);

    resultsCount = new Span();
    resultsCount.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

    headerLayout.add(resultsHeader, resultsCount);
    headerLayout.expand(resultsHeader);

    // Results grid - create before searchField listener
    resultsGrid = createResultsGrid();
    resultsGrid.setVisible(false);

    // Help section - create before searchField listener
    helpSection = createHelpSection();

    // Search input - create last so listener can use initialized fields
    searchField = new TextField();
    searchField.setPlaceholder(
        "Search transactions, contacts, invoices, bills, products, accounts...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setWidthFull();
    searchField.addKeyDownListener(Key.ENTER, e -> performSearch());
    searchField.setClearButtonVisible(true);
    searchField.addValueChangeListener(
        e -> {
          if (e.getValue().isEmpty()) {
            resultsGrid.setItems(List.of());
            resultsHeader.setVisible(false);
            helpSection.setVisible(true);
          }
        });

    add(searchField, headerLayout, resultsGrid, helpSection);
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    QueryParameters params = event.getLocation().getQueryParameters();
    Map<String, List<String>> parameters = params.getParameters();

    if (parameters.containsKey("q")) {
      List<String> queryValues = parameters.get("q");
      if (!queryValues.isEmpty() && !queryValues.get(0).isBlank()) {
        currentQuery = queryValues.get(0);
        searchField.setValue(currentQuery);
        performSearch();
      }
    }
  }

  private Grid<GlobalSearchResult> createResultsGrid() {
    Grid<GlobalSearchResult> grid = new Grid<>();
    grid.setWidthFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    // Type column with icon
    grid.addColumn(
            new ComponentRenderer<>(
                result -> {
                  HorizontalLayout layout = new HorizontalLayout();
                  layout.setAlignItems(FlexComponent.Alignment.CENTER);
                  layout.setSpacing(true);

                  Icon icon = getIconForType(result.entityType());
                  icon.addClassNames(LumoUtility.TextColor.SECONDARY);

                  Span typeLabel = new Span(result.entityType().getDisplayName());
                  typeLabel.addClassNames(
                      LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

                  layout.add(icon, typeLabel);
                  return layout;
                }))
        .setHeader("Type")
        .setWidth("150px")
        .setFlexGrow(0);

    // Title and subtitle column
    grid.addColumn(
            new ComponentRenderer<>(
                result -> {
                  VerticalLayout layout = new VerticalLayout();
                  layout.setSpacing(false);
                  layout.setPadding(false);

                  Span title = new Span(result.title());
                  title.addClassNames(LumoUtility.FontWeight.SEMIBOLD);

                  if (result.subtitle() != null) {
                    Span subtitle = new Span(result.subtitle());
                    subtitle.addClassNames(
                        LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
                    layout.add(title, subtitle);
                  } else {
                    layout.add(title);
                  }

                  return layout;
                }))
        .setHeader("Details")
        .setFlexGrow(2);

    // Date column
    grid.addColumn(result -> result.date() != null ? result.date().format(DATE_FORMATTER) : "")
        .setHeader("Date")
        .setWidth("120px")
        .setFlexGrow(0);

    // Amount column
    grid.addColumn(result -> result.amount() != null ? CURRENCY_FORMAT.format(result.amount()) : "")
        .setHeader("Amount")
        .setWidth("120px")
        .setFlexGrow(0)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    // Status column
    grid.addColumn(
            new ComponentRenderer<>(
                result -> {
                  if (result.status() == null) {
                    return new Span("");
                  }
                  Span badge = new Span(result.status());
                  badge.getElement().getThemeList().add("badge");
                  switch (result.status().toUpperCase()) {
                    case "POSTED", "ISSUED", "PAID", "ACTIVE" ->
                        badge.getElement().getThemeList().add("success");
                    case "DRAFT" -> badge.getElement().getThemeList().add("contrast");
                    case "OVERDUE" -> badge.getElement().getThemeList().add("error");
                    case "VOID", "INACTIVE" -> badge.getElement().getThemeList().add("contrast");
                  }
                  return badge;
                }))
        .setHeader("Status")
        .setWidth("100px")
        .setFlexGrow(0);

    // Row click handler for navigation
    grid.addItemClickListener(event -> navigateToResult(event.getItem()));

    return grid;
  }

  private Icon getIconForType(GlobalSearchResult.EntityType type) {
    return switch (type) {
      case TRANSACTION -> VaadinIcon.EXCHANGE.create();
      case CONTACT -> VaadinIcon.USERS.create();
      case PRODUCT -> VaadinIcon.PACKAGE.create();
      case ACCOUNT -> VaadinIcon.BOOK.create();
      case SALES_INVOICE -> VaadinIcon.INVOICE.create();
      case SUPPLIER_BILL -> VaadinIcon.RECORDS.create();
    };
  }

  private Div createHelpSection() {
    Div help = new Div();
    help.addClassNames(
        LumoUtility.Padding.LARGE,
        LumoUtility.Background.CONTRAST_5,
        LumoUtility.BorderRadius.MEDIUM);

    H2 title = new H2("Search Tips");
    title.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.Margin.Bottom.MEDIUM);

    VerticalLayout tips = new VerticalLayout();
    tips.setSpacing(false);
    tips.setPadding(false);

    tips.add(createTip("Free text search", "Just type to search across all fields"));
    tips.add(
        createTip(
            "type:invoice",
            "Search only invoices (also: bill, transaction, contact, product, account)"));
    tips.add(
        createTip(
            "status:overdue", "Filter by status (draft, posted, issued, overdue, paid, unpaid)"));
    tips.add(createTip("amount>1000", "Filter by amount (use >, <, >=, <=)"));
    tips.add(createTip("older_than:30d", "Items older than 30 days"));
    tips.add(createTip("newer_than:7d", "Items from the last 7 days"));

    Paragraph example = new Paragraph("Example: type:invoice status:overdue amount>500 Acme");
    example.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.TextColor.SECONDARY);
    example.getStyle().set("font-style", "italic");

    help.add(title, tips, example);
    return help;
  }

  private Component createTip(String code, String description) {
    HorizontalLayout layout = new HorizontalLayout();
    layout.setAlignItems(FlexComponent.Alignment.BASELINE);
    layout.setSpacing(true);

    Span codeSpan = new Span(code);
    codeSpan
        .getStyle()
        .set("font-family", "monospace")
        .set("background-color", "var(--lumo-contrast-10pct)")
        .set("padding", "2px 6px")
        .set("border-radius", "4px");

    Span descSpan = new Span(" - " + description);
    descSpan.addClassNames(LumoUtility.TextColor.SECONDARY);

    layout.add(codeSpan, descSpan);
    return layout;
  }

  private void performSearch() {
    String query = searchField.getValue().trim();
    if (query.isEmpty()) {
      resultsGrid.setItems(List.of());
      resultsGrid.setVisible(false);
      resultsHeader.setVisible(false);
      helpSection.setVisible(true);
      return;
    }

    currentQuery = query;
    Company company = companyContextService.getCurrentCompany();
    if (company == null) {
      resultsGrid.setItems(List.of());
      return;
    }

    List<GlobalSearchResult> results = globalSearchService.search(company, query);

    resultsGrid.setItems(results);
    resultsGrid.setVisible(true);
    resultsHeader.setVisible(true);
    helpSection.setVisible(false);
    resultsCount.setText(results.size() + " result" + (results.size() != 1 ? "s" : ""));

    // Update URL with search query
    UI.getCurrent()
        .getPage()
        .getHistory()
        .pushState(
            null,
            "search?q="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
  }

  private void navigateToResult(GlobalSearchResult result) {
    String route = result.getNavigationRoute();
    if (route != null) {
      // Navigate to the entity's view
      // The view should support a query parameter to select the item
      UI.getCurrent().navigate(route + "?id=" + result.entityId());
    }
  }
}
