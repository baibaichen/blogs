> 1. [ ] [[video] Scalaz State Monad](https://www.youtube.com/watch?v=Jg3Uv_YWJqI)
> 2. [ ] [[video] Scala Monads: Declutter Your Code With Monadic Design](https://www.youtube.com/watch?v=Mw_Jnn_Y5iA)


**Functional programming is the practice of composing programs using functions**

Each <- of the for expression is converted into a map or flatMap call. These methods are each associated with a concept in category theory. The map method is associated with functors, and the flatMap method is associated with monads.

In essence, Monad is important because it gives us a principled way to wrap context information around a value, then propagate and evolve that context as the value evolves. Hence, it minimizes coupling between the values and contexts while the presence of the Monad wrapper informs the reader of the context’s existence.

本质上，Monad之所以重要，是因为它提供了『**一个用于包装某个值上下文**』的规范化方法，当这个值发生变化时，传播和改变对应的上下文。因此，它最大限度地减少了值和上下文之间的耦合，而又可以通过Monad通知读者上下文的存在。

|_| for 产生式|结果|
|---|---|---|
|Translating for expressions with one generator |for (x <- expr1) yield expr2|**expr1.map(x => expr2)**|
|Translating for expressions starting with a generator and a filter |for (x <-expr1 if expr2) yield expr3|for (x <-expr1 withFilter (x => expr2)) yield expr3 <br />=> **expr1 withFilter (x => expr2) map (x => expr3)**|
|Translating for expressions starting with two generators|for (x <-expr1;y <-expr2;seq) yield expr3|**expr1.flatMap(x => for (y <-expr2;seq) yield expr3)**|
|Translating patterns in generators|TODO:|-|
|Translating definitions|for (x <-expr1;y = expr2; seq) yield expr3|for ((x, y) <-**for(x <-expr1) yield (x, expr2);** seq) yield expr3|
|Translating for loops|for (x <-expr1) body|expr1.foreach (x => body)|
|-|for (x <-expr1;if expr2; y <-expr3) body|expr1.withFilter(x => expr2).foreach(x =>expr3.foreach (y => body))|

    def converter: IO[Unit] = for {
      _ <- PrintLine("Enter a temperature in degrees Fahrenheit: ")
      d <- ReadLine.map(_.toDouble)
      _ <- PrintLine(fahrenheitToCelsius(d).toString)
    } yield ()

    PrintLine("Enter a temperature in degrees Fahrenheit: ").
      flatMap(_ => for {d <- ReadLine.map(_.toDouble)
                        _ <- PrintLine(fahrenheitToCelsius(d).toString)
                       } yield ()
             )

    PrintLine("Enter a temperature in degrees Fahrenheit: ").
      flatMap(_ => ReadLine.map(_.toDouble).
                     flatMap(d => for {_ <- PrintLine(fahrenheitToCelsius(d).toString)} yield()
                            )
             )

    PrintLine("Enter a temperature in degrees Fahrenheit: ").
      flatMap(_ => ReadLine.map(_.toDouble).
                     flatMap(d => PrintLine(fahrenheitToCelsius(d).toString).
                                  map.(_ =>())
                            )
             )

