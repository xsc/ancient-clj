(ns ancient-clj.test.generators
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as string]
            [version-clj.core :as v]))

;; ## Generators

;; ### Helpers

(defn maybe
  [g]
  (gen/one-of [(gen/return nil) g]))

(def gen-id
  (gen/let [first-char gen/char-alpha
            rest-str   gen/string-alphanumeric]
    (str first-char rest-str)))

(def gen-artifact-name
  (gen/let [g (maybe gen-id)
            i gen-id]
    (if g (symbol g i) (symbol i))))

(defn gen-version*
  [suffixes]
  (gen/let [suffix (gen/elements suffixes)
            version (->> (gen/vector gen/s-pos-int 1 3)
                         (gen/fmap #(string/join "." %)))]
    (if suffix
      (str version "-" suffix)
      version)))

(def gen-version
  (gen-version* [nil "SNAPSHOT" "RC" "alpha"]))

(defn gen-versions*
  [suffixes]
  (->> (gen/vector-distinct
         (gen-version* suffixes)
         {:min-elements 0, :max-elements 3})
       (gen/fmap v/version-sort)))

(def gen-versions
  (gen-versions* [nil "SNAPSHOT" "RC" "alpha"]))

(defn gen-dependency
  [version-gen]
  (gen/let [artifact-name gen-artifact-name
            version       version-gen]
    (if version
      [artifact-name version]
      [artifact-name])))

(def gen-profile-key
  (gen/fmap keyword gen-id))

(defn gen-profile
  [version-gen]
  (gen/map
    (gen/elements [:dependencies :managed-dependencies :plugins :java-agents])
    (gen/vector (gen-dependency version-gen) 0 3)))

(defn gen-nested-profile
  [version-gen]
  (gen/vector
    (gen/one-of [gen-profile-key (gen-profile version-gen)])
    0
    3))

(defn gen-profiles
  [version-gen]
  (gen/map
    gen-profile-key
    (gen/one-of [(gen-profile version-gen)
                 (gen-nested-profile version-gen)])))

(defn gen-invalid-profiles
  [version-gen]
  (gen/one-of
    [(gen/map gen-id          (gen-profile version-gen))
     (gen/map gen-profile-key gen-id)
     (gen/map gen-profile-key (gen/vector gen-id 0 3))]))

(defn gen-project-data
  [version-gen]
  (gen/let [profile (gen-profile version-gen)
            other   (gen/hash-map
                      :url         gen/string-alphanumeric
                      :description gen/string-alphanumeric)
            profiles (maybe (gen-profiles version-gen))]
    (cond-> (merge other profile)
      profiles (assoc :profiles profiles))))

;; ### Files

(defn gen-defproject*
  "Generator for a 'defproject' form, where dependency versions are
   created by the given version-generator."
  [version-gen]
  (gen/let [project-name    gen-artifact-name
            project-version gen-version
            project-data    (maybe (gen-project-data version-gen))]
    `(~'defproject ~project-name ~project-version
       ~@(mapcat identity project-data))))

(def gen-defproject
  "Generator for a random 'defproject' form."
  (gen-defproject* (maybe gen-version)))

(defn gen-profiles-map*
  "Generator for a profiles map, where dependency versions are created
   by the given version-generator."
  [version-gen]
  (gen-profiles version-gen))

(def gen-profiles-map
  "Generator for a random profiles map."
  (gen-profiles-map* (maybe gen-version)))

;; ### Repositories

(def gen-repositories
  (gen/let [{:strs [releases snapshots qualified] :as base}
            (gen/hash-map
              "releases" (gen-versions* [nil])
              "snapshots" (gen-versions* ["SNAPSHOT"])
              "qualified" (gen-versions* ["RC" "alpha"]))]
    (let [data (->> (concat releases snapshots qualified)
                    (v/version-sort)
                    (assoc base "all"))]
      {:data           data
       :latest-version (last (get data "all"))
       :repositories   (->> (for [[k v] data]
                              [k (constantly v)])
                            (into {}))})))
