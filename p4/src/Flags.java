public class Flags {
    private int S;
    private int F;
    private int A;

    public Flags(int S, int F, int A) {
        this.S = S;
        this.F = F;
        this.A = A;
    }

    public Flags(String s) {
        if (s.contains("S")) {
            this.S = 1;
        }
        if (s.contains("F")) {
            this.F = 1;
        }
        if (s.contains("A")) {
            this.A = 1;
        }
    }

    public int getS() {
        return S;
    }

    public int getF() {
        return F;
    }

    public int getA() {
        return A;
    }

    public void setS(int S) {
        this.S = S;
    }

    public void setF(int F) {
        this.F = F;
    }

    public void setA(int A) {
        this.A = A;
    }

    public boolean equals(Flags f) {
        if (f == null) return false;
        return this.S == f.getS() && this.F == f.getF() && this.A == f.getA();
    }

    public boolean equals(String f) {
        return equals(new Flags(f));
    }

}
