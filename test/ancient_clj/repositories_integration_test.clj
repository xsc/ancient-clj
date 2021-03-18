(ns ancient-clj.repositories-integration-test
  (:require [ancient-clj.repositories :as r]
            [clojure.test :refer [deftest is]]))

(deftest ^:integration t-loader-integration
  (let [load! (r/loader {})
        {:keys [artifact versions]} (load! 'ancient-clj)]
    (is (= "ancient-clj" (:group artifact)))
    (is (= "ancient-clj" (:id artifact)))
    (is (= #{"clojars" "central"}
           (set (keys versions))))
    (is (contains?
          (->> (get versions "clojars")
               (map :version-string)
               (into #{}))
          "1.0.0"))))
