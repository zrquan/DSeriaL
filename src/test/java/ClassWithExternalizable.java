import java.io.Serializable;

public class ClassWithExternalizable implements Serializable {
    public static final long serialVersionUID = 1L;

    public SimpleExternalizableClass e;

    public ClassWithExternalizable(SimpleExternalizableClass e) {
        this.e = e;
    }
}
