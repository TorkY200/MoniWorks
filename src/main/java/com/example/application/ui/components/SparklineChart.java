package com.example.application.ui.components;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * A lightweight sparkline chart component that displays trend data using CSS-based bars. Supports
 * line-style (area fills) and bar-style visualizations without requiring external charting
 * libraries.
 *
 * <p>Usage example:
 *
 * <pre>
 * List<SparklineChart.DataPoint> data = List.of(
 *     new SparklineChart.DataPoint("Jan", new BigDecimal("1000")),
 *     new SparklineChart.DataPoint("Feb", new BigDecimal("1200")),
 *     new SparklineChart.DataPoint("Mar", new BigDecimal("900"))
 * );
 * SparklineChart chart = new SparklineChart(data);
 * chart.setBarColor("var(--lumo-success-color)");
 * </pre>
 */
public class SparklineChart extends VerticalLayout {

  /** Represents a single data point in the sparkline. */
  public record DataPoint(String label, BigDecimal value) {}

  private final List<DataPoint> data;
  private String barColor = "var(--lumo-primary-color)";
  private String negativeBarColor = "var(--lumo-error-color)";
  private boolean showLabels = true;
  private boolean showValues = false;
  private int barWidth = 24;
  private int maxHeight = 60;
  private NumberFormat numberFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));

  public SparklineChart(List<DataPoint> data) {
    this.data = data;
    setPadding(false);
    setSpacing(false);
    render();
  }

  /** Sets the color for positive value bars. */
  public SparklineChart setBarColor(String color) {
    this.barColor = color;
    render();
    return this;
  }

  /** Sets the color for negative value bars. */
  public SparklineChart setNegativeBarColor(String color) {
    this.negativeBarColor = color;
    render();
    return this;
  }

  /** Sets whether to show labels below the bars. */
  public SparklineChart setShowLabels(boolean show) {
    this.showLabels = show;
    render();
    return this;
  }

  /** Sets whether to show values above the bars. */
  public SparklineChart setShowValues(boolean show) {
    this.showValues = show;
    render();
    return this;
  }

  /** Sets the width of each bar in pixels. */
  public SparklineChart setBarWidth(int width) {
    this.barWidth = width;
    render();
    return this;
  }

  /** Sets the maximum height of the chart area in pixels. */
  public SparklineChart setMaxHeight(int height) {
    this.maxHeight = height;
    render();
    return this;
  }

  /** Sets the number format for displaying values. */
  public SparklineChart setNumberFormat(NumberFormat format) {
    this.numberFormat = format;
    render();
    return this;
  }

  private void render() {
    removeAll();

    if (data == null || data.isEmpty()) {
      add(new Span("No data"));
      return;
    }

    // Find min and max values for scaling
    BigDecimal maxValue = BigDecimal.ZERO;
    BigDecimal minValue = BigDecimal.ZERO;

    for (DataPoint point : data) {
      if (point.value() != null) {
        if (point.value().compareTo(maxValue) > 0) {
          maxValue = point.value();
        }
        if (point.value().compareTo(minValue) < 0) {
          minValue = point.value();
        }
      }
    }

    // Calculate range for scaling
    BigDecimal range = maxValue.subtract(minValue);
    if (range.compareTo(BigDecimal.ZERO) == 0) {
      range = BigDecimal.ONE; // Avoid division by zero
    }

    // Determine if we have negative values (need baseline)
    boolean hasNegatives = minValue.compareTo(BigDecimal.ZERO) < 0;
    int baselineOffset = 0;
    if (hasNegatives) {
      // Calculate baseline position as percentage of total height
      baselineOffset =
          minValue
              .abs()
              .multiply(BigDecimal.valueOf(maxHeight))
              .divide(range, 0, RoundingMode.HALF_UP)
              .intValue();
    }

    // Create bars container
    HorizontalLayout barsContainer = new HorizontalLayout();
    barsContainer.setPadding(false);
    barsContainer.setSpacing(false);
    barsContainer.setAlignItems(FlexComponent.Alignment.END);
    barsContainer.getStyle().set("gap", "2px").set("height", maxHeight + "px");

    // Create labels container if needed
    HorizontalLayout labelsContainer = new HorizontalLayout();
    labelsContainer.setPadding(false);
    labelsContainer.setSpacing(false);
    labelsContainer.getStyle().set("gap", "2px");

    for (DataPoint point : data) {
      VerticalLayout barColumn = new VerticalLayout();
      barColumn.setPadding(false);
      barColumn.setSpacing(false);
      barColumn.setWidth(barWidth + "px");
      barColumn.setAlignItems(FlexComponent.Alignment.CENTER);
      barColumn.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
      barColumn.setHeight(maxHeight + "px");

      BigDecimal value = point.value() != null ? point.value() : BigDecimal.ZERO;

      // Calculate bar height as percentage of max
      int barHeight;
      if (hasNegatives) {
        barHeight =
            value
                .abs()
                .multiply(BigDecimal.valueOf(maxHeight))
                .divide(range, 0, RoundingMode.HALF_UP)
                .intValue();
      } else if (maxValue.compareTo(BigDecimal.ZERO) > 0) {
        barHeight =
            value
                .multiply(BigDecimal.valueOf(maxHeight))
                .divide(maxValue, 0, RoundingMode.HALF_UP)
                .intValue();
      } else {
        barHeight = 0;
      }

      // Ensure minimum visible height for non-zero values
      if (value.compareTo(BigDecimal.ZERO) != 0 && barHeight < 2) {
        barHeight = 2;
      }

      // Create the bar
      Div bar = new Div();
      bar.setWidth((barWidth - 4) + "px");
      bar.setHeight(barHeight + "px");

      boolean isNegative = value.compareTo(BigDecimal.ZERO) < 0;
      bar.getStyle()
          .set("background-color", isNegative ? negativeBarColor : barColor)
          .set("border-radius", "2px 2px 0 0")
          .set("transition", "height 0.3s ease");

      // Add tooltip
      bar.getElement()
          .setAttribute("title", point.label() + ": " + numberFormat.format(value.doubleValue()));

      // For negative values, adjust positioning
      if (hasNegatives) {
        if (isNegative) {
          barColumn.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
          bar.getStyle().set("border-radius", "0 0 2px 2px");
          bar.getStyle().set("margin-top", (maxHeight - baselineOffset) + "px");
        } else {
          bar.getStyle().set("margin-bottom", baselineOffset + "px");
        }
      }

      // Add value label above bar if enabled
      if (showValues) {
        Span valueLabel = new Span(formatCompact(value));
        valueLabel
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xxs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("white-space", "nowrap");
        barColumn.add(valueLabel);
      }

      barColumn.add(bar);
      barsContainer.add(barColumn);

      // Create label if enabled
      if (showLabels) {
        Span label = new Span(point.label());
        label
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xxs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("width", barWidth + "px")
            .set("text-align", "center")
            .set("overflow", "hidden")
            .set("text-overflow", "ellipsis")
            .set("white-space", "nowrap");
        labelsContainer.add(label);
      }
    }

    add(barsContainer);
    if (showLabels) {
      add(labelsContainer);
    }
  }

  /** Formats a value in compact notation (e.g., 1.2K, 3.5M). */
  private String formatCompact(BigDecimal value) {
    if (value == null) return "";

    double absValue = value.abs().doubleValue();
    String sign = value.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";

    if (absValue >= 1_000_000) {
      return sign + String.format("%.1fM", absValue / 1_000_000);
    } else if (absValue >= 1_000) {
      return sign + String.format("%.1fK", absValue / 1_000);
    } else {
      return sign + String.format("%.0f", absValue);
    }
  }

  /**
   * Creates a simple trend indicator showing direction and percentage change.
   *
   * @param currentValue The current period value
   * @param previousValue The previous period value
   * @return A span element showing the trend
   */
  public static Span createTrendIndicator(BigDecimal currentValue, BigDecimal previousValue) {
    if (currentValue == null
        || previousValue == null
        || previousValue.compareTo(BigDecimal.ZERO) == 0) {
      return new Span("-");
    }

    BigDecimal change =
        currentValue
            .subtract(previousValue)
            .divide(previousValue.abs(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

    boolean isPositive = change.compareTo(BigDecimal.ZERO) > 0;
    boolean isNegative = change.compareTo(BigDecimal.ZERO) < 0;

    String arrow = isPositive ? "\u25B2" : (isNegative ? "\u25BC" : "\u2500");
    String text = arrow + " " + change.abs().setScale(1, RoundingMode.HALF_UP) + "%";

    Span indicator = new Span(text);
    if (isPositive) {
      indicator.getStyle().set("color", "var(--lumo-success-color)");
    } else if (isNegative) {
      indicator.getStyle().set("color", "var(--lumo-error-color)");
    } else {
      indicator.getStyle().set("color", "var(--lumo-secondary-text-color)");
    }

    indicator.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500");

    return indicator;
  }
}
