// package com.kouleshoff.tools.unicode;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.List;

/**
 * <dependency>
 *   <groupId>com.ibm.icu</groupId>
 *   <artifactId>icu4j</artifactId>
 *   <version>4.8.1.1</version>
 * </dependency>
 * <dependency>
 *   <groupId>org.apache.commons</groupId>
 *   <artifactId>commons-lang3</artifactId>
 *   <version>3.3.2</version>
 * </dependency>
 */
public class UnicodeBitmap {

    static int UNICODE_DATA_SIZE = 1 << 21 - 1;

    static int ADDITIONAL_DATA[] = { '_' };

    /**
     * Contains a block of unicode property values
     * One bit per actual code point
     */
    static class BitmapBlock {
        static int SIZE = 0x200;
        int rangeStart;
        int numOfBits = 0;
        int codePoint;
        final BitSet bitSet = new BitSet(SIZE);

        BitmapBlock(int start, int uProperty) {
            int k = 0, c, end = start + SIZE;
            rangeStart = start;
            for (c = start; c < end; ++c, ++k) {
                if (UCharacter.hasBinaryProperty(c, uProperty)) {
                    bitSet.set(k);
                    numOfBits++;
                    codePoint = c;
                }
            }
        }
    }

    /**
     * Scan entire unicode range and assign each bit to appropriate BitmapBlock
     * Bit value is the uProperty value for one code point
     * @param uProperty a UProperty constant value
     * @param blocks mutable list of bitmap data blocks
     * @return pair of lastIndex, uniqueBlocksCount
     */
    static Pair<Integer,Integer> buildBlocksForProperty(int uProperty, List<BitmapBlock> blocks) {
        int n = 0;
        int lastIndex = -1;
        int uniqueBlocksCount = 0;
        while (n < UNICODE_DATA_SIZE) {
            BitmapBlock block = new BitmapBlock(n, uProperty);
            if (block.numOfBits != 0) {
                lastIndex = blocks.size();
                if (block.numOfBits != BitmapBlock.SIZE) {
                    uniqueBlocksCount++;
                }
            }
            blocks.add(block);
            n += BitmapBlock.SIZE;
        }
        return new ImmutablePair<>(lastIndex, uniqueBlocksCount);
    }

    static void printBitmapData(BitmapBlock[] blocks) {
        int i, k;
        for (i = 0; i < blocks.length; ++i) {
            if (blocks[i].numOfBits != 0 && blocks[i].numOfBits != BitmapBlock.SIZE) {
                System.out.printf("\n  /* 0x%06x-0x%06x | number of bits: %d */",
                        blocks[i].rangeStart,
                        blocks[i].rangeStart + BitmapBlock.SIZE - 1,
                        blocks[i].numOfBits);
                byte[] bytes = blocks[i].bitSet.toByteArray();
                for (k = 0; k < 64; ++k) {
                    if (k % 8 == 0) {
                        if (k != 0) {
                            System.out.printf("/* 0x%06x-0x%06x */",
                                    blocks[i].rangeStart,
                                    blocks[i].rangeStart + k - 1);
                        }
                        System.out.print("\n  ");
                    }
                    if (k < bytes.length) {
                        System.out.printf("0x%02x, ", bytes[k]);
                    }
                    else {
                        System.out.print("0x00, ");
                    }
                }
                System.out.printf("/* 0x%06x-0x%06x */",
                        blocks[i].rangeStart + BitmapBlock.SIZE - 8,
                        blocks[i].rangeStart + BitmapBlock.SIZE - 1);
            }
        }
        System.out.println("\n  0x00");
    }

    static void printBitmapIndices(BitmapBlock[] blocks, int maxIndex) {
        int n, i = 0, k = 0;
        for (n = 0; n < UNICODE_DATA_SIZE && i<= maxIndex; n+= BitmapBlock.SIZE, i++) {
            if (i % 8 == 0) System.out.print("\n  ");
            if (blocks[i].numOfBits == 0) {
                System.out.print(" -1, ");
            }
            else if (blocks[i].numOfBits == BitmapBlock.SIZE) {
                System.out.print(" -2, ");
            }
            else {
                System.out.printf("%3d, ", k++);
            }
        }
        System.out.print("-1\n");
    }

    private static List<Integer> buildEastAsianWidthTable() {
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

    private static String widthToName(int value) {
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

    /**
     * Scan the entire unicode range for specified property
     * and output C code tables to be used in c_ident.c from libutf8
     * @param args
     */
    public static void main(String[] args) {
        List<BitmapBlock> blocks = Lists.newArrayList();
        // returns pair of lastIndex, uniqueBlocksCount
        Pair<Integer,Integer> counts = buildBlocksForProperty(UProperty.ALPHABETIC, blocks);
        // set special code points if any
        for (int k = 0; k < ADDITIONAL_DATA.length; k++) {
            if (ADDITIONAL_DATA[k] < BitmapBlock.SIZE
                && ! blocks.get(0).bitSet.get(ADDITIONAL_DATA[k])) {
                blocks.get(0).bitSet.set(ADDITIONAL_DATA[k]);
                blocks.get(0).numOfBits++;

            }
        }
        System.out.println("/* This data is generated using icu4j:4.8.1.1 \n *" +
                           " by calling UCharacter.hasBinaryProperty(c, UProperty.ALPHABETIC) */\n");
        BitmapBlock[] res = blocks.toArray(new BitmapBlock[blocks.size()]);
        System.out.printf("static const unsigned char unicode_letters_data[%d*64 + 1] = {", counts.getRight());
        printBitmapData(res);
        System.out.printf("};\n\nstatic const signed char unicode_letters_ind[%d] = {", counts.getLeft() + 2);
        printBitmapIndices(res, counts.getLeft());
        System.out.printf("};\n");

        System.out.printf("\n\n/* c < 0x%06x */\n", (counts.getLeft() + 1) * BitmapBlock.SIZE);

        System.out.print("/*\n");
        List<Integer> eastAsianWidth = buildEastAsianWidthTable();
        System.out.printf("\n*/\n\nint east_asian_width[%d] = {", eastAsianWidth.size());
        System.out.print(Joiner.on(",").join(
            Iterables.transform(
                eastAsianWidth,
                new Function<Integer,String>() {
                    @Nullable
                    @Override
                    public String apply(@Nullable Integer input) {
                        return String.format("0x%06x", input);
                    }
                }
            )
        ));
        System.out.print("};\n");
    }
}
