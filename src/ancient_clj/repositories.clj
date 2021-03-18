(ns ancient-clj.repositories
  (:require [ancient-clj.artifact :as artifact]
            [ancient-clj.repositories.aether :as aether]
            [version-clj.core :as version]))

;; ## Default Repositories

(def default-repositories
  {"central" "https://repo1.maven.org/maven2"
   "clojars" "https://clojars.org/repo"})

;; ## Postprocessing

(defn- parse-versions
  [versions]
  (if (sequential? versions)
    (mapv
      (fn [version]
        (assoc (version/parse version) :version-string version))
      versions)
    versions))

;; ## Loaders

(defn- as-loader-fn
  [repository-id repository-spec {:keys [wrap] :as opts}]
  (cond->> (if (fn? repository-spec)
             repository-spec
             (aether/loader repository-id repository-spec opts))
    wrap (wrap repository-id)))

(defn- create-single-loader
  "Create a loader function that takes a dependency spec and returns a map
   associating the repository-id with either the available versions or an
   exception."
  [repository-id repository-spec opts]
  (let [loader-fn (as-loader-fn repository-id repository-spec opts)]
    (fn ancient-clj-loader
      [spec]
      (->> (try
             (loader-fn spec)
             (catch Exception e e))
           (hash-map repository-id)))))

(defn loader
  "Create a loader function that is able to resolve all versions of an artifact
  from multiple repositories using pomegranate/aether. The loader function will
  return a map with `:artifact` (the parsed artifact) and `:versions` a map
  of versions per repository."
  [{:keys [repositories]
    :or {repositories default-repositories}
    :as opts}]
  (let [loaders (vec
                  (for [[id spec] repositories]
                    (create-single-loader id spec opts)))]
    (fn [dependency]
      (let [spec (artifact/read-artifact dependency)
            results (->> (pmap #(% spec) loaders)
                         (into {})
                         (reduce-kv
                           (fn [acc id versions]
                             (assoc acc id (parse-versions versions)))
                           {}))]
        {:artifact spec
         :versions results}))))
