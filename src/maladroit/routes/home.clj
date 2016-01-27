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
        {:keys [filename content-type tempfile]} file-data]
    (try
      (println "\t\t\tPreparing to process file" file-data)
      (catch Exception e (timbre/error (str "An error occurred while uploading: " (.getMessage e)))))
    {:body "Working on the processing"}
    ;{:body (m/process-file tempfile regexp num-iterations)}
    ))

(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/upload" req (upload-doc req))
  (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp))))

