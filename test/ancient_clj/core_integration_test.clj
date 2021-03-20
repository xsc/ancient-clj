(ns ancient-clj.core-integration-test
  (:require [ancient-clj.core :as ancient]
            [clojure.test :refer [deftest is]]))

(deftest ^:integration t-sorted-versions
  (let [results (ancient/sorted-versions 'ancient-clj)]
    (is (seq results))
    (is (every? :version results))
    (is (every? :version-string results))
    (is (some (comp #{"0.7.0"} :version-string) results))))

(deftest ^:integration t-latest-version
  (let [result (ancient/latest-version 'ancient-clj)]
    (is (:version result))
    (is (:version-string result))))
