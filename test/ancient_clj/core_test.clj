(ns ancient-clj.core-test
  (:require [ancient-clj.core :as ancient]
            [ancient-clj.zip :refer [project-clj]]
            [rewrite-clj.zip :as z]
            [version-clj.core :as v]
            [ancient-clj.test
             [generators :as test-gen]
             [zip :refer [form->zloc]]]
            [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :as chuck]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]])
  (:import (java.io File)
           (java.util.concurrent CountDownLatch ExecutionException)))

;; ## Helpers

(def known-versions
  ["v1.0" "v1.1" "v.1.2"])

(def gen-known-version
  (gen/elements known-versions))

(defn- versions-replaced?
  [zloc zloc' target-version]
  (let [orig (z/root-string zloc)
        updt (z/root-string zloc')]
    (if target-version
      (= updt
         (reduce
           (fn [orig version]
             (if (v/newer? version target-version)
               orig
               (string/replace orig version target-version)))
           orig
           known-versions))
      (= updt orig))))

(defn- zloc-identical?
  [zloc zloc']
  (let [orig (z/root-string zloc)
        updt (z/root-string zloc')]
    (= updt orig)))

(defn- matches?
  [data repository-ids versions]
  (= (set (mapcat data repository-ids))
     (set (map :version-string versions))))

(defn- matches-exactly?
  [data repository-id versions]
  (= (get data repository-id)
     (map :version-string versions)))

(defn- with-temp-file
  [f]
  (let [file (File/createTempFile "ancient-clj" ".clj")]
    (try
      (f file)
      (finally
        (.delete file)))))

;; ## Tests

(defspec t-loader (chuck/times 50)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories]
    (let [load! (ancient/loader {:repositories repositories})
          versions (load! 'ancient-clj)]
      (matches? data ["all"] versions))))

(defspec t-loader-with-exception (chuck/times 20)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories]
    (let [repos (assoc repositories "ex" #(throw (ex-info "FAIL" %)))
          load! (ancient/loader {:repositories repos})
          versions (load! 'ancient-clj)]
      (matches? data ["all"] versions))))

(deftest t-default-loader
  (is (fn? (ancient/default-loader))))

(defspec t-sorted-versions (chuck/times 50)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories]
    (let [load! (ancient/loader {:repositories repositories})
          versions (ancient/sorted-versions load! 'ancient-clj)]
      (matches-exactly? data "all" versions))))

(defspec t-latest-version (chuck/times 50)
  (prop/for-all
    [{:keys [repositories latest-version]} test-gen/gen-repositories]
    (let [load! (ancient/loader {:repositories repositories})
          version (ancient/latest-version load! 'ancient-clj)]
      (= latest-version (:version-string version)))))

(defspec t-updater (chuck/times 50)
  (prop/for-all
    [{:keys [repositories latest-version]} test-gen/gen-repositories
     form   (test-gen/gen-defproject* gen-known-version)]
    (let [load!   (ancient/loader {:repositories repositories})
          update! (ancient/updater {:visitor (project-clj), :loader load!})
          zloc    (form->zloc form)
          zloc'   (update! zloc)]
      (versions-replaced? zloc zloc' latest-version))))

(defspec t-updater-with-check-predicate (chuck/times 20)
  (prop/for-all
    [form (test-gen/gen-defproject* gen-known-version)]
    (let [update! (ancient/updater
                    {:visitor (project-clj)
                     :loader #(throw (ex-info "FAIL" %))
                     :check? (constantly false)})
          zloc    (form->zloc form)
          zloc'   (update! zloc)]
      (zloc-identical? zloc zloc'))))

(defspec t-updater-with-update-predicate (chuck/times 20)
  (prop/for-all
    [{:keys [repositories]} test-gen/gen-repositories
     form   (test-gen/gen-defproject* gen-known-version)]
    (let [load!   (ancient/loader {:repositories repositories})
          update! (ancient/updater
                    {:visitor (project-clj)
                     :loader load!
                     :update? (constantly false)})
          zloc    (form->zloc form)
          zloc'   (update! zloc)]
      (zloc-identical? zloc zloc'))))

(defspec t-file-updater (chuck/times 10)
  (prop/for-all
    [{:keys [repositories latest-version]} test-gen/gen-repositories
     form   (test-gen/gen-defproject* gen-known-version)]
    (let [load!   (ancient/loader {:repositories repositories})
          update! (ancient/file-updater
                    {:visitor (project-clj)
                     :loader load!})
          zloc  (form->zloc form)
          zloc' (with-temp-file
                   (fn [tmp]
                     (spit tmp form)
                     (update! tmp)
                     (z/of-file tmp)))]
      (versions-replaced? zloc zloc' latest-version))))

(deftest t-file-updater-checksum-verification
  (let [form       '(defproject prj "1.0.0" :dependencies [[dep "1.0.0"]])
        wrote-file (CountDownLatch. 1)
        checking   (CountDownLatch. 1)
        check?     (fn [_]
                     (.countDown checking)
                     (.await wrote-file)
                     false)
        update! (ancient/file-updater
                  {:visitor (project-clj)
                   :loader  (constantly {})
                   :check?  check?})]
    (is (thrown-with-msg?
          ExecutionException
          #"File was modified while performing dependency update!"
          @(with-temp-file
             (fn [^File tmp]
               (spit tmp form)
               (let [fut (future (update! tmp))]
                 (.await checking)
                 (.setLastModified tmp 100)
                 (.countDown wrote-file)
                 fut)))))))

(defspec t-wrap-ignore (chuck/times 10)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories
     [what-to-ignore expected-contents]
     (gen/elements
       {[:snapshot]            ["releases" "qualified"]
        [:qualified]           ["releases" "snapshots"]
        [:snapshot :qualified] ["releases"]})]
    (let [load! (-> (ancient/loader {:repositories repositories})
                    (ancient/wrap-ignore what-to-ignore))
          versions (load! 'ancient-clj)]
      (matches? data expected-contents versions))))

(defspec t-wrap-sort (chuck/times 10)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories
     sort-direction (gen/elements [:asc :desc])]
    (let [load! (-> (ancient/loader {:repositories repositories})
                    (ancient/wrap-sort sort-direction))
          versions (load! 'ancient-clj)]
      (->> (if (= sort-direction :asc)
             versions
             (reverse versions))
           (matches-exactly? data "all")))))

(defspec t-wrap-as-string (chuck/times 10)
  (prop/for-all
    [{:keys [repositories]} test-gen/gen-repositories]
    (let [load! (-> (ancient/loader {:repositories repositories})
                    (ancient/wrap-as-string))
          versions (load! 'ancient-clj)]
      (every? string? versions))))

(deftest t-wrap-sort-exception
  (is (thrown?
        IllegalArgumentException
        (ancient/wrap-sort identity :unknown))))
