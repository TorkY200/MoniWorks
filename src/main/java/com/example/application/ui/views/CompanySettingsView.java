package com.example.application.ui.views;

import com.example.application.domain.Attachment;
import com.example.application.domain.Company;
import com.example.application.domain.CompanySettings;
import com.example.application.domain.PdfSettings;
import com.example.application.domain.TaxReturn;
import com.example.application.service.AttachmentService;
import com.example.application.service.CompanyContextService;
import com.example.application.service.CompanyService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * View for managing company settings including PDF template customization.
 * Allows admins to configure company branding, logo, contact details,
 * and PDF generation settings.
 */
@Route(value = "company-settings", layout = MainLayout.class)
@PageTitle("Company Settings | MoniWorks")
@RolesAllowed({"ADMIN", "ROLE_ADMIN"})
public class CompanySettingsView extends VerticalLayout {

    private final CompanyService companyService;
    private final CompanyContextService companyContextService;
    private final AttachmentService attachmentService;

    private Company company;
    private CompanySettings companySettings;
    private PdfSettings pdfSettings;

    // PDF Settings form fields
    private TextArea companyAddressField;
    private TextField companyPhoneField;
    private TextField companyEmailField;
    private TextField companyWebsiteField;
    private TextField taxIdField;
    private TextField bankNameField;
    private TextField bankAccountNameField;
    private TextField bankAccountNumberField;
    private TextArea footerTextField;
    private ComboBox<String> paperSizeCombo;
    private TextField primaryColorField;
    private TextField accentColorField;

    // Tax Settings form fields
    private ComboBox<TaxReturn.Basis> taxBasisCombo;

    // Logo preview
    private Div logoPreview;
    private Long currentLogoId;

    public CompanySettingsView(CompanyService companyService,
                               CompanyContextService companyContextService,
                               AttachmentService attachmentService) {
        this.companyService = companyService;
        this.companyContextService = companyContextService;
        this.attachmentService = attachmentService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        company = companyContextService.getCurrentCompany();
        if (company == null) {
            add(new Span("No company selected"));
            return;
        }

        companySettings = companyService.getSettings(company);
        pdfSettings = companySettings.getOrCreatePdfSettings();
        currentLogoId = pdfSettings.getLogoAttachmentId();

        // Header
        H2 title = new H2("Company Settings - " + company.getName());
        title.addClassNames(LumoUtility.Margin.NONE);
        add(title);

        // Tabs for different settings sections
        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();

        tabs.add(new Tab("PDF & Branding"), createPdfSettingsTab());
        tabs.add(new Tab("Company Details"), createCompanyDetailsTab());
        tabs.add(new Tab("Tax Settings"), createTaxSettingsTab());

        add(tabs);
    }

    private VerticalLayout createPdfSettingsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        // Logo section
        H3 logoTitle = new H3("Company Logo");
        logoTitle.addClassNames(LumoUtility.Margin.Top.NONE);
        layout.add(logoTitle);

        Span logoHelp = new Span("Upload a logo image (PNG, JPEG, GIF) to appear on invoices, statements, and reports.");
        logoHelp.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        layout.add(logoHelp);

        // Logo preview
        logoPreview = new Div();
        logoPreview.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderRadius.MEDIUM,
            LumoUtility.Padding.MEDIUM, LumoUtility.Margin.Vertical.SMALL);
        logoPreview.setWidth("200px");
        logoPreview.setMinHeight("80px");
        updateLogoPreview();
        layout.add(logoPreview);

        // Logo upload
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("image/png", "image/jpeg", "image/gif");
        upload.setMaxFileSize(2 * 1024 * 1024); // 2MB max for logo
        upload.setDropAllowed(true);

        Button uploadButton = new Button("Upload Logo");
        upload.setUploadButton(uploadButton);

        upload.addSucceededListener(event -> {
            try {
                byte[] content = buffer.getInputStream().readAllBytes();
                Attachment attachment = attachmentService.uploadFile(
                    company,
                    event.getFileName(),
                    event.getMIMEType(),
                    content,
                    companyContextService.getCurrentUser()
                );
                currentLogoId = attachment.getId();
                pdfSettings.setLogoAttachmentId(currentLogoId);
                updateLogoPreview();
                Notification.show("Logo uploaded successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IOException e) {
                Notification.show("Failed to upload logo: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        upload.addFailedListener(event ->
            Notification.show("Upload failed: " + event.getReason().getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR)
        );

        HorizontalLayout logoActions = new HorizontalLayout(upload);
        if (currentLogoId != null) {
            Button removeLogo = new Button("Remove Logo", VaadinIcon.TRASH.create());
            removeLogo.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            removeLogo.addClickListener(e -> {
                currentLogoId = null;
                pdfSettings.setLogoAttachmentId(null);
                updateLogoPreview();
            });
            logoActions.add(removeLogo);
        }
        layout.add(logoActions);

        // PDF appearance settings
        H3 appearanceTitle = new H3("PDF Appearance");
        layout.add(appearanceTitle);

        FormLayout appearanceForm = new FormLayout();
        appearanceForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        paperSizeCombo = new ComboBox<>("Paper Size");
        paperSizeCombo.setItems("A4", "LETTER", "LEGAL");
        paperSizeCombo.setValue(pdfSettings.getEffectivePaperSize());
        paperSizeCombo.setHelperText("Page size for generated PDFs");

        footerTextField = new TextArea("Footer Text");
        footerTextField.setValue(pdfSettings.getFooterText() != null ? pdfSettings.getFooterText() : "");
        footerTextField.setPlaceholder("Thank you for your business");
        footerTextField.setHelperText("Text shown at the bottom of invoices and statements");
        footerTextField.setMaxLength(200);

        primaryColorField = new TextField("Primary Color");
        primaryColorField.setValue(pdfSettings.getEffectivePrimaryColor());
        primaryColorField.setHelperText("Hex color for headings (e.g., #34495e)");
        primaryColorField.setPattern("#[0-9a-fA-F]{6}");

        accentColorField = new TextField("Accent Color");
        accentColorField.setValue(pdfSettings.getEffectiveAccentColor());
        accentColorField.setHelperText("Hex color for highlights (e.g., #2980b9)");
        accentColorField.setPattern("#[0-9a-fA-F]{6}");

        appearanceForm.add(paperSizeCombo, footerTextField, primaryColorField, accentColorField);
        layout.add(appearanceForm);

        // Bank details for invoices
        H3 bankTitle = new H3("Bank Details for Invoices");
        layout.add(bankTitle);

        Span bankHelp = new Span("These details appear in the payment information section of invoices.");
        bankHelp.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        layout.add(bankHelp);

        FormLayout bankForm = new FormLayout();
        bankForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        bankNameField = new TextField("Bank Name");
        bankNameField.setValue(pdfSettings.getBankName() != null ? pdfSettings.getBankName() : "");
        bankNameField.setPlaceholder("e.g., ANZ Bank");

        bankAccountNameField = new TextField("Account Name");
        bankAccountNameField.setValue(pdfSettings.getBankAccountName() != null ? pdfSettings.getBankAccountName() : "");
        bankAccountNameField.setPlaceholder("e.g., My Company Ltd");

        bankAccountNumberField = new TextField("Account Number");
        bankAccountNumberField.setValue(pdfSettings.getBankAccountNumber() != null ? pdfSettings.getBankAccountNumber() : "");
        bankAccountNumberField.setPlaceholder("e.g., 01-1234-5678901-00");

        bankForm.add(bankNameField, bankAccountNameField, bankAccountNumberField);
        layout.add(bankForm);

        // Save button
        Button saveBtn = new Button("Save PDF Settings", VaadinIcon.CHECK.create());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveAllSettings());
        layout.add(saveBtn);

        return layout;
    }

    private VerticalLayout createCompanyDetailsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        H3 detailsTitle = new H3("Company Contact Details");
        detailsTitle.addClassNames(LumoUtility.Margin.Top.NONE);
        layout.add(detailsTitle);

        Span detailsHelp = new Span("These details appear on invoices, statements, and reports.");
        detailsHelp.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        layout.add(detailsHelp);

        FormLayout detailsForm = new FormLayout();
        detailsForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        companyAddressField = new TextArea("Address");
        companyAddressField.setValue(pdfSettings.getCompanyAddress() != null ? pdfSettings.getCompanyAddress() : "");
        companyAddressField.setPlaceholder("123 Main Street\nAuckland 1010\nNew Zealand");
        companyAddressField.setMaxLength(500);

        companyPhoneField = new TextField("Phone");
        companyPhoneField.setValue(pdfSettings.getCompanyPhone() != null ? pdfSettings.getCompanyPhone() : "");
        companyPhoneField.setPlaceholder("+64 9 123 4567");

        companyEmailField = new TextField("Email");
        companyEmailField.setValue(pdfSettings.getCompanyEmail() != null ? pdfSettings.getCompanyEmail() : "");
        companyEmailField.setPlaceholder("accounts@example.com");

        companyWebsiteField = new TextField("Website");
        companyWebsiteField.setValue(pdfSettings.getCompanyWebsite() != null ? pdfSettings.getCompanyWebsite() : "");
        companyWebsiteField.setPlaceholder("www.example.com");

        taxIdField = new TextField("GST Number / Tax ID");
        taxIdField.setValue(pdfSettings.getTaxId() != null ? pdfSettings.getTaxId() : "");
        taxIdField.setPlaceholder("123-456-789");
        taxIdField.setHelperText("Shown on invoices as GST #");

        detailsForm.add(companyAddressField, companyPhoneField, companyEmailField, companyWebsiteField, taxIdField);
        layout.add(detailsForm);

        // Save button
        Button saveBtn = new Button("Save Company Details", VaadinIcon.CHECK.create());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveAllSettings());
        layout.add(saveBtn);

        return layout;
    }

    private VerticalLayout createTaxSettingsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        H3 taxTitle = new H3("Tax Settings");
        taxTitle.addClassNames(LumoUtility.Margin.Top.NONE);
        layout.add(taxTitle);

        Span taxHelp = new Span("Configure how tax is calculated and reported for this company.");
        taxHelp.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        layout.add(taxHelp);

        FormLayout taxForm = new FormLayout();
        taxForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        taxBasisCombo = new ComboBox<>("Tax Basis");
        taxBasisCombo.setItems(TaxReturn.Basis.values());
        taxBasisCombo.setItemLabelGenerator(basis -> {
            switch (basis) {
                case CASH: return "Cash Basis";
                case INVOICE: return "Invoice/Accrual Basis";
                default: return basis.name();
            }
        });

        // Load current value
        String currentBasis = companySettings.getTaxBasis();
        if (currentBasis != null) {
            try {
                taxBasisCombo.setValue(TaxReturn.Basis.valueOf(currentBasis));
            } catch (IllegalArgumentException e) {
                taxBasisCombo.setValue(TaxReturn.Basis.INVOICE);
            }
        } else {
            taxBasisCombo.setValue(TaxReturn.Basis.INVOICE);
        }

        taxBasisCombo.setHelperText("Determines when tax is recognised for GST returns");

        // Cash basis explanation
        Span cashExplanation = new Span("Cash Basis: Tax is reported when payments are received or made. " +
            "Simpler for small businesses but requires tracking payment dates.");
        cashExplanation.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        cashExplanation.getStyle().set("display", "block").set("margin-top", "8px");

        Span invoiceExplanation = new Span("Invoice Basis: Tax is reported when invoices or bills are issued. " +
            "Standard for most businesses and required above certain turnover thresholds.");
        invoiceExplanation.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        invoiceExplanation.getStyle().set("display", "block").set("margin-top", "4px");

        taxForm.add(taxBasisCombo);
        layout.add(taxForm);
        layout.add(cashExplanation, invoiceExplanation);

        // Note about current implementation
        Span implementationNote = new Span("Note: The selected tax basis will be used as the default when generating GST returns. " +
            "You can still override it for individual returns if needed.");
        implementationNote.addClassNames(LumoUtility.FontSize.SMALL);
        implementationNote.getStyle()
            .set("display", "block")
            .set("margin-top", "16px")
            .set("padding", "8px")
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "4px");
        layout.add(implementationNote);

        // Save button
        Button saveBtn = new Button("Save Tax Settings", VaadinIcon.CHECK.create());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveAllSettings());
        layout.add(saveBtn);

        return layout;
    }

    private void updateLogoPreview() {
        logoPreview.removeAll();

        if (currentLogoId != null) {
            try {
                byte[] logoBytes = attachmentService.downloadFile(currentLogoId);
                if (logoBytes != null && logoBytes.length > 0) {
                    StreamResource resource = new StreamResource("logo",
                        () -> new ByteArrayInputStream(logoBytes));
                    Image logo = new Image(resource, "Company Logo");
                    logo.setMaxWidth("180px");
                    logo.setMaxHeight("80px");
                    logoPreview.add(logo);
                    return;
                }
            } catch (Exception e) {
                // Fall through to show "no logo" message
            }
        }

        Span noLogo = new Span("No logo uploaded");
        noLogo.addClassNames(LumoUtility.TextColor.SECONDARY);
        logoPreview.add(noLogo);
    }

    private void saveAllSettings() {
        // Collect PDF settings values from form
        pdfSettings.setLogoAttachmentId(currentLogoId);
        pdfSettings.setCompanyAddress(companyAddressField.getValue());
        pdfSettings.setCompanyPhone(companyPhoneField.getValue());
        pdfSettings.setCompanyEmail(companyEmailField.getValue());
        pdfSettings.setCompanyWebsite(companyWebsiteField.getValue());
        pdfSettings.setTaxId(taxIdField.getValue());
        pdfSettings.setBankName(bankNameField.getValue());
        pdfSettings.setBankAccountName(bankAccountNameField.getValue());
        pdfSettings.setBankAccountNumber(bankAccountNumberField.getValue());
        pdfSettings.setFooterText(footerTextField.getValue());
        pdfSettings.setPaperSize(paperSizeCombo.getValue());
        pdfSettings.setPrimaryColor(primaryColorField.getValue());
        pdfSettings.setAccentColor(accentColorField.getValue());

        // Update company settings with PDF settings
        companySettings.setPdfSettings(pdfSettings);

        // Save tax basis if the combo has been initialized
        if (taxBasisCombo != null && taxBasisCombo.getValue() != null) {
            companySettings.setTaxBasis(taxBasisCombo.getValue().name());
        }

        try {
            companyService.saveSettings(company, companySettings);
            Notification.show("Settings saved successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            Notification.show("Failed to save settings: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
