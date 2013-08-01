package com.earldouglas.scalacats.tests

object Calc {
  val abs: Int => Int = math.abs
  val add2: Int => Int => Int = { x => y => x + y }
}

class Tests extends org.scalatest.FunSuite {

  import Calc._
  import com.earldouglas.scalacats._

  test("Either functor") {

    import com.earldouglas.scalacats.EitherFunctor._

    def atoi(x: String): Either[String,Int] =
      try {
        Right(x.toInt)
      } catch {
        case _ : Throwable => Left("'" + x + "' is not an integer")
      }

    assert(Right(20) === (atoi("-20") map abs))
    assert(Right(20) === (abs <%> atoi("-20")))

    assert(Left("'negative twenty' is not an integer") === (atoi("negative twenty") map abs))
    assert(Left("'negative twenty' is not an integer") === (abs <%> atoi("negative twenty")))

  }

  test("Either applicative") {

    import com.earldouglas.scalacats.EitherApplicative._

    def atoi(x: String): Either[List[String],Int] =
      try {
        Right(x.toInt)
      } catch {
        case _ : Throwable => Left(List("'" + x + "' is not an integer"))
      }

    assert(Right(42) === (atoi("20") ap (atoi("22") map add2)))
    assert(Right(42) === (add2 <%> atoi("20") <*> atoi("22")))
    assert(Right(42) === (add2 <%> (abs <%> atoi("-20")) <*> atoi("22")))

    assert(Left(List("'twenty' is not an integer")) === (atoi("twenty") ap (atoi("22") map add2)))
    assert(Left(List("'twenty' is not an integer")) === (add2 <%> atoi("twenty") <*> atoi("22")))

    assert(Left(List("'twenty' is not an integer",
                     "'twenty-two' is not an integer")) === (atoi("twenty") ap (atoi("twenty-two") map add2)))
    assert(Left(List("'twenty-two' is not an integer",
                     "'twenty' is not an integer")) === (add2 <%> atoi("twenty") <*> atoi("twenty-two")))

  }

}
