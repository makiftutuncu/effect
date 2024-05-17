# Effect [![](https://img.shields.io/badge/docs-0.2.0-brightgreen.svg?style=for-the-badge&logo=scala&color=dc322f&labelColor=333333)](https://javadoc.io/doc/dev.akif/effect-core_3)

## Table of Contents

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Development and Testing](#development-and-testing)
4. [Contributing](#contributing)
5. [License](#license)

## Introduction

Effect is a basic, home-made functional effect system with powerful errors.

A value of type `Effect[A]` is a description of a computation that can produce a value of type `A`, can fail with an error `E`, can fail unexpectedly with a `Throwable` or can be interrupted.

Effect has only one type parameter. `Effect[A]` is mentally equivalent to `ZIO[Any, E, A]` where error type `E` is fixed to the error model [`e.scala.E`](https://github.com/makiftutuncu/e/tree/master/e-scala#1-e).

Effect is **only a functional effect and not a complete solution to everything**.

## Installation

Add following to your `build.sbt` (for now, Scala 3 only):

```scala
libraryDependencies += "dev.akif" %% "effect-core" % "0.2.0"
```

## Development and Testing

Effect is built with SBT. So, standard SBT tasks like `clean`, `compile` and `test` can be used.

To run all tests:

```shell
sbt test
```

To run specific test(s):

```shell
sbt 'testOnly fullyQualifiedTestClassName1 fullyQualifiedTestClassName2 ...'
```

## Contributing

All contributions are welcome. Please feel free to send a pull request. You may check [project page](https://github.com/users/makiftutuncu/projects/1) for current status of development and issues. Thank you!

## License

Effect is licensed with [MIT License](LICENSE.md).
