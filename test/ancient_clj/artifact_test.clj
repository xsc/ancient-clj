(ns ancient-clj.artifact-test
  (:require [ancient-clj.artifact :refer [read-artifact]]
            [version-clj.core :as v]
            [clojure.test :refer [deftest are is]]))

(deftest t-read-artifact
  (are [artifact group id version]
       (let [m (read-artifact artifact)]
         (is (= (name group) (:group m)))
         (is (= (name id) (:id m)))
         (is (= (v/version->seq version) (:version m)))
         (is (= version (:version-string m)))
         (is (= (:form m) [(:symbol m) (:version-string m)])))
       '[pandect "0.3.0"]              'pandect      'pandect     "0.3.0"
       '[org.clojure/clojure "1.5.1"]  'org.clojure  'clojure     "1.5.1"
       '[pandect]                      'pandect      'pandect     ""
       '[org.clojure/clojure]          'org.clojure  'clojure     ""
       'pandect                        'pandect      'pandect     ""
       'org.clojure/clojure            'org.clojure  'clojure     ""
       "pandect"                       'pandect      'pandect     ""
       "org.clojure/clojure"           'org.clojure  'clojure     ""
       :pandect                        'pandect      'pandect     ""
       :org.clojure/clojure            'org.clojure  'clojure     ""))
