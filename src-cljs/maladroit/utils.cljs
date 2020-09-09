(ns maladroit.utils) 

(defn toArray
  "http://www.dotkam.com/2012/11/23/convert-html5-filelist-to-clojure-vector/"
  [js-col]
  (-> (clj->js []) 
      (.-slice)
      (.call js-col)
      (js->clj)))

(defn deep-merge* [& maps]
  (let [f (fn [old new]
             (if (and (map? old) (map? new))
                 (merge-with deep-merge* old new)
                 new))]
    (if (every? map? maps)
      (apply merge-with f maps)
     (last maps))))

(defn deep-merge [& maps]
  (let [maps (filter identity maps)]
    (assert (every? map? maps))
   (apply merge-with deep-merge* maps)))

;; Need deep sort, so a structure of nested maps is sorted at each level

(defn deep-sort-asc
  "sort each level of a map recursively, by sort-key if present"
  [map sort-key]
  (let [sort-fn (fn [k1 k2] (compare [(get-in map [k1 sort-key]) k1]
                                     [(get-in map [k2 sort-key]) k2]))
        sorted-children (into (sorted-map-by sort-fn) 
                              (for [[k v] map]
                                (if (=  (type v) (type {}))
                                  {k (deep-sort-asc v sort-key)}
                                  {k v})))]
    sorted-children))
