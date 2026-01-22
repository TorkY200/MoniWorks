package com.example.application.ui.views;

import com.example.application.domain.Transaction;
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
 * View for managing transactions (payments, receipts, journals).
 */
@Route(value = "transactions", layout = MainLayout.class)
@PageTitle("Transactions | MoniWorks")
@PermitAll
public class TransactionsView extends VerticalLayout {

    private final Grid<Transaction> grid = new Grid<>(Transaction.class, false);

    public TransactionsView() {
        addClassName("transactions-view");
        setSizeFull();

        configureGrid();

        add(createToolbar(), grid);
    }

    private void configureGrid() {
        grid.addClassNames("transactions-grid");
        grid.setSizeFull();

        grid.addColumn(Transaction::getTransactionDate)
            .setHeader("Date")
            .setSortable(true)
            .setAutoWidth(true);
        grid.addColumn(Transaction::getType)
            .setHeader("Type")
            .setSortable(true)
            .setAutoWidth(true);
        grid.addColumn(Transaction::getReference)
            .setHeader("Reference")
            .setAutoWidth(true);
        grid.addColumn(Transaction::getDescription)
            .setHeader("Description")
            .setFlexGrow(1);
        grid.addColumn(Transaction::getStatus)
            .setHeader("Status")
            .setSortable(true)
            .setAutoWidth(true);
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Transactions");

        Button paymentBtn = new Button("Payment", VaadinIcon.MINUS.create());
        paymentBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button receiptBtn = new Button("Receipt", VaadinIcon.PLUS.create());
        receiptBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button journalBtn = new Button("Journal", VaadinIcon.BOOK.create());

        HorizontalLayout buttons = new HorizontalLayout(paymentBtn, receiptBtn, journalBtn);
        buttons.setSpacing(true);

        HorizontalLayout toolbar = new HorizontalLayout(title, buttons);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.setAlignItems(Alignment.CENTER);

        return toolbar;
    }
}
