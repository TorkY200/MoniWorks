package com.example.application.ui.views;

import com.example.application.domain.Account;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * View for managing the Chart of Accounts.
 */
@Route(value = "accounts", layout = MainLayout.class)
@PageTitle("Chart of Accounts | MoniWorks")
@PermitAll
public class AccountsView extends VerticalLayout {

    private final Grid<Account> grid = new Grid<>(Account.class, false);

    public AccountsView() {
        addClassName("accounts-view");
        setSizeFull();

        configureGrid();

        add(createToolbar(), grid);
    }

    private void configureGrid() {
        grid.addClassNames("accounts-grid");
        grid.setSizeFull();

        grid.addColumn(Account::getCode)
            .setHeader("Code")
            .setSortable(true)
            .setAutoWidth(true);
        grid.addColumn(Account::getName)
            .setHeader("Name")
            .setSortable(true)
            .setFlexGrow(1);
        grid.addColumn(Account::getType)
            .setHeader("Type")
            .setSortable(true)
            .setAutoWidth(true);
        grid.addColumn(account -> account.isActive() ? "Active" : "Inactive")
            .setHeader("Status")
            .setAutoWidth(true);
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Chart of Accounts");

        Button addButton = new Button("Add Account", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(title, addButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.setAlignItems(Alignment.CENTER);

        return toolbar;
    }
}
