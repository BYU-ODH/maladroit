(ns maladroit.routes.home
  (:require [maladroit.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :refer [ok]]
            [ring.util.response :as r]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [maladroit.mallet :as m]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream]))

(defn decode-transit-string [tstring]
  (let [bis (-> tstring .getBytes ByteArrayInputStream.)
        r (transit/reader bis :json)]
    (transit/read r)))

(defn parse-stopwords [sw]
  (into-array (clojure.string/split sw #" ")))

(defn home-page []
  (layout/render "home.html"))

(defn make-file-download [data file-name content-type]
  (-> data r/response
      (#(r/header % "Content-Disposition" (str "attachment; filename=\"" file-name "\"")))
      (#(r/header % "Content-Type" content-type))
      ))

(defn upload-doc [req]
  (timbre/info "Request is: >>>\n" req)
  #_(def req {:params {:file {:filename "test.txt", :content-type "text/plain" :tempfile "/home/torysa/tmp/test.txt"}, :data ["^ ","~:regexp","~^\\* ","~:passes",300,"~:num-topics",8,"~:num-keywords",10,"~:stopwords",""]}})

  (let [file-data (-> req :params :file)
        {:keys [filename content-type tempfile]} file-data        ;; AJAX version
        data (-> req :params :data decode-transit-string) ;; AJAX (def string (->> req :params :data (apply str)))
        {:keys [regexp passes num-keywords num-topics stopwords]} data
        default-re #"(?m)^\* " ;(def regexp default-re)
        re (try
             (re-pattern (str "(?m)" regexp))
             (catch Exception e (do (timbre/warn "Failed to convert regexp")
                                    default-re)))
        results (m/process-file :file tempfile
                                :regexp re
                                :num-iterations passes
                                :num-keywords num-keywords
                                :num-topics num-topics
                                :stopwords (parse-stopwords stopwords)
                                )
        topics-keys-results (-> results
                    :topics-keys
                    m/to-tsv
                    str)
        topics-results (-> results
                           :doc-topics
                           m/to-tsv
                           str)
        gephi-results (-> results
                          :stacked
                          m/to-gephi-csv
                          str)]
    (make-file-download
     [[topics-keys-results]
      [topics-results]
      [gephi-results]]
     "results.csv"
     "application/transit+json")))
(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/upload" req (upload-doc req))
  (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp))))

