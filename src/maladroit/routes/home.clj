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

(defn home-page []
  (layout/render "home.html"))

(defn make-file-download [data file-name content-type]
  (-> data r/response
      (#(r/header % "Content-Disposition" (str "attachment; filename=\"" file-name "\"")))
      (#(r/header % "Content-Type" content-type))
      ))

(defn upload-doc [req]
  (timbre/info "Request is: >>>\n" req)
  (let [ ;params (-> req :params)
        file-data (-> req :params :file)
        {:keys [filename content-type tempfile]} file-data        ;; AJAX version
                                        ;file-data (:file params) ;; FORM version
                                        ;process-req (-> req :params :data decode-transit-string)
        data (-> req :params :data decode-transit-string) ;; AJAX
        {:keys [regexp passes num-keywords num-topics]} data
        _ (println "passes is " passes)
        default-re #"(?m)^\* "
        re (try
             (re-pattern (str "(?m)" regexp))
             (catch Exception e (do (timbre/warn "Failed to convert regexp")
                                    default-re)))
        _ (println "Regexp is " re)
        file-stream (io/input-stream tempfile)
        results (m/process-file :file tempfile
                                :regexp re
                                :num-iterations passes
                                :num-keywords num-keywords
                                :num-topics num-topics)
        topics-keys-results (-> results
                    :topics-keys
                    m/to-tsv
                    str)
        topics-results (-> results
                           :doc-topics
                           m/to-tsv
                           str)
        txt-results {:keys topics-keys-results
                     :topics topics-results}]
    ;(println "Topics results is " topics-results)
    (make-file-download
     [[topics-keys-results]
      [topics-results]]
     "results.csv"
     "application/transit+json")))

(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/upload" req (upload-doc req))
  (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp))))

