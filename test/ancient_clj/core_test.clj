(ns ancient-clj.core-test
  (:require [ancient-clj.test :as test]
            [ancient-clj.core :as ancient]
            [clojure.test :refer [deftest is testing]]))

;; ## Fixtures

(def repositories
  {"all"       (constantly test/versions)
   "releases"  (constantly test/release-versions)
   "qualified" (constantly test/qualified-versions)
   "snapshots" (constantly test/snapshot-versions)})

(defn version-strings
  [v k]
  (map :version-string (get v k)))

(defn no-duplicates?
  [sq]
  (= (count (set sq)) (count sq)))

;; ## Tests

(deftest t-maybe-create-loader
  (is (fn? (ancient/maybe-create-loader {:uri "https://clojars.org/repo"})))
  (is (fn? (ancient/maybe-create-loader {:uri "https://clojars.org/repo", :id "clojars"})))
  (is (fn? (ancient/maybe-create-loader (constantly [])))))

(deftest t-maybe-create-loaders
  (let [loaders (ancient/maybe-create-loaders repositories)]
    (is (fn? (get loaders "all")))
    (is (fn? (get loaders "snapshots")))
    (is (fn? (get loaders "qualified")))
    (is (fn? (get loaders "releases")))))

(deftest t-versions-per-repository!
  (testing "sort descending (default)"
    (let [opts {:repositories repositories}
          versions (ancient/versions-per-repository! 'pandect opts)
          all-results (apply concat (vals versions))]
      (is (= (set (keys repositories))
             (set (keys versions))))
      (is (every? :version all-results))
      (is (every? :version-string all-results))
      (is (= (reverse test/release-versions-sorted)
             (version-strings versions "releases")))
      (is (= (reverse test/qualified-versions-sorted)
             (version-strings versions "qualified")))
      (is (= (reverse test/snapshot-versions-sorted)
             (version-strings versions "snapshots")))
      (is (= (reverse test/versions-sorted)
             (version-strings versions "all")))))
  (testing "sort ascending"
    (let [opts {:repositories repositories, :sort :asc}
          versions (ancient/versions-per-repository! 'pandect opts)]
      (is (= test/release-versions-sorted
             (version-strings versions "releases")))
      (is (= test/qualified-versions-sorted
             (version-strings versions "qualified")))
      (is (= test/snapshot-versions-sorted
             (version-strings versions "snapshots")))
      (is (= test/versions-sorted
             (version-strings versions "all")))))
  (testing "sort none"
    (let [opts {:repositories repositories, :sort :none}
          versions (ancient/versions-per-repository! 'pandect opts) ]
      (is (= test/release-versions
             (version-strings versions "releases")))
      (is (= test/qualified-versions
             (version-strings versions "qualified")))
      (is (= test/snapshot-versions
             (version-strings versions "snapshots")))
      (is (= test/versions
             (version-strings versions "all")))))
  (testing "exclude SNAPSHOT versions"
    (let [opts {:repositories repositories, :sort :asc, :snapshots? false}
          versions (ancient/versions-per-repository! 'pandect opts)]
      (is (= test/release-versions-sorted
             (version-strings versions "releases")))
      (is (= test/qualified-versions-sorted
             (version-strings versions "qualified")))
      (is (empty? (version-strings versions "snapshots")))
      (is (not= test/versions-sorted
                (version-strings versions "all")))))
  (testing "exclude qualified versions"
    (let [opts {:repositories repositories, :sort :asc, :qualified? false}
          versions (ancient/versions-per-repository! 'pandect opts)]
      (is (= test/release-versions-sorted
             (version-strings versions "releases")))
      (is (empty? (version-strings versions "qualified")))
      (is (= test/snapshot-versions-sorted
             (version-strings versions "snapshots")))
      (is (not= test/versions-sorted
                (version-strings versions "all"))))))

(deftest t-versions!
  (testing "sort descending (default)"
    (let [opts {:repositories repositories}
          versions (ancient/versions! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (every? :version versions))
      (is (every? :version-string versions))
      (is (= (reverse test/versions-sorted)
             (map :version-string versions)))))
  (testing "sort ascending"
    (let [opts {:repositories repositories, :sort :asc}
          versions (ancient/versions! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (every? :version versions))
      (is (every? :version-string versions))
      (is (= test/versions-sorted
             (map :version-string versions))))))

(deftest t-version-strings!
  (testing "sort descending (default)"
    (let [opts {:repositories repositories}
          versions (ancient/version-strings! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (= (reverse test/versions-sorted) versions))))
  (testing "sort ascending"
    (let [opts {:repositories repositories, :sort :asc}
          versions (ancient/version-strings! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (= test/versions-sorted versions)))))

(deftest t-latest-version!
  (testing "all versions"
    (let [opts {:repositories repositories}
          version (ancient/latest-version! 'pandect opts)]
      (is (= "0.1.3-SNAPSHOT" (:version-string version)))))
  (testing "only releases"
    (let [opts {:repositories repositories,
                :snapshots? false,
                :qualified? false}
          version (ancient/latest-version! 'pandect opts)]
      (is (= "0.1.2" (:version-string version)))))
  (testing "including qualified versions"
    (let [opts {:repositories repositories,
                :snapshots? false}
          version (ancient/latest-version! 'pandect opts)]
      (is (= "0.1.3-alpha" (:version-string version))))))

(deftest t-artifact-outdated?
  (testing "newer version available"
    (let [opts {:repositories repositories}
          version (ancient/artifact-outdated?
                    ['pandect (first test/versions-sorted)]
                    opts)]
      (is (= "0.1.3-SNAPSHOT" (:version-string version)))))
  (testing "already latest version"
    (let [opts {:repositories repositories}
          version (ancient/artifact-outdated?
                    ['pandect (last test/versions-sorted)]
                    opts)]
      (is (nil? version)))))

(deftest t-exception
  (testing "return exception"
    (let [opts {:repositories
                (merge
                  {"ex" (constantly (ex-info "FAIL" {}))}
                  repositories)}
          versions (ancient/versions-per-repository! 'ancient-clj opts)]
      (is (seq (get versions "all")))
      (is (instance? Exception (get versions "ex")))))
  (testing "throw exception"
    (let [opts {:repositories
                (merge
                  {"ex" (fn [_ _] (throw (ex-info "FAIL" {})))}
                  repositories)}
          versions (ancient/versions-per-repository! 'ancient-clj opts)]
      (is (seq (get versions "all")))
      (is (instance? Exception (get versions "ex"))))))

;; ## Integration Tests

(deftest ^:integration t-integration-versions-per-repository!
  (let [results (ancient/versions-per-repository! 'ancient-clj)]
    (is (contains? (set (keys results)) "clojars"))
    (is (some #{"0.7.0"} (version-strings results "clojars")))))

(deftest ^:integration t-integration-versions!
  (let [results (ancient/versions! 'ancient-clj)]
    (is (seq results))
    (is (every? :version results))
    (is (every? :version-string results))
    (is (some (comp #{"0.7.0"} :version-string) results))))

(deftest ^:integration t-integration-version-strings!
  (let [results (ancient/version-strings! 'ancient-clj)]
    (is (some #{"0.7.0"} results))))

(deftest ^:integration t-integration-latest-version!
  (let [result (ancient/latest-version! 'ancient-clj)]
    (is (:version result))
    (is (:version-string result))))

(deftest ^:integration t-integration-artifact-outdated?
  (let [result (ancient/artifact-outdated? '[ancient-clj "0.1.0"])]
    (is (:version result))
    (is (:version-string result))))

(deftest ^:integration t-integration-artifact-outdated-string?
  (let [result (ancient/artifact-outdated-string? '[pandect "0.1.0"])]
    (is (string? result))))

(deftest ^:integration t-integration-mirrors
  (let [opts   {:mirrors {#"clojars" {:name "unavailable mirror"
                                      :url "file://.repo"}}}
        result (ancient/artifact-outdated-string? '[pandect "0.1.0"] opts)]
    (is (not result))))
