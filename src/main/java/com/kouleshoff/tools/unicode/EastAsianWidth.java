package com.kouleshoff.tools.unicode;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.List;

public class EastAsianWidth implements IUPropertyGenerator {

    static int UNICODE_DATA_SIZE = 1 << 21 - 1;

    public EastAsianWidth()
    {}

    @Override
    public void generateData(PrintStream out)
    {
        List<Integer> eastAsianWidth = buildEastAsianWidthTable();
        out.printf("\n\nint east_asian_width[%d] = {\n", eastAsianWidth.size());
        out.print(Joiner.on(",\n").join(
                Iterables.transform(
                        eastAsianWidth,
                        new Function<Integer, String>() {
                            @Nullable
                            @Override
                            public String apply(@Nullable Integer input) {
                                return String.format("0x%06x", input);
                            }
                        }
                )
        ));
        out.print("};\n");
    }

    @Override
    public void generateTests(PrintStream out) {
        int ch = 32;
        int blockStart = 0;
        int lastWidth = UCharacter.EastAsianWidth.NEUTRAL;
        out.printf("const proc: check_width is func\n" +
                "  local\n" +
                "    var boolean: success is TRUE;\n" +
                "  begin\n");
        while (ch < UNICODE_DATA_SIZE) {
            int width = UCharacter.getIntPropertyValue(++ch, UProperty.EAST_ASIAN_WIDTH);
            if (width != lastWidth) {
                if (blockStart > 0) {
                    int midCh = (ch - blockStart) / 2;
                    printCheckExpr(out, blockStart);
                    if (midCh != 0) {
                        printCheckExpr(out, blockStart + midCh);
                    }
                }
                lastWidth = width;
                blockStart = ch;
            }
        }
        out.printf("    if success then\n" +
                        "      writeln(\"width function works correctly.\");\n" +
                        "    end if;\n" +
                        "  end func;\n\n\n");
    }

    private void printCheckExpr(PrintStream out, int ch) {
        int width = 1;
        if (UCharacter.isISOControl(ch) ||
                UCharacter.hasBinaryProperty(ch, UProperty.DEFAULT_IGNORABLE_CODE_POINT) ||
                UCharacter.hasBinaryProperty(ch, UProperty.DIACRITIC) ||
                UCharacter.hasBinaryProperty(ch, UProperty.EXTENDER)) {
            width = 0;
        }
        else {
            int eaw = UCharacter.getIntPropertyValue(ch, UProperty.EAST_ASIAN_WIDTH);
            if (eaw == UCharacter.EastAsianWidth.WIDE) {
                width = 2;
            }
        }
        String extName = UCharacter.getExtendedName(ch);
        out.printf("    if width('\\16#%05x;') <> %d then " +
                        "writeln(\"width(#%5x) incorrect\"); success := FALSE; end if; # %s\n",
                ch,
                width,
                ch,
                extName);
    }

    private List<Integer> buildEastAsianWidthTable() {
        int ch = 0;
        // int uniqueBlocksCount = 0;
        boolean isDoubleWidth = false;
        int lastWidth = UCharacter.EastAsianWidth.NEUTRAL;
        List<Integer> result = Lists.newArrayList();
        result.add(0);
        while (ch < UNICODE_DATA_SIZE) {
            int width = UCharacter.getIntPropertyValue(ch++, UProperty.EAST_ASIAN_WIDTH);
            if (width == UCharacter.EastAsianWidth.AMBIGUOUS) {
                width = UCharacter.EastAsianWidth.NEUTRAL;
            }
            if (width != lastWidth) {
                System.out.printf("0x%06x : UProperty.EAST_ASIAN_WIDTH changed from %s to %s\n",
                       ch, widthToName(lastWidth), widthToName(width));
                lastWidth = width;
                boolean isWide = false;
                if (width == UCharacter.EastAsianWidth.FULLWIDTH
                  ||width == UCharacter.EastAsianWidth.WIDE) {
                    isWide = true;
                }
                if (isWide != isDoubleWidth) {
                    result.add(ch);
                    //uniqueBlocksCount++;
                    isDoubleWidth = isWide;
                }
            }
        }
        return result;
    }

    private String widthToName(int value) {
        switch (value) {
            case UCharacter.EastAsianWidth.AMBIGUOUS:
                return "AMBIGUOUS";
            case UCharacter.EastAsianWidth.COUNT:
                return "COUNT";
            case UCharacter.EastAsianWidth.FULLWIDTH:
                return "FULLWIDTH";
            case UCharacter.EastAsianWidth.HALFWIDTH:
                return "HALFWIDTH";
            case UCharacter.EastAsianWidth.NARROW:
                return "NARROW";
            case UCharacter.EastAsianWidth.NEUTRAL:
                return "NEUTRAL";
            case UCharacter.EastAsianWidth.WIDE:
                return "WIDE";
            default:
                return "UNDEFINED";
        }
    }

}
