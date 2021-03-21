(ns ancient-clj.core-integration-test
  (:require [ancient-clj.core :as ancient]
            [ancient-clj.artifact :as artifact]
            [version-clj.core :as version]
            [clojure.test :refer [deftest is]]))

(deftest ^:integration t-sorted-versions
  (let [results (ancient/sorted-versions 'ancient-clj)]
    (is (seq results))
    (is (every? ::artifact/version results))
    (is (every? ::version/version results))
    (is (some (comp #{"0.7.0"} ::artifact/version) results))))

(deftest ^:integration t-latest-version
  (let [result (ancient/latest-version 'ancient-clj)]
    (is (::version/version result))
    (is (::artifact/version result))))
