(ns maladroit.config
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [maladroit.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[maladroit started successfully using the development profile]=-"))
   :middleware wrap-dev})
