### Dependency and version information
<details>
  <summary>Click to show</summary>

[Leiningen][lein]

``` clojure
[jmh-clojure/task "0.1.0"]
```

[tools.deps][deps]

```clojure
{jmh-clojure/task {:mvn/version "0.1.0"}}
```

[Maven](http://maven.apache.org)

``` xml
<dependency>
  <groupId>jmh-clojure</groupId>
  <artifactId>task</artifactId>
  <version>0.1.0</version>
</dependency>
```

JDK versions 8 to 14 and Clojure versions 1.7 to 1.10 are currently [supported][ci].
</details>

### What is it?

Various file and output utilities for [jmh-clojure][jmh-clj] intended to be used by higher-level tools like [Leiningen][plugin], etc.

### Example

As a simple example, let's create an `uberjar` to run our benchmarks standalone using [`tools.deps`][deps] via [`uberdeps`][udeps]. In a hypothetical project root:

```bash
mkdir -p classes uberdeps
echo '{:deps {uberdeps {:mvn/version "0.1.11"}}}' > uberdeps/deps.edn
clj -e "(compile 'jmh.main)"
cd uberdeps
clj -m uberdeps.uberjar --deps-file ../deps.edn --main-class jmh.main --target ../target/jmh.jar
java -cp classes:../target/jmh.jar jmh.main :help
```

For this to work, your `deps.edn` must contain this library and `"classes"` must exist and be in your `:paths`. Also note, as shown on the last line, since `jmh-clojure` generates `.class` files dynamically (written to `*compile-path*`, by default), we add this output directory to the classpath when running the uberjar main class. Running with `-Dfile.encoding=UTF-8` is also advisable depending on your platform due to the unicode characters JMH can output.

For Leiningen, just put the following into your `project.clj` and run `lein uberjar`.

```clojure
(defproject your-project #_...
  :profiles {:uberjar {:dependencies [[jmh-clojure/task "0.1.0"]]
                       :aot [jmh.main]
                       :main jmh.main}})
```

The procedure for other tools should be similarly straightforward.

### More information

See the companion Leiningen [plugin][plugin] for information on supported options. See the [`jmh-clojure`][jmh-clj] project for everything else.

### Running the tests

```bash
lein test
```

Or, `lein test-all` for all supported Clojure versions.



[ci]:       https://github.com/jgpc42/lein-jmh/blob/master/.github/workflows/test.yml
[deps]:     https://github.com/clojure/tools.deps.alpha
[jmh-clj]:  https://github.com/jgpc42/jmh-clojure
[lein]:     http://github.com/technomancy/leiningen
[plugin]:   https://github.com/jgpc42/lein-jmh
[udeps]:    https://github.com/tonsky/uberdeps
