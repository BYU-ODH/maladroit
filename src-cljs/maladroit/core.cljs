(ns maladroit.core
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.browser.dom :as dom]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [maladroit.utils :as ut])
  (:import goog.History))
(def *data* (atom {:regexp #"(?m)^\*"
                   :passes 3
                   :num-keywords 10}))

(def *dragging* (atom false))
(def *up-error* (atom {:page ()
                       :default-message [:span.label.label-warning ".docx files only"]
                       :message ()}))
;;;;;;;;;;;;;;;;;
;; FILE UPLOAD ;;
;;;;;;;;;;;;;;;;;
(defn response-data-listener
  "Act when the response comes after an upload"
  [e]
  (println "response data listener" e))

(defn upload-file
  "Upload the given file to the server, using 
  the url and session info to define the target"
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
           url-target "/upload"
           anti-forgery-token (.-value (.getElementById js/document "__anti-forgery-token"))]
       (.open xhr "post" url-target true)
       (.setRequestHeader xhr "x-csrf-token" anti-forgery-token)
       ;(.setRequestHeader xhr "accept" "application/transit+json")
       (.append form-data "file" file)
       (.send xhr form-data)))))

(defn drag-hover
  "When an item is dragged over the element, 
  display the hover screen / alter styles appropriately."
  [event]
  (dom/log "Dragged over")
  (.stopPropagation event)
  (.preventDefault event)
  (reset! *dragging* true))

(defn drag-off
  "When an item is dragged off the element, 
  undo the drag-hover"
  [event]
  (dom/log "Dragged off")
  (.stopPropagation event)
  (.preventDefault event)
  (reset! *dragging* false))

(defn parse-file [file]
  (let [file-type (.-type file)
        ;; file-size (.-size file)
        ;; file-name (.-name file)
        allowed-types #{
                        "text/plain"
                        }]
    (if (allowed-types file-type)
      (upload-file file)
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

(defn drag-drop
  "Stop events and handle dropped file"
  [event]
  (.stopPropagation event)
  (.preventDefault event)
  (reset! *dragging* false)
  (file-handler event))

(defn droppable-class
  "Render the element appropriately when hovered, if it's a doc page"
  []
  (if (session/get :doc)
    (when @*dragging*
      "dragoon")
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
    :class (droppable-class) 
    :on-drag-over #(drag-hover %)
    :on-drag-leave #(drag-off %)
    :on-drop #(drag-drop %)
    :on-click #(.click (upload-input))
    }
   [:div.upload-text.text-center
    "Drop or Click to Upload"
    [:div (@*up-error* :message)]]])

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
          [nav-link "#/about" "About" :about collapsed?]]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     "this is the story of Maladroit... work in progress"]]])

(defn home-page []
  [:div.container
   [:div.jumbotron
    [:h1 "Welcome to Maladroit"]]
   [:div.well "Upload your document and specify your settings to receive Mallet data"]
    [:div.regexp
     [:span.label.label-info "Regexp for Splitting"]
     [:input.regexp {:type "text"
                     :value (@*data* :regexp)}]]
    [:div.passes
     [:span.label.label-info "Training Passes"]
     [:input.passes {:type "text"
                     :value (@*data* :passes)
                     }]]
    [:div.keywords
     [:span.label.label-info "Number of Keywords"]
     [:input.passes {:type "text"
                     :value (@*data* :num-keywords)}]]
      [:div.doc-up
    [:div.file 
     (upload-prompt "doc-up")]
    ]])

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
  (GET (str js/context "/docs") {:handler #(session/put! :docs %)}))

(defn mount-components []
  (reagent/render [#'navbar] (.getElementById js/document "navbar"))
  (reagent/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))
