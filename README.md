# ancient-clj

[![Clojars Artifact](https://img.shields.io/clojars/v/ancient-clj.svg)](https://clojars.org/ancient-clj)
[![Documentation](https://cljdoc.org/badge/ancient-clj/ancient-clj)](https://cljdoc.org/d/ancient-clj/ancient-clj/CURRENT)
[![CI](https://github.com/xsc/ancient-clj/workflows/CI/badge.svg?branch=master)](https://github.com/xsc/ancient-clj/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/xsc/ancient-clj/branch/master/graph/badge.svg?token=GLSK1G95TX)](https://codecov.io/gh/xsc/ancient-clj)

__ancient-clj__ is a library for accessing versioning metadata in Maven repositories.
It is the base for the Leiningen plugin [lein-ancient](https://github.com/xsc/lein-ancient).

Version comparison is done using [version-clj](https://github.com/xsc/version-clj).

## Usage

```clojure
(require '[ancient-clj.core :as ancient])
```

### Maven Repository Access

You can create a loader function that'll allow you to pass an artifact (e.g. as
a symbol) and returns a list of versions contained in that repository,
represented as maps:

```clojure
(let [load! (ancient/loader
              {:repositories {"clojars" "https://clojars.org/repo"}})]
  (load! 'ancient-clj))
;; => ({:version [(0 1 0) ["snapshot"]],
;;      :qualifiers #{"snapshot"},
;;      :snapshot? true,
;;      :qualified? true,
;;      :version-string "0.1.0-SNAPSHOT"},
;;     {:version [(0 1 0)], ...}, ...)
```

This loader will return a raw list of versions, unsorted and unfiltered. You can
use the following wrappers:

- `wrap-ignore`: remove SNAPSHOT/qualified versions as desired,
- `wrap-sort`: sort the returned list of version maps,
- `wrap-as-string`: only return the version string, not the whole map.

If you need a lower-level loader function that allows you to access exceptions
and per-repository results, check out `ancient-clj.repositories/loader`.

### Simple API

You can obtain a loader function via `(default-loader)` that will be pointing at
Clojars and Maven Central. It's used automatically, when e.g. fetching the
latest version via:

```clojure
(ancient/latest-version 'ancient-clj)
;; => {:version [(1 0 0)],
;;     :qualifiers #{},
;;     :snapshot? false,
;;     :qualified? false,
;;     :version-string "1.0.0"}
```

Same goes for the sorted list of versions in:

```clojure
(ancient/sorted-versions 'ancient-clj)
;; => ({:version [(0 1 0) ["snapshot"]], ...}, ...)
```

Both take an additional first parameter if you want to supply a custom (or
wrapped loader).

### File Operations

At the core of `lein-ancient` is the ability to collect dependencies from
files and automatically update them in-place. With version 2.0.0 this has moved
into `ancient-clj`.

```clojure
(require '[ancient-clj.zip :refer [project-clj]]
         '[rewrite-clj.zip :as z])
```

The operations rely on a `rewrite-clj` zippers that can be created from files,
strings, forms, etc... Internally, the concept of a _visitor_ is used that will
call a function for each dependency it encounters.

#### Finding outdated dependencies

```clojure
(let [collect! (ancient/collector {:visitor (project-clj)})]
  (collect! (z/of-file "project.clj")))
;; => ({:id "clojure",
;;      :group "org.clojure",
;;      :version [(1 10 3)],
;;      :version-string "1.10.3",
;;      :symbol org.clojure/clojure,
;;      :form [org.clojure/clojure "1.10.3"],
;;      :value [org.clojure/clojure "1.10.3" :scope "provided"],
;;      :latest-version {:version [(1 11 0) ("alpha" 1)],
;;                       :qualifiers #{"alpha"},
;;                       :snapshot? false,
;;                       :qualified? true,
;;                       :version-string "1.11.0-alpha1"}}, ...)
```

You can supply a custom `:loader`, as well as a `:check?` predicate that allows
you to be selective about what to check.

#### Updating outdated dependencies

```clojure
(let [update! (ancient/updater {:visitor (project-clj)})]
  (-> (z/of-file "project.clj")
      (update!)
      (z/root-string)))
;; => "(defproject ... :dependencies [[org.clojure/clojure \"1.11.0-alpha1\" :scope \"provided\"]\n ...)"
```

Again, you can supply a custom `:loader`, a `:check?` predicate, as well as an
`:update?` predicate. The last one can be especially useful, e.g. if you want
to avoid performing major version updates.

#### Updating files directly

Use `ancient-clj.core/file-updater` to update files in-place. An exception will
be thrown if the file changed before changes are written back.

## Deprecated API

In version 2.0.0, part of the original API has been removed while most of the
remaining functions have been marked deprecated. All functionality can be
replicated with the new facilities, but if you're missing a convencience
function, please open a PR and we can discuss.

## Supported Repository Types

Since version 1.0.0, ancient-clj depends on [pomegranate][] (and thus Aether)
and can benefit from any wagons that are registered there. Please use
`cemerick.pomegranate.aether/register-wagon-factory!` to utilise this feature.

[pomegranate]: https://github.com/clj-commons/pomegranate

## License

```
MIT License

Copyright (c) 2013-2021 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
