package example;

public class Main {
    String name;
    public static void main(String[] args) throws Exception {
        Main m = new Main("foo");
        m.bar("a");
        try {
            m.bar(null);
        } catch (RuntimeException re) {}
        String s = m.buz("blah", 4.2d, "");
        m.method(3);
        quz("what");
        quz(null);
        System.out.println("Done!");
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

    String buz(String x, double z, String y) {
        return x;
    }

    static String quz(String y) {
        return y;
    }

    String method(int y) {
       return "";
    }
}
