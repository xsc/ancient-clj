(ns ancient-clj.repositories-test
  (:require [ancient-clj.repositories :as r]
            [ancient-clj.test.generators :as test-gen]
            [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :as chuck]))

;; ## Helpers

(defn- matches?
  [versions data]
  (and (= (set (keys versions)) (set (keys data)))
       (every?
         (fn [[repository-id vs]]
           (= (get data repository-id) (map :version-string vs)))
         versions)))

;; ## Test

(defspec t-loader (chuck/times 50)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories]
    (let [load! (r/loader {:repositories repositories})
          {:keys [artifact versions]} (load! 'ancient-clj)]
      (and (= "ancient-clj" (:group artifact))
           (= "ancient-clj" (:id artifact))
           (matches? versions data)))))

(defspec t-loader-with-wrap (chuck/times 10)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories]
    (let [calls (atom #{})
          add-call #(swap! calls conj [%1 (:symbol %2)])
          opts {:repositories repositories
                :wrap (fn [repository-id f]
                        (fn [artifact]
                          (add-call repository-id artifact)
                          (f artifact)))}
          load! (r/loader opts)
          {:keys [versions]} (load! 'ancient-clj)]
      (and (matches? versions data)
           (= (->> (keys data)
                   (map #(vector % 'ancient-clj))
                   (set))
              @calls)))))

(defspec t-loader-with-exception (chuck/times 10)
  (prop/for-all
    [{:keys [repositories data]} test-gen/gen-repositories
     repo-fn (gen/elements [(constantly (ex-info "FAIL" {}))
                            (fn [_] (throw (ex-info "FAIL" {})))])]
    (let [opts  {:repositories (merge {"ex" repo-fn} repositories)}
          load! (r/loader opts)
          {:keys [versions]} (load! 'ancient-clj)]
      (and (matches? (dissoc versions "ex") data)
           (instance? Exception (get versions "ex"))))))
