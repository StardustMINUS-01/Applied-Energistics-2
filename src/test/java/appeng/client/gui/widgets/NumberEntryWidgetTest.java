package appeng.client.gui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.client.gui.NumberEntryType;

class NumberEntryWidgetTest {
    private DecimalFormat format;

    @BeforeEach
    void setUp() {
        format = new DecimalFormat("#.######", DecimalFormatSymbols.getInstance(Locale.US));
        format.setParseBigDecimal(true);
        format.setNegativePrefix("-");
    }

    @Test
    void rejectsFractionalAmountsForUnitlessItems() {
        assertEquals(OptionalLong.empty(),
                NumberEntryWidget.convertParsedValue(new BigDecimal("0.1"), NumberEntryType.UNITLESS, 1,
                        Long.MAX_VALUE));
    }

    @Test
    void acceptsFractionalAmountsForScaledUnits() {
        var fluidBuckets = new NumberEntryType(1000, "B");

        assertEquals(OptionalLong.of(100),
                NumberEntryWidget.convertParsedValue(new BigDecimal("0.1"), fluidBuckets, 1, Long.MAX_VALUE));
    }

    @Test
    void rejectsFractionalScientificNotationForUnitlessItems() {
        assertEquals(OptionalLong.empty(),
                NumberEntryWidget.parseTextValue("1e-1", format, NumberEntryType.UNITLESS, 1, Long.MAX_VALUE));
    }

    @Test
    void acceptsFractionalScientificNotationForScaledUnits() {
        var fluidBuckets = new NumberEntryType(1000, "B");

        assertEquals(OptionalLong.of(100),
                NumberEntryWidget.parseTextValue("1e-1", format, fluidBuckets, 1, Long.MAX_VALUE));
    }

    @Test
    void parsesNormalizedChineseInputThroughNumberEntryPath() {
        assertEquals(OptionalLong.of(1024),
                NumberEntryWidget.parseTextValue("（1，000 + 24）", format, NumberEntryType.UNITLESS, 1,
                        Long.MAX_VALUE));
    }

    @Test
    void parsesLargeScientificNotationExample() {
        assertEquals(OptionalLong.of(80_000_000_000L),
                NumberEntryWidget.parseTextValue("8e10", format, NumberEntryType.UNITLESS, 1, Long.MAX_VALUE));
    }
}
