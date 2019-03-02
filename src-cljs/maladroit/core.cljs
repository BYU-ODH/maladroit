(ns maladroit.core
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.browser.dom :as dom]
            [clojure.string :as s]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [cognitect.transit :as transit]
            [maladroit.utils :as ut])
  (:import goog.History))
(def FILE (atom nil))
(def DATA (atom {:regexp "^\\* "
                 :passes 300
                 :num-topics 8
                 :num-keywords 10
                 :stopwords ""}))
(def SUBMIT-TEXT (atom [:div.submit-text "Drop or click to select .txt file"]))
(def DATA-LINK (atom [:div.data ""]))
(def DRAGGING (atom false))
(def DROP-CLASSES (atom ""))
(def UP-ERROR (atom {:page ()
                     :default-message [:span.label.label-warning ".docx files only"]
                     :message ()}))
(def SUBMIT-BUTTON (atom {:text "Upload File First"
                          :class "disabled"
                          :disabled true}))
(defn space-split [string]
  (s/split string \space))

(defn update-data
  ([key id] (update-data key id nil))
  ([key id conversion-fun]
   (let [value (->  (.getElementById js/document id) .-value)]
     (if conversion-fun
       (when-let [casted (conversion-fun value)]
         (swap! DATA assoc key casted))
       (swap! DATA assoc key value)))))

;;;;;;;;;;;;;;;;;
;; FILE UPLOAD ;;
;;;;;;;;;;;;;;;;;
(defn decode-transit
  "Handles full-data responses, e.g. for doc uploads.
  When an error returns (e.g. bad format), update error instead."
  [data]
  (let [r (transit/reader :json)]
    (try 
      (transit/read r data)
      (catch js/Error er (dom/log "error with parsing response received")))))


(defn generate-csv-data-link [csv-data doc-name]
  (let [charset "utf-8,"
        data (js/encodeURIComponent csv-data)
        link-text (str "Download " doc-name) 
        uri (str "data: application/csv;" charset data)]
    [:a {:href uri
         :download doc-name} link-text]))

(defn response-data-listener
  "Act when the response comes after an upload"
  [e]
  (let [response (-> e .-target .-response)
        transit-data (-> (decode-transit response)
                         (#(for [[t] %]
                             (do (println "dat is " (type t))
                                 (s/replace t "\t" \tab)
                                 ))))
        doc-names ["keywords.csv" "topics.csv" "gephi.csv"]]
    (reset! SUBMIT-BUTTON {:text "Process Again"
                           :class "enabled"
                           :disabled false})
    (reset! DROP-CLASSES "")
    (reset! DATA-LINK (into [:div.data] (for [[csv name] (map vector transit-data doc-names)] (do (println "name: " name) (generate-csv-data-link (str csv) name)))))))

(defn upload-file
  "Upload the given file to the server"
  ([file] 
   (letfn [(listen-progress [e]
             (let [done (or (.-position e)
                            (.-loaded e))
                   total (or (.-totalsize e)
                             (.-total e))
                   percent (.round js/Math (/ done (* 100 total)))]               
               (.log js/console (str "xhr progress " percent "%"))))]
     (let [xhr (js/XMLHttpRequest.)
           progress-listener (.addEventListener xhr "progress" listen-progress)
           load-listener (.addEventListener xhr "load" response-data-listener)
           form-data (js/FormData.)
           doc-key (-> :doc session/get keyword)
           url-target "upload"
           anti-forgery-token (.-value (.getElementById js/document "__anti-forgery-token"))
           w (transit/writer :json)
           data (transit/write w @DATA)]
       (swap! DROP-CLASSES str " three-quarters-loader")
       (reset! SUBMIT-BUTTON {:text "Processing File..."
                              :class "disabled"
                              :disabled true})
       (.open xhr "POST" url-target true)
       (.setRequestHeader xhr "x-csrf-token" anti-forgery-token)
       (.setRequestHeader xhr "accept" "application/transit+json")
                                        ;(.setRequestHeader xhr "accept" "text/csv")
       (.append form-data "file" file)
       (.append form-data "data" data)
       (.send xhr form-data))))) ;; test

(defn drag-hover
  "When an item is dragged over the element, 
  display the hover screen / alter styles appropriately."
  [event]
  (dom/log "Dragged over")
  (.stopPropagation event)
  (.preventDefault event)
  (reset! DRAGGING true))

(defn drag-off
  "When an item is dragged off the element, 
  undo the drag-hover"
  [event]
  (dom/log "Dragged off")
  (.stopPropagation event)
  (.preventDefault event)
  (reset! DRAGGING false))

(defn round-to-2 "round to two decimals" [num]
  (-> num (* 100) (->> (.round js/Math)) (/ 100)))

(defn size-format [size]
  (let [kb (->  (/ size 1024) round-to-2)
        mb (-> (/ kb 1024) round-to-2)
        threshold 0.85
        label (cond
                (> mb threshold) (str mb " mb")
                (> kb threshold) (str kb " kb")
                :default (str size " bytes"))]
    label))

(defn parse-file [file]
  (let [file-type (.-type file)
        file-size (size-format (.-size file))
        file-name (.-name file)
        allowed-types #{
                        "text/plain"
                        }]
    (if (allowed-types file-type)
      (do
                                        ;(reset! SUBMIT-TEXT (str file-name "\n(" file-size ")" ))
        (reset! SUBMIT-BUTTON {:text "Process File"
                               :class "enabled"
                               :disabled false})
        (reset! SUBMIT-TEXT [:div.submit-text
                             [:span.filename file-name]
                             [:span.filesize file-size]])
        (reset! FILE file))
      (js/alert (str  "Sorry; you can't upload files of type " file-type)))))

(defn file-handler [event]
  (let [files (or
               (-> event .-dataTransfer .-files)
               (-> event .-target .-files))
        fcount (.-length files)
        files (ut/toArray files)
        file (first files)]
    (.log js/console files)
    (if (> fcount 1)
      (js/alert "Please select only one file")
      (parse-file file))))

(defn try-submit-file
  "Try to process the file that has been added"
  []
  (let [file @FILE]
    (if file
      (upload-file file)
      (js/alert "You must provide a file to process first"))))

(defn drag-drop
  "Stop events and handle dropped file"
  [event]
  (.stopPropagation event)
  (.preventDefault event)
  (reset! DRAGGING false)
  (file-handler event))

(defn droppable-class
  "Render the element appropriately when hovered, if it's a doc page"
  []
  (if @DRAGGING
    "dragoon"
    ""))

(defn upload-input
  "Generate a hidden input prompt for 
  the on-click event of the drop zone"
  []
  (let [input (.createElement js/document "input")]
    (doto input
      (.setAttribute "type" "file")
      (.addEventListener "change" #(-> % .-target .-files (.item 0) parse-file)))))

(defn upload-prompt
  "File Upload Element"
  [element-id]
  [:div.upload.col-md-3
   {:id "droppable"
    :on-drag-over #(drag-hover %)
    :on-drag-leave #(drag-off %)
    :on-drop #(drag-drop %)
    :on-click #(.click (upload-input))
    }
   [:div.upload-text.text-center
    {:class @DROP-CLASSES}
    @SUBMIT-TEXT
    [:div (@UP-ERROR :message)]]])

(defn nav-link [uri title page collapsed?]
  [:li {:class (when (= page (session/get :page)) "active")}
   [:a {:href uri
        :on-click #(reset! collapsed? true)}
    title]])

(defn navbar []
  (let [collapsed? (atom true)]
    (fn []
      [:nav.navbar.navbar-inverse.navbar-fixed-top
       [:div.container
        [:div.navbar-header
         [:button.navbar-toggle
          {:class         (when-not @collapsed? "collapsed")
           :data-toggle   "collapse"
           :aria-expanded @collapsed?
           :aria-controls "navbar"
           :on-click      #(swap! collapsed? not)}
          [:span.sr-only "Toggle Navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a.navbar-brand {:href "#/"} "Maladroit"]]
        [:div.navbar-collapse.collapse
         (when-not @collapsed? {:class "in"})
         [:ul.nav.navbar-nav
          [nav-link "#/" "Home" :home collapsed?]
                                        ;[nav-link "#/about" "About" :about collapsed?]
          ]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     "Maladroit: Text-processing with Mallet"]]])

(defn home-page []
  (let [token (.-value (.getElementById js/document "__anti-forgery-token"))]
    [:div.container
     [:div.jumbotron
      [:h1 "Welcome to Maladroit"]]
     [:div.well "Upload your document and specify your settings to receive Mallet data"]
     [:div.regexp
      [:span.label.label-info "Regexp for Splitting"]
      [:input.regexp {:type "text"
                      :name "regex"
                      :id "regex"
                      :value (@DATA :regexp)
                      :on-change #(update-data :regexp "regex")}]]
     [:div.passes
      [:span.label.label-info "Training Passes"]
      [:input.passes {:type "text"
                      :name "passes"
                      :id "passes"
                      :value (@DATA :passes)
                      :on-change #(update-data :passes "passes" js/parseInt)}]
      [:span.text-info "Granularity/distinctiveness at the cost of processing time"]]
     [:div.keywords
      [:span.label.label-info "Number of Keywords"]
      [:input.passes {:type "text"
                      :name "keywords"
                      :id "keywords"
                      :on-change #(update-data :num-keywords "keywords" js/parseInt)
                      :value (@DATA :num-keywords)}]]
     [:div.topics
      [:span.label.label-info "Number of Topics"]
      [:input.passes {:type "text"
                      :name "topics"
                      :id "topics"
                      :on-change #(update-data :num-topics "topics" js/parseInt)
                      :value (@DATA :num-topics)}]]
     [:div.stopwords
      [:span.label.label-info "Extra Stopwords"]
      [:input.passes {:type "text"
                      :name "stopwords"
                      :id "stopwords"
                      :on-change #(update-data :stopwords "stopwords")
                      :value (@DATA :stopwords)}]
      [:span.text-info "Space separated"]]
     [:div.doc-up
      [:div.file 
       (upload-prompt "doc-up")]]
     [:div.submit {:style {:clear "both"}}
      [:button 
       {:class (@SUBMIT-BUTTON :class)        
        :disabled (@SUBMIT-BUTTON :disabled)
        :on-click #(try-submit-file)}
       (@SUBMIT-BUTTON :text)]]
     [:div#result @DATA-LINK]]))

(def pages
  {:home #'home-page
   :about #'about-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn fetch-docs! []
  (GET (str js/context "docs") {:handler #(session/put! :docs %)}))

(defn mount-components []
                                        ;(reagent/render [#'navbar] (.getElementById js/document "navbar"))
  (reagent/render [#'page] (.getElementById js/document "app")))

(defn init! []
                                        ;(fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))
