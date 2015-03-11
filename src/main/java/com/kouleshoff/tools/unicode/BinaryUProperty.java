package com.kouleshoff.tools.unicode;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.PrintStream;
import java.util.BitSet;
import java.util.List;

public class BinaryUProperty implements IUPropertyGenerator {

    static int UNICODE_DATA_SIZE = 1 << 21 - 1;

    static int ADDITIONAL_DATA[] = { };

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

    private int propertyCode;

    private String propertyName;

    public BinaryUProperty(int propertyCode, String name) {
        this.propertyCode = propertyCode;
        if (propertyCode == UProperty.ALPHABETIC) {
            this.propertyName = "Letter";
        }
        else {
            this.propertyName = name.toLowerCase();
            this.propertyName = Character
                    .toUpperCase(this.propertyName.charAt(0)) +
                     this.propertyName.substring(1);
        }
    }

    @Override
    public void generateData(PrintStream out) {
        List<BitmapBlock> blocks = Lists.newArrayList();
        // returns pair of lastIndex, uniqueBlocksCount
        Pair<Integer,Integer> counts = buildBlocksForProperty(propertyCode, blocks);
        // set special code points if any
        for (int k = 0; k < ADDITIONAL_DATA.length; k++) {
            if (ADDITIONAL_DATA[k] < BitmapBlock.SIZE
                    && ! blocks.get(0).bitSet.get(ADDITIONAL_DATA[k])) {
                blocks.get(0).bitSet.set(ADDITIONAL_DATA[k]);
                blocks.get(0).numOfBits++;

            }
        }
        out.printf("/* This data is generated using icu4j:4.8.1.1 \n *" +
                " by calling UCharacter.hasBinaryProperty(c, UProperty.%s) */\n\n",
                  propertyName.toUpperCase());
        BitmapBlock[] res = blocks.toArray(new BitmapBlock[blocks.size()]);
        out.printf("static const unsigned char unicode_%s_data[%d*64] = {",
                    propertyName.toLowerCase(),
                    counts.getRight());
        printBitmapData(res);
        out.printf("};\n\nstatic const signed char unicode_%s_ind[%d] = {",
                    propertyName.toLowerCase(),
                    counts.getLeft() + 1);
        printBitmapIndices(res, counts.getLeft());
        out.printf("};\n");

        out.printf("\n\n/* c < 0x%06x */\n", (counts.getLeft()) * BitmapBlock.SIZE);
    }

    @Override
    public void generateTests(PrintStream out) {
        // foreach UProperty.BLOCK
        int codePoint = 32;
        int prevBlockId = -1;
        int blockStart = -1;
        out.printf("const proc: check_is%s is func\n" +
                "  local\n" +
                "    var boolean: success is TRUE;\n" +
                "  begin\n", propertyName);
        for (;codePoint < UNICODE_DATA_SIZE; codePoint++) {
            int blockId = UCharacter.UnicodeBlock.of(codePoint).getID();
            if (blockId != prevBlockId) {
                if (blockStart > 0) {
                    int incr = (codePoint - blockStart + 1) / 4;
                    int [] points = new int [] {
                            blockStart,
                            blockStart + incr,
                            blockStart + incr * 2,
                            blockStart + incr * 3 };
                    generateCondition(out, points);
                }
                prevBlockId = blockId;
                blockStart = codePoint;
            }
        }
        out.printf("    if success then\n" +
                "      writeln(\"is%s function works correctly.\");\n" +
                "    end if;\n" +
                "  end func;\n\n\n",
                propertyName,
                propertyName);
    }

    private void generateCondition(PrintStream out, int[] points) {
        for (int i = 0; i < points.length; i++) {
            String hasProperty = UCharacter.hasBinaryProperty(points[i], this.propertyCode) ? "TRUE " : "FALSE";
            String extName = UCharacter.getExtendedName(points[i]);
            if (extName == null || extName.contains("unassigned-")) continue;
            out.printf("    if is%s('\\16#%05x;') <> %s then " +
                           "writeln(\"is%s( #%5x ) incorrect\"); success := FALSE; end if; # %s\n",
                        propertyName,
                        points[i],
                        hasProperty,
                        propertyName,
                        points[i],
                        extName);
        }
    }

    /**
     * Scan entire unicode range and assign each bit to appropriate BitmapBlock
     * Bit value is the uProperty value for one code point
     * @param uProperty a UProperty constant value
     * @param blocks mutable list of bitmap data blocks
     * @return pair of lastIndex, uniqueBlocksCount
     */
    private Pair<Integer,Integer> buildBlocksForProperty(int uProperty, List<BitmapBlock> blocks) {
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

    private void printBitmapData(BitmapBlock[] blocks) {
        int i, k;
        for (i = 0; i < blocks.length; ++i) {
            if (blocks[i].numOfBits != 0 && blocks[i].numOfBits != BitmapBlock.SIZE) {
                System.out.printf("\n  /* 0x%06x-0x%06x | number of bits: %d */",
                        blocks[i].rangeStart,
                        blocks[i].rangeStart + BitmapBlock.SIZE - 1,
                        blocks[i].numOfBits);
                byte[] bytes = blocks[i].bitSet.toByteArray();
                for (k = 0; k < 64; k+=8) {
                    if (k % 32 == 0) {
                        System.out.print("\n  ");
                    }
                    String hex = toHexString(bytes, k);
                    System.out.printf("0x%sL, ", hex);
                }
            }
        }
        System.out.println("\n  0x00");
    }

    /**
     * Converts up to 8 bytes of binary data
     * to a hexadecimal string assuming the
     * representation is Little Endian for each 64-bit block
     * The data is padded by 00 to the left
     * @param data
     * @param index
     * @return
     */
    private static String toHexString(byte[] data, int index) {
        int n;
        StringBuilder sb = new StringBuilder();
        int endIndex = Math.min(index + 8, data.length);
        for (n = index + 8 - 1; n >= index && n >= endIndex; n--) {
            sb.append("00");
        }
        for (; n >= index; n--) {
            sb.append(String.format("%02x", data[n]));
        }
        return sb.toString();
    }

    private void printBitmapIndices(BitmapBlock[] blocks, int maxIndex) {
        int n, i = 0, k = 0;
        for (n = 0; n < UNICODE_DATA_SIZE && i<= maxIndex; n+= BitmapBlock.SIZE, i++) {
            if (i % 8 == 0) {
                if (i > 0) {
                    System.out.printf(" /* 0x%06x-0x%06x */",
                                        blocks[i - 8].rangeStart,
                                        blocks[i].rangeStart + BitmapBlock.SIZE - 1);
                }
                System.out.print("\n  ");
            }
            if (blocks[i].numOfBits == 0) {
                System.out.print(" -1");
            }
            else if (blocks[i].numOfBits == BitmapBlock.SIZE) {
                System.out.print(" -2");
            }
            else {
                System.out.printf("%3d", k++);
            }
            if (i< maxIndex) {
                System.out.print(", ");   
            }
        }
    }

}
