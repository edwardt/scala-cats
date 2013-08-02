package com.earldouglas.scalacats

trait Functor[A,F[_]] {
  def map[B](f: A => B): F[B]
}

object EitherFunctor {

  implicit def eitherFunctor[A,Z](x: Either[Z,A]) =
    new Functor[A,({type EitherZ[B] = Either[Z,B]})#EitherZ] {
      override def map[B](f: A => B): Either[Z,B] =
        x match {
          case Left(l) => Left(l)
          case Right(r) => Right(f(r))
        }
    }

  implicit def fnCofunctor[A,B](g: A => B) =
    new {
      def <%>[Z](x: Either[Z,A]) = x map g
    }

}
