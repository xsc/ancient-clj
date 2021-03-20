(ns ancient-clj.artifact
  (:require [version-clj.core :as v]))

;; ## Protocol

(defprotocol Artifact
  "Protocol for dependency representations."
  (read-artifact [this]
    "Create a map of ':group', ':id', ':version' and ':version-string',
     as well as `:symbol` and `:form`."))

;; ## Helpers

(defn- parse-id
  [s]
  (let [[g a] (if (string? s)
                (if (.contains ^String s "/")
                  (.split ^String s "/" 2)
                  [s s])
                (let [id (name s)]
                  (if-let [n (namespace s)]
                    [n id]
                    [id id])))]
    {:id     a
     :group  g}))

(defn- parse-version
  [version]
  (when (string? version)
    (let [v (str version)]
      {:version        (v/version->seq v)
       :version-string (when (seq version) v)})))

(defn- ->spec
  [{:keys [group id] :as id-map}
   {:keys [version-string] :as v-map}]
  {:pre [(string? group)
         (string? id)]}
  (let [sym (if (= group id)
              (symbol id)
              (symbol group id))
        form (if version-string
               [sym version-string]
               [sym])]
    (-> (merge id-map v-map)
        (assoc :symbol sym
               :form form
               :value form))))

;; ## Implementations

(extend-protocol Artifact
  clojure.lang.IPersistentVector
  (read-artifact [v]
    {:pre [(seq v)]}
    (let [[id version] v]
      (-> (->spec (parse-id id) (parse-version version))
          (assoc :value v))))

  clojure.lang.IPersistentMap
  (read-artifact [{:keys [version] :as v}]
    (->spec v (parse-version version)))

  clojure.lang.Named
  (read-artifact [n]
    (->spec (parse-id n) nil))

  String
  (read-artifact [n]
    (->spec (parse-id n) nil)))
