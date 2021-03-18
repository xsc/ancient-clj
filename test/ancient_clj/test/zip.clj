(ns ancient-clj.test.zip
  (:require [rewrite-clj.zip :as z]))

(defn form->zloc
  [form]
  (z/of-string (pr-str form)))
