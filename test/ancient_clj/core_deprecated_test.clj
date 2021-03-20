(ns ancient-clj.core-deprecated-test
  (:require [ancient-clj.core :as ancient]
            [version-clj.core :as v]
            [clojure.test :refer [deftest is testing]]))

;; ## Fixtures

(def snapshot-versions
  ["0.1.0-SNAPSHOT" "0.1.1-SNAPSHOT" "0.1.3-SNAPSHOT"])

(def snapshot-versions-sorted
  (v/version-sort snapshot-versions))

(def qualified-versions
  ["0.1.1-RC0" "0.1.3-alpha"])

(def qualified-versions-sorted
  (v/version-sort qualified-versions))

(def release-versions
  ["0.1.0" "0.1.1" "0.1.2"])

(def release-versions-sorted
  (v/version-sort release-versions))

(def all-versions
  (concat
    snapshot-versions
    qualified-versions
    release-versions))

(def all-versions-sorted
  (v/version-sort all-versions))

(def repositories
  {"all"       (constantly all-versions)
   "releases"  (constantly release-versions)
   "qualified" (constantly qualified-versions)
   "snapshots" (constantly snapshot-versions)})

(defn version-strings
  [v k]
  (map :version-string (get v k)))

(defn no-duplicates?
  [sq]
  (= (count (set sq)) (count sq)))

;; ## Tests

(deftest t-versions!
  (testing "sort descending (default)"
    (let [opts {:repositories repositories}
          versions (ancient/versions! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (every? :version versions))
      (is (every? :version-string versions))
      (is (= (reverse all-versions-sorted)
             (map :version-string versions)))))
  (testing "sort ascending"
    (let [opts {:repositories repositories, :sort :asc}
          versions (ancient/versions! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (every? :version versions))
      (is (every? :version-string versions))
      (is (= all-versions-sorted
             (map :version-string versions)))))
  (testing "excluding snapshots/qualified versions"
    (let [opts {:repositories repositories,
                :sort :asc
                :snapshots? false,
                :qualified? false}
          versions (ancient/versions! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (every? :version versions))
      (is (every? :version-string versions))
      (is (= release-versions-sorted
             (map :version-string versions))))))

(deftest t-version-strings!
  (testing "sort descending (default)"
    (let [opts {:repositories repositories}
          versions (ancient/version-strings! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (= (reverse all-versions-sorted) versions))))
  (testing "sort ascending"
    (let [opts {:repositories repositories, :sort :asc}
          versions (ancient/version-strings! 'pandect opts)]
      (is (no-duplicates? versions))
      (is (= all-versions-sorted versions)))))

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
                    ['pandect (first all-versions-sorted)]
                    opts)]
      (is (= "0.1.3-SNAPSHOT" (:version-string version)))))
  (testing "already latest version"
    (let [opts {:repositories repositories}
          version (ancient/artifact-outdated?
                    ['pandect (last all-versions-sorted)]
                    opts)]
      (is (nil? version)))))

;; ## Integration Tests

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

(deftest ^:integration t-integration-latest-version-string!
  (let [result (ancient/latest-version-string! 'ancient-clj)]
    (is (string? result))))

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
