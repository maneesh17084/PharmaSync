package com.pharmasync.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pharmasync.model.Customer;
import com.pharmasync.model.IndianState;
import com.pharmasync.model.Purchase;
import com.pharmasync.model.PurchaseItem;
import com.pharmasync.model.Sale;
import com.pharmasync.model.SaleItem;
import com.pharmasync.model.SupplyType;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


/** Writes tax invoices (sales) and goods-received notes (purchases) as PDF files. */
public class InvoicePdfGenerator {

    public static final Path INVOICE_DIR = Path.of("invoices");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(0x1F, 0x4E, 0x79));
    private static final Font BOLD = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 9);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);
    private static final Font HEADER = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Color HEADER_BG = new Color(0x1F, 0x4E, 0x79);

    private final SettingsService settings;

    public InvoicePdfGenerator(SettingsService settings) {
        this.settings = settings;
    }

    public Path writeSaleInvoice(Sale sale, Customer customer, List<SaleItem> items) throws IOException {
        Path file = INVOICE_DIR.resolve(sale.invoiceNo() + ".pdf");
        write(file, doc -> {
            addCompanyHeader(doc, "TAX INVOICE");
            PdfPTable info = twoColumn();
            info.addCell(block("Bill To", customer.name(), customer.address(),
                    "State: " + (customer.state() == null ? "-" : customer.state().toString()),
                    "Phone: " + nz(customer.phone()), "GSTIN: " + nz(customer.gstin()),
                    "Drug Lic.: " + nz(customer.drugLicenseNo())));
            info.addCell(block("Invoice", "Invoice No: " + sale.invoiceNo(), "Date: " + sale.saleDate().format(DATE_TIME),
                    "Place of supply: " + (sale.placeOfSupply() == null ? "-" : sale.placeOfSupply().toString()),
                    "Supply: " + (sale.supplyType() == SupplyType.INTER_STATE ? "Inter-state (IGST)" : "Intra-state (CGST + SGST)"),
                    "Payment: " + sale.paymentMode() + " / " + sale.paymentStatus(), "Prepared by: " + nz(sale.createdBy())));
            doc.add(info);

            PdfPTable t = table(new float[]{4, 22, 10, 9, 7, 9, 7, 6, 9, 11},
                    "#", "Medicine", "Batch", "Expiry", "Qty", "Rate", "Disc %", "GST %", "GST Amt", "Amount");
            int n = 1;
            for (SaleItem i : items) {
                cells(t, String.valueOf(n++), i.medicineName(), i.batchNo(), i.expiryDate().format(MONTH_YEAR),
                        String.valueOf(i.quantity()), money(i.unitPrice()), i.discountPct().toPlainString(),
                        i.gstRate().toPlainString(), money(i.taxAmount()), money(i.lineTotal()));
            }
            doc.add(t);

            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Gross amount", money(sale.subtotal())});
            rows.add(new String[]{"Discount", "- " + money(sale.discountAmount())});
            rows.addAll(taxRows(sale.taxAmount(), sale.supplyType(), sale.placeOfSupply()));
            rows.add(new String[]{"Invoice total (INR)", money(sale.totalAmount())});
            rows.add(new String[]{"Amount paid", money(sale.amountPaid())});
            rows.add(new String[]{"Balance due (INR)", money(sale.balanceDue())});
            doc.add(totals(rows.toArray(new String[0][])));
            addFooter(doc, "Medicines dispatched by FEFO (First Expiry, First Out). Goods once sold will be taken back "
                    + "only as per company return policy. This is a computer-generated invoice.");
        });
        return file;
    }

    public Path writePurchaseRecord(Purchase p, List<PurchaseItem> items) throws IOException {
        Path file = INVOICE_DIR.resolve(p.purchaseNo() + ".pdf");
        write(file, doc -> {
            addCompanyHeader(doc, p.receivedDate() == null ? "PURCHASE ORDER" : "GOODS RECEIVED NOTE");
            PdfPTable info = twoColumn();
            info.addCell(block("Supplier", p.supplierName(), "Supplier invoice: " + nz(p.supplierInvoiceRef())));
            info.addCell(block("Purchase", "PO No: " + p.purchaseNo(), "Order date: " + p.orderDate().format(DATE),
                    "Received: " + (p.receivedDate() == null ? "-" : p.receivedDate().format(DATE)),
                    "Supply: " + (p.supplyType() == SupplyType.INTER_STATE ? "Inter-state (IGST)" : "Intra-state (CGST + SGST)"),
                    "Status: " + p.status() + " / " + p.paymentStatus()));
            doc.add(info);

            PdfPTable t = table(new float[]{4, 26, 11, 10, 10, 7, 9, 7, 12},
                    "#", "Medicine", "Batch", "Mfg", "Expiry", "Qty", "Cost", "GST %", "Amount");
            int n = 1;
            for (PurchaseItem i : items) {
                cells(t, String.valueOf(n++), i.medicineName(), nz(i.batchNo()),
                        i.mfgDate() == null ? "-" : i.mfgDate().format(MONTH_YEAR),
                        i.expiryDate() == null ? "-" : i.expiryDate().format(MONTH_YEAR),
                        String.valueOf(i.quantity()), money(i.unitCost()), i.gstRate().toPlainString(), money(i.lineTotal()));
            }
            doc.add(t);
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Subtotal", money(p.subtotal())});
            // Purchases are received at our premises, so an intra-state bill uses our state's SGST/UTGST name.
            rows.addAll(taxRows(p.taxAmount(), p.supplyType(), IndianState.ofCode(settings.get(SettingsService.COMPANY_STATE))));
            rows.add(new String[]{"Total (INR)", money(p.totalAmount())});
            rows.add(new String[]{"Paid", money(p.amountPaid())});
            rows.add(new String[]{"Balance (INR)", money(p.totalAmount().subtract(p.amountPaid()))});
            doc.add(totals(rows.toArray(new String[0][])));
            addFooter(doc, nz(p.notes()));
        });
        return file;
    }

    /** CGST + SGST/UTGST for intra-state supplies, a single IGST line for inter-state ones. */
    private static List<String[]> taxRows(BigDecimal tax, SupplyType type, IndianState state) {
        Gst.Split split = Gst.split(tax, type);
        if (type == SupplyType.INTER_STATE) {
            return List.<String[]>of(new String[]{"IGST", money(split.igst())});
        }
        String stateTax = state == null ? "SGST" : state.stateTaxName();
        return List.of(new String[]{"CGST", money(split.cgst())}, new String[]{stateTax, money(split.sgst())});
    }

    // ---- layout helpers ----

    @FunctionalInterface
    private interface Body {
        void write(Document doc) throws DocumentException;
    }

    private static void write(Path file, Body body) throws IOException {
        Files.createDirectories(file.getParent());
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try (OutputStream out = Files.newOutputStream(file)) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            try {
                body.write(doc);
            } finally {
                doc.close();
            }
        } catch (DocumentException e) {
            throw new IOException("Could not create PDF: " + e.getMessage(), e);
        }
    }

    private void addCompanyHeader(Document doc, String title) throws DocumentException {
        Paragraph name = new Paragraph(settings.get(SettingsService.COMPANY_NAME), TITLE);
        doc.add(name);
        IndianState state = IndianState.ofCode(settings.get(SettingsService.COMPANY_STATE));
        doc.add(new Paragraph(settings.get(SettingsService.COMPANY_ADDRESS)
                + (state == null ? "" : "   State: " + state), SMALL));
        StringBuilder ids = new StringBuilder();
        appendIfSet(ids, "Phone: ", settings.get(SettingsService.COMPANY_PHONE));
        appendIfSet(ids, "GSTIN: ", settings.get(SettingsService.COMPANY_GSTIN));
        appendIfSet(ids, "Drug Lic.: ", settings.get(SettingsService.COMPANY_LICENSE));
        if (!ids.isEmpty()) {
            doc.add(new Paragraph(ids.toString(), SMALL));
        }
        Paragraph t = new Paragraph(title, new Font(Font.HELVETICA, 12, Font.BOLD));
        t.setAlignment(Element.ALIGN_CENTER);
        t.setSpacingBefore(10);
        t.setSpacingAfter(8);
        doc.add(t);
    }

    private static PdfPTable twoColumn() {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingAfter(10);
        return t;
    }

    private static PdfPCell block(String heading, String... lines) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6);
        cell.addElement(new Paragraph(heading, BOLD));
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                cell.addElement(new Paragraph(line, NORMAL));
            }
        }
        return cell;
    }

    private static PdfPTable table(float[] widths, String... headers) throws DocumentException {
        PdfPTable t = new PdfPTable(widths.length);
        t.setWidthPercentage(100);
        t.setWidths(widths);
        t.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, HEADER));
            c.setBackgroundColor(HEADER_BG);
            c.setPadding(4);
            t.addCell(c);
        }
        return t;
    }

    private static void cells(PdfPTable t, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i], NORMAL));
            c.setPadding(3);
            if (i >= 4) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(c);
        }
    }

    private static PdfPTable totals(String[]... rows) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(40);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingBefore(8);
        t.setWidths(new float[]{3, 2});
        for (int i = 0; i < rows.length; i++) {
            Font f = rows[i][0].endsWith("(INR)") ? BOLD : NORMAL;
            PdfPCell label = new PdfPCell(new Phrase(rows[i][0], f));
            PdfPCell value = new PdfPCell(new Phrase(rows[i][1], f));
            label.setBorder(Rectangle.NO_BORDER);
            value.setBorder(Rectangle.NO_BORDER);
            value.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(label);
            t.addCell(value);
        }
        return t;
    }

    private static void addFooter(Document doc, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, SMALL);
        p.setSpacingBefore(20);
        doc.add(p);
        Paragraph sign = new Paragraph("Authorised Signatory", BOLD);
        sign.setAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(30);
        doc.add(sign);
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : String.format("%,.2f", v);
    }

    private static void appendIfSet(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(sb.isEmpty() ? "" : "   ").append(label).append(value);
        }
    }

    private static String nz(String s) {

        return s == null ? "-" : s;
    }
}
