## 运行时反射
> ## Runtime Reflection
> What is runtime reflection? Given a type or instance of some object at **runtime**, reflection is the ability to:
>
> - inspect the type of that object, including generic types,
> - to instantiate new objects,
> - or to access or invoke members of that object.
>
> Let’s jump in and see how to do each of the above with a few examples.

什么是运行时反射？给定某个对象在运行时的类型或实例，**反射**能够：

- 检查该对象的类型，包括泛型类型，
- 创建新对象，
- 访问或调用该对象的成员。

~~==让我们跳进去，看看如何通过一些例子来完成上述每一个。==~~

### Examples

#### 检查运行时类型（包括运行时的泛型类型）

> #### INSPECTING A RUNTIME TYPE (INCLUDING GENERIC TYPES AT RUNTIME)
>
> As with other JVM languages, Scala’s types are *erased* at compile time. This means that if you were to inspect the runtime type of some instance, that you might not have access to all type information that the Scala compiler has available at compile time.
>
> `TypeTags` can be thought of as objects which carry along all type information available at compile time, to runtime. Though, it’s important to note that`TypeTag`s are always generated by the compiler. This generation is triggered whenever an implicit parameter or context bound requiring a `TypeTag` is used. This means that, typically, one can only obtain a `TypeTag` using implicit parameters or context bounds.
>
> For example, using context bounds:

与其他JVM语言一样，Scala编译时会擦除类型。 这意味着如果要检查某个实例的运行时类型，则可能无法访问Scala编译器在编译时可用的所有类型信息。

`TypeTag`可以被认为是将编译时**所有可用的类型信息**传送到**运行时对象**。但需要注意的是，`TypeTags`总是由编译器生成。每当使用需要`TypeTag`的**隐式参数**或**上下文绑定**时，就会触发编译器生成。这意味着，通常只能使用**隐式参数**或**上下文边界**获得`TypeTag`。

例如，使用**上下文边界**：

```scala
scala> import scala.reflect.runtime.{universe => ru}
import scala.reflect.runtime.{universe=>ru}

scala> val l = List(1,2,3)
l: List[Int] = List(1, 2, 3)

scala> def getTypeTag[T: ru.TypeTag](obj: T) = ru.typeTag[T]
getTypeTag: [T](obj: T)(implicit evidence$1: ru.TypeTag[T])ru.TypeTag[T]

scala> val theType = getTypeTag(l).tpe
theType: ru.Type = List[Int]
```

> In the above, we first import `scala.reflect.runtime.universe` (it must always be imported in order to use `TypeTag`s), and we create a `List[Int]` called`l`. Then, we define a method `getTypeTag` which has a type parameter `T`that has a context bound (as the REPL shows, this is equivalent to defining an implicit “evidence” parameter, which causes the compiler to generate a `TypeTag`for `T`). Finally, we invoke our method with `l` as its parameter, and call `tpe`which returns the type contained in the `TypeTag`. As we can see, we get the correct, complete type (including `List`’s concrete type argument), `List[Int]`.
>
> Once we have obtained the desired `Type` instance, we can inspect it, e.g.:

**首先**，导入`scala.reflect.runtime.universe`（只有导入了它才能使用`TypeTags`），并创建了一个`List[Int]`类型的`l`变量。**接着**，定义了一个`getTypeTag`方法，该方法具有**上下文绑定**的类型参数`T`（如*REPL*所示，这等价于定义隐式的`evidence`参数，促使编译器生成`T`的`TypeTag`）。**最后**，我们以`l`作为参数调用`getTypeTag`，并调用`tpe`，它返回`TypeTag`中包含的类型。可以看到，获得了正确且完整的类型（包括`List`的具体类型参数），`List[Int]`。

一旦我们获得了所需的`Type`实例，我们就可以检查它，例如：

```scala
scala> val decls = theType.decls.take(10)
decls: Iterable[ru.Symbol] = List(constructor List, method companion, method isEmpty, method head, method tail, method ::, method :::, method reverse_:::, method mapConserve, method ++)
```

####运行时创建类型实例

> #### INSTANTIATING A TYPE AT RUNTIME
>
> Types obtained through reflection can be instantiated by invoking their constructor using an appropriate “invoker” mirror (mirrors are expanded upon [below](https://docs.scala-lang.org/overviews/reflection/overview.html#mirrors)). Let’s walk through an example using the REPL:

使用适当的**调用者镜像**（mirror，在[下面](https://docs.scala-lang.org/overviews/reflection/overview.html#mirrors)展开），就能调用（**通过反射获得的类型**）的**构造函数**来创建实例。用*REPL*来看一个例子：

```scala
scala> case class Person(name: String)
defined class Person

scala> val m = ru.runtimeMirror(getClass.getClassLoader)
m: scala.reflect.runtime.universe.Mirror = JavaMirror with ...
```

> In the first step we obtain a mirror `m` which makes all classes and types available that are loaded by the current classloader, including class `Person`.

**第一步**获得一个镜像`m`，它使得由**当前类加载器加载**的所有类和类型都可用，包括类`Person`。

```scala
scala> val classPerson = ru.typeOf[Person].typeSymbol.asClass
classPerson: scala.reflect.runtime.universe.ClassSymbol = class Person

scala> val cm = m.reflectClass(classPerson)
cm: scala.reflect.runtime.universe.ClassMirror = class mirror for Person (bound to null)
```

> The second step involves obtaining a `ClassMirror` for class `Person` using the `reflectClass` method. The `ClassMirror` provides access to the constructor of class `Person`.

**第二步**，使用`reflectClass`方法为类`Person`获取`ClassMirror`，它提供了对`Person`类构造函数的访问。

```scala
scala> val ctor = ru.typeOf[Person].decl(ru.termNames.CONSTRUCTOR).asMethod
ctor: scala.reflect.runtime.universe.MethodSymbol = constructor Person
```

> The symbol for `Person`s constructor can be obtained using only the runtime universe `ru` by looking it up in the declarations of type `Person`.

**第三步**，只需使用~~==运行时宇宙==~~（runtime universe）`ru`，就能在`Person`类的声明中查找其构造函数的符号（注意上面*REPL*里`ctor`的类型`MethodSymbol`，关于符号的解释可以看[这](http://www.360doc.com/content/13/0819/21/7377734_308378587.shtml)）。

```scala
scala> val ctorm = cm.reflectConstructor(ctor)
ctorm: scala.reflect.runtime.universe.MethodMirror = constructor mirror for Person.<init>(name: String): Person (bound to null)

scala> val p = ctorm("Mike")
p: Any = Person(Mike)
```

**最后**，通过`ClassMirror`获取`Person`类构造器**符号**的`MethodMirror`，就可以创建`Person`的实例了。

#### 访问和调用运行时类型的成员

> #### ACCESSING AND INVOKING MEMBERS OF RUNTIME TYPES
>
> In general, members of runtime types are accessed using an appropriate “invoker” mirror (mirrors are expanded upon [below](https://docs.scala-lang.org/overviews/reflection/overview.html#mirrors)). Let’s walk through an example using the REPL:

通常，使用适当的**调用者镜像**（mirror，在[下面](https://docs.scala-lang.org/overviews/reflection/overview.html#mirrors)展开），就能访问运行时类型的成员。用*REPL*来看一个例子：

```scala
scala> case class Purchase(name: String, orderNumber: Int, var shipped: Boolean)
defined class Purchase

scala> val p = Purchase("Jeff Lebowski", 23819, false)
p: Purchase = Purchase(Jeff Lebowski,23819,false)
```

> In this example, we will attempt to get and set the `shipped` field of `Purchase` `p`, reflectively.

在这个例子中，将分别获取和设置变量`p`（`Purchase`类）的`shipped`字段。

```scala
scala> import scala.reflect.runtime.{universe => ru}
import scala.reflect.runtime.{universe=>ru}

scala> val m = ru.runtimeMirror(p.getClass.getClassLoader)
m: scala.reflect.runtime.universe.Mirror = JavaMirror with ...
```

> As we did in the previous example, we’ll begin by obtaining a mirror `m`, which makes all classes and types available that are loaded by the classloader that also loaded the class of `p` (`Purchase`), which we need in order to access member `shipped`.

和前面一样，为了访问`p`的`shipped`成员，需加载`Purchase`类，因此首先获得镜像`m`，这使得加载`Purchase`类的类加载器，加载所有可用的类和类型（因此加载了`Purchase`类）。

```scala
scala> val shippingTermSymb = ru.typeOf[Purchase].decl(ru.TermName("shipped")).asTerm
shippingTermSymb: scala.reflect.runtime.universe.TermSymbol = method shipped
```

> We now look up the declaration of the `shipped` field, which gives us a`TermSymbol` (a type of `Symbol`). We’ll need to use this `Symbol` later to obtain a mirror that gives us access to the value of this field (for some instance).

然后，查找`shiped`字段的声明，得到`TermSymbol`（一种`Symbol`）。后面我们需要使用这个`Symbol`来获得一个镜像，它允许我们访问某个实例该字段的值。

```scala
scala> val im = m.reflect(p)
im: scala.reflect.runtime.universe.InstanceMirror = instance mirror for Purchase(Jeff Lebowski,23819,false)

scala> val shippingFieldMirror = im.reflectField(shippingTermSymb)
shippingFieldMirror: scala.reflect.runtime.universe.FieldMirror = field mirror for Purchase.shipped (bound to Purchase(Jeff Lebowski,23819,false))
```

> In order to access a specific instance’s `shipped` member, we need a mirror for our specific instance, `p`’s instance mirror, `im`. Given our instance mirror, we can obtain a `FieldMirror` for any `TermSymbol` representing a field of `p`’s type.
>
> Now that we have a `FieldMirror` for our specific field, we can use methods`get` and `set` to get/set our specific instance’s `shipped` member. Let’s change the status of `shipped` to `true`.

为了访问实例的`shipped`成员，我们需要这个实例的镜像，即`p`的实例镜像`im`。有了实例镜像，我们可以为任何`TermSymbol`获得`FieldMirror`来表示`p`的字段。

现在我们有了用于特定字段的`FieldMirror`，可以使用`get`和`set`方法来获取/设置特定实例的`shipped`成员。下面，我们将``shipped``（发货）的状态更改为true。

```scala
scala> shippingFieldMirror.get
res7: Any = false

scala> shippingFieldMirror.set(true)

scala> shippingFieldMirror.get
res9: Any = true
```

### Java的运行时类 vs.  Scala的运行时类型

> ### Runtime Classes in Java vs. Runtime Types in Scala
>
> Those who are comfortable using Java reflection to obtain Java *Class* instances at runtime might have noticed that, in Scala, we instead obtain runtime *types*.
>
> The REPL-run below shows a very simple scenario where using Java reflection on Scala classes might return surprising or incorrect results.
>
> First, we define a base class `E` with an abstract type member `T`, and from it, we derive two subclasses, `C` and `D`.



```scala
scala> class E {
     |   type T
     |   val x: Option[T] = None
     | }
defined class E

scala> class C extends E
defined class C

scala> class D extends C
defined class D
```

Then, we create an instance of both `C` and `D`, meanwhile making type member`T` concrete (in both cases, `String`)

```
scala> val c = new C { type T = String }
c: C{type T = String} = $anon$1@7113bc51

scala> val d = new D { type T = String }
d: D{type T = String} = $anon$1@46364879
```

Now, we use methods `getClass` and `isAssignableFrom` from Java Reflection to obtain an instance of `java.lang.Class` representing the runtime classes of`c` and `d`, and then we test to see that `d`’s runtime class is a subclass of `c`’s runtime representation.

```
scala> c.getClass.isAssignableFrom(d.getClass)
res6: Boolean = false
```

Since above, we saw that `D` extends `C`, this result is a bit surprising. In performing this simple runtime type check, one would expect the result of the question “is the class of `d` a subclass of the class of `c`?” to be `true`. However, as you might’ve noticed above, when `c` and `d` are instantiated, the Scala compiler actually creates anonymous subclasses of `C` and `D`, respectively. This is due to the fact that the Scala compiler must translate Scala-specific (*i.e.,* non-Java) language features into some equivalent in Java bytecode in order to be able to run on the JVM. Thus, the Scala compiler often creates synthetic classes (i.e. automatically-generated classes) that are used at runtime in place of user-defined classes. This is quite commonplace in Scala and can be observed when using Java reflection with a number of Scala features, *e.g.* closures, type members, type refinements, local classes, *etc*.

In situations like these, we can instead use Scala reflection to obtain precise runtime *types* of these Scala objects. Scala runtime types carry along all type info from compile-time, avoiding these types mismatches between compile-time and run-time.

Below, we use define a method which uses Scala reflection to get the runtime types of its arguments, and then checks the subtyping relationship between the two. If its first argument’s type is a subtype of its second argument’s type, it returns `true`.

```
scala> import scala.reflect.runtime.{universe => ru}
import scala.reflect.runtime.{universe=>ru}

scala> def m[T: ru.TypeTag, S: ru.TypeTag](x: T, y: S): Boolean = {
    |   val leftTag = ru.typeTag[T]
    |   val rightTag = ru.typeTag[S]
    |   leftTag.tpe <:< rightTag.tpe
    | }
m: [T, S](x: T, y: S)(implicit evidence$1: scala.reflect.runtime.universe.TypeTag[T], implicit evidence$2: scala.reflect.runtime.universe.TypeTag[S])Boolean

scala> m(d, c)
res9: Boolean = true
```

As we can see, we now get the expected result– `d`’s runtime type is indeed a subtype of `c`’s runtime type.

## Environment

All reflection tasks require a proper environment to be set up. This environment differs based on whether the reflective task is to be done at run time or at compile time. The distinction between an environment to be used at run time or compile time is encapsulated in a so-called *universe*. Another important aspect of the reflective environment is the set of entities that we have reflective access to. This set of entities is determined by a so-called *mirror*.

Mirrors not only determine the set of entities that can be accessed reflectively. They also provide reflective operations to be performed on those entities. For example, in runtime reflection an *invoker mirror* can be used to invoke a method or constructor of a class.

### Universes

`Universe` is the entry point to Scala reflection. A universe provides an interface to all the principal concepts used in reflection, such as `Types`, `Trees`, and `Annotations`. For more details, see the section of this guide on [Universes](https://docs.scala-lang.org/overviews/reflection/environment-universes-mirrors.html), or the [Universes API docs](http://www.scala-lang.org/api/current/scala-reflect/scala/reflect/api/Universe.html) in package `scala.reflect.api`.

To use most aspects of Scala reflection, including most code examples provided in this guide, you need to make sure you import a `Universe` or the members of a `Universe`. Typically, to use runtime reflection, one can import all members of `scala.reflect.runtime.universe`, using a wildcard import:

```
import scala.reflect.runtime.universe._
```

### Mirrors

`Mirror`s are a central part of Scala Reflection. All information provided by reflection is made accessible through these so-called mirrors. Depending on the type of information to be obtained, or the reflective action to be taken, different flavors of mirrors must be used.

For more details, see the section of this guide on [Mirrors](https://docs.scala-lang.org/overviews/reflection/environment-universes-mirrors.html), or the [Mirrors API docs](http://www.scala-lang.org/api/current/scala-reflect/scala/reflect/api/Mirrors.html) in package `scala.reflect.api`.