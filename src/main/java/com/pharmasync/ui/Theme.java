package com.pharmasync.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.CategoryDataset;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * PharmaSync's look: FlatLaf light/dark with the custom defaults in resources/com/pharmasync/themes, plus the
 * app's own palette (cards, sidebar, row tints, status tones). Colours are looked up on every call so
 * everything follows a theme switch.
 */
public final class Theme {

    /** Semantic colour families used for status pills, dashboard tiles and row tints. */
    public enum Tone { SUCCESS, WARNING, DANGER, INFO, NEUTRAL }

    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static final String DARK_KEY = "darkMode";

    private static final Set<String> SUCCESS = Set.of("PAID", "ACTIVE", "RECEIVED", "VALID", "OK", "PURCHASE_IN", "OPENING");
    private static final Set<String> WARNING = Set.of("PARTIAL", "ORDERED", "NEAR_EXPIRY", "NEAR EXPIRY", "QUARANTINED",
            "QUARANTINE", "LOW STOCK", "ADJUSTMENT");
    private static final Set<String> DANGER = Set.of("UNPAID", "EXPIRED", "CANCELLED", "DISPOSAL", "LOGIN_FAILED");
    private static final Set<String> INFO = Set.of("ADMIN", "SALE_OUT");

    private Theme() {
    }

    /** Installs the saved light/dark theme. Call once, before any window is created. */
    public static void setup() {
        FlatLaf.registerCustomDefaultsSource("com.pharmasync.themes");
        install(PREFS.getBoolean(DARK_KEY, legacyDarkMode()));
    }

    /** Dark-mode choice saved before the app was renamed from MedStock (preferences node /com/medstore/ui). */
    private static boolean legacyDarkMode() {
        try {
            return Preferences.userRoot().nodeExists("/com/medstore/ui")
                    && Preferences.userRoot().node("/com/medstore/ui").getBoolean(DARK_KEY, false);
        } catch (java.util.prefs.BackingStoreException e) {
            return false;
        }
    }

    public static boolean isDark() {
        return FlatLaf.isLafDark();
    }

    /** Switches light/dark with a short cross-fade and remembers the choice. */
    public static void toggle() {
        boolean dark = !isDark();
        PREFS.putBoolean(DARK_KEY, dark);
        FlatAnimatedLafChange.showSnapshot();
        install(dark);
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    private static void install(boolean dark) {
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    // ---- palette ----

    public static Color color(String key, Color fallback) {
        Color c = UIManager.getColor("PharmaSync." + key);
        return c != null ? c : fallback;
    }

    public static Color accent() { return color("accent", new Color(0x0D9488)); }
    public static Color card() { return color("card.background", Color.WHITE); }
    public static Color cardBorder() { return color("card.border", new Color(0xE3E8EF)); }
    public static Color strong() { return color("text.strong", UIManager.getColor("Label.foreground")); }
    public static Color muted() { return color("text.muted", Color.GRAY); }

    public static Color toneBackground(Tone t) {
        return color("tone." + t.name().toLowerCase(Locale.ROOT) + ".background", new Color(0xE2E8F0));
    }

    public static Color toneForeground(Tone t) {
        return color("tone." + t.name().toLowerCase(Locale.ROOT) + ".foreground", new Color(0x334155));
    }

    /** Tone for a status word such as PAID, NEAR_EXPIRY or LOW STOCK; NEUTRAL when unknown. */
    public static Tone toneOf(String status) {
        if (status == null) {
            return Tone.NEUTRAL;
        }
        String s = status.toUpperCase(Locale.ROOT);
        if (SUCCESS.contains(s)) return Tone.SUCCESS;
        if (WARNING.contains(s)) return Tone.WARNING;
        if (DANGER.contains(s)) return Tone.DANGER;
        if (INFO.contains(s)) return Tone.INFO;
        return Tone.NEUTRAL;
    }

    /** True when the text is a status word that should be shown as a coloured pill. */
    public static boolean isStatusWord(String s) {
        // Only upper-case values ("LOW STOCK", "EXPIRED"): a username such as "admin" stays plain text.
        return s != null && s.equals(s.toUpperCase(Locale.ROOT)) && toneOf(s) != Tone.NEUTRAL;
    }


    /**
     * Maps the semantic row colours used by screens (StyledTable.RED/AMBER/GREEN/GREY) to the current theme,
     * so alert rows stay readable in dark mode.
     */
    public static Color rowTint(Color semantic) {
        if (semantic == null) {
            return null;
        }
        if (semantic.equals(StyledTable.RED)) return color("tint.red", semantic);
        if (semantic.equals(StyledTable.AMBER)) return color("tint.amber", semantic);
        if (semantic.equals(StyledTable.GREEN)) return color("tint.green", semantic);
        if (semantic.equals(StyledTable.GREY)) return color("tint.grey", semantic);
        return semantic;
    }

    // ---- fonts ----

    /** The theme's base font, made bold/plain and resized by delta points. */
    public static Font font(int style, float delta) {
        Font base = UIManager.getFont("Label.font");
        return base.deriveFont(style, base.getSize2D() + delta);
    }

    // ---- charts ----

    /** Gives a JFreeChart bar chart the card background, theme text colours, light gridlines and flat bars. */
    public static void styleChart(JFreeChart chart, Color... series) {
        Color bg = card();
        chart.setBackgroundPaint(bg);
        chart.setBorderVisible(false);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(font(Font.BOLD, 1));
            chart.getTitle().setPaint(strong());
        }
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(bg);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(color("chart.grid", new Color(0xEDF0F4)));
        plot.setRangeGridlineStroke(new java.awt.BasicStroke(1f));
        plot.setDomainGridlinesVisible(false);
        for (Axis axis : new Axis[]{plot.getDomainAxis(), plot.getRangeAxis()}) {
            axis.setTickLabelPaint(muted());
            axis.setTickLabelFont(font(Font.PLAIN, -1));
            axis.setLabelPaint(muted());
            axis.setAxisLinePaint(cardBorder());
            axis.setTickMarksVisible(false);
        }
        if (plot.getRenderer() instanceof BarRenderer r) {
            r.setBarPainter(new StandardBarPainter());
            r.setShadowVisible(false);
            r.setDrawBarOutline(false);
            r.setItemMargin(0.05);
            for (int i = 0; i < series.length; i++) {
                r.setSeriesPaint(i, series[i]);
            }
        }
    }

    /**
     * Hover text for a single-series bar chart, on the bars and on the category labels, e.g.
     * "Electral: 308 units" or "20 Oct: Rs 5,950.00". prefix/suffix must not contain quotes or braces.
     */
    public static void chartTooltips(JFreeChart chart, String prefix, NumberFormat format, String suffix) {
        CategoryPlot plot = chart.getCategoryPlot();
        plot.getRenderer().setDefaultToolTipGenerator(
                new StandardCategoryToolTipGenerator("{1}: " + prefix + "{2}" + suffix, format));
        CategoryDataset data = plot.getDataset();
        CategoryAxis axis = plot.getDomainAxis();
        for (int col = 0; col < data.getColumnCount(); col++) {
            Number v = data.getValue(0, col);
            Comparable<?> key = data.getColumnKey(col);
            axis.addCategoryLabelToolTip(key, key + ": " + prefix + (v == null ? "-" : format.format(v)) + suffix);
        }
    }

    /** Prints each bar's value at its end (right of horizontal bars, above vertical ones). */
    public static void chartValueLabels(JFreeChart chart, NumberFormat format) {
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", format));
        r.setDefaultItemLabelsVisible(true);
        r.setDefaultItemLabelFont(font(Font.BOLD, -1));
        r.setDefaultItemLabelPaint(strong());
        boolean horizontal = plot.getOrientation() == PlotOrientation.HORIZONTAL;
        r.setDefaultPositiveItemLabelPosition(horizontal
                ? new ItemLabelPosition(ItemLabelAnchor.OUTSIDE3, TextAnchor.CENTER_LEFT)
                : new ItemLabelPosition(ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER));
        r.setItemLabelAnchorOffset(5);
        plot.getRangeAxis().setUpperMargin(0.14);
    }
}
