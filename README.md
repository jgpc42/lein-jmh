[![Clojars Project](https://img.shields.io/clojars/v/lein-jmh.svg)](https://clojars.org/lein-jmh)
[![](https://github.com/jgpc42/lein-jmh/workflows/Test%20runner/badge.svg)][ci]

### Adding to your project

Add `[lein-jmh "0.3.0"]` to your `:plugins` section. For example:

```clojure
(defproject your-project #_...
  :plugins [[lein-jmh "0.3.0"]])
```

### What is it?

Leiningen plugin for running [jmh-clojure][jmh-clj] benchmarks.

### Usage

Run `lein help jmh` to get started.

As mentioned in the help, by default, a `jmh.edn` file at the root of your project is used to configure lein-jmh. Please see the [sample file][sample] for a complete guide. The task takes an optional single argument that gives the task and/or benchmark options. If omitted, all defined benchmarks will be run:

```bash
$ lein jmh
```

Give a map to configure the runner. For example, to run all benchmarks that match a selector from a data file in an alternate location:

```bash
$ lein jmh '{:file "benchmarks/parser.edn", :select :decode}'
```

Additionally, the available JMH profilers may be listed with: `lein jmh :profilers`.

A more involved example can be found [here][async].

### More information

This plugin is a very thin wrapper for the [`jmh-clojure-task`][task] library. Please see the documentation there for full usage instructions and extended examples.

### Note about tiered compilation

The JVM option `-XX:TieredStopAtLevel=1` is normally set automatically by Leiningen when running code in your project. This option speeds up JVM startup time but is normally problematic for benchmarking as it disables the [C2][c2] compiler.

Since lein-jmh merges the `:jmh` profile automatically when running benchmarks, adding the following to your project's `:profiles` key should be sufficient for most users:

```clojure
:profiles {:jmh {:jvm-opts []}}
```

Alternatively, use `:fork` and specify different `:jvm :args` to override the Leiningen parent process arguments. This can be specified in your `jmh.edn` file, or globally via the task options map.

### Running the tests

```bash
lein test
```

Or, `lein test-all` for all supported Clojure versions.

### License

Copyright © 2017-2024 Justin Conklin

Distributed under the Eclipse Public License, the same as Clojure.



[async]:    https://gist.github.com/jgpc42/a694c8b4255ed332dac38428bd4e0546
[c2]:       http://openjdk.java.net/groups/hotspot/docs/HotSpotGlossary.html
[ci]:       https://github.com/jgpc42/lein-jmh/blob/master/.github/workflows/test.yml
[jmh-clj]:  https://github.com/jgpc42/jmh-clojure
[sample]:   https://github.com/jgpc42/jmh-clojure/blob/master/resources/sample.jmh.edn
[task]:     https://github.com/jgpc42/jmh-clojure-task
