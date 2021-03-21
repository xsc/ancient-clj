(ns ancient-clj.core
  (:require [ancient-clj
             [artifact :as artifact]
             [repositories :as r]
             [zip :as z]]
            [version-clj.core :as version]
            [rewrite-clj.zip :as rewrite]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; ## Loader

;; ### Basic Loader

(defn loader
  "See [[ancient-clj.repositories/loader]]. Will only return a flattened seq
   of version maps."
  [opts]
  (let [load-fn (r/loader opts)]
    (fn [dependency]
      (->> dependency
           (artifact/read-artifact)
           (load-fn)
           (:versions)
           (vals)
           (filter coll?)
           (apply concat)
           (distinct)))))

(defn default-loader
  "See [[loader]]. Will use the default repositories and only return a flattened
   seq of version maps."
  []
  (loader {}))

;; ### Middlewares

(defn- remove-snapshots
  [versions]
  (remove (comp :snapshot? ::version/version) versions))

(defn- remove-qualified-except-snapshots
  [versions]
  (remove
    (fn [{{:keys [snapshot? qualified?]} ::version/version}]
      (and qualified? (not snapshot?)))
    versions))

(defn wrap-ignore
  "Wrap the given loader to ignore certain version types, given as a seq of
   keywords:

   - `:snapshot`: If given, removes SNAPSHOT versions from the result.
   - `:qualified`: If given, removes qualified versions from the result (except
     SNAPSHOT ones)."
  [loader what-to-ignore]
  (let [ignore? (set what-to-ignore)]
    (fn [dependency]
      (cond-> (loader dependency)
        (ignore? :snapshot) remove-snapshots
        (ignore? :qualified) remove-qualified-except-snapshots))))

(defn wrap-sort
  [loader asc-or-desc]
  (let [comparator (case asc-or-desc
                     :asc  version/version-seq-compare
                     :desc (comp - version/version-seq-compare))]
    (fn [dependency]
      (sort-by
        #(get-in % [::version/version :version])
        comparator
        (loader dependency)))))

(defn wrap-as-string
  "Wrap a [[loader]] to only return the contained version strings."
  [loader]
  (fn [dependency]
    (->> (loader dependency)
         (map ::artifact/version))))

;; ## Collector

(defn- outdated?
  [{version-a ::version/version} {version-b ::version/version}]
  (neg? (version/version-seq-compare
          (:version version-a)
          (:version version-b))))

(defn- select-latest-version
  [versions]
  (reduce
    (fn [latest version]
      (if latest
        (if (outdated? latest version)
          version
          latest)
        version))
    nil
    versions))

(defn collector
  "Will create a collector function that takes a `rewrite-clj` zipper, collects
  all dependencies, loads the latest versions and returns each artifact that
  needs to be updated, incl. a `:latest-version` key."
  [{:keys [visitor
           check?
           loader]
    :or {check?  (constantly true)
         loader  (default-loader)}}]
  (fn [zloc]
    (->> (for [dependency (z/dependencies visitor zloc)
               :when (check? dependency)
               :let  [version (->> (loader dependency)
                                   (select-latest-version))]
               :when version
               :when (outdated? dependency version)]
           (assoc dependency ::artifact/latest-version version))
         (into []))))

;; ## Updater

;; ### Zipper Updater

(defn updater
  "Will create an updater functions that takes a `rewrite-clj` zipper, collects
  all dependencies, loads the latest versions and updates them. The result will
  be an updated zipper."
  #_{:clj-kondo/ignore [:unused-binding]}
  [{:keys [visitor
           check?
           update?
           loader]
    :or {check?  (constantly true)
         update? (constantly true)
         loader  (default-loader)}
    :as opts}]
  (let [collect-zloc (collector opts)
        select (fn [latest-versions artifact]
                 (get latest-versions (::artifact/symbol artifact)))]
    (fn [zloc]
      (->> (for [dependency (collect-zloc zloc)
                 :when (update? dependency)
                 :let [latest (get dependency ::artifact/latest-version)]]
             [(::artifact/symbol dependency) (::artifact/version latest)])
           (into {})
           (partial select)
           (z/update-dependencies visitor zloc)))))

;; ### File Updater

(defn- checksum-of
  [^File file]
  (.lastModified file))

(defn- assert-checksum
  [^File file checksum]
  (when (not= (checksum-of file) checksum)
    (throw
      (IllegalStateException.
        "File was modified while performing dependency update!"))))

(defn file-updater
  "See [[updater]]. Will update a file in place.

  If the file is modified while the update is ongoing, this will throw an
  exception."
  [opts]
  (let [update-zloc (updater opts)]
    (fn update-file
      ([file]
       (update-file file spit))
      ([file write-fn]
       (let [file     (io/file file)
             checksum (checksum-of file)
             zloc     (update-zloc (rewrite/of-file file))]
         (assert-checksum file checksum)
         (write-fn file (rewrite/root-string zloc)))))))

;; ## API

(defn read-artifact
  "Convert artifact vector or symbol to a map of:
   - `:group`:  artifact group,
   - `:id`:     artifact ID,
   - `:symbol`: normalized artifact symbol (incl. group/ID),
   - `:form`:   normalized artifact vector,
   - `:value`:  original value (if a vector was given).
  "
  [artifact]
  (artifact/read-artifact artifact))

(defn sorted-versions
  "Return a sorted list of versions (oldest -> newest), represented as
   version maps."
  ([artifact]
   (sorted-versions (default-loader) artifact))
  ([loader artifact]
   (->> (loader artifact)
        (sort-by #(get-in % [::version/version :version])
                 version/version-seq-compare))))

(defn latest-version
  "Return the latest version from the given loader, represented as a version
   map."
  ([artifact]
   (latest-version (default-loader) artifact))
  ([loader artifact]
   (->> (loader artifact)
        (select-latest-version))))

;; ## Deprecated API

(defn- as-loader
  [fn-or-map]
  (if (fn? fn-or-map)
    fn-or-map
    (let [{:keys [sort snapshots? qualified?]
           :or {sort       :desc
                snapshots? true
                qualified? true}} fn-or-map]
      (cond-> (loader fn-or-map)
        (#{:asc :desc} sort) (wrap-sort sort)
        (not snapshots?)     (wrap-ignore [:snapshot])
        (not qualified?)     (wrap-ignore [:qualified])))))

(defn- unpack-version
  [version]
  (assoc (::version/version version)
         :version-string (::artifact/version version)))

(defn ^{:deprecated "2.0.0"} versions!
  [artifact & [opts]]
  (let [load! (as-loader opts)]
    (->> (load! artifact)
         (map unpack-version))))

(defn ^{:deprecated "2.0.0"} version-strings!
  [artifact & [opts]]
  (->> (versions! artifact opts)
       (map :version-string)))

(defn ^{:deprecated "2.0.0"} latest-version!
  [artifact & [opts]]
  (-> (as-loader opts)
      (latest-version artifact)
      (unpack-version)))

(defn ^{:deprecated "2.0.0"} latest-version-string!
  [artifact & [opts]]
  (:version-string (latest-version! artifact opts)))

(defn ^{:deprecated "2.0.0"} artifact-outdated?
  [artifact & [opts]]
  (let [artifact' (read-artifact artifact)]
    (when-let [latest (latest-version (as-loader opts) artifact')]
      (when (outdated? artifact' latest)
        (unpack-version latest)))))

(defn ^{:deprecated "2.0.0"} artifact-outdated-string?
  [artifact & [opts]]
  (:version-string (artifact-outdated? artifact opts)))
