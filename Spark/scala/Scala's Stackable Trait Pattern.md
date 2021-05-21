# Scala's Stackable Trait Pattern
> Summary
>
> This article describes a Scala design pattern in which traits provide stackable modifications to underlying core classes or traits.

**One way to use Scala's traits is as stackable modifications**. In this pattern, a trait (or class) can play one of three roles: the *base*, a *core*, or a *stackable*. The base trait (or abstract class) defines an abstract interface that all the cores and stackables extend, as shown in Figure 1. The core traits (or classes) implement the abstract methods defined in the base trait, and provide basic, core functionality. Each stackable overrides one or more of the abstract methods defined in the base trait, using Scala's `abstract` `override` modifiers, and provides some behavior and at some point invokes the `super` implementation of the same method. In this manner, the stackables modify the behavior of whatever core they are mixed into.

> **一种使用Scala `trait` 的方法是作为可堆叠的修改**。在这种模式中，一个 `trait`（或类）可以扮演三个角色中的一个：基类、`core` 或 `stackable`。作为基类的 `trait`（或抽象类）定义了`core` 和 `stackable` 所有可以扩展的抽象接口，如图1所示。作为 `core` 的 `trait`（或类）实现基类 `triat` 中定义的抽象方法，并提供基本的核心功能。每个 `stackable` 使用 Scala 的 `abstract override` 修饰符重写基类 `trait` 中定义的一个或多个抽象方法以提供某些行为，并在某些时候调用重写方法的超类实现。通过这种方式，`stackable` 可以修改它们所混合到的任何 `core trait` 的行为。

![img](https://www.artima.com/images/StackableTraits.gif)**Figure 1. Roles in the stackable trait pattern.**

**This pattern is similar in structure to the decorator pattern**, except it involves decoration for the purpose of class composition instead of object composition. Stackable traits *decorate* the core traits at compile time, similar to the way decorator objects modify core objects at run time in the decorator pattern.

> **结构上，堆叠器模式和装饰器模式**相似，不同之处在于它基于**类组合**而非**对象组合**进行装饰。可堆叠特征在编译时**装饰**核心特征，类似于装饰器模式在运行时，通过==装饰器对象==修改核心对象。

As an example, consider stacking modifications to a queue of integers. (This example is adapted from chapter 12 or [*Programming in Scala*](https://www.artima.com/shop/programming_in_scala), by Martin Odersky, Lex Spoon, and Bill Venners.) The queue will have two operations: `put`, which places integers in the queue, and `get`, which takes them back out. Queues are first-in, first-out, so `get` should return the integers in the same order they were put in the queue.

Given a class that implements such a queue, you could define traits to perform modifications such as these:

- `Doubling`: double all integers that are put in the queue
- `Incrementing`: increment all integers that are put in the queue
- `Filtering`: filter out negative integers from a queue

These three traits represent *modifications*, because they modify the behavior of an underlying "core" queue class rather than defining a full queue class themselves. The three are also *stackable*. You can select any of the three you like, mix them into a class, and obtain a new class that has all of the modifications you chose.

> 这三个特征表示“修改”，因为它们修改了底层“核心”队列类的行为，而不是自己定义完整的队列类。 这三个也是**可堆叠的**。可选择将你喜欢的 `trait ` 混入到某个类中，获得一个包含所有修改的新类。

```scala
// Listing 1: Abstract class IntQueue.
abstract class IntQueue {
  def get(): Int
  def put(x: Int)
}
```

An abstract `IntQueue` class (the "base") is shown in Listing 1. `IntQueue` has a `put` method that adds new integers to the queue and a `get` method that removes and returns them. A basic implementation of `IntQueue` (a "core" class), which uses an `ArrayBuffer`, is shown in Listing 2.


```scala
// Listing 2: A `BasicIntQueue` implemented with an `ArrayBuffer`.
import scala.collection.mutable.ArrayBuffer

class BasicIntQueue extends IntQueue {
  private val buf = new ArrayBuffer[Int]
  def get() = buf.remove(0)
  def put(x: Int) { buf += x }
}
```

Class `BasicIntQueue` has a private field holding an array buffer. The `get` method removes an entry from one end of the buffer, while the `put` method adds elements to the other end. Here's how this implementation looks when you use it:

``` scala
  scala> val queue = new BasicIntQueue
  queue: BasicIntQueue = BasicIntQueue@24655f

  scala> queue.put(10)

  scala> queue.put(20)

  scala> queue.get()
  res9: Int = 10

  scala> queue.get()
  res10: Int = 20
```

So far so good. Now take a look at using traits to modify this behavior. Listing 3 shows a trait that doubles integers as they are put in the queue. The `Doubling` trait has two funny things going on. The first is that it declares a superclass, `IntQueue`. This declaration means that the trait can only be mixed into a class that also extends `IntQueue`.

```scala
//Listing 3: The `Doubling` stackable modification trait.
  trait Doubling extends IntQueue {
    abstract override def put(x: Int) { super.put(2 * x) } // 注意这里的 abstract override
  }
```

The second funny thing is that the trait has a `super` call on a method declared abstract. Such calls are illegal for normal classes, because they will certainly fail at run time. For a trait, however, such a call can actually succeed. Since `super` calls in a trait are dynamically bound, the `super` call in trait `Doubling` will work so long as the trait is mixed in *after* another trait or class that gives a concrete definition to the method.

> 第二个有趣的事情是，`trait` 可调用超类声明为 `abstract` 的方法（即 `super.put` ）。对于普通类而言，这种调用是非法的，因为在运行时肯定会失败。 但是，对于 `trait`  而言，这样的调用实际上是可以成功。 这是因为特征中的 `super` 调用是**动态绑定**的，因此只要将 `trait`  混入另一个为该方法提供具体定义的 `trait`  或类之后，`Doubling` 中的 `super` 调用就会起作用。

This arrangement is frequently needed with traits that implement stackable modifications. To tell the compiler you are doing this on purpose, you must mark such methods as `abstract override`. This combination of modifiers is only allowed for members of traits, not classes, and it means that the trait must be mixed into some class that has a concrete definition of the method in question.

Here's how it looks to use the trait:

> 实现可堆叠修改的特征经常需要这样操作。为了告诉编译器是有意这么做，必须将这些方法标记为 `abstract override`。这种修饰符组合不适用于类成员，只适用于 `trait` 成员，这意味着`trait` 必须混入某个类，且该类具体定义了 `abstract override` 的方法。
> 
>
> 下面是如何使用 `trait`：

```scala
  scala> class MyQueue extends BasicIntQueue with Doubling
  defined class MyQueue

  scala> val queue = new MyQueue
  queue: MyQueue = MyQueue@91f017

  scala> queue.put(10)

  scala> queue.get()
  res12: Int = 20
```

In the first line in this interpreter session, we define class `MyQueue`, which extends `BasicIntQueue` and mixes in `Doubling`. We then put a 10 in the queue, but because `Doubling` has been mixed in, the 10 is doubled. When we get an integer from the queue, it is a 20.

Note that `MyQueue` defines no new code. It simply identifies a class and mixes in a trait. In this situation, you could supply "`BasicIntQueue` `with` `Doubling`" directly to `new` instead of defining a named class. It would look as shown in Listing 4:

```scala
// Listing 4: Mixing in a trait when instantiating with `new`.
scala> val queue = new BasicIntQueue with Doubling
queue: BasicIntQueue with Doubling = \$anon\$1@5fa12d

scala> queue.put(10)

scala> queue.get()
res14: Int = 20
```

To see how to stack modifications, we need to define the other two modification traits, `Incrementing` and `Filtering`. Implementations of these traits are shown in Listing 5:

```scala
// Listing 5: Stackable modification traits `Incrementing` and `Filtering`.
trait Incrementing extends IntQueue {
  abstract override def put(x: Int) { super.put(x + 1) }
}

trait Filtering extends IntQueue {
  abstract override def put(x: Int) {
    if (x >= 0) super.put(x)
  }
}
```

Given these modifications, you can now pick and choose which ones you want for a particular queue. For example, here is a queue that both filters negative numbers and adds one to all numbers that it keeps:

```
scala> val queue = (new BasicIntQueue
|     with Incrementing with Filtering)
queue: BasicIntQueue with Incrementing with Filtering...

scala> queue.put(-1); queue.put(0); queue.put(1)

scala> queue.get()
res15: Int = 1

scala> queue.get()
res16: Int = 2
```

The order of mixins is significant. (Once a trait is mixed into a class, you can alternatively call it a *mixin*.) Roughly speaking, traits further to the right take effect first. When you call a method on a class with mixins, the method in the trait furthest to the right is called first. If that method calls `super`, it invokes the method in the next trait to its left, and so on. In the previous example, `Filtering`'s `put` is invoked first, so it removes integers that were negative to begin with. `Incrementing`'s `put` is invoked second, so it adds one to those integers that remain.

If you reverse the order, first integers will be incremented, and *then* the integers that are still negative will be discarded:

```scala
scala> val queue = (new BasicIntQueue
                    |     with Filtering with Incrementing)
queue: BasicIntQueue with Filtering with Incrementing...

scala> queue.put(-1); queue.put(0); queue.put(1)

scala> queue.get()
res17: Int = 0

scala> queue.get()
res18: Int = 1

scala> queue.get()
res19: Int = 2
```

Overall, code written in this style gives you a great deal of flexibility. You can define sixteen different classes by mixing in these three traits in different combinations and orders. That's a lot of flexibility for a small amount of code, so you should keep your eyes open for opportunities to arrange code as stackable modifications.



## 参考

https://cloud.tencent.com/developer/article/1054329?from=10680

https://pavelfatin.com/design-patterns-in-scala/