package example;

public class Main {
    String name;
    int blah;
    String otherString;

    public static void main(String[] args) throws Exception {
        Main m = new Main("foo");
        m.bar("a");
        m.baz("shouldn't count");
        m.bar("");
        m.otherString = "something";
        m.bar("blah");
    }

    Main(String name) {
        this.name = name;
    }

    String bar(String x) throws Exception {
        if (x == null) {
            throw new RuntimeException("oof");
        } else {
            return null;
        }
    }

    static String baz(String y) {
        return y;
    }
}
