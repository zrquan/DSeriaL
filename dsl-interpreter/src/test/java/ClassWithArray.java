import java.io.Serializable;

public class ClassWithArray implements Serializable {
    public static final long serialVersionUID = 1L;

    public int[] ints;
    public Object[] objects;

    public ClassWithArray(int[] ints, Object[] objects) {
        this.ints = ints;
        this.objects = objects;
    }
}
