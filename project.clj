(defproject maladroit "0.1.0-SNAPSHOT"

  :description "Maladroit is a mallet interface for processing uploaded books"
  :url "example.com"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [selmer "1.12.8"]
                 [hiccup "1.0.5"]
                 [markdown-clj "1.0.7"]
                 [luminus/config "0.8"]
                 [ring-middleware-format "0.7.4"]
                 [metosin/ring-http-response "0.9.1"]
                 [bouncer "1.0.1"]
                 [org.webjars/bootstrap "4.3.1"]
                 [org.webjars/jquery "3.3.1-2"]
                 [org.clojure/tools.logging "0.4.1"]
                 [luminus-log4j "0.1.5"]
                 [com.taoensso/tower "3.0.2"]
                 [compojure "1.6.1"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-defaults "0.3.2"]
                 [ring "1.7.1" :exclusions [ring/ring-jetty-adapter]]
                 [mount "0.1.16"]
                 ;[luminus-nrepl "0.1.6"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"]
                 [reagent "0.8.1"]
                 [reagent-forms "0.5.43"]
                 [reagent-utils "0.3.2"]
                 [secretary "1.2.3"]
                 [org.clojure/core.async "0.4.490"]
                 [cljs-ajax "0.8.0"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [luminus-immutant "0.2.5"]
                 [org.clojure/data.csv "0.1.4"]
                 [cc.mallet/mallet "2.0.8"]
                 [com.cognitect/transit-cljs "0.8.256"]
                 [com.cognitect/transit-clj "0.8.313"]
                 [garden "1.3.6"]]

  :min-lein-version "2.0.0"
  :uberjar-name "maladroit.jar"
  :jvm-opts ["-server"]
  :resource-paths ["resources" "target/cljsbuild"]

  :main maladroit.core

  :plugins [[lein-environ "1.0.1"]
            [lein-immutant "2.1.0"]
            [lein-cljsbuild "1.1.1"]
            [lein-garden "0.2.6"]]
  :immutant {:war {:name "maladroit%t"}}
  :garden {:builds [{:id "maladroit"
                     :source-paths ["src/styles"]
                     :stylesheet maladroit.styles/maladroit
                     :compiler {:output-to "resources/public/css/maladroit.css"
                                :pretty-print? true}}]}
  :clean-targets ^{:protect false} [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]
  :cljsbuild
  {:builds
   {:app
    {:source-paths ["src-cljs"]
     :compiler
     {:output-to "target/cljsbuild/public/js/app.js"
      :output-dir "target/cljsbuild/public/js/out"
      :externs ["react/externs/react.js"]
      :pretty-print true}}}}
  
  :profiles
  {:uberjar {:omit-source true
             :env {:production true}
              :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
              :cljsbuild
              {:builds
               {:min
                {:source-paths ["env/prod/cljs"]
                 :compiler
                 {:optimizations :advanced
                  :pretty-print false
                  :closure-warnings
                  {:externs-validation :off :non-standard-jsdoc :off}}}}} 
             
             :aot :all
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}
   :dev           [:project/dev :profiles/dev]
   :test          [:project/test :profiles/test]
   :project/dev  {:dependencies [[prone "1.6.1"]
                                 [ring/ring-mock "0.3.2"]
                                 [ring/ring-devel "1.7.1"]
                                 [pjstadig/humane-test-output "0.9.0"]]
                  :cljsbuild
                   {:builds
                    {:app
                     {:source-paths ["env/dev/cljs"]
                      :compiler
                      {:main "maladroit.app"
                       :asset-path "/js/out"
                       :optimizations :none
                       :source-map true}}}} 
                  
                  :figwheel
                  {:http-server-root "public"
                   :server-port 3449
                   :nrepl-port 7002
                   :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
                   :css-dirs ["resources/public/css"]
                   :ring-handler maladroit.handler/app}
                  
                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]
                  ;;when :nrepl-port is set the application starts the nREPL server on load
                  :env {:dev        true
                        :port       3000
                        :nrepl-port 7000}}
   :project/test {:env {:test       true
                        :port       3001
                        :nrepl-port 7001}}
   :profiles/dev {}
   :profiles/test {}})
