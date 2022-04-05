import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

public class ClassWithWriteObject implements Serializable {
    public static final long serialVersionUID = 1L;

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(5);
        out.writeBoolean(true);

        out.writeObject("test");
        out.writeObject(new Serializable[]{new SimpleSerializableClass(1)});
    }
}
