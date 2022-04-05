import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import static org.junit.jupiter.api.Assertions.fail;

public class ExternalExtendsSerial extends SimpleSerializableClass implements Externalizable {
    public static final long serialVersionUID = 1L;

    public ExternalExtendsSerial() {
        this(1);
    }

    public ExternalExtendsSerial(int i) {
        super(i);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(5);
        out.writeBoolean(true);
        out.writeObject("test");
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fail("Reading is not supported");
    }
}
