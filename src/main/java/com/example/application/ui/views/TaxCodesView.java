package com.example.application.ui.views;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

import com.example.application.domain.Company;
import com.example.application.domain.TaxCode;
import com.example.application.domain.TaxCode.TaxType;
import com.example.application.service.CompanyContextService;
import com.example.application.service.TaxCodeService;
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
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing Tax Codes (GST/VAT codes). Allows creating, editing, and deactivating tax
 * codes. Tax codes define rates and types for tax calculations on transactions.
 */
@Route(value = "tax-codes", layout = MainLayout.class)
@PageTitle("Tax Codes | MoniWorks")
@PermitAll
public class TaxCodesView extends VerticalLayout {

  private final TaxCodeService taxCodeService;
  private final CompanyContextService companyContextService;

  private final Grid<TaxCode> grid = new Grid<>();
  private final TextField searchField = new TextField();

  private static final DecimalFormat RATE_FORMAT = new DecimalFormat("0.##%");

  public TaxCodesView(TaxCodeService taxCodeService, CompanyContextService companyContextService) {
    this.taxCodeService = taxCodeService;
    this.companyContextService = companyContextService;

    addClassName("tax-codes-view");
    setSizeFull();

    configureGrid();
    add(createToolbar(), grid);

    loadTaxCodes();
  }

  private void configureGrid() {
    grid.addClassNames("tax-codes-grid");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    grid.addColumn(TaxCode::getCode)
        .setHeader("Code")
        .setSortable(true)
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(TaxCode::getName).setHeader("Name").setSortable(true).setFlexGrow(1);

    grid.addColumn(tc -> formatRate(tc.getRate()))
        .setHeader("Rate")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(tc -> tc.getType().name().replace("_", " "))
        .setHeader("Type")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(TaxCode::getReportBox).setHeader("Report Box").setAutoWidth(true);

    grid.addColumn(tc -> tc.isActive() ? "Active" : "Inactive")
        .setHeader("Status")
        .setAutoWidth(true);

    grid.addComponentColumn(this::createActionButtons)
        .setHeader("Actions")
        .setAutoWidth(true)
        .setFlexGrow(0);
  }

  private String formatRate(BigDecimal rate) {
    if (rate == null) return "";
    return RATE_FORMAT.format(rate);
  }

  private HorizontalLayout createActionButtons(TaxCode taxCode) {
    Button editBtn = new Button(VaadinIcon.EDIT.create());
    editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    editBtn.addClickListener(e -> openTaxCodeDialog(taxCode));
    editBtn.getElement().setAttribute("title", "Edit tax code");

    HorizontalLayout actions = new HorizontalLayout(editBtn);

    if (taxCode.isActive()) {
      Button deactivateBtn = new Button(VaadinIcon.BAN.create());
      deactivateBtn.addThemeVariants(
          ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
      deactivateBtn.addClickListener(e -> deactivateTaxCode(taxCode));
      deactivateBtn.getElement().setAttribute("title", "Deactivate tax code");
      actions.add(deactivateBtn);
    } else {
      Button activateBtn = new Button(VaadinIcon.CHECK.create());
      activateBtn.addThemeVariants(
          ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
      activateBtn.addClickListener(e -> activateTaxCode(taxCode));
      activateBtn.getElement().setAttribute("title", "Activate tax code");
      actions.add(activateBtn);
    }

    actions.setSpacing(false);
    actions.setPadding(false);
    return actions;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Tax Codes");

    searchField.setPlaceholder("Search tax codes...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setClearButtonVisible(true);
    searchField.addValueChangeListener(e -> filterTaxCodes(e.getValue()));
    searchField.setWidth("250px");

    Button addButton = new Button("Add Tax Code", VaadinIcon.PLUS.create());
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(e -> openTaxCodeDialog(null));

    Button refreshButton = new Button(VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> loadTaxCodes());
    refreshButton.getElement().setAttribute("title", "Refresh");

    Button defaultsButton = new Button("Create Defaults", VaadinIcon.MAGIC.create());
    defaultsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    defaultsButton.addClickListener(e -> createDefaultTaxCodes());
    defaultsButton.getElement().setAttribute("title", "Create default NZ GST tax codes");

    HorizontalLayout actions =
        new HorizontalLayout(searchField, addButton, defaultsButton, refreshButton);
    actions.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout toolbar = new HorizontalLayout(title, actions);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

    return toolbar;
  }

  private void loadTaxCodes() {
    Company company = companyContextService.getCurrentCompany();
    List<TaxCode> taxCodes = taxCodeService.findByCompany(company);
    grid.setItems(taxCodes);
  }

  private void filterTaxCodes(String searchTerm) {
    Company company = companyContextService.getCurrentCompany();
    List<TaxCode> taxCodes = taxCodeService.findByCompany(company);

    if (searchTerm == null || searchTerm.isBlank()) {
      grid.setItems(taxCodes);
    } else {
      String lowerSearch = searchTerm.toLowerCase();
      List<TaxCode> filtered =
          taxCodes.stream()
              .filter(
                  tc ->
                      tc.getCode().toLowerCase().contains(lowerSearch)
                          || tc.getName().toLowerCase().contains(lowerSearch)
                          || tc.getType().name().toLowerCase().contains(lowerSearch))
              .toList();
      grid.setItems(filtered);
    }
  }

  private void openTaxCodeDialog(TaxCode taxCode) {
    boolean isNew = taxCode == null;

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(isNew ? "Add Tax Code" : "Edit Tax Code");
    dialog.setWidth("500px");

    FormLayout form = new FormLayout();

    TextField codeField = new TextField("Code");
    codeField.setMaxLength(10);
    codeField.setRequired(true);
    codeField.setHelperText("Up to 10 characters");
    if (!isNew) {
      codeField.setValue(taxCode.getCode());
      codeField.setReadOnly(true); // Can't change code after creation
    }

    TextField nameField = new TextField("Name");
    nameField.setMaxLength(50);
    nameField.setRequired(true);
    if (!isNew) {
      nameField.setValue(taxCode.getName());
    }

    BigDecimalField rateField = new BigDecimalField("Rate (decimal)");
    rateField.setHelperText("e.g., 0.15 for 15%");
    rateField.setWidth("100%");
    if (!isNew) {
      rateField.setValue(taxCode.getRate());
    } else {
      rateField.setValue(BigDecimal.ZERO);
    }

    ComboBox<TaxType> typeCombo = new ComboBox<>("Type");
    typeCombo.setItems(TaxType.values());
    typeCombo.setItemLabelGenerator(t -> t.name().replace("_", " "));
    typeCombo.setRequired(true);
    if (!isNew) {
      typeCombo.setValue(taxCode.getType());
    } else {
      typeCombo.setValue(TaxType.STANDARD);
    }

    TextField reportBoxField = new TextField("Report Box");
    reportBoxField.setMaxLength(20);
    reportBoxField.setHelperText("GST return box mapping (e.g., Box1, Box5)");
    if (!isNew && taxCode.getReportBox() != null) {
      reportBoxField.setValue(taxCode.getReportBox());
    }

    Checkbox activeCheckbox = new Checkbox("Active");
    activeCheckbox.setValue(isNew || taxCode.isActive());

    form.add(codeField, nameField, rateField, typeCombo, reportBoxField, activeCheckbox);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          // Validate required fields
          if (codeField.isEmpty() || nameField.isEmpty() || typeCombo.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          BigDecimal rate = rateField.getValue();
          if (rate == null) {
            rate = BigDecimal.ZERO;
          }

          // Validate rate range (0 to 1 for percentages stored as decimal)
          if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            Notification.show(
                    "Rate must be between 0 and 1 (e.g., 0.15 for 15%)",
                    3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            Company company = companyContextService.getCurrentCompany();

            if (isNew) {
              TaxCode newTaxCode =
                  taxCodeService.createTaxCode(
                      company,
                      codeField.getValue().trim().toUpperCase(),
                      nameField.getValue().trim(),
                      rate,
                      typeCombo.getValue());
              newTaxCode.setReportBox(
                  reportBoxField.getValue().isBlank() ? null : reportBoxField.getValue().trim());
              newTaxCode.setActive(activeCheckbox.getValue());
              taxCodeService.save(newTaxCode);

              Notification.show(
                      "Tax code created successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
              taxCode.setName(nameField.getValue().trim());
              taxCode.setRate(rate);
              taxCode.setType(typeCombo.getValue());
              taxCode.setReportBox(
                  reportBoxField.getValue().isBlank() ? null : reportBoxField.getValue().trim());
              taxCode.setActive(activeCheckbox.getValue());
              taxCodeService.save(taxCode);

              Notification.show(
                      "Tax code updated successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            dialog.close();
            loadTaxCodes();
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

  private void deactivateTaxCode(TaxCode taxCode) {
    taxCodeService.deactivate(taxCode);
    Notification.show("Tax code deactivated", 3000, Notification.Position.BOTTOM_START)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    loadTaxCodes();
  }

  private void activateTaxCode(TaxCode taxCode) {
    taxCode.setActive(true);
    taxCodeService.save(taxCode);
    Notification.show("Tax code activated", 3000, Notification.Position.BOTTOM_START)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    loadTaxCodes();
  }

  private void createDefaultTaxCodes() {
    try {
      Company company = companyContextService.getCurrentCompany();

      // Check if defaults already exist
      if (!taxCodeService.findByCompany(company).isEmpty()) {
        Notification.show(
                "Tax codes already exist for this company. Defaults not created.",
                3000,
                Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        return;
      }

      taxCodeService.createDefaultTaxCodes(company);
      Notification.show(
              "Default NZ GST tax codes created", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      loadTaxCodes();
    } catch (Exception ex) {
      Notification.show(
              "Error creating defaults: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}
