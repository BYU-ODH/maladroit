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
  (let [params (-> req :params)
        file-data (:file-up params)
        ;{:keys [filename content-type tempfile]} file-data
        _ (println "type of file-up is " (type file-data))
        ;process-req (-> req :params :data decode-transit-string)
        ;_ (println "\n\n Process req is " process-req "of type " (type process-req))
        {:keys [regexp passes num-keywords]} params
        _ (println "passes is " passes)
        default-re #"(?m)^\* "
        re (try
             (re-pattern regexp)
             (catch Exception e default-re))
        ;; file-stream (io/input-stream tempfile)
        ;; topics-keys-results (-> (m/process-file tempfile default-re passes)
        ;;             :topics-keys
        ;;             m/to-tsv)
        ]
    ;; (with-open [os  (io/output-stream "/home/torysa/temp/uploaded.txt")]
    ;;   (spit os (slurp tempfile))) ;; this works, too
    ;(println "Wrote to /home/torysa/temp/uploaded.txt")
                                        ;(timbre/info (slurp tempfile)) ;; this works
    ;(make-file-download topics-keys-results "topicskeys.csv" "text/csv")
    ))

(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/upload" req (upload-doc req))
  (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp))))

