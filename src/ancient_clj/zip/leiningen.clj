(ns ^:no-doc ancient-clj.zip.leiningen
  (:require [rewrite-clj.zip :as z]))

;; ## Find

(def ^:private dependency-keys
  #{:dependencies
    :managed-dependencies
    :plugins
    :java-agents})

(defn- find-dependency-vector
  [zloc]
  (loop [zloc zloc]
    (when-let [candidate (z/find-tag zloc :token)]
      (if (contains? dependency-keys (z/sexpr candidate))
        (z/right candidate)
        (recur (z/right candidate))))))

(defn- find-profiles-value
  [zloc]
  (loop [zloc zloc]
    (when-let [candidate (z/find-tag zloc :token)]
      (when (keyword? (z/sexpr candidate))
        (if-let [vloc (z/right candidate)]
          (if (contains? #{:map :vector} (z/tag vloc))
            vloc
            (recur (z/right vloc)))
          (recur (z/right candidate)))))))

;; ## Visitor

(defn- visit
  [zloc find-fn alter-fn]
  {:post [(some? %)]}
  (if-let [inner (z/down zloc)]
    (loop [zloc inner]
      (if-let [vloc (find-fn zloc)]
        (let [vloc' (alter-fn vloc)]
          (if-let [zloc' (z/right vloc')]
            (recur zloc')
            (z/up vloc')))
        (z/up zloc)))
    zloc))

(defn- visit-dependency-vector
  "Visit each dependency inside the given zipper (currently on a dependency
   vector)."
  [zloc f]
  (visit zloc #(z/find-tag % :vector) f))

(defn- visit-profile
  "Visit each dependency inside the given zipper (currently on a single profile
   map)."
  [zloc f]
  (visit zloc find-dependency-vector #(visit-dependency-vector % f)))

(defn- visit-nested-profile
  "Visit each dependency inside the given zipper (currently on a nested profile
   vector)."
  [zloc f]
  (visit zloc #(z/find-tag % :map) #(visit-profile % f)))

(defn- visit-profiles
  "Visit each dependency inside the given zipper (currently on a profiles map)."
  [zloc f]
  (->> (fn [zloc]
         (if (= :map (z/tag zloc))
           (visit-profile zloc f)
           (visit-nested-profile zloc f)))
       (visit zloc find-profiles-value)))

;; ## Leiningen Files

(defn- raise
  [^String message zloc]
  (throw
    (ex-info message {:zloc zloc})))

(defn visit-project-clj
  "Find the '(defproject ...)' list and visit each dependency within."
  [zloc f]
  (if-let [zloc' (some-> (z/find-value zloc z/next 'defproject)
                         (z/up)
                         (visit-profile f))]
    (or (some-> zloc'
                (z/down)
                (z/find-value :profiles)
                (z/right)
                (visit-profiles f)
                (z/up))
        zloc')
    (raise "Invalid project file! (No 'defproject' found.)" zloc)))

(defn visit-profiles-clj
  "Find the profiles map and visit each dependency within."
  [zloc f]
  (or (some-> (z/find-tag zloc :map)
              (visit-profiles f))
      (raise "Invalid profiles file! (No map found." zloc)))
