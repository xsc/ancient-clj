(ns ancient-clj.zip-test
  (:require [ancient-clj.zip
             :refer [dependencies
                     update-dependencies
                     project-clj
                     profiles-clj]]
            [ancient-clj.artifact :as artifact]
            [ancient-clj.test
             [zip :refer [form->zloc]]
             [generators :as test]]
            [rewrite-clj.zip :as z]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [com.gfredericks.test.chuck :as chuck]))

;; ## Helpers

(def gen-deps-vector
  (gen/vector (test/gen-dependency (test/maybe test/gen-version))))

(def gen-form-and-visitor
  (gen/one-of
    [(gen/tuple
       test/gen-defproject
       (gen/return (project-clj)))
     (gen/tuple
       test/gen-profiles-map
       (gen/return (profiles-clj)))]))

(defn- vector-visitor
  [zloc f]
  (assert (= (z/tag zloc) :vector))
  (z/map f zloc))

(def opts
  {:visitor         vector-visitor
   :sexpr->artifact artifact/read-artifact})

;; ## Tests

(defspec t-dependencies (chuck/times 50)
  (prop/for-all
    [deps gen-deps-vector]
    (let [zloc  (form->zloc deps)
          found (dependencies opts zloc)]
      (= deps (map ::artifact/form found)))))

(defspec t-update-dependencies (chuck/times 50)
  (prop/for-all
    [deps    gen-deps-vector
     version test/gen-version]
    (let [zloc  (form->zloc deps)
          zloc' (update-dependencies opts zloc (constantly version))]
      (every? (comp #{version} second) (z/sexpr zloc')))))

(defspec t-update-dependencies-with-selective-version (chuck/times 50)
  (prop/for-all
    [deps    (->> gen-deps-vector
                  (gen/such-that seq)
                  (gen/fmap distinct)
                  (gen/fmap vec))
     version test/gen-version]
    (let [zloc  (form->zloc deps)
          zloc' (->> (fn [dep]
                       (when (= (::artifact/form dep) (first deps))
                         version))
                     (update-dependencies opts zloc))
          deps' (z/sexpr zloc')]
      (and (= (next deps) (next deps'))
           (= version (second (first deps')))))))

(defspec t-dependencies-known-formats (chuck/times 50)
  (prop/for-all
    [[form visitor] gen-form-and-visitor]
    (let [zloc (form->zloc form)]
      (dependencies visitor zloc))))

(defspec t-update-dependencies-known-formats (chuck/times 50)
  (prop/for-all
    [[form visitor] gen-form-and-visitor
     version        test/gen-version]
    (let [zloc (form->zloc form)]
      (update-dependencies visitor zloc (constantly version)))))
