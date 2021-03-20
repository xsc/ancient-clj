(ns ancient-clj.zip.leiningen-test
  (:require [ancient-clj.zip.leiningen
             :refer [visit-project-clj visit-profiles-clj]]
            [ancient-clj.test
             [zip :refer [form->zloc]]
             [generators :as test]]
            [rewrite-clj.zip :as z]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer [deftest is]]
            [clojure.string :as string]
            [com.gfredericks.test.chuck :as chuck]))

;; ## Helpers

(def gen-known-version
  (gen/elements ["v1.0" "v1.1"]))

(defn- replace-versions
  [zloc]
  (let [[dep version & rst] (z/sexpr zloc)
        version' (case version
                   "v1.0" "v1.1"
                   version)]
    (z/replace zloc (into [dep version'] rst))))

(defn- versions-replaced?
  [zloc zloc']
  (let [orig (z/root-string zloc)
        updt (z/root-string zloc')]
    (= (string/replace orig "v1.0" "v1.1") updt)))

;; ## Test

(defspec t-visit-project-clj (chuck/times 50)
  (prop/for-all
    [form (test/gen-defproject* gen-known-version)]
    (let [zloc  (form->zloc form)
          zloc' (visit-project-clj zloc replace-versions)]
      (versions-replaced? zloc zloc'))))

(deftest t-visit-project-clj-exceptions
  (let [zloc (z/of-string "()")]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Invalid project file!"
          (visit-project-clj zloc identity)))))

(defspec t-visit-profiles-clj (chuck/times 50)
  (prop/for-all
    [form (test/gen-profiles-map* gen-known-version)]
    (let [zloc  (form->zloc form)
          zloc' (visit-profiles-clj zloc replace-versions)]
      (versions-replaced? zloc zloc'))))

(defspec t-visit-profiles-clj-ignores-invalid-profiles (chuck/times 50)
  (prop/for-all
    [mixed-form
     (gen/let [valid   (test/gen-profiles (gen/return "dont-replace"))
               invalid (test/gen-invalid-profiles gen-known-version)]
       (merge valid invalid))]
    (let [zloc  (form->zloc mixed-form)
          zloc' (visit-profiles-clj zloc replace-versions)]
      (= (z/root-string zloc) (z/root-string zloc')))))

(deftest t-visit-profiles-clj-exceptions
  (let [zloc (z/of-string "()")]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Invalid profiles file!"
          (visit-profiles-clj zloc identity)))))
