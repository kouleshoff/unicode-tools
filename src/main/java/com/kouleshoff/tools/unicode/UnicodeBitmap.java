package com.kouleshoff.tools.unicode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.ibm.icu.lang.UProperty;

public class UnicodeBitmap {

    /**
     * Scan the entire unicode range for specified property
     * and output C code tables to be used in c_ident.c from libutf8
     * @param args
     */
    public static void main(String[] args) {
        String uPropertyName = "ALPHABETIC";
        if (args.length > 0) {
            IUPropertyGenerator generator;
            uPropertyName = args[0].trim();
            if ("EastAsianWidth".equals(uPropertyName)) {
                generator = new EastAsianWidth();
            }
            else {
                int propertyCode;
                try {
                    propertyCode = UProperty.class
                            .getDeclaredField(uPropertyName).getInt(null);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    propertyCode = UProperty.ALPHABETIC;
                } catch (NoSuchFieldException e) {
                    System.out.printf("Property %s not found.\n", uPropertyName);
                    propertyCode = UProperty.ALPHABETIC;
                }
                generator = new BinaryUProperty(propertyCode, uPropertyName);
            }

            generator.generateData(System.out);
            if (Iterables.contains(ImmutableList.of(args), "--tests")) {
                generator.generateData(System.out);
            }
        }
        else {
            System.out.println("Usage: UnicodeBitmap <UProperty> [--tests]");
        }
    }
}
