(ns ancient-clj.test
  (:require [version-clj.core :as v]))

(def snapshot-versions
  ["0.1.0-SNAPSHOT" "0.1.1-SNAPSHOT" "0.1.3-SNAPSHOT"])

(def snapshot-versions-sorted
  (v/version-sort snapshot-versions))

(def qualified-versions
  ["0.1.1-RC0" "0.1.3-alpha"])

(def qualified-versions-sorted
  (v/version-sort qualified-versions))

(def release-versions
  ["0.1.0" "0.1.1" "0.1.2"])

(def release-versions-sorted
  (v/version-sort release-versions))

(def versions
  (concat
    snapshot-versions
    qualified-versions
    release-versions))

(def versions-sorted
  (v/version-sort versions))
