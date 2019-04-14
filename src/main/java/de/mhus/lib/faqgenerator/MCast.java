package de.mhus.lib.faqgenerator;

public class MCast {

    public static boolean toboolean(String ins, boolean def) {
        if (ins == null) return def;
        ins = ins.toLowerCase().trim();
        if (
        ins.equals("yes")
                ||
        ins.equals("on")
                ||
        ins.equals("true")
                ||
        ins.equals("ja")  // :-)
                ||
        ins.equals("tak") // :-)
                ||
        ins.equals("oui") // :-)
                ||
        ins.equals("si") // :-)
                ||
        ins.equals("\u4fc2") // :-) chinese
                ||
        ins.equals("HIja'") // :-) // klingon
                ||
        ins.equals("1")
                ||
        ins.equals("t")
                ||
        ins.equals("y")
                ||
        ins.equals("\u2612")
        ) {
            return true;
        }

        if (
        ins.equals("no")
                ||
        ins.equals("off")
                ||
        ins.equals("false")
                ||
        ins.equals("nein") // :-)
                ||
        ins.equals("nie")  // :-)
                ||
        ins.equals("non")  // :-)
                ||
        ins.equals("\u5514\u4fc2")  // :-) chinese
                ||
        ins.equals("Qo'")  // :-) klingon
                ||
        ins.equals("0")
                ||
        ins.equals("-1")
                ||
        ins.equals("f")
                ||
        ins.equals("n")
                ||
        ins.equals("\u2610")
                ) {
            return false;
        }
        return def;
    }

    public static String toString(String in, String def) {
        if (in == null || in.length() == 0) return def;
        return in;
    }

}
