[![Clojars Project](https://img.shields.io/clojars/v/lein-jmh.svg)](https://clojars.org/lein-jmh)
[![Travis CI](https://travis-ci.org/jgpc42/lein-jmh.svg?branch=master)](https://travis-ci.org/jgpc42/lein-jmh)

### Adding to your project

Add `[lein-jmh "0.2.5"]` to your `:plugins` section. For example:

```clojure
(defproject your-project "0.1.0-SNAPSHOT"
  #_...
  :plugins [[lein-jmh "0.2.5"]])
```

### What is it?

Leiningen plugin for running [jmh-clojure][jmh-clj] benchmarks.

### Usage

By default, a `jmh.edn` file at the root of your project is used to configure lein-jmh. Please see the [sample file][sample] for documentation. For example, the following will run all benchmarks:

```bash
$ lein jmh
```

The task takes a single argument that gives the task and/or benchmark options. Please see the jmh-clojure [docs][run-doc] or `lein help jmh` for more information. For example, to run all benchmarks that match a selector from a data file in an alternate location:

```bash
$ lein jmh '{:file "benchmarks/parser.edn", :select :decode}'
```

### Available options

In addition to the normal run [options][run-doc], extra task options include:

| Option        | Description                                    |
| ------------- | ---------------------------------------------- |
| `:exclude`    | keys to remove from each benchmark result map. |
| `:file`       | read the given file instead of `jmh.edn`.      |
| `:format`     | print results in the given format.             |
| `:only`       | keys to select from each benchmark result map. |
| `:output`     | specify the location to write the results.     |
| `:pprint`     | equivalent to `:format :pprint`.               |
| `:progress`   | display progress data while running.           |
| `:sort`       | key(s) to order the results by.                |

Please see `lein help jmh` for more information on the available options. Note that some formats (e.g., `:table`) exclude some result information due to space considerations. See the previously mentioned help for details.

### Tiered compilation

The JVM option `-XX:TieredStopAtLevel=1` is normally set automatically by Leiningen when running code in your project. This option speeds up JVM startup time but is normally problematic for benchmarking as it disables the [C2][c2] compiler.

Since lein-jmh merges the `:jmh` profile automatically when running benchmarks, adding the following to your project's `:profiles` key should be sufficient for most users:

```clojure
:profiles {:jmh {:jvm-opts []}}
```

Alternatively, use `:fork` and specify different `:jvm :args` to override the Leiningen parent process arguments. This can be specified in your `jmh.edn` file, or globally via the task options map.

### More information

This plugin is a wrapper for the [jmh-clojure][jmh-clj] library. Please see the documentation there for more.

### Running the tests

```bash
lein test
```

Or, `lein test-all` for all supported Clojure versions.

### License

Copyright © 2017 Justin Conklin

Distributed under the Eclipse Public License, the same as Clojure.



[c2]:       http://openjdk.java.net/groups/hotspot/docs/HotSpotGlossary.html
[jmh-clj]:  https://github.com/jgpc42/jmh-clojure
[run-doc]:  https://jgpc42.github.io/jmh-clojure/doc/jmh.core.html#var-run
[sample]:   https://github.com/jgpc42/jmh-clojure/blob/master/resources/sample.jmh.edn
