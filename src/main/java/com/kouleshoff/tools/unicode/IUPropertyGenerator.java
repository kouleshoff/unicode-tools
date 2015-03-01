package com.kouleshoff.tools.unicode;

import java.io.PrintStream;

/**
 * Created by arkadio on 3/1/15.
 */
public interface IUPropertyGenerator {
    void generateData(PrintStream out);

    void generateTests(PrintStream out);
}
