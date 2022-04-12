import java.io.Serializable;

public class NestedSerializableClass implements Serializable {
    public static class NestedClass implements Serializable {
        public static final long serialVersionUID = 1L;

        public long l;

        public NestedClass(long l) {
            this.l = l;
        }
    }

    public static final long serialVersionUID = 1L;

    public int i;
    public float f;

    public Class<?> c;
    public String s;
    public NestedClass nested;
    public SimpleSerializableClass other;

    public NestedSerializableClass(int i, float f, Class<?> c, String s, NestedClass nested) {
        this.i = i;
        this.f = f;
        this.c = c;
        this.s = s;
        this.nested = nested;
        this.other = null;
    }
}
