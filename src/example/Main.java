package example;

public class Main {
    String name;
    public static void main(String[] args) throws Exception {
        Main m = new Main("foo");
        m.bar("a");
        m.bar(null);
        String s = m.buz("blah", 4.2d, "");
        m.method(3);
    }

    Main(String name) {
        this.name = name;
    }

    String bar(String x) throws Exception {
        if (x == null) {
            return name;
        } else {
            return null;
        }
    }

    String buz(String x, double z, String y) {
        return null;
    }

    String method(int y) {
       return "";
    }
}
