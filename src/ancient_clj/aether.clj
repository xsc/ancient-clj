(ns ancient-clj.aether
  (:require [cemerick.pomegranate.aether :as aether])
  (:import (org.eclipse.aether.resolution VersionRangeRequest)
           (org.eclipse.aether.artifact DefaultArtifact)
           (org.eclipse.aether.artifact DefaultArtifact)
           (org.eclipse.aether.repository LocalRepository)
           (org.eclipse.aether
             DefaultRepositorySystemSession
             RepositorySystem
             RepositorySystemSession)
           (java.io File)))

;; ## Repository System

(defmacro ^:private defhack
  [sym]
  `(def ~(with-meta sym {:private true})
     "HACK: We need direct access to this (but it's private - for good reason)."
     (try
       (-> (ns-resolve 'cemerick.pomegranate.aether '~sym)
           (deref))
       (catch Throwable e#))))

(defhack repository-system)
(defhack mirror-selector-fn)
(defhack mirror-selector)

;; ## Disable Local Repository

(defn- disable-local-repository!
  "We do not need the local repository since we want to always reach out
   to the data available in the remote repositories. "
  [^DefaultRepositorySystemSession session]
  (doto session
    (.setUpdatePolicy (:always aether/update-policies))))

;; ## Infrastructure

(defn- as-repository-system
  ^RepositorySystem
  []
  (repository-system))


(defn- as-repository-session
  ^RepositorySystemSession
  [^RepositorySystem system
   {:keys [repository-session-fn
           mirrors
           proxy
           local-repo
           offline?]}]
  (let [mirror-selector-fn (memoize (partial mirror-selector-fn mirrors))]
    (doto ((or repository-session-fn
               aether/repository-session)
           {:repository-system system
            :local-repo        local-repo
            :offline?          false
            :mirror-selector   (mirror-selector mirror-selector-fn proxy)})
      (disable-local-repository!))))

(defn- as-remote-repositories
  [^RepositorySystemSession session {:keys [repositories proxy]}]
  (vec
    (for [repository repositories
          :let [remote-repository (aether/make-repository repository proxy)]]
      (or (.. session (getMirrorSelector) (getMirror remote-repository))
          remote-repository))))

;; ## Version Range Request

(defn- as-range-request
  [remote-repos group id]
  (let [artifact (DefaultArtifact. (str group ":" id ":jar:[0,)"))]
    (VersionRangeRequest. artifact remote-repos nil)))

;; ## Loader

(defn loader
  "Loader depending on `clj-commons/pomegranate`. Takes the same options as
   `cemerick.pomegranate.aether/resolve-artifacts`."
  [[id spec] & [opts]]
  (if repository-system
    (let [opts         (assoc opts :repositories {id spec})
          system       (as-repository-system)
          session      (as-repository-session system opts)
          remote-repos (as-remote-repositories session opts)]
      (fn [group id]
        (->> (as-range-request remote-repos group id)
             (.resolveVersionRange system session)
             (.getVersions)
             (mapv str))))))
