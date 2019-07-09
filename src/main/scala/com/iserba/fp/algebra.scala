package com.iserba.fp

import scala.language.{higherKinds, implicitConversions}

object algebra {
  trait Functor[F[_]] {
    def map[A,B](a: F[A])(f: A => B): F[B]
  }

  trait Monad[F[_]] extends Functor[F] {
    def unit[A](a: => A): F[A]
    def flatMap[A,B](a: F[A])(f: A => F[B]): F[B]
    def map[A,B](a: F[A])(f: A => B): F[B] =
      flatMap(a)(a => unit(f(a)))
    def map2[A,B,C](a: F[A], b: F[B])(f: (A,B) => C): F[C] =
      flatMap(a)(a => map(b)(b => f(a,b)))
    def as[A,B](a: F[A])(b: B): F[B] =
      map(a)(_ => b)
    def skip[A](a: F[A]): F[Unit] =
      as(a)(())
    def when[A](b: Boolean)(fa: => F[A]): F[Boolean] =
      if (b) as(fa)(true) else unit(false)
    def forever[A,B](a: F[A]): F[B] = {
      lazy val t: F[B] = a flatMap (_ => t)
      t
    }
    def while_(a: F[Boolean])(b: F[Unit]): F[Unit] = {
      lazy val t: F[Unit] = while_(a)(b)
      a flatMap (c => skip(when(c)(t)))
    }
    def doWhile[A](a: F[A])(cond: A => F[Boolean]): F[Unit] = for {
      a1 <- a
      ok <- cond(a1)
      _ <- if (ok) doWhile(a)(cond) else unit(())
    } yield ()
    def seq[A,B,C](f: A => F[B])(g: B => F[C]): A => F[C] =
      f andThen (fb => flatMap(fb)(g))

    // syntax
    implicit def toMonadic[A](a: F[A]): Monadic[F,A] =
      new Monadic[F,A] { val F = Monad.this; def get = a }
  }

  trait Monadic[F[_],A] {
    val F: Monad[F]
    def get: F[A]
    private val a = get
    def map[B](f: A => B): F[B] = F.map(a)(f)
    def flatMap[B](f: A => F[B]): F[B] = F.flatMap(a)(f)
    def **[B](b: F[B]) = F.map2(a,b)((_,_))
    def *>[B](b: F[B]) = F.map2(a,b)((_,b) => b)
    def map2[B,C](b: F[B])(f: (A,B) => C): F[C] = F.map2(a,b)(f)
    def as[B](b: B): F[B] = F.as(a)(b)
    def skip: F[Unit] = F.skip(a)
  }

  sealed trait Free[F[_],A] {
    def flatMap[B](f: A => Free[F,B]): Free[F,B] =
      FlatMap(this, f)
    def map[B](f: A => B): Free[F,B] =
      flatMap(f andThen (Return(_)))
  }
  case class Return[F[_],A](a: A) extends Free[F, A]
  case class Suspend[F[_],A](s: F[A]) extends Free[F, A]
  case class FlatMap[F[_],A,B](s: Free[F, A],
                               f: A => Free[F, B]) extends Free[F, B]

  /*
 * A context in which exceptions can be caught and
 * thrown.
 */
  trait MonadCatch[F[_]] extends Monad[F] {
    def attempt[A](a: F[A]): F[Either[Throwable,A]]
    def fail[A](t: Throwable): F[A]
  }

  trait Par[F[_]] extends MonadCatch[F] {
    def lazyUnit[A](a: => A): F[A]
    def delay[A](a: => A): F[A]
    def fork[A](a: => F[A]): F[A]
    def async[A](f: (A => Unit) => Unit): F[A]
    def asyncF[A,B](f: A => B): A => F[B]
    def async[A](f: (Either[Throwable,A] => Unit) => Unit): F[A]
    /**
      * Helper function, for evaluating an action
      * asynchronously.
      */
    def eval[A](r: => A): F[A]
    def parRun[A](p: F[A]): A
  }

}