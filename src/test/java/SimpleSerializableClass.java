import java.io.Serializable;

public class SimpleSerializableClass implements Serializable {
    public static final long serialVersionUID = 1L;

    public int i;

    public SimpleSerializableClass(int i) {
        this.i = i;
    }
}
