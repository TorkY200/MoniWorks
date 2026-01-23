package com.example.application.ui.views;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.application.domain.*;
import com.example.application.service.*;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing KPIs (Key Performance Indicators). Allows creating, editing KPIs and entering
 * values per period.
 */
@Route(value = "kpis", layout = MainLayout.class)
@PageTitle("KPIs | MoniWorks")
@PermitAll
public class KPIsView extends VerticalLayout {

  private final KPIService kpiService;
  private final CompanyContextService companyContextService;
  private final FiscalYearService fiscalYearService;

  private final Grid<KPI> kpiGrid = new Grid<>();
  private final Grid<KPIValue> valueGrid = new Grid<>();
  private final VerticalLayout detailPanel = new VerticalLayout();

  private KPI selectedKPI;
  private FiscalYear selectedFiscalYear;

  private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("#,##0.####");
  private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

  public KPIsView(
      KPIService kpiService,
      CompanyContextService companyContextService,
      FiscalYearService fiscalYearService) {
    this.kpiService = kpiService;
    this.companyContextService = companyContextService;
    this.fiscalYearService = fiscalYearService;

    addClassName("kpis-view");
    setSizeFull();

    SplitLayout splitLayout = new SplitLayout();
    splitLayout.setSizeFull();
    splitLayout.setSplitterPosition(35);

    VerticalLayout masterPanel = createMasterPanel();
    splitLayout.addToPrimary(masterPanel);
    splitLayout.addToSecondary(detailPanel);

    add(splitLayout);
    loadKPIs();
  }

  private VerticalLayout createMasterPanel() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);
    layout.setSpacing(false);

    HorizontalLayout toolbar = createToolbar();
    configureGrid();

    layout.add(toolbar, kpiGrid);
    layout.setFlexGrow(1, kpiGrid);

    return layout;
  }

  private void configureGrid() {
    kpiGrid.addClassNames("kpis-grid");
    kpiGrid.setSizeFull();
    kpiGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    kpiGrid.addColumn(KPI::getCode).setHeader("Code").setSortable(true).setAutoWidth(true);

    kpiGrid.addColumn(KPI::getName).setHeader("Name").setSortable(true).setFlexGrow(1);

    kpiGrid
        .addColumn(k -> k.getUnit() != null ? k.getUnit() : "")
        .setHeader("Unit")
        .setAutoWidth(true);

    kpiGrid
        .addColumn(k -> k.isActive() ? "Active" : "Inactive")
        .setHeader("Status")
        .setAutoWidth(true);

    kpiGrid
        .asSingleSelect()
        .addValueChangeListener(
            e -> {
              selectedKPI = e.getValue();
              updateDetailPanel();
            });
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("KPIs");

    Button addButton = new Button("Add KPI", VaadinIcon.PLUS.create());
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(e -> openKPIDialog(null));

    Button refreshButton = new Button(VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> loadKPIs());
    refreshButton.getElement().setAttribute("title", "Refresh");

    Button defaultsButton = new Button("Create Defaults", VaadinIcon.MAGIC.create());
    defaultsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    defaultsButton.addClickListener(e -> createDefaultKPIs());
    defaultsButton.getElement().setAttribute("title", "Create default KPIs");

    HorizontalLayout actions = new HorizontalLayout(addButton, defaultsButton, refreshButton);
    actions.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout toolbar = new HorizontalLayout(title, actions);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
    toolbar.setPadding(true);

    return toolbar;
  }

  private void loadKPIs() {
    Company company = companyContextService.getCurrentCompany();
    List<KPI> kpis = kpiService.findByCompany(company);
    kpiGrid.setItems(kpis);
  }

  private void updateDetailPanel() {
    detailPanel.removeAll();
    detailPanel.setPadding(true);
    detailPanel.setSpacing(true);

    if (selectedKPI == null) {
      detailPanel.add(new Span("Select a KPI to view details"));
      return;
    }

    // Header with KPI info and actions
    H3 header = new H3(selectedKPI.getCode() + " - " + selectedKPI.getName());
    if (selectedKPI.getUnit() != null && !selectedKPI.getUnit().isEmpty()) {
      header.getElement().appendChild(new Span(" (" + selectedKPI.getUnit() + ")").getElement());
    }

    Button editBtn = new Button("Edit", VaadinIcon.EDIT.create());
    editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    editBtn.addClickListener(e -> openKPIDialog(selectedKPI));

    Button addValueBtn = new Button("Add Value", VaadinIcon.PLUS.create());
    addValueBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addValueBtn.addClickListener(e -> openKPIValueDialog(null));

    HorizontalLayout headerLayout = new HorizontalLayout(header, editBtn, addValueBtn);
    headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
    headerLayout.setWidthFull();
    headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

    // Description if present
    if (selectedKPI.getDescription() != null && !selectedKPI.getDescription().isEmpty()) {
      Span description = new Span(selectedKPI.getDescription());
      description.getStyle().set("color", "var(--lumo-secondary-text-color)");
      detailPanel.add(description);
    }

    // Fiscal year selector
    Company company = companyContextService.getCurrentCompany();
    List<FiscalYear> fiscalYears = fiscalYearService.findByCompany(company);

    ComboBox<FiscalYear> fiscalYearCombo = new ComboBox<>("Fiscal Year");
    fiscalYearCombo.setItems(fiscalYears);
    fiscalYearCombo.setItemLabelGenerator(FiscalYear::getLabel);
    fiscalYearCombo.setWidth("200px");

    if (!fiscalYears.isEmpty()) {
      LocalDate today = LocalDate.now();
      FiscalYear currentFy =
          fiscalYears.stream()
              .filter(fy -> !today.isBefore(fy.getStartDate()) && !today.isAfter(fy.getEndDate()))
              .findFirst()
              .orElse(fiscalYears.get(0));
      fiscalYearCombo.setValue(currentFy);
      selectedFiscalYear = currentFy;
    }

    fiscalYearCombo.addValueChangeListener(
        e -> {
          selectedFiscalYear = e.getValue();
          loadKPIValues();
        });

    // KPI values grid
    configureValueGrid();
    loadKPIValues();

    detailPanel.add(headerLayout, fiscalYearCombo, valueGrid);
    detailPanel.setFlexGrow(1, valueGrid);
  }

  private void configureValueGrid() {
    valueGrid.removeAllColumns();
    valueGrid.addClassNames("kpi-values-grid");
    valueGrid.setSizeFull();
    valueGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    valueGrid
        .addColumn(kv -> formatPeriod(kv.getPeriod()))
        .setHeader("Period")
        .setSortable(true)
        .setAutoWidth(true);

    valueGrid
        .addColumn(kv -> formatValue(kv.getValue()))
        .setHeader("Value")
        .setSortable(true)
        .setAutoWidth(true);

    valueGrid
        .addColumn(kv -> kv.getNotes() != null ? kv.getNotes() : "")
        .setHeader("Notes")
        .setFlexGrow(1);

    valueGrid
        .addComponentColumn(this::createValueActionButtons)
        .setHeader("Actions")
        .setAutoWidth(true)
        .setFlexGrow(0);
  }

  private String formatValue(BigDecimal value) {
    if (value == null) return "";
    return VALUE_FORMAT.format(value);
  }

  private String formatPeriod(Period period) {
    if (period == null) return "";
    return PERIOD_FORMAT.format(period.getStartDate());
  }

  private HorizontalLayout createValueActionButtons(KPIValue kpiValue) {
    Button editBtn = new Button(VaadinIcon.EDIT.create());
    editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    editBtn.addClickListener(e -> openKPIValueDialog(kpiValue));
    editBtn.getElement().setAttribute("title", "Edit value");

    Button deleteBtn = new Button(VaadinIcon.TRASH.create());
    deleteBtn.addThemeVariants(
        ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
    deleteBtn.addClickListener(e -> deleteKPIValue(kpiValue));
    deleteBtn.getElement().setAttribute("title", "Delete value");

    HorizontalLayout actions = new HorizontalLayout(editBtn, deleteBtn);
    actions.setSpacing(false);
    actions.setPadding(false);
    return actions;
  }

  private void loadKPIValues() {
    if (selectedKPI == null || selectedFiscalYear == null) {
      valueGrid.setItems(List.of());
      return;
    }

    List<KPIValue> values =
        kpiService.getValuesForKPIAndFiscalYear(selectedKPI, selectedFiscalYear.getId());
    valueGrid.setItems(values);
  }

  private void openKPIDialog(KPI kpi) {
    boolean isNew = kpi == null;

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(isNew ? "Add KPI" : "Edit KPI");
    dialog.setWidth("500px");

    FormLayout form = new FormLayout();

    TextField codeField = new TextField("Code");
    codeField.setMaxLength(20);
    codeField.setRequired(true);
    codeField.setHelperText("Up to 20 characters");
    if (!isNew) {
      codeField.setValue(kpi.getCode());
      codeField.setReadOnly(true);
    }

    TextField nameField = new TextField("Name");
    nameField.setMaxLength(100);
    nameField.setRequired(true);
    if (!isNew) {
      nameField.setValue(kpi.getName());
    }

    TextField unitField = new TextField("Unit");
    unitField.setMaxLength(20);
    unitField.setHelperText("e.g., $, #, %, score");
    if (!isNew && kpi.getUnit() != null) {
      unitField.setValue(kpi.getUnit());
    }

    TextArea descriptionField = new TextArea("Description");
    descriptionField.setMaxLength(255);
    if (!isNew && kpi.getDescription() != null) {
      descriptionField.setValue(kpi.getDescription());
    }

    Checkbox activeCheckbox = new Checkbox("Active");
    activeCheckbox.setValue(isNew || kpi.isActive());

    form.add(codeField, nameField, unitField, descriptionField, activeCheckbox);
    form.setColspan(descriptionField, 2);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          if (codeField.isEmpty() || nameField.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            Company company = companyContextService.getCurrentCompany();

            if (isNew) {
              KPI newKPI =
                  kpiService.createKPI(
                      company,
                      codeField.getValue().trim().toUpperCase(),
                      nameField.getValue().trim(),
                      unitField.getValue().trim(),
                      null);
              newKPI.setDescription(
                  descriptionField.isEmpty() ? null : descriptionField.getValue().trim());
              newKPI.setActive(activeCheckbox.getValue());
              kpiService.save(newKPI, null);

              Notification.show(
                      "KPI created successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
              kpi.setName(nameField.getValue().trim());
              kpi.setUnit(unitField.isEmpty() ? null : unitField.getValue().trim());
              kpi.setDescription(
                  descriptionField.isEmpty() ? null : descriptionField.getValue().trim());
              kpi.setActive(activeCheckbox.getValue());
              kpiService.save(kpi, null);

              Notification.show(
                      "KPI updated successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            dialog.close();
            loadKPIs();
            if (selectedKPI != null) {
              updateDetailPanel();
            }
          } catch (IllegalArgumentException ex) {
            Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  private void openKPIValueDialog(KPIValue kpiValue) {
    if (selectedKPI == null) {
      Notification.show("Please select a KPI first", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    boolean isNew = kpiValue == null;

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(isNew ? "Add KPI Value" : "Edit KPI Value");
    dialog.setWidth("500px");

    FormLayout form = new FormLayout();

    // Period selector
    List<Period> periods =
        selectedFiscalYear != null
            ? fiscalYearService
                .findById(selectedFiscalYear.getId())
                .map(fy -> fy.getPeriods().stream().toList())
                .orElse(List.of())
            : List.of();

    ComboBox<Period> periodCombo = new ComboBox<>("Period");
    periodCombo.setItems(periods);
    periodCombo.setItemLabelGenerator(this::formatPeriod);
    periodCombo.setRequired(true);
    if (!isNew) {
      periodCombo.setValue(kpiValue.getPeriod());
    }

    BigDecimalField valueField = new BigDecimalField("Value");
    valueField.setRequired(true);
    if (!isNew) {
      valueField.setValue(kpiValue.getValue());
    } else {
      valueField.setValue(BigDecimal.ZERO);
    }

    TextArea notesField = new TextArea("Notes");
    notesField.setMaxLength(255);
    if (!isNew && kpiValue.getNotes() != null) {
      notesField.setValue(kpiValue.getNotes());
    }

    form.add(periodCombo, valueField, notesField);
    form.setColspan(notesField, 2);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          if (periodCombo.isEmpty() || valueField.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            kpiService.setKPIValue(
                selectedKPI,
                periodCombo.getValue(),
                valueField.getValue(),
                notesField.isEmpty() ? null : notesField.getValue().trim());

            Notification.show(
                    "KPI value saved successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadKPIValues();
          } catch (Exception ex) {
            Notification.show(
                    "Error saving KPI value: " + ex.getMessage(),
                    3000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  private void deleteKPIValue(KPIValue kpiValue) {
    kpiService.deleteKPIValue(kpiValue);
    Notification.show("KPI value deleted", 3000, Notification.Position.BOTTOM_START)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    loadKPIValues();
  }

  private void createDefaultKPIs() {
    try {
      Company company = companyContextService.getCurrentCompany();

      if (!kpiService.findByCompany(company).isEmpty()) {
        Notification.show(
                "KPIs already exist for this company. Defaults not created.",
                3000,
                Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        return;
      }

      kpiService.createDefaultKPIs(company, null);
      Notification.show("Default KPIs created", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      loadKPIs();
    } catch (Exception ex) {
      Notification.show(
              "Error creating defaults: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}
