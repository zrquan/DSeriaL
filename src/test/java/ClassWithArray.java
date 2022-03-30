import java.io.Serializable;

public class ClassWithArray implements Serializable {
    public static final long serialVersionUID = 1L;

    public int[] ints;
    // todo: object array

    public ClassWithArray(int[] ints) {
        this.ints = ints;
    }
}
