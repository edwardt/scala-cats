# Hands-On Category Theory


## Introduction

Imagine building a Web service that takes input from a client, performs some computation on the server, and returns output to the client.

```
  Client    ||    Server        Service
  ------    ||    ------        -------
     |      ||       |             |
    .-.     ||       |             |
    | |-----||----->.-.            |
    | |     ||      | |---------->.-.
    | |     ||      | |           | |
    | |     ||      | |<----------|_|
    | |<----||------|_|            |
    |_|     ||       |             |
     |      ||       |             |
============||===========================
  Dragons   ||         Rainbows
```

When processing raw input from a client, we don't have the luxury of validated, well-typed data -- the input might be malformed, incomplete, malicious, or missing entirely.

To bridge the gap between the unsafe Web and our warm and fuzzy functional code, we can build and use a small library out a few principles from category theory.

## Background

In the examples below, we focus specifically on Scala's `Either` data type, which takes one of two values:

```
sealed abstract class Either[+A, +B]
case class Left[+A, +B](a: A) extends Either[A, B]
case class Right[+A, +B](b: B) extends Either[A, B]
```

There's nothing inherently special about `Left` or `Right` -- they're just boxes for holding values.

By convention, `Left` captures "erroneous" or "invalid" values, and `Right` contains "error-free" or "valid" values, where "erroneousness" and "validity" are defined by context.

For example, consider the function `atoi`, which attempts to parse a string representation of an integer:

```
def atoi(x: String): Either[String,Int] =
  try {
    Right(x.toInt)
  } catch {
    case _ : Throwable => Left("'" + x + "' is not an integer")
  }
```

If `x.toInt` succeeds, `atoi` returns a `Right` containing the parsed integer value.  If not, it returns a `Left` containing an error message:

```
scala> val one = atoi("1")
one: Either[String,Int] = Right(1)

scala> val two = atoi("two")
two: Either[String,Int] = Left('two' is not an integer)
```

## Functors

Consider a simple absolute value function:

```
val abs: Int => Int = math.abs
```

The type of this function is `Int => Int`.  When we supply it an integer, we get back an integer.

```
scala> val twenty = abs(-20)
twenty: Int = 20
```

Now imagine that we want to turn this function into a Web service.  We can't be sure that the input provided by the client will indeed be an integer, so this is a good time to use `Either`.  We'll pass our client's input through `atoi` to get an `Either[String,Int]`.

Now we want a nice way to apply our `abs` function if and only if `atoi` returns valid data, and return an error message if not.  We need a functor.

Given some `F[A]`, a functor lifts a function of type `A => B` to a function of type `F[A] => F[B]`.

```
trait Functor[A,F[_]] {
  def map[B](f: A => B): F[B]
}
```

Picture the category of "regular" types next to the category of "lifted" types.  The `map` function lets us apply a morphism from the "regular category" to an object in the "lifted" category.

```
.--------------.         .-----------------------.
|  Category _  |         |     Category F[_]     |
|--------------|         |-----------------------|
| a: A         |         |   aF: F[A]            |
| b: B         |         |   bF: F[B]            |
| f: A => B  ~~~~~ map ~~~~> fF: F[A] => F[B]    |
'--------------'         '-----------------------'

```

We need to lift `Int => Int` to `F[Int] => F[Int]`, where `F[_]` is defined as `Either[String,_]` using a type lambda.  This produces a function of type `Either[String,Int] => Either[String,Int]`.

While we're at it, let's put it in an implicit function to implement a type class:

```
implicit def eitherFunctor[A,Z](x: Either[Z,A]) =
  new Functor[A,({type EitherZ[B] = Either[Z,B]})#EitherZ] {
    override def map[B](f: A => B): Either[Z,B] =
      x match {
        case Left(l) => Left(l)
        case Right(r) => Right(f(r))
      }
  }
```

Now we can pimp `map` onto any `Either` instance:

```
scala> val negativeTwenty: Either[String,Int] = Right(-20)
negativeTwenty: Either[String,Int] = Right(-20)

scala> val twenty = negativeTwenty map abs
twenty: scala.util.Either[String,Int] = Right(20)
```

Finally, we can plug it into our `atoi` function:

```
scala> val twenty = atoi("-20") map abs
twenty: scala.util.Either[String,Int] = Right(20)

scala> val notTwenty = atoi("negative twenty") map abs
notTwenty: scala.util.Either[String,Int] = Left('negative twenty' is not an integer)
```

Sometimes it's handy to do all this in reverse by first lifting a function and then applying it to a lifted value:

```
implicit def fnCofunctor[A,B](g: A => B) =
  new {
    def <%>[C](x: Either[C,A]) = x map g
  }
```

Now we can use `<%>` as an infix operator to implicity lift `abs` and apply it to a value returned by `atoi`:

```
scala> val twenty = abs <%> atoi("-20")
twenty: scala.util.Either[String,Int] = Right(20)
```

We will come back to the `<%>` operator in the next section.

## Applicative functors

So far we've built enough code to cleanly parse some input and, if it is valid, apply it to a function.

Now consider a higher arity function:

```
val add2: Int => Int => Int = { x => y => x + y }
```

This function has two inputs.  To turn it into a Web service as before, we'll need to parse two separate values and apply them both.  We need an applicative functor.

An applicative functor builds upon a functor with a way to apply an already-lifted function of type `F[A => B]` as a function of type `F[A] => F[B]`.

```
trait Applicative[A, F[_]] extends Functor[A, F] {
  def ap[B](f: F[A => B]): F[B]
}
```

Picture the categories from before with an additional transformation, `ap`, that converts a morphism within the "lifted" category:

```
.--------------.         .-----------------------.
|  Category _  |         |     Category F[_]     |
|--------------|         |-----------------------|
| a: A         |         |   aF: F[A]            |
| b: B         |         |   bF: F[B]            |
| f: A => B  ~~~~~ map ~~~~> fF: F[A] => F[B]  <~~~ ap ~.
|              |         |                       |      |
|              |         |   gF: F[A => B]  ~~~~~~~~~~~~'
'--------------'         '-----------------------'

```

This gives us a way to apply an `Either[Z,A]` to an `Either[Z,A => B]` to get an `Either[Z,B]`, where `F[_]` is `Either[Z,_]`.

Note that `B` might itself be a function `C => D`, giving us another `ap`-able `Either[Z,C => D]`.  In our case, `A => B` is `Int => Int => Int`.

Note also that, since we have multiple inputs to parse, we will potentially be capturing multiple error messages (one per input).  We need to change our parser slightly:

```
def atoi(x: String): Either[List[String], Int] =
  try {
    Right(x.toInt)
  } catch {
    case _ : Throwable => Left(List("'" + x + "' is not an integer"))
  }
```

Our parser now gives us either the parsed integer, or a list of error messages.

To build up lists of errors without coupling ourselves to the `List` API, we introduce `Semigroup` to generalize the characteristic of "appendability":

```
trait Semigroup[A] {
  def append(x: A): A
}
```

```
implicit def listSemigroup[A](x: List[A]): Semigroup[List[A]] =
  new Semigroup[List[A]] {
    override def append(y: List[A]) = x ++ y
  }
```

Now we can bang out an applicative functor for `Either`.  As before, we'll put it in an implicit function:

```
implicit def EitherApplicative[A,Z](x: Either[Z,A])(implicit zs: Z => Semigroup[Z]) =
  new Applicative[A,({type EitherZ[B] = Either[Z,B]})#EitherZ] {
    override def map[B](f: A => B) =
      x match {
        case Left(l) => Left(l)
        case Right(r) => Right(f(r))
      }
    override def ap[B](f: Either[Z,A => B]) =
      x match {
        case Left(l) =>
          f match {
            case Left(l2) => Left(l append l2)
            case Right(_) => Left(l)
          }
        case Right(r) =>
          f match {
            case Left(l2) => Left(l2)
            case Right(r2) => Right(r2(r))
          }
      }
  }
```

Now we can use `ap` on our parsed values:

```
scala> val fortyTwo = atoi("20") ap (atoi("22") map add2)
fortyTwo: scala.util.Either[List[String],Int] = Right(42)
```

```
scala> val notFortyTwo = atoi("twenty") ap (atoi("twenty two") map add2)
notFortyTwo: scala.util.Either[List[String],Int] = Left(List('twenty' is not an integer, 'twenty two' is not an integer))
```

Again, it can be handy to do all this in reverse by first lifting a function and then applying it to a lifted value:

```
implicit def fnCofunctor[A,B](g: A => B) =
  new {
    def <%>[Z](x: Either[Z,A])(implicit zs: Z => Semigroup[Z]) = x map g
  }

implicit def EitherCofunctor[A,B,Z](f: Either[Z,A => B])(implicit zs: Z => Semigroup[Z]) =
  new {
    def <*>(a: Either[Z,A]) = a ap f
  }
```

Now we can implicity lift `add2` and apply it to two values returned by `atoi`:

```
scala> val fortyTwo = add2 <%> atoi("20") <*> atoi("22")
fortyTwo: scala.util.Either[List[String],Int] = Right(42)
```

```
scala> val notFortyTwo = add2 <%> atoi("twenty") <*> atoi("twenty two")
notFortyTwo: scala.util.Either[List[String],Int] = Left(List('twenty two' is not an integer, 'twenty' is not an integer))
```

Note the difference in evaluation order -- the error messages are returned in the reverse order when using `<*>` instead of `ap`.

We can combine `<%>` and `<*>` as needed:

```
scala> val fortyTwo = add2 <%> (abs <%> atoi("-20")) <*> atoi("22")
fortyTwo: scala.util.Either[List[String],Int] = Right(42)
```