(ns ancient-clj.artifact
  (:require [version-clj.core :as v]))

;; ## Protocol

(defprotocol Artifact
  "Protocol for dependency representations."
  (read-artifact [this]
    "Create a map of ':group', ':id', ':version' and ':version-string',
     as well as `:symbol` and `:form`."))

;; ## Helpers

(defn- parse-name
  [s]
  (let [[g a] (if (string? s)
                (if (.contains ^String s "/")
                  (.split ^String s "/" 2)
                  [s s])
                (let [id (name s)]
                  (if-let [n (namespace s)]
                    [n id]
                    [id id])))]
    {::id    a
     ::group g}))

(defn- parse-version
  [version]
  (if (and (string? version) (seq version))
    {::v/version (v/parse version)
     ::version version}
    {::v/version nil, ::version nil}))

(defn- add-artifact-form
  [{:keys [::id ::group ::version] :as data} form]
  (let [sym (if (= group id)
              (symbol id)
              (symbol group id))]
    (assoc data
           ::symbol sym
           ::form   (or form (if version [sym version] [sym])))))

(defn- create-artifact-map
  [artifact-name artifact-version & [form]]
  (add-artifact-form
    (merge
      (parse-name artifact-name)
      (parse-version artifact-version))
    form))

;; ## Implementations

(extend-protocol Artifact
  clojure.lang.IPersistentVector
  (read-artifact [[artifact-name artifact-version :as form]]
    {:pre [(seq form)]}
    (create-artifact-map artifact-name artifact-version form))

  clojure.lang.Named
  (read-artifact [n]
    (create-artifact-map n ""))

  clojure.lang.IPersistentMap
  (read-artifact [{:keys [::group
                          ::id
                          ::symbol
                          ::form] :as data}]
    (assert (and group id symbol form))
    data)

  String
  (read-artifact [n]
    (create-artifact-map n "")))
