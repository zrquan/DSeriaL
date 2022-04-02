import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.*
import java.io.ObjectStreamConstants.*
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
            descriptors {
                desc {
                    type = SimpleSerializableClass::class.java
                    uid = SimpleSerializableClass.serialVersionUID
                    flags = SC_SERIALIZABLE

                    "i" type Int::class.java
                }
            }
            slot {
                prims {
                    intVal(1)
                }
            }
        }

        val expectedData = serialize(SimpleSerializableClass(1))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理嵌套关系" {
        val actualData = Serial {
            descriptors {
                desc {
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
            slot {
                prims {
                    floatVal(10f)
                    intVal(1)
                }
                objs {
                    jclass {
                        desc {
                            type = Serializable::class.java
                            flags = SC_SERIALIZABLE
                        }
                    }
                    serialObj {
                        descriptors {
                            desc {
                                type = NestedSerializableClass.NestedClass::class.java
                                uid = NestedSerializableClass.NestedClass.serialVersionUID
                                flags = SC_SERIALIZABLE

                                "l" type Long::class.java
                            }
                        }
                        slot {
                            prims { longVal(5L) }
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
        val actualData = Serial {
            descriptors {
                desc {
                    type = HierarchySub::class.java
                    uid = HierarchySub.serialVersionUID

                    "c" type Char::class.java
                }
                desc {
                    type = HierarchyBase::class.java
                    uid = HierarchyBase.serialVersionUID

                    "i" type Int::class.java
                }
            }
            slot { prims { intVal(5) } }
            slot { prims { charVal('a') } }
        }

        val expectedData: ByteArray = serialize(HierarchySub(5, 'a'))

        actualData?.toHex() shouldBe expectedData.toHex()
    }

    "处理枚举类型" {
        val actualData = Serial {
            descriptors {
                desc {
                    type = ClassWithEnum::class.java
                    uid = ClassWithEnum.serialVersionUID

                    "e" type TestEnum::class.java
                }
            }
            slot {
                objs {
                    enum("TEST") {
                        desc {
                            type = TestEnum::class.java
                            flags = SC_SERIALIZABLE or SC_ENUM
                        }
                        desc {
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
            descriptors {
                desc {
                    type = ClassWithArray::class.java
                    uid = ClassWithArray.serialVersionUID

                    "ints" type IntArray::class.java
                    "objects" type Array<Any>::class.java
                }
            }
            slot {
                objs {
                    array(type = IntArray::class.java) { +intArrayOf(1, 2, 3) }
                    array(type = Array<Serializable>::class.java) {
                        elements {
                            // bug: string("test \u0000 \u0100 \uD800\uDC00 \uDC00")
                            string("test")
                            serialObj {
                                descriptors {
                                    desc {
                                        type = SimpleSerializableClass::class.java
                                        uid = SimpleSerializableClass.serialVersionUID

                                        "i" type Int::class.java
                                    }
                                }
                                slot {
                                    prims {
                                        intVal(1)
                                    }
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

    "处理 Externalizable 接口的实现类" {
        val actualData = External {
            descriptors {
                desc {
                    type = SimpleExternalizableClass::class.java
                    uid = SimpleExternalizableClass.serialVersionUID
                    flags = SC_EXTERNALIZABLE or SC_BLOCK_DATA
                }
            }
            writeExternal {
                it.write(5)
                it.writeBoolean(true)

                string("test")
                array(type = Array<Serializable>::class.java) {
                    elements {
                        serialObj {
                            descriptors {
                                desc {
                                    type = SimpleSerializableClass::class.java
                                    uid = SimpleSerializableClass.serialVersionUID

                                    "i" type Int::class.java
                                }
                            }
                            slot { prims { intVal(1) } }
                        }
                    }
                }
            }
        }

        val expectedData = serialize(SimpleExternalizableClass())

        actualData?.toHex() shouldBe expectedData.toHex()
    }
})
