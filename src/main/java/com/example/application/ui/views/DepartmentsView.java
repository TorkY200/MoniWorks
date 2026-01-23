package com.example.application.ui.views;

import java.util.List;

import com.example.application.domain.Company;
import com.example.application.domain.Department;
import com.example.application.service.CompanyContextService;
import com.example.application.service.DepartmentService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing Departments (cost centres). Allows creating, editing, and deactivating
 * departments. Departments provide dimensional reporting on transactions.
 */
@Route(value = "departments", layout = MainLayout.class)
@PageTitle("Departments | MoniWorks")
@PermitAll
public class DepartmentsView extends VerticalLayout {

  private final DepartmentService departmentService;
  private final CompanyContextService companyContextService;

  private final Grid<Department> grid = new Grid<>();
  private final TextField searchField = new TextField();

  public DepartmentsView(
      DepartmentService departmentService, CompanyContextService companyContextService) {
    this.departmentService = departmentService;
    this.companyContextService = companyContextService;

    addClassName("departments-view");
    setSizeFull();

    configureGrid();
    add(createToolbar(), grid);

    loadDepartments();
  }

  private void configureGrid() {
    grid.addClassNames("departments-grid");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    grid.addColumn(Department::getCode)
        .setHeader("Code")
        .setSortable(true)
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(Department::getName).setHeader("Name").setSortable(true).setFlexGrow(1);

    grid.addColumn(d -> d.getGroupName() != null ? d.getGroupName() : "")
        .setHeader("Group")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(d -> d.getClassification() != null ? d.getClassification() : "")
        .setHeader("Classification")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(d -> d.isActive() ? "Active" : "Inactive")
        .setHeader("Status")
        .setAutoWidth(true);

    grid.addComponentColumn(this::createActionButtons)
        .setHeader("Actions")
        .setAutoWidth(true)
        .setFlexGrow(0);
  }

  private HorizontalLayout createActionButtons(Department department) {
    Button editBtn = new Button(VaadinIcon.EDIT.create());
    editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    editBtn.addClickListener(e -> openDepartmentDialog(department));
    editBtn.getElement().setAttribute("title", "Edit department");

    HorizontalLayout actions = new HorizontalLayout(editBtn);

    if (department.isActive()) {
      Button deactivateBtn = new Button(VaadinIcon.BAN.create());
      deactivateBtn.addThemeVariants(
          ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
      deactivateBtn.addClickListener(e -> deactivateDepartment(department));
      deactivateBtn.getElement().setAttribute("title", "Deactivate department");
      actions.add(deactivateBtn);
    } else {
      Button activateBtn = new Button(VaadinIcon.CHECK.create());
      activateBtn.addThemeVariants(
          ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
      activateBtn.addClickListener(e -> activateDepartment(department));
      activateBtn.getElement().setAttribute("title", "Activate department");
      actions.add(activateBtn);
    }

    actions.setSpacing(false);
    actions.setPadding(false);
    return actions;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Departments");

    searchField.setPlaceholder("Search departments...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setClearButtonVisible(true);
    searchField.addValueChangeListener(e -> filterDepartments(e.getValue()));
    searchField.setWidth("250px");

    Button addButton = new Button("Add Department", VaadinIcon.PLUS.create());
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(e -> openDepartmentDialog(null));

    Button refreshButton = new Button(VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> loadDepartments());
    refreshButton.getElement().setAttribute("title", "Refresh");

    Button defaultsButton = new Button("Create Defaults", VaadinIcon.MAGIC.create());
    defaultsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    defaultsButton.addClickListener(e -> createDefaultDepartments());
    defaultsButton.getElement().setAttribute("title", "Create default departments");

    HorizontalLayout actions =
        new HorizontalLayout(searchField, addButton, defaultsButton, refreshButton);
    actions.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout toolbar = new HorizontalLayout(title, actions);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

    return toolbar;
  }

  private void loadDepartments() {
    Company company = companyContextService.getCurrentCompany();
    List<Department> departments = departmentService.findByCompany(company);
    grid.setItems(departments);
  }

  private void filterDepartments(String searchTerm) {
    Company company = companyContextService.getCurrentCompany();
    List<Department> departments = departmentService.findByCompany(company);

    if (searchTerm == null || searchTerm.isBlank()) {
      grid.setItems(departments);
    } else {
      String lowerSearch = searchTerm.toLowerCase();
      List<Department> filtered =
          departments.stream()
              .filter(
                  d ->
                      d.getCode().toLowerCase().contains(lowerSearch)
                          || d.getName().toLowerCase().contains(lowerSearch)
                          || (d.getGroupName() != null
                              && d.getGroupName().toLowerCase().contains(lowerSearch))
                          || (d.getClassification() != null
                              && d.getClassification().toLowerCase().contains(lowerSearch)))
              .toList();
      grid.setItems(filtered);
    }
  }

  private void openDepartmentDialog(Department department) {
    boolean isNew = department == null;

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(isNew ? "Add Department" : "Edit Department");
    dialog.setWidth("500px");

    FormLayout form = new FormLayout();

    TextField codeField = new TextField("Code");
    codeField.setMaxLength(5);
    codeField.setRequired(true);
    codeField.setHelperText("Up to 5 alphanumeric characters");
    if (!isNew) {
      codeField.setValue(department.getCode());
      codeField.setReadOnly(true); // Can't change code after creation
    }

    TextField nameField = new TextField("Name");
    nameField.setMaxLength(50);
    nameField.setRequired(true);
    if (!isNew) {
      nameField.setValue(department.getName());
    }

    TextField groupField = new TextField("Group");
    groupField.setMaxLength(50);
    groupField.setHelperText("Optional grouping for reports");
    if (!isNew && department.getGroupName() != null) {
      groupField.setValue(department.getGroupName());
    }

    TextField classificationField = new TextField("Classification");
    classificationField.setMaxLength(50);
    classificationField.setHelperText("Optional classification");
    if (!isNew && department.getClassification() != null) {
      classificationField.setValue(department.getClassification());
    }

    Checkbox activeCheckbox = new Checkbox("Active");
    activeCheckbox.setValue(isNew || department.isActive());

    form.add(codeField, nameField, groupField, classificationField, activeCheckbox);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          // Validate required fields
          if (codeField.isEmpty() || nameField.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          // Validate code format (alphanumeric only)
          String code = codeField.getValue().trim().toUpperCase();
          if (!code.matches("^[A-Z0-9]+$")) {
            Notification.show(
                    "Code must contain only letters and numbers",
                    3000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            Company company = companyContextService.getCurrentCompany();

            if (isNew) {
              Department newDepartment =
                  departmentService.createDepartment(
                      company,
                      code,
                      nameField.getValue().trim(),
                      null // Actor is null for UI operations
                      );
              if (!groupField.isEmpty()) {
                newDepartment.setGroupName(groupField.getValue().trim());
              }
              if (!classificationField.isEmpty()) {
                newDepartment.setClassification(classificationField.getValue().trim());
              }
              newDepartment.setActive(activeCheckbox.getValue());
              departmentService.save(newDepartment, null);

              Notification.show(
                      "Department created successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
              department.setName(nameField.getValue().trim());
              department.setGroupName(groupField.isEmpty() ? null : groupField.getValue().trim());
              department.setClassification(
                  classificationField.isEmpty() ? null : classificationField.getValue().trim());
              department.setActive(activeCheckbox.getValue());
              departmentService.save(department, null);

              Notification.show(
                      "Department updated successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            dialog.close();
            loadDepartments();
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

  private void deactivateDepartment(Department department) {
    departmentService.deactivate(department, null);
    Notification.show("Department deactivated", 3000, Notification.Position.BOTTOM_START)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    loadDepartments();
  }

  private void activateDepartment(Department department) {
    departmentService.activate(department, null);
    Notification.show("Department activated", 3000, Notification.Position.BOTTOM_START)
        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    loadDepartments();
  }

  private void createDefaultDepartments() {
    try {
      Company company = companyContextService.getCurrentCompany();

      // Check if departments already exist
      if (!departmentService.findByCompany(company).isEmpty()) {
        Notification.show(
                "Departments already exist for this company. Defaults not created.",
                3000,
                Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        return;
      }

      departmentService.createDefaultDepartments(company, null);
      Notification.show("Default departments created", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      loadDepartments();
    } catch (Exception ex) {
      Notification.show(
              "Error creating defaults: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}
