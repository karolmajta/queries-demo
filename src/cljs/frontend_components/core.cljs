(ns frontend-components.core
  (:require [goog.object]
            [com.stuartsierra.component :as component]
            [reagent.core :as r]
            [frontend-components.query :refer [new-query]]
            [frontend-components.components :refer [new-history new-router new-root-component new-letters-channel new-database route]]
            [frontend-components.config :as config])
  (:import [goog History]))

(defn hist [datoms]
  (->> datoms
      (group-by second)
      (into {} (map (fn [[k v]] [k (count v)])))))

(defn max-eid [datoms]
  (apply max (map first datoms)))

(defn render-histogram [histogram-data selected-letter]
  (let [total (apply + (vals histogram-data))
        bars (for [[letter cnt] (sort-by first histogram-data)]
               [:div {:style {:width (str (* 100 (/ cnt total)) "%")
                              :text-align "right"
                              :font-weight "bold"
                              :height "20px"
                              :margin-bottom "2px"
                              :background (if (= selected-letter letter) "green" "red")
                              :cursor :pointer
                              :color "white"}
                      :on-click #(goog.object/set js/window "location" (str "/#/letters/" letter))} ;; this should be done via some kind
                                                                                                    ;; of event bus, it's a hack for now
                letter])]
    (into [:div] bars)))

(defn render-letters [_]
  (fn [histogram-query]
      [:div
       [:h1 "Displaying histogram for all letters"]
       [:hr]
       [render-histogram @(:ratom histogram-query) nil]]))

(defn render-letter-detail [_ _ _]
  (fn [histogram-query eid-query selected-letter]
    [:div
     [:h1 "Displaying histogram for letter " selected-letter]
     [:h2 "Latest encoudneted eid for this letter is: " @(:ratom eid-query)]
     [:hr]
     [render-histogram @(:ratom histogram-query) selected-letter]]))


(def home (route
  :render (fn []
            [:h1
             "This is a home route, go see the "
             [:a {:href "/#/letters/"} "letters histogram"]])))

(def letters (route
   :render render-letters
   :start (fn [system]
            [(component/start
               (new-query "histogram" (:conn system) '[:find ?e ?m
                                                       :where [?e :message ?m]] [] hist))])
   :stop (fn [histogram-query]
           (component/stop histogram-query))))

(def letter (route
  :render render-letter-detail
  :start (fn [system]
           [(component/start
              (new-query "histogram" (:conn system) '[:find ?e ?m
                                                      :where [?e :message ?m]] [] hist))
            (component/start
              (new-query "max-eid" (:conn system) '[:find ?e
                                                    :in $ ?selected-letter
                                                    :where [?e :message ?selected-letter]] [(get-in system [:params :id])] max-eid))
            (get-in system [:params :id])])
  :stop (fn [histogram-query eid-query selected-letter]
          (component/stop histogram-query)
          (component/stop eid-query))))

(def not-found (route
  :render (fn []
    [:h1 "Not found..."])))

(def routes {"/" home
             "/letters/" letters
             "/letters/:id" letter
             "*" not-found})

(defonce app-system
  (let [goog-history (History.)
        mount-point (.getElementById js/document "app")]
    (component/system-map
      :root-component (component/using
        (new-root-component mount-point goog-history)
        [:database])
      :letters-channel (new-letters-channel "ws://localhost:9090")
      :database (component/using
        (new-database {})
        [:letters-channel])
      :router (component/using
        (new-router routes goog-history)
        [:root-component])
      :history (component/using
        (new-history goog-history)
        [:router]))))

(defonce app (atom nil))

(defn start-app []
  (reset! app (component/start app-system)))

(defn stop-app []
  (component/stop @app)
  (reset! app nil))

(defn ^:export main []
  (when config/debug?
    (.info js/console "dev mode")
    (enable-console-print!))
  (start-app))

(defn mount-root []
  (stop-app)
  (start-app))
