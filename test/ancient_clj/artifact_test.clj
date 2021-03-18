(ns ancient-clj.artifact-test
  (:require [ancient-clj.artifact :as artifact]
            [clojure.test :refer [deftest are is testing]]))

(deftest t-read-artifact
  (testing "basic artifact"
    (let [{:keys [group id version-string version symbol form value]}
          (artifact/read-artifact '[pandect "0.3.0"])]
      (is (= "pandect" group))
      (is (= "pandect" id))
      (is (= "0.3.0" version-string))
      (is (= [[0 3 0]] version))
      (is (= 'pandect symbol))
      (is (= '[pandect "0.3.0"] form))
      (is (= '[pandect "0.3.0"] value))))
  (testing "artifact with explicit group"
    (let [{:keys [group id version-string version symbol form value]}
          (artifact/read-artifact '[xsc/pandect "0.3.0"])]
      (is (= "xsc" group))
      (is (= "pandect" id))
      (is (= "0.3.0" version-string))
      (is (= [[0 3 0]] version))
      (is (= 'xsc/pandect symbol))
      (is (= '[xsc/pandect "0.3.0"] form))
      (is (= '[xsc/pandect "0.3.0"] value))))
  (testing "artifact with explicit group (same as id)"
    (let [{:keys [group id version-string version symbol form value]}
          (artifact/read-artifact '[pandect/pandect "0.3.0"])]
      (is (= "pandect" group))
      (is (= "pandect" id))
      (is (= "0.3.0" version-string))
      (is (= [[0 3 0]] version))
      (is (= 'pandect symbol))
      (is (= '[pandect "0.3.0"] form))
      (is (= '[pandect/pandect "0.3.0"] value))))
  (testing "artifact with extra keys"
    (let [artifact '[pandect "0.3.0" :scope "provided"]
          {:keys [group id version-string version symbol form value]}
          (artifact/read-artifact artifact)]
      (is (= "pandect" group))
      (is (= "pandect" id))
      (is (= "0.3.0" version-string))
      (is (= [[0 3 0]] version))
      (is (= 'pandect symbol))
      (is (= '[pandect "0.3.0"] form))
      (is (= artifact value))))
  (testing "artifact without version"
    (let [artifact '[pandect :scope "provided"]
          {:keys [group id version-string version symbol form value]}
          (artifact/read-artifact artifact)]
      (is (= "pandect" group))
      (is (= "pandect" id))
      (is (nil? version-string))
      (is (nil? version))
      (is (= 'pandect symbol))
      (is (= '[pandect] form))
      (is (= artifact value))))
  (testing "literals"
    (let [expected (artifact/read-artifact '[pandect])]
      (are [value] (= expected (artifact/read-artifact value))
           "pandect"
           :pandect
           'pandect
           {:group "pandect", :id "pandect"}))
    (let [expected (artifact/read-artifact '[xsc/pandect])]
      (are [value] (= expected (artifact/read-artifact value))
           "xsc/pandect"
           :xsc/pandect
           'xsc/pandect
           {:group "xsc", :id "pandect"}))))
