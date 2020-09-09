(ns maladroit.config
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[maladroit started successfully]=-"))
   :middleware identity})
