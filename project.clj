(defproject ancient-clj "1.0.0-SNAPSHOT"
  :description "Maven Version Utilities for Clojure"
  :url "https://github.com/xsc/ancient-clj"
  :license {:name "MIT"
            :comment "MIT License"
            :url "https://choosealicense.com/licenses/mit"
            :year 2013
            :key "mit"}

  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [clj-commons/pomegranate "1.2.0"]
                 [version-clj "0.1.2"]
                 [potemkin "0.4.5"]
                 [riddley "0.2.0"]]

  :profiles {:dev
             {:global-vars {*warn-on-reflection* true}}
             :kaocha
             {:dependencies [[lambdaisland/kaocha "1.0.732"
                              :exclusions [org.clojure/spec.alpha]]
                             [lambdaisland/kaocha-cloverage "1.0.75"]
                             [org.tcrawley/dynapath "1.1.0"]]}
             :ci
             [:kaocha
              {:global-vars {*warn-on-reflection* false}}]}

  :aliases {"kaocha"    ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "ci"        ["with-profile" "+ci" "run" "-m" "kaocha.runner"
                         "--reporter" "documentation"
                         "--plugin"   "cloverage"
                         "--codecov"
                         "--no-cov-html"]}

  :pedantic? :abort)
