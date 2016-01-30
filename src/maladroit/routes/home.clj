(ns maladroit.routes.home
  (:require [maladroit.layout :as layout]
            [compojure.core :refer [defroutes GET POST ]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [maladroit.mallet :as m]))

(defn home-page []
  (layout/render "home.html"))

(defn upload-doc [req]
  (timbre/info "Request is: >>>\n" req)
  (let [file-data (-> req :params :file)
        {:keys [filename content-type tempfile]} file-data
        process-params (-> req :params :data)
        {:keys [regexp passes num-keywords]} process-params
        default-re #"(?m)^\* "
        re (try
             (re-pattern regexp)
             (catch Exception e default-re))
        ;results (m/process-file tempfile default-re passes)
        ]
    ;(timbre/info (slurp tempfile)) ;; this works
    {:body "done"}
                                        ;{:body (m/process-file tempfile regexp num-iterations)}
    ))

(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/upload" req (upload-doc req))
  (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp))))

