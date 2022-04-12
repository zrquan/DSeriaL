import java.io.*;

import static org.junit.jupiter.api.Assertions.fail;

public class SimpleExternalizableClass implements Externalizable {
    public static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    public int i;

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(5);
        out.writeBoolean(true);

        out.writeObject("test");
        out.writeObject(new Serializable[]{new SimpleSerializableClass(1)});
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fail("Reading is not supported");
    }
}
