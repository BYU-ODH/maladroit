(ns maladroit.styles
  (:require [garden.def :refer [defstylesheet defstyles]]
            [garden.units :as u :refer [px]]
            [garden.color :as c :refer [hex->hsl hsl->hex]] ;:rename {hex->rgb hr, rgb->hex rh}]
            ))

;;;;;;;;;;;;;;;;;;;;
;; COLOR PALLETTE ;;
;;;;;;;;;;;;;;;;;;;;
(def ycolors
  {:blue "#002255"
   :blue2 "#001948"
   :blue3 "#002255"
   :blue4 "#003366"
   :blue5 "#114477"
   :blue6 "#336699"
   :blue7 "#628CB6"
   :blue8 "#91B2D2"
   :blue9 "#ABC8E4"
   :blue10 "#D1E4F6"
   :blue11 "#E0EDF9"
   :blue12 "#EFF6FC"
   :blue13 "#F9FCFE"
   :gray "#A7A9AC"
   :offwhite "#FFFFEE"
   :lightestblue "#F9FCFE"
   :midblue "#628CB6"})
(def ycolor-obj (into {} (for [[k v] ycolors] {k (hex->hsl v)}))) ;; Object version of ycolors
;; (def yretrieve-hex [key]
;;   "Get the hex number from the ycolor-obj"
;;   (hsl->hex (key ycolor-obj)))


;;;;;;;;;;;;
;; STYLES ;;
;;;;;;;;;;;;
(defn gradient [col1 col2]
  "Takes two color hex-strings, returns the 'linear-gradient' command that can be put into a :background element"
  (str "linear-gradient(" col1 ", " col2 ")")
  )

(defn nav-plain-blue []
  {:background-image "none"
   :background-color (ycolors :blue)
   :background (gradient (ycolors :blue) (ycolors :blue5))})

(defn nav-hover-blue []
  (let [original (ycolor-obj :blue)]
    ;; lightened ((c/lighten original 25))
    ;; darkened ((c/darken original 5))]
    (assoc (nav-plain-blue) :background-color original))); (hsl->hex (c/lighten original 25 )))))

(defstyles maladroit
  {:output-to "resources/public/css/turbo-tenure.css"}
  [:body
   {:background-color (ycolors :lightestblue)
    :font-size (px 16)
    :line-height 1.5}]
  [:div.navbar :nav.navbar (nav-plain-blue)]
  [:.navbar-inverse [:.navbar-nav [:> [:.active [:> [:a (nav-plain-blue)]]]]]]
  [:.navbar-inverse [:.navbar-nav [:> [:.active [:> [:a:hover (nav-hover-blue)]]]]]]
  [:.jumbotron {:background-color (ycolors :blue)
                :background (gradient (ycolors :blue5) (ycolors :blue6))
                :color (ycolors :lightestblue)}]
  [:div.doc {:display "inline-block"
             :margin (px 3)}]
  [:.fileUpload {:position "relative" :overflow "hidden" :margin (px 10)}]
  [:.fileUpload [:input.upload {:position "absolute"
                                :top 0
                                :right 0
                                :margin 0
                                :padding 0
                                :font-size (px 20)
                                :cursor "pointer"
                                :opacity 0
                                :filter "alpha(opacity=0)"}]]
  [:div.row.filedetail :span {:padding 0
                              :margin 0
                              :line-height (px 12)
                              :font-size (px 16)}]
  [:.btn-group.review-selector {:padding-top (px 13)}]
  [:.file-list-entry {:margin [["1em" 0]]}]
  [:div.upload {:border "5px dashed"
                :border-color (ycolors :gray)
                :display "table"}]
  [:div.upload-text {
                     :height (px 120)
                     :display "table-cell"
                     :vertical-align "middle"
                     }]
  [:div.upload.dragoon {:border-color (ycolors :blue9)}]
  [:div#droppable:hover {:cursor "pointer"} ]
  [:.icon {:text-align "center"}]
  [:.file-text {:margin-left (px 10)}]
  [:.data [:a {:display "block"}]])
