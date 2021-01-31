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
                 [potemkin "0.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.9.9"]
                                  [clj-time "0.15.2"]
                                  [http-kit "2.5.0"]]
                   :plugins [[lein-midje "3.2.2"]]}}
  :aliases {"test" ["midje"]}

  :pedantic? :abort)
