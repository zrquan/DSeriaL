import java.io.Serializable;

public class ClassWithEnum implements Serializable {
    public static final long serialVersionUID = 1L;

    public TestEnum e;

    public ClassWithEnum(TestEnum e) {
        this.e = e;
    }
}
