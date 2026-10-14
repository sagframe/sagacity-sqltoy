# Contributing

Thanks for your interest in improving SqlToy!

## Questions and use cases

Please use [GitHub Discussions](https://github.com/sagframe/sagacity-sqltoy/discussions) for questions —
it keeps the issue tracker actionable. The [documentation (English / 中文)](https://sagframe.github.io/sqltoy-docs/#/en/)
covers most topics, and the [quickstart lessons](https://github.com/sagframe/sqltoy-quickstart) contain
runnable examples for every core feature.

## Reporting bugs

Open an issue with the bug template: include the sqltoy version, environment (JDK / Spring Boot / database)
and a minimal reproducible snippet. Bugs that reproduce in the [quickstart](https://github.com/sagframe/sqltoy-quickstart)
project get fixed fastest.

## Pull requests

1. Keep the change focused — one topic per PR is much easier to review.
2. Follow the style of the surrounding code.
3. For behavior changes, describe the scenario the change covers (ideally with a test).
4. The 5.6.x line (JDK 8) only receives critical fixes; new features target 6.x.

## Getting familiar with the code

See the "Key Source-Code Guide" section of the [README](README.md):
start from `LightDao`, then `SqlToyDaoSupport` → `DialectFactory` for the dialect layer,
and `SqlToyContext` for the full picture.
