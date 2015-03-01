package com.kouleshoff.tools.unicode;

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

    private int propertyCode;

    private String propertyName;

    public BinaryUProperty(int propertyCode, String name) {
        this.propertyCode = propertyCode;
        this.propertyName = name;
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
        out.printf("static const unsigned char unicode_%s_data[%d*64 + 1] = {",
                    propertyName.toLowerCase(),
                    counts.getRight());
        printBitmapData(res);
        out.printf("};\n\nstatic const signed char unicode_%s_ind[%d] = {",
                    propertyName.toLowerCase(),
                    counts.getLeft() + 2);
        printBitmapIndices(res, counts.getLeft());
        out.printf("};\n");

        out.printf("\n\n/* c < 0x%06x */\n", (counts.getLeft() + 1) * BitmapBlock.SIZE);
    }

    @Override
    public void generateTests(PrintStream out) {
        // foreach UProperty.BLOCK
        // is_letter('\16#043D;') <> TRUE or # CYRILLIC SMALL LETTER EN
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

    private void printBitmapIndices(BitmapBlock[] blocks, int maxIndex) {
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

}
