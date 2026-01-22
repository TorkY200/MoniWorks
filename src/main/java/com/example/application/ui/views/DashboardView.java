package com.example.application.ui.views;

import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * Dashboard view showing key metrics and quick actions.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | MoniWorks")
@PermitAll
public class DashboardView extends VerticalLayout {

    public DashboardView() {
        addClassName("dashboard-view");

        H2 title = new H2("Dashboard");
        Paragraph welcome = new Paragraph(
            "Welcome to MoniWorks - your accounting system.");

        add(title, welcome);

        // TODO: Add dashboard tiles for:
        // - Cash balance
        // - Overdue AR/AP
        // - Income trend
        // - GST due estimate
    }
}
