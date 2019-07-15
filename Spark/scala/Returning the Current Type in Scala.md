## Returning the "Current" Type in Scala

A common question on the `#scala` IRC channel is

> I have a type hierarchy … how do I declare a supertype method that returns the “current” type?

This question comes up a lot because Scala encourages immutability, so methods that return a modified copy of `this` are quite common. Making the return type of such methods sufficiently precise is tricky, and is the subject of this article.

The closest thing to a “standard” approach (as seen in stdlib collections, for example) is to use an **F-bounded type**, which *mostly* works, but cannot fully enforce the constraint we’re after (it still takes some discipline and leaves room for error). Shout-out here to [@nuttycom](https://twitter.com/nuttycom) for his [prior work](http://logji.blogspot.com/2012/11/f-bounded-type-polymorphism-give-up-now.html) exploring the pitfalls of this approach.

A better strategy is to use a **typeclass**, which solves the problem neatly and leaves little room for worry. In fact it’s worth considering abandoning subtype polymorphism altogether in these situations.

We will examine the problem and both solutions, and finish up by exploring **heterogeneous collections** of these beasties, which involves some pleasantly fancy types. But first let’s talk about…

### The Problem

Say we have an open trait for pets, with an unknown number of implementations. Let’s say every type of `Pet` has a name, as well as a method that returns an otherwise identical copy with a new name.

> **Our problem is this:** for any expression `x` with some type `A <: Pet`, ensure that `x.renamed(...)` also has type `A`. To be clear: this is a *static* guarantee that we want, not a runtime property.

Right. So here is our first attempt, and one implementation.

```scala
trait Pet {
  def name: String
  def renamed(newName: String): Pet
}

case class Fish(name: String, age: Int) extends Pet {
  def renamed(newName: String): Fish = copy(name = newName)
}
```

In our `Fish` implementation `name` is implemented via a case class field, and the `renamed` method simply delegates to the generated `copy` method … but note that it returns a `Fish`rather than a `Pet`. This is allowed because return types are in covariant position; it’s always ok to return something **more specific** than what is promised.

Just as a sanity check, we can create a `Fish` and rename it and all is well; the static type of the returned value is what we want.

```scala
scala> val a = Fish("Jimmy", 2)
a: Fish = Fish(Jimmy,2)

scala> val b = a.renamed("Bob")
b: Fish = Fish(Bob,2)
```

However a limitation of this approach is that our trait doesn’t actually constrain the implementation very much; we are simply required to return a `Pet`, not necessarily the *same type*of pet. So here is a `Kitty` that turns into a `Fish` when we rename it.

```scala
case class Kitty(name: String, color: Color) extends Pet {
  def renamed(newName: String): Fish = new Fish(newName, 42) // oops
}
```

We also run into problems trying to abstract over renaming. For example, this attempt at a general renaming method fails to compile because the return type of `renamed` for an arbitrary `A <: Pet` is not specific enough; the best we can do is return `Pet`.

```
def esquire[A <: Pet](a: A): A = a.renamed(a.name + ", Esq.")
<console>:28: error: type mismatch;
 found   : Pet
 required: A
       def esquire[A <: Pet](a: A): A = a.renamed(a.name + ", Esq.")
                                                 ^
```

So this approach doesn’t meet our stated goal of requiring that `renamed` return the same type as its receiver, and we can’t abstract over our renaming operation. So let’s see what we can do if we make our types a bit fancier.

### F-Bounded Types

An F-bounded type is **parameterized over its own subtypes**, which allows us to “pass” the implementing type as an argument to the superclass. The self-referential nature of `Pet[A <: Pet[A]]` is puzzling when you first see it; if it doesn’t click for you just keep on reading and it should start making more sense.

```scala
trait Pet[A <: Pet[A]] {
  def name: String
  def renamed(newName: String): A // note this return type
}
```

Ok, so any subtype of `Pet` needs to pass “itself” as a type argument.

```scala
case class Fish(name: String, age: Int) extends Pet[Fish] { // note the type argument
  def renamed(newName: String) = copy(name = newName)
}
```

And all is well.

```scala
scala> val a = Fish("Jimmy", 2)
a: Fish = Fish(Jimmy,2)

scala> val b = a.renamed("Bob")
b: Fish = Fish(Bob,2)
```

This time we **can** write our generic renaming method because we now have a more specific return type for `renamed`: any `Pet[A]` will return an `A`.

```scala
scala> def esquire[A <: Pet[A]](a: A): A = a.renamed(a.name + ", Esq.")
esquire: [A <: Pet[A]](a: A)A

scala> esquire(a)
res8: Fish = Fish(Jimmy, Esq.,2)
```

So this is a big win. We now have a way to talk about the “current” type because it appears as a parameter.

However we still have a problem with lying about what the “current” type is; **there is nothing forcing us to pass the correct type argument**. So here again is our `Kitty` that turns into a `Fish`.

```scala
case class Kitty(name: String, color: Color) extends Pet[Fish] { // oops
  def renamed(newName: String): Fish = new Fish(newName, 42)
}
```

Rats. What we need is a way to restrict the implementing class *claiming* to be an `A` to *actually* be an `A`. And it turns out that Scala does give us a way to do that: a **self-type**annotation.

```
trait Pet[A <: Pet[A]] { this: A => // self-type
  def name: String
  def renamed(newName: String): A 
}
```

Now when we try to define our fishy `Kitty` the compiler says nope.

```
case class Kitty(name: String, color: Color) extends Pet[Fish] {
  def renamed(newName: String): Fish = new Fish(newName, 42)
}
<console>:19: error: illegal inheritance;
 self-type Kitty does not conform to Pet[Fish]'s selftype Fish
       case class Kitty(name: String, color: Color) extends Pet[Fish] {
                                                            ^
```

This boxes us in considerably, and we may think we have won. But alas it turns out that we can still lie about the “current” type by extending *another* type that correctly meets the constraint. Subtyping has provided an unwanted loophole.

```
class Mammal(val name: String) extends Pet[Mammal] {
  def renamed(newName: String) = new Mammal(newName)
}

class Monkey(name: String) extends Mammal(name) // hmm, Monkey is a Pet[Mammal]
```

And on it goes. I am not aware of any way to further constrain the F-bounded type. So if we use this technique we can do fairly well, but we still can’t totally guarantee that `renamed`meets the specificaton. Also note that the clutter introduced by the type parameter on `Pet` doesn’t add any information; it’s purely a mechanism to restrict implementations.

So let’s try another approach.

### How about a Typeclass?

As is often the case, we can avoid our subtyping-related problems by using a typeclass. Let’s redefine `Pet` without our `renamed` method, and instead define an orthogonal [typeclass](http://tpolecat.github.io/2013/10/12/typeclass.html)to deal with this operation.

```
trait Pet {
  def name: String
}

trait Rename[A] {
  def rename(a: A, newName: String): A
}
```

We can now define `Fish` and an *instance* of `Rename[Fish]`. We make the instance implicit and place it on the companion object so it will be available during implicit search.

```
case class Fish(name: String, age: Int) extends Pet

object Fish {
  implicit val FishRename = new Rename[Fish] {
    def renamed(a: Fish, newName: String) = a.copy(name = newName)
  }
}
```

And we can use an implicit class to make this operation act like a method as before. With this extra help any `Pet` with a `Rename` intance will automatically gain a `renamed` method by implicit conversion.

```
implicit class RenameOps[A](a: A)(implicit ev: Rename[A]) {
  def renamed(newName: String) = ev.renamed(a, newName)
}
```

And our simple test still works, although the mechanism is quite different.

```
scala> val a = Fish("Jimmy", 2)
a: Fish = Fish(Jimmy,2)

scala> val b = a.renamed("Bob")
b: Fish = Fish(Bob,2)
```

With the typeclass-based design there is no simply way to define a `Rename[Kitty]` instance that returns anything other than another `Kitty`; the types make this quite clear. And our `esquire` method is a snap; the type bounds are different, but the implementation it is identical to the one in the F-bounded case above.

```
scala> def esquire[A <: Pet : Rename](a: A): A = a.renamed(a.name + ", Esq.")
esquire: [A <: Pet](a: A)(implicit evidence$1: Rename[A])A

scala> esquire(a)
res10: Fish = Fish(Jimmy, Esq.,2)e
```

This is a **general strategy**. By identifying methods that require us to return the “current” type and moving them to a typeclass we can guarantee that our desired constraint is met. However it does have a bit of a smell: functionality is divided between trait and typeclass, and there is nothing requiring that all `Pet` implementations have a `Rename` instance (we had to specify both an upper bound *and* a context bound in `esquire` above).

So what if we abandon the super-trait altogether?

### How about *only* a Typeclass?

Consider the following implementation, where `Pet` is a typeclass with associated syntax. We have abandoned subtype polymorphism altogether and are defining pets via *ad-hoc*polymorphism: any type `A` can act as a `Pet`, given an instance of `Pet[A]`.

```
trait Pet[A] {
  def name(a: A): String
  def renamed(a: A, newName: String): A
}

implicit class PetOps[A](a: A)(implicit ev: Pet[A]) {
  def name = ev.name(a)
  def renamed(newName: String): A = ev.renamed(a, newName)
}
```

Here is our `Fish` class, now without an interesting superclass, and an implicit instance `Pet[Fish]` on its companion object.

```
case class Fish(name: String, age: Int)

object Fish {
  implicit val FishPet = new Pet[Fish] {
    def name(a: Fish) = a.name
    def renamed(a: Fish, newName: String) = a.copy(name = newName)
  }
}
```

And the `renamed` method works by implicit application of `PetOps`.

```
scala> Fish("Bob", 42).renamed("Steve")
res0: Fish = Fish(Steve,42)
```

There is an informal conjecture that *ad-hoc* and parametric polymorphism are really all we need in a programming language; we can get along just fine without subtyping. Haskell is the prime example of such a language, and it’s an interesting exercise to take this approach in Scala, or at least making it part of our design space. In my experience I have never regretted replacing a superclass with a typeclass.

------

This is probably a good place to end this post, but I’m going to press on a bit further. Because once we have answered the lead-in question at the top of the page, the inevitable next question is:

> Ok cool, I have an F-bounded type (or a typeclass) working, but I can’t figure out how to put a bunch of instances in a list without losing all my type information.

So let’s chase that down.

### Bonus Round: How do we deal with collections?

An interesting exercise is to consider the case where we have a heterogeneous collection of pets. Specifically, how might we map `esquire` over a list of them? I should note that at least for me this is an academic exercise; I have never had to do this in real life.

Let’s consider the F-bounded case first. Here is our full implementation:

```
import java.awt.Color

trait Pet[A <: Pet[A]] { this: A =>
  def name: String
  def renamed(newName: String): A 
}

case class Fish(name: String, age: Int) extends Pet[Fish] { 
  def renamed(newName: String) = copy(name = newName)
}

case class Kitty(name: String, color: Color) extends Pet[Kitty] {
  def renamed(newName: String) = copy(name = newName)
}

def esquire[A <: Pet[A]](a: A): A = a.renamed(a.name + ", Esq.")

val bob  = Fish("Bob", 12)
val thor = Kitty("Thor", Color.ORANGE)
```

Mapping `esquire` over a list containing both `bob` and `thor` is tricker than you might expect, but it *is* representable in Scala. You might want to **stop here** and try it out in the REPL (you can `:paste` the block above) … `List(bob, thor).map(esquire)` is a reasonable place to start, although it doesn’t come close to compiling.

So it turns out that the properly quantified element type for such a list is `A forSome { A <: Pet[A] }`, meaning that each element is a distinct, properly f-bounded subtype of `Pet`. Furthermore, the type of the default η-expanded `esquire` is not precise enough; this is a rare case where `foo _` and `foo(_)` are not equivalent. In any case, behold:

```
scala> List[A forSome { type A <: Pet[A] }](bob, thor).map(esquire(_))
res18: List[A forSome { type A <: Pet[A] }] = List(Fish(Bob, Esq.,12), Kitty(Thor, Esq.,java.awt.Color[r=255,g=200,b=0]))
```

In the *ad-hoc* implementation we have a different problem. For reference, here is our full implementation.

```
import java.awt.Color

trait Pet[A] {
  def name(a: A): String
  def renamed(a: A, newName: String): A
}

implicit class PetOps[A](a: A)(implicit ev: Pet[A]) {
  def name = ev.name(a)
  def renamed(newName: String): A = ev.renamed(a, newName)
}

case class Fish(name: String, age: Int)

object Fish {
  implicit object FishPet extends Pet[Fish] {
    def name(a: Fish) = a.name
    def renamed(a: Fish, newName: String) = a.copy(name = newName)
  }
}

case class Kitty(name: String, color: Color)

object Kitty {
  implicit object KittyPet extends Pet[Kitty] {
    def name(a: Kitty) = a.name
    def renamed(a: Kitty, newName: String) = a.copy(name = newName)
  }
}

def esquire[A: Pet](a: A): A = a.renamed(a.name + ", Esq.")

val bob  = Fish("Bob", 12)
val thor = Kitty("Thor", Color.ORANGE)
```

Here we have a challenge because it’s not clear at all what the element type of a list containing `bob` and `thor` should be. They have no common supertype, and the existence of `Pet`instances is not something we can express as a type argument; `List[A: Pet]` is not a valid type.

In order to map `esquire` over our list we must remember that it actually takes *two* arguments: an `A` and a `Pet[A]`. And indeed each list element will need to carry its `Pet` instance along. So the type of the list needs to be `(A, Pet[A]) forSome { type A }`, meaning that each element has a distinct type `A` but is paired with a corresponding `Pet` instance.

```
scala> val pets = List[(A, Pet[A]) forSome { type A }]((bob, implicitly[Pet[Fish]]), (thor, implicitly[Pet[Kitty]]))
pets: List[(A, Pet[A]) forSome { type A }] = List((Fish(Bob,12),Fish$FishPet$@1d4c9cde), (Kitty(Thor,java.awt.Color[r=255,g=200,b=0]),Kitty$KittyPet$@31f63352))
```

Mapping over this list should be easy, right?

```
scala> pets.map(p => esquire(p._1)(p._2))
<console>:23: error: type mismatch;
 found   : Pet[(some other)A(in value pets)]
 required: Pet[A(in value pets)]
              pets.map(p => esquire(p._1)(p._2))
                                            ^
```

Uhhh. Okay? The problem here is that the connection between the types of `p._1` and `p._2` is lost in this context, so the compiler no longer knows that they line up correctly. The way to fix this, and **in general** the way to prevent the loss of existentials, is to use a pattern match.

```
scala> pets.map { case (a, pa)  => esquire(a)(pa) }
res6: List[Any] = List(Fish(Bob, Esq.,12), Kitty(Thor, Esq.,java.awt.Color[r=255,g=200,b=0]))
```

So there you go. You should be unsettled at this point. Sorry about that.

### But Wait, There’s More!

After posting this article it was suggested that I demonstrate some other ways to deal with the `map` operation over a lists where the elements are of unrelated types but have instances of a common typeclass. So here goes.

[@nuttycom](https://twitter.com/nuttycom) suggested turning the existential in `(A, Pet[A]) forSome { type A }` into a type member, so let’s look at that one first. Our `∃[F[_]]` trait (sorry, couldn’t resist) is a wrapper for a value of *some type* that has an instance of `F`. In this encoding we can map over a `List[∃[Pet]]` without the need for a pattern-match. Still kind of a pain, but worthwhile if you’re doing it more than once.

```
trait ∃[F[_]] {
  type A
  val a: A
  val fa: F[A]
  override def toString = a.toString
}

object ∃ {
  def apply[F[_], A0](a0: A0)(implicit ev: F[A0]): ∃[F] =
    new ∃[F] {
      type A = A0
      val a = a0
      val fa = ev
    }
}

scala> List[∃[Pet]](∃(bob), ∃(thor)).map(e => ∃(esquire(e.a)(e.fa))(e.fa))
res15: List[∃[Pet]] = List(Fish(Bob, Esq.,12), Kitty(Thor, Esq.,java.awt.Color[r=255,g=200,b=0]))
```

And finally, a truly spectacular approach is to use a [shapeless](https://github.com/milessabin/shapeless) `HList`, which has a very precise type that identifies each element individually. We can `map` over an `HList` using a polymorphic function value called a `Poly1`, which (unlike `Function1`) **does** allow us to express the desired typeclass constraint. I admit that I had to ask [@travisbrown](https://twitter.com/travisbrown) for help … I really do need to sit down and learn this stuff.

```
import shapeless._

object polyEsq extends Poly1 {
  implicit def default[A: Pet] = at[A](esquire(_))
}

scala> (bob :: thor :: HNil) map polyEsq // output reformatted for readability
res11: shapeless.::[Fish,shapeless.::[Kitty,shapeless.HNil]] = 
  Fish(Bob, Esq.,12) :: 
  Kitty(Thor, Esq.,java.awt.Color[r=255,g=200,b=0]) :: 
  HNil
```

### Wrapping Up

Right. So we have explored two ways to return the “current” type in Scala, touched on the tension between subtyping and *ad-hoc* polymorphism, and gotten slightly bloodied playing with existential types. I hope this answered some questions and opened new avenues of exploration.

If you have any questions or comments, or wish to argue about anything above, please find me on IRC or Twitter.