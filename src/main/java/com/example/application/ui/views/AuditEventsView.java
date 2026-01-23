package com.example.application.ui.views;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.application.domain.AuditEvent;
import com.example.application.domain.Company;
import com.example.application.service.AuditService;
import com.example.application.service.CompanyContextService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * View for displaying the audit trail. Shows all audit events for the current company with
 * filtering and detail view. Audit events are immutable - they cannot be edited or deleted.
 */
@Route(value = "audit-trail", layout = MainLayout.class)
@PageTitle("Audit Trail | MoniWorks")
@PermitAll
public class AuditEventsView extends VerticalLayout {

  private final AuditService auditService;
  private final CompanyContextService companyContextService;

  private final Grid<AuditEvent> grid = new Grid<>();
  private final TextField eventTypeFilter = new TextField();
  private final TextField entityTypeFilter = new TextField();
  private final DateTimePicker fromDateFilter = new DateTimePicker();
  private final DateTimePicker toDateFilter = new DateTimePicker();

  private static final DateTimeFormatter DATETIME_FORMAT =
      DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

  public AuditEventsView(AuditService auditService, CompanyContextService companyContextService) {
    this.auditService = auditService;
    this.companyContextService = companyContextService;

    addClassName("audit-events-view");
    setSizeFull();

    configureGrid();
    add(createToolbar(), createFilters(), grid);

    loadAuditEvents();
  }

  private void configureGrid() {
    grid.addClassNames("audit-events-grid");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    grid.addColumn(this::formatTimestamp)
        .setHeader("Timestamp")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(AuditEvent::getEventType)
        .setHeader("Event Type")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(this::formatActor).setHeader("Actor").setSortable(true).setAutoWidth(true);

    grid.addColumn(AuditEvent::getEntityType)
        .setHeader("Entity Type")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(ae -> ae.getEntityId() != null ? ae.getEntityId().toString() : "-")
        .setHeader("Entity ID")
        .setAutoWidth(true);

    grid.addColumn(AuditEvent::getSummary).setHeader("Summary").setFlexGrow(1);

    grid.addComponentColumn(this::createViewButton).setHeader("").setAutoWidth(true).setFlexGrow(0);
  }

  private String formatTimestamp(AuditEvent event) {
    if (event.getCreatedAt() == null) return "-";
    LocalDateTime ldt = LocalDateTime.ofInstant(event.getCreatedAt(), ZoneId.systemDefault());
    return ldt.format(DATETIME_FORMAT);
  }

  private String formatActor(AuditEvent event) {
    if (event.getActor() == null) return "System";
    return event.getActor().getDisplayName();
  }

  private Button createViewButton(AuditEvent event) {
    Button viewBtn = new Button(VaadinIcon.EYE.create());
    viewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    viewBtn.addClickListener(e -> openDetailDialog(event));
    viewBtn.getElement().setAttribute("title", "View details");
    return viewBtn;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Audit Trail");

    Button refreshBtn = new Button(VaadinIcon.REFRESH.create());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshBtn.addClickListener(e -> loadAuditEvents());
    refreshBtn.getElement().setAttribute("title", "Refresh");

    HorizontalLayout left = new HorizontalLayout(title);
    left.setAlignItems(FlexComponent.Alignment.CENTER);

    HorizontalLayout right = new HorizontalLayout(refreshBtn);
    right.setAlignItems(FlexComponent.Alignment.CENTER);

    HorizontalLayout toolbar = new HorizontalLayout(left, right);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

    return toolbar;
  }

  private HorizontalLayout createFilters() {
    eventTypeFilter.setPlaceholder("Event Type");
    eventTypeFilter.setClearButtonVisible(true);
    eventTypeFilter.setWidth("150px");
    eventTypeFilter.addValueChangeListener(e -> loadAuditEvents());

    entityTypeFilter.setPlaceholder("Entity Type");
    entityTypeFilter.setClearButtonVisible(true);
    entityTypeFilter.setWidth("150px");
    entityTypeFilter.addValueChangeListener(e -> loadAuditEvents());

    fromDateFilter.setLabel("From");
    fromDateFilter.setWidth("200px");
    fromDateFilter.addValueChangeListener(e -> loadAuditEvents());

    toDateFilter.setLabel("To");
    toDateFilter.setWidth("200px");
    toDateFilter.addValueChangeListener(e -> loadAuditEvents());

    Button clearBtn =
        new Button(
            "Clear Filters",
            e -> {
              eventTypeFilter.clear();
              entityTypeFilter.clear();
              fromDateFilter.clear();
              toDateFilter.clear();
              loadAuditEvents();
            });
    clearBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    HorizontalLayout filters =
        new HorizontalLayout(
            eventTypeFilter, entityTypeFilter, fromDateFilter, toDateFilter, clearBtn);
    filters.setAlignItems(FlexComponent.Alignment.END);
    filters.setSpacing(true);

    return filters;
  }

  private void loadAuditEvents() {
    Company company = companyContextService.getCurrentCompany();

    List<AuditEvent> events;

    // Check if date range is specified
    if (fromDateFilter.getValue() != null && toDateFilter.getValue() != null) {
      Instant start = fromDateFilter.getValue().atZone(ZoneId.systemDefault()).toInstant();
      Instant end = toDateFilter.getValue().atZone(ZoneId.systemDefault()).toInstant();
      events = auditService.findByCompanyAndDateRange(company, start, end);
    } else if (eventTypeFilter.getValue() != null && !eventTypeFilter.getValue().isBlank()) {
      events = auditService.findByCompanyAndEventType(company, eventTypeFilter.getValue());
    } else {
      // Use paginated query for all events
      events =
          auditService
              .findByCompany(company, org.springframework.data.domain.Pageable.ofSize(500))
              .getContent();
    }

    // Apply client-side filters
    if (eventTypeFilter.getValue() != null && !eventTypeFilter.getValue().isBlank()) {
      String filter = eventTypeFilter.getValue().toLowerCase();
      events =
          events.stream()
              .filter(
                  e -> e.getEventType() != null && e.getEventType().toLowerCase().contains(filter))
              .toList();
    }

    if (entityTypeFilter.getValue() != null && !entityTypeFilter.getValue().isBlank()) {
      String filter = entityTypeFilter.getValue().toLowerCase();
      events =
          events.stream()
              .filter(
                  e ->
                      e.getEntityType() != null && e.getEntityType().toLowerCase().contains(filter))
              .toList();
    }

    grid.setItems(events);
  }

  private void openDetailDialog(AuditEvent event) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Audit Event Details");
    dialog.setWidth("600px");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);

    content.add(createDetailRow("Timestamp:", formatTimestamp(event)));
    content.add(createDetailRow("Event Type:", event.getEventType()));
    content.add(createDetailRow("Actor:", formatActor(event)));
    content.add(createDetailRow("Entity Type:", event.getEntityType()));
    content.add(
        createDetailRow(
            "Entity ID:", event.getEntityId() != null ? event.getEntityId().toString() : "-"));
    content.add(createDetailRow("Summary:", event.getSummary() != null ? event.getSummary() : "-"));

    // Details JSON section
    if (event.getDetailsJson() != null && !event.getDetailsJson().isBlank()) {
      H3 detailsTitle = new H3("Details");
      content.add(detailsTitle);

      // Format JSON nicely
      Pre jsonPre = new Pre();
      jsonPre.setText(formatJson(event.getDetailsJson()));
      jsonPre
          .getStyle()
          .set("background-color", "var(--lumo-contrast-5pct)")
          .set("padding", "var(--lumo-space-m)")
          .set("border-radius", "var(--lumo-border-radius-m)")
          .set("overflow-x", "auto")
          .set("white-space", "pre-wrap")
          .set("word-break", "break-word")
          .set("max-height", "300px");
      content.add(jsonPre);
    }

    Button closeBtn = new Button("Close", e -> dialog.close());

    dialog.add(content);
    dialog.getFooter().add(closeBtn);
    dialog.open();
  }

  private HorizontalLayout createDetailRow(String label, String value) {
    Span labelSpan = new Span(label);
    labelSpan.getStyle().set("font-weight", "bold").set("min-width", "100px");

    Span valueSpan = new Span(value != null ? value : "-");

    HorizontalLayout row = new HorizontalLayout(labelSpan, valueSpan);
    row.setSpacing(true);
    return row;
  }

  private String formatJson(String json) {
    if (json == null || json.isBlank()) return "";
    // Basic JSON formatting - indent nested objects
    try {
      StringBuilder formatted = new StringBuilder();
      int indent = 0;
      boolean inString = false;

      for (char c : json.toCharArray()) {
        if (c == '"'
            && (formatted.length() == 0 || formatted.charAt(formatted.length() - 1) != '\\')) {
          inString = !inString;
        }

        if (!inString) {
          if (c == '{' || c == '[') {
            formatted.append(c).append("\n").append("  ".repeat(++indent));
          } else if (c == '}' || c == ']') {
            formatted.append("\n").append("  ".repeat(--indent)).append(c);
          } else if (c == ',') {
            formatted.append(c).append("\n").append("  ".repeat(indent));
          } else if (c == ':') {
            formatted.append(c).append(" ");
          } else {
            formatted.append(c);
          }
        } else {
          formatted.append(c);
        }
      }
      return formatted.toString();
    } catch (Exception e) {
      return json; // Return original if formatting fails
    }
  }
}
