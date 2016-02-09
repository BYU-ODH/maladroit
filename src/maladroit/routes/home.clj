(ns maladroit.routes.home
  (:require [maladroit.layout :as layout]
            [compojure.core :refer [defroutes GET POST ]]
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
      (#(r/header % "Content-Type" content-type))))

(defn upload-doc [req]
  (timbre/info "Request is: >>>\n" req)
  (let [ ;params (-> req :params)
        file-data (-> req :params :file)
        {:keys [filename content-type tempfile]} file-data        ;; AJAX version
                                        ;file-data (:file params) ;; FORM version
        _ (println "type of tempfile is " (type tempfile))
        ;process-req (-> req :params :data decode-transit-string)
        ;_ (println "\n\n Process req is " process-req "of type " (type process-req))
        data (-> req :params :data decode-transit-string) ;; AJAX
        {:keys [regexp passes num-keywords]} data
        _ (println "passes is " passes)
        default-re #"(?m)^\* "
        re (try
             (re-pattern regexp)
             (catch Exception e default-re))
        file-stream (io/input-stream tempfile)
        results (m/process-file tempfile default-re passes)
        topics-keys-results (-> results
                    :topics-keys
                    m/to-tsv
                    str
                    ;m/to-input-stream
                    )
        topics-results (-> results
                           :topics
                           m/to-tsv
                           str)
        txt-results {:keys topics-keys-results
                     :topics topics-results}]
    ;; (with-open [os  (io/output-stream "/home/torysa/temp/uploaded.txt")]
    ;;   (spit os (slurp tempfile))) ;; this works, too
    ;(println "Wrote to /home/torysa/temp/uploaded.txt")
                                        ;(timbre/info (slurp tempfile)) ;; this works
    ;; {:body [(make-file-download topics-keys-results "topicskeys.csv" "application/csv")
    ;;         (make-file-download topics-results "topics.csv" "application/csv")]}
    (make-file-download topics-keys-results "topicskeys.csv" "application/csv")
    ;(make-file-download topics-keys-results "doctopics.csv" "application/csv")
    ))

(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/upload" req (upload-doc req))
  (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp))))

