(ns ancient-clj.repositories-integration-test
  (:require [ancient-clj.repositories :as r]
            [ancient-clj.artifact :as artifact]
            [clojure.test :refer [deftest is]]))

(deftest ^:integration t-loader-integration
  (let [load! (r/loader {})
        {:keys [artifact versions]} (load! 'ancient-clj)]
    (is (= "ancient-clj" (::artifact/group artifact)))
    (is (= "ancient-clj" (::artifact/id artifact)))
    (is (= #{"clojars" "central"}
           (set (keys versions))))
    (is (contains?
          (->> (get versions "clojars")
               (map ::artifact/version)
               (into #{}))
          "1.0.0"))))
