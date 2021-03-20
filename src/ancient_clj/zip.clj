(ns ancient-clj.zip
  (:require [ancient-clj.artifact :as artifact]
            [ancient-clj.zip
             [leiningen :as leiningen]]
            [rewrite-clj.zip :as z]))

;; ## Dependency Collection

(defn dependencies
  "Collect all dependencies from the given zipper using the given visitor."
  [{:keys [visitor sexpr->artifact]} zloc]
  (let [deps (volatile! [])]
    (->> (fn [dependency-loc]
           (vswap! deps conj (sexpr->artifact (z/sexpr dependency-loc)))
           dependency-loc)
         (visitor zloc))
    @deps))

;; ## Dependency Update

(defn- update-or-insert-version
  [dependency-loc version]
  (-> (or (when-let [vloc (-> dependency-loc z/down z/right)]
            (when (and (= :token (z/tag vloc)) (string? (z/sexpr vloc)))
              (z/replace vloc version)))
          (-> dependency-loc z/down (z/insert-right version)))
      (z/up)))

(defn update-dependencies
  "Update all dependencies using the given zipper using the given visitor."
  [{:keys [visitor sexpr->artifact]} zloc new-version-fn]
  (->> (fn [dependency-loc]
         (if-let [version' (some-> (z/sexpr dependency-loc)
                                   (sexpr->artifact)
                                   (new-version-fn))]
           (update-or-insert-version dependency-loc version')
           dependency-loc))
       (visitor zloc)))

;; ## File Types

(defn project-clj
  "Return a visitor to be used with [[update-dependencies]] and
   [[dependencies]], capable of handling `project.clj` files."
  []
  {:visitor         leiningen/visit-project-clj
   :sexpr->artifact artifact/read-artifact})

(defn profiles-clj
  "Return a visitor to be used with [[update-dependencies]] and
   [[dependencies]], capable of handling `profiles.clj` files."
  []
  {:visitor         leiningen/visit-profiles-clj
   :sexpr->artifact artifact/read-artifact})
