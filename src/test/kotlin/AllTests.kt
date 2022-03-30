import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.*
import java.io.ObjectStreamConstants.SC_ENUM
import java.io.ObjectStreamConstants.SC_SERIALIZABLE
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
        val actualData = serial {
            descriptors {
                desc(
                    type = SimpleSerializableClass::class.java,
                    uid = SimpleSerializableClass.serialVersionUID,
                    flags = SC_SERIALIZABLE
                ) {
                    primitiveFields {
                        "i" ptype Int::class.java
                    }
                }
            }
            slot {
                primitiveFields {
                    intVal(1)
                }
            }
        }

        val expectedData = serialize(SimpleSerializableClass(1))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理嵌套关系" {
        val actualData = serial {
            descriptors {
                desc(
                    type = NestedSerializableClass::class.java,
                    uid = NestedSerializableClass.serialVersionUID,
                    flags = SC_SERIALIZABLE
                ) {
                    primitiveFields {
                        "f" ptype Float::class.java
                        "i" ptype Int::class.java
                    }
                    objectFields {
                        "c" otype Class::class.java
                        "nested" otype NestedSerializableClass.NestedClass::class.java
                        "other" otype SimpleSerializableClass::class.java
                        "s" otype String::class.java
                    }
                }
            }
            slot {
                primitiveFields {
                    floatVal(10f)
                    intVal(1)
                }
                objectFields {
                    jclass {
                        desc(type = Serializable::class.java, flags = SC_SERIALIZABLE) {}
                    }
                    serialObj {
                        descriptors {
                            desc(
                                type = NestedSerializableClass.NestedClass::class.java,
                                uid = NestedSerializableClass.NestedClass.serialVersionUID,
                                flags = SC_SERIALIZABLE
                            ) {
                                primitiveFields { "l" ptype Long::class.java }
                            }
                        }
                        slot {
                            primitiveFields { longVal(5L) }
                        }
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
        val actualData = serial {
            descriptors {
                desc(
                    type = HierarchySub::class.java,
                    uid = HierarchySub.serialVersionUID,
                ) {
                    primitiveFields { "c" ptype Char::class.java }
                }
                desc(
                    type = HierarchyBase::class.java,
                    uid = HierarchyBase.serialVersionUID,
                ) {
                    primitiveFields { "i" ptype Int::class.java }
                }
            }
            slot { primitiveFields { intVal(5) } }
            slot { primitiveFields { charVal('a') } }
        }

        val expectedData: ByteArray = serialize(HierarchySub(5, 'a'))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理枚举类型" {
        val actualData = serial {
            descriptors {
                desc(
                    type = ClassWithEnum::class.java,
                    uid = ClassWithEnum.serialVersionUID
                ) {
                    objectFields { "e" otype TestEnum::class.java }
                }
            }
            slot {
                objectFields {
                    enum("TEST") {
                        desc(
                            type = TestEnum::class.java,
                            flags = SC_SERIALIZABLE or SC_ENUM
                        ) {}
                        desc(
                            type = Enum::class.java,
                            flags = SC_SERIALIZABLE or SC_ENUM
                        ) {}
                    }
                }
            }
        }

        val expectedData = serialize(ClassWithEnum(TestEnum.TEST))

        actualData?.toHex() shouldBe expectedData.toHex()
    }
})
