(ns ancient-clj.artifact-test
  (:require [ancient-clj.artifact :as artifact]
            [version-clj.core :as v]
            [clojure.test :refer [deftest are testing]]))

(deftest t-read-artifact
  (testing "parsing group/id/version"
    (are [value expected-group expected-version]
         (= {::artifact/group   (name expected-group)
             ::artifact/id      "ancient-clj"
             ::artifact/version expected-version
             ::v/version        (some-> expected-version v/parse)}
            (-> (artifact/read-artifact value)
                (select-keys [::artifact/group
                              ::artifact/id
                              ::artifact/version
                              ::v/version])))
         ;; cases
         '[ancient-clj "1.0.0"]               'ancient-clj "1.0.0"
         '[ancient-clj/ancient-clj "1.0.0"]   'ancient-clj "1.0.0"
         '[xsc/ancient-clj "1.0.0"]           'xsc         "1.0.0"
         '[ancient-clj "1.0.0" :scope "test"] 'ancient-clj "1.0.0"
         '[ancient-clj]                       'ancient-clj nil
         'ancient-clj                         'ancient-clj nil
         :ancient-clj                         'ancient-clj nil
         "ancient-clj"                        'ancient-clj nil))
  (testing "parsing symbol"
    (are [value expected-symbol]
         (= expected-symbol (::artifact/symbol (artifact/read-artifact value)))
         ;; cases
         '[ancient-clj "1.0.0"]             'ancient-clj
         '[ancient-clj/ancient-clj "1.0.0"] 'ancient-clj
         '[xsc/ancient-clj "1.0.0"]         'xsc/ancient-clj))
  (testing "parsing form"
    (are [value]
         (= '[ancient-clj] (::artifact/form (artifact/read-artifact value)))
         ;; cases
         '[ancient-clj]
         'ancient-clj
         :ancient-clj
         "ancient-clj")
    (are [value]
         (= value (::artifact/form (artifact/read-artifact value)))
         ;; cases
         '[ancient-clj "1.0.0"]
         '[ancient-clj/ancient-clj "1.0.0"]
         '[xsc/ancient-clj "1.0.0"]
         '[ancient-clj "1.0.0" :scope "test"])))
