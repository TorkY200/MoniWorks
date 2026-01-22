package com.example.application.ui.components;

import com.example.application.domain.Company;
import com.example.application.domain.SavedView;
import com.example.application.domain.SavedView.EntityType;
import com.example.application.domain.User;
import com.example.application.service.SavedViewService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Component for customizing grid column visibility and ordering.
 * Integrates with SavedViewService for persisting user preferences.
 *
 * Usage:
 * <pre>
 * GridCustomizer<Person> customizer = new GridCustomizer<>(
 *     grid, EntityType.CONTACT, savedViewService, companyContextService, userService
 * );
 * toolbar.add(customizer);
 * </pre>
 */
public class GridCustomizer<T> extends HorizontalLayout {

    private static final Logger log = LoggerFactory.getLogger(GridCustomizer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Grid<T> grid;
    private final EntityType entityType;
    private final SavedViewService savedViewService;
    private final Company company;
    private final User user;

    private final ComboBox<SavedView> viewSelector;
    private final Button customizeButton;
    private final Button saveButton;
    private final Button saveAsButton;

    // Track original column configuration for reset
    private Map<String, ColumnConfig> originalColumns = new LinkedHashMap<>();

    /**
     * Configuration for a single column.
     */
    public record ColumnConfig(
        String key,
        String header,
        boolean visible,
        int order
    ) {}

    /**
     * Creates a new grid customizer.
     *
     * @param grid The grid to customize
     * @param entityType The type of entity the grid displays
     * @param savedViewService Service for managing saved views
     * @param company Current company context
     * @param user Current user
     */
    public GridCustomizer(Grid<T> grid, EntityType entityType,
                          SavedViewService savedViewService,
                          Company company, User user) {
        this.grid = grid;
        this.entityType = entityType;
        this.savedViewService = savedViewService;
        this.company = company;
        this.user = user;

        // Enable column reordering on the grid
        grid.setColumnReorderingAllowed(true);

        // Capture original column configuration
        captureOriginalColumns();

        // View selector dropdown
        viewSelector = new ComboBox<>();
        viewSelector.setPlaceholder("Select view");
        viewSelector.setWidth("180px");
        viewSelector.setClearButtonVisible(true);
        viewSelector.setItemLabelGenerator(SavedView::getName);
        viewSelector.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                applyView(e.getValue());
            }
        });

        // Customize columns button
        customizeButton = new Button("Columns", VaadinIcon.COG.create());
        customizeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        customizeButton.addClickListener(e -> openColumnDialog());

        // Save current configuration
        saveButton = new Button(VaadinIcon.DISC.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        saveButton.setTooltipText("Save current view");
        saveButton.addClickListener(e -> saveCurrentView());

        // Save as new view
        saveAsButton = new Button(VaadinIcon.PLUS.create());
        saveAsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        saveAsButton.setTooltipText("Save as new view");
        saveAsButton.addClickListener(e -> openSaveAsDialog());

        add(viewSelector, customizeButton, saveButton, saveAsButton);
        setAlignItems(FlexComponent.Alignment.CENTER);
        setSpacing(true);
        setPadding(false);

        // Load available views
        refreshViews();

        // Apply default view if exists
        loadDefaultView();
    }

    /**
     * Captures the original column configuration when the grid is first created.
     */
    private void captureOriginalColumns() {
        int order = 0;
        for (var column : grid.getColumns()) {
            String key = column.getKey();
            if (key != null) {
                String header = getColumnHeader(column);
                originalColumns.put(key, new ColumnConfig(key, header, column.isVisible(), order++));
            }
        }
    }

    /**
     * Gets the header text for a column.
     */
    private String getColumnHeader(Grid.Column<T> column) {
        // Get header text from the column
        var headerComponent = column.getHeaderComponent();
        if (headerComponent instanceof Span span) {
            return span.getText();
        }
        // Try to get from header text
        String key = column.getKey();
        if (key != null) {
            // Convert camelCase to Title Case
            return key.substring(0, 1).toUpperCase() +
                   key.substring(1).replaceAll("([A-Z])", " $1");
        }
        return "Column";
    }

    /**
     * Refreshes the list of available saved views.
     */
    public void refreshViews() {
        if (company != null && user != null) {
            List<SavedView> views = savedViewService.findByEntityType(company, user, entityType);
            viewSelector.setItems(views);
        }
    }

    /**
     * Loads and applies the default view if one exists.
     */
    private void loadDefaultView() {
        if (company != null && user != null) {
            savedViewService.findDefaultView(company, user, entityType)
                .ifPresent(view -> {
                    viewSelector.setValue(view);
                    applyView(view);
                });
        }
    }

    /**
     * Applies a saved view configuration to the grid.
     */
    public void applyView(SavedView view) {
        if (view == null || view.getColumnsJson() == null) {
            return;
        }

        try {
            List<ColumnConfig> configs = objectMapper.readValue(
                view.getColumnsJson(),
                new TypeReference<List<ColumnConfig>>() {}
            );

            // Build a map of column positions
            Map<String, ColumnConfig> configMap = configs.stream()
                .collect(Collectors.toMap(ColumnConfig::key, c -> c));

            // Apply visibility
            for (var column : grid.getColumns()) {
                String key = column.getKey();
                if (key != null && configMap.containsKey(key)) {
                    column.setVisible(configMap.get(key).visible());
                }
            }

            // Apply column ordering
            List<Grid.Column<T>> sortedColumns = new ArrayList<>(grid.getColumns());
            sortedColumns.sort((a, b) -> {
                int orderA = configMap.containsKey(a.getKey()) ? configMap.get(a.getKey()).order() : 999;
                int orderB = configMap.containsKey(b.getKey()) ? configMap.get(b.getKey()).order() : 999;
                return Integer.compare(orderA, orderB);
            });
            grid.setColumnOrder(sortedColumns);

            log.debug("Applied saved view: {} with {} columns", view.getName(), configs.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse saved view columns: {}", e.getMessage());
            Notification.show("Failed to apply saved view", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Opens the column customization dialog.
     */
    private void openColumnDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Customize Columns");
        dialog.setWidth("400px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Column visibility checkboxes
        Map<String, Checkbox> checkboxes = new LinkedHashMap<>();
        for (var column : grid.getColumns()) {
            String key = column.getKey();
            if (key != null) {
                String header = originalColumns.containsKey(key)
                    ? originalColumns.get(key).header()
                    : getColumnHeader(column);

                Checkbox checkbox = new Checkbox(header);
                checkbox.setValue(column.isVisible());
                checkbox.addValueChangeListener(e -> column.setVisible(e.getValue()));
                checkboxes.put(key, checkbox);
                content.add(checkbox);
            }
        }

        // Show all / Hide all buttons
        HorizontalLayout toggleButtons = new HorizontalLayout();
        Button showAll = new Button("Show All", e -> {
            checkboxes.values().forEach(cb -> cb.setValue(true));
        });
        showAll.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Button hideAll = new Button("Hide All", e -> {
            checkboxes.values().forEach(cb -> cb.setValue(false));
        });
        hideAll.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Button reset = new Button("Reset", e -> {
            for (var entry : originalColumns.entrySet()) {
                Checkbox cb = checkboxes.get(entry.getKey());
                if (cb != null) {
                    cb.setValue(entry.getValue().visible());
                }
            }
            resetColumnOrder();
        });
        reset.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        toggleButtons.add(showAll, hideAll, reset);
        content.add(toggleButtons);

        // Hint about reordering
        Div hint = new Div();
        hint.setText("Drag column headers in the grid to reorder columns.");
        hint.getStyle().set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin-top", "var(--lumo-space-m)");
        content.add(hint);

        dialog.add(content);

        // Close button
        Button closeButton = new Button("Close", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(closeButton);

        dialog.open();
    }

    /**
     * Resets column order to original configuration.
     */
    private void resetColumnOrder() {
        List<Grid.Column<T>> sortedColumns = new ArrayList<>(grid.getColumns());
        sortedColumns.sort((a, b) -> {
            int orderA = originalColumns.containsKey(a.getKey()) ? originalColumns.get(a.getKey()).order() : 999;
            int orderB = originalColumns.containsKey(b.getKey()) ? originalColumns.get(b.getKey()).order() : 999;
            return Integer.compare(orderA, orderB);
        });
        grid.setColumnOrder(sortedColumns);
    }

    /**
     * Saves the current view configuration.
     */
    private void saveCurrentView() {
        SavedView currentView = viewSelector.getValue();
        if (currentView == null) {
            openSaveAsDialog();
            return;
        }

        try {
            String columnsJson = buildColumnsJson();
            savedViewService.update(currentView, columnsJson, null, null);
            Notification.show("View saved", 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            log.error("Failed to save view: {}", e.getMessage());
            Notification.show("Failed to save view: " + e.getMessage(), 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Opens dialog to save current configuration as a new view.
     */
    private void openSaveAsDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Save View As");

        TextField nameField = new TextField("View Name");
        nameField.setWidth("100%");
        nameField.setRequired(true);
        nameField.setRequiredIndicatorVisible(true);

        Checkbox defaultCheckbox = new Checkbox("Set as default view");

        VerticalLayout content = new VerticalLayout(nameField, defaultCheckbox);
        content.setPadding(false);
        dialog.add(content);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveButton = new Button("Save", e -> {
            String name = nameField.getValue();
            if (name == null || name.isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            try {
                String columnsJson = buildColumnsJson();
                SavedView newView = savedViewService.create(
                    company, user, entityType, name, columnsJson, null, null
                );

                if (defaultCheckbox.getValue()) {
                    savedViewService.setAsDefault(newView);
                }

                refreshViews();
                viewSelector.setValue(newView);
                dialog.close();

                Notification.show("View \"" + name + "\" saved", 2000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalArgumentException ex) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(ex.getMessage());
            } catch (Exception ex) {
                log.error("Failed to save view: {}", ex.getMessage());
                Notification.show("Failed to save view: " + ex.getMessage(), 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
        nameField.focus();
    }

    /**
     * Builds the JSON representation of current column configuration.
     */
    private String buildColumnsJson() throws JsonProcessingException {
        List<ColumnConfig> configs = new ArrayList<>();
        int order = 0;
        for (var column : grid.getColumns()) {
            String key = column.getKey();
            if (key != null) {
                String header = originalColumns.containsKey(key)
                    ? originalColumns.get(key).header()
                    : getColumnHeader(column);
                configs.add(new ColumnConfig(key, header, column.isVisible(), order++));
            }
        }
        return objectMapper.writeValueAsString(configs);
    }

    /**
     * Creates a button to manage (delete/rename) saved views.
     */
    public Button createManageViewsButton() {
        Button button = new Button("Manage Views", VaadinIcon.EDIT.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClickListener(e -> openManageViewsDialog());
        return button;
    }

    /**
     * Opens dialog for managing saved views.
     */
    private void openManageViewsDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Manage Saved Views");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        List<SavedView> views = savedViewService.findByEntityType(company, user, entityType);

        if (views.isEmpty()) {
            content.add(new Span("No saved views"));
        } else {
            for (SavedView view : views) {
                HorizontalLayout row = new HorizontalLayout();
                row.setWidthFull();
                row.setAlignItems(FlexComponent.Alignment.CENTER);

                Span nameLabel = new Span(view.getName());
                nameLabel.getStyle().set("flex-grow", "1");

                if (view.isDefault()) {
                    Icon defaultIcon = VaadinIcon.STAR.create();
                    defaultIcon.setSize("16px");
                    defaultIcon.setColor("var(--lumo-primary-color)");
                    defaultIcon.getElement().setAttribute("title", "Default view");
                    row.add(nameLabel, defaultIcon);
                } else {
                    Button setDefaultBtn = new Button(VaadinIcon.STAR_O.create());
                    setDefaultBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                    setDefaultBtn.setTooltipText("Set as default");
                    setDefaultBtn.addClickListener(ev -> {
                        savedViewService.setAsDefault(view);
                        dialog.close();
                        openManageViewsDialog();
                        Notification.show("Set as default", 2000, Notification.Position.BOTTOM_START);
                    });
                    row.add(nameLabel, setDefaultBtn);
                }

                Button renameBtn = new Button(VaadinIcon.PENCIL.create());
                renameBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                renameBtn.setTooltipText("Rename");
                renameBtn.addClickListener(ev -> {
                    dialog.close();
                    openRenameDialog(view);
                });

                Button deleteBtn = new Button(VaadinIcon.TRASH.create());
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
                deleteBtn.setTooltipText("Delete");
                deleteBtn.addClickListener(ev -> {
                    savedViewService.delete(view);
                    refreshViews();
                    if (viewSelector.getValue() != null && viewSelector.getValue().getId().equals(view.getId())) {
                        viewSelector.clear();
                    }
                    dialog.close();
                    openManageViewsDialog();
                    Notification.show("View deleted", 2000, Notification.Position.BOTTOM_START);
                });

                row.add(renameBtn, deleteBtn);
                content.add(row);
            }
        }

        dialog.add(content);

        Button closeButton = new Button("Close", e -> dialog.close());
        dialog.getFooter().add(closeButton);

        dialog.open();
    }

    /**
     * Opens dialog to rename a saved view.
     */
    private void openRenameDialog(SavedView view) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Rename View");

        TextField nameField = new TextField("New Name");
        nameField.setValue(view.getName());
        nameField.setWidth("100%");
        nameField.setRequired(true);

        dialog.add(nameField);

        Button cancelButton = new Button("Cancel", e -> {
            dialog.close();
            openManageViewsDialog();
        });
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveButton = new Button("Rename", e -> {
            String newName = nameField.getValue();
            if (newName == null || newName.isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }

            try {
                savedViewService.rename(view, newName);
                refreshViews();
                dialog.close();
                openManageViewsDialog();
                Notification.show("View renamed", 2000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalArgumentException ex) {
                nameField.setInvalid(true);
                nameField.setErrorMessage(ex.getMessage());
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelButton, saveButton);
        nameField.setAutoselect(true);
        dialog.open();
        nameField.focus();
    }
}
