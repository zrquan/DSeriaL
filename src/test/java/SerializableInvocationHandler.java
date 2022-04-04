import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class SerializableInvocationHandler implements InvocationHandler, Serializable {
    public static final long serialVersionUID = 1L;

    @SuppressWarnings("SuspiciousInvocationHandlerImplementation")
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return null;
    }
}
