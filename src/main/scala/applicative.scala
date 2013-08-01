package com.earldouglas.scalacats

trait Semigroup[A] {
  def append(x: A): A
}

trait Applicative[A,F[_]] extends Functor[A,F] {
  def ap[B](f: F[A => B]): F[B]
}

object EitherApplicative {

  implicit def listSemigroup[A](x: List[A]): Semigroup[List[A]] =
    new Semigroup[List[A]] {
      override def append(y: List[A]) = x ++ y
    }

  implicit def eitherApplicative[A,Z](x: Either[Z,A])(implicit zs: Z => Semigroup[Z]) =
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

  implicit def fnCofunctor[A,B](g: A => B) =
    new {
      def <%>[Z](x: Either[Z,A])(implicit zs: Z => Semigroup[Z]) = x map g
    }

  implicit def eitherCofunctor[A,B,Z](f: Either[Z,A => B])(implicit zs: Z => Semigroup[Z]) =
    new {
      def <*>(a: Either[Z,A]) = a ap f
    }

}
