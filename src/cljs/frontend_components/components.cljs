(ns frontend-components.components
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [cljs.core.async :refer [chan <! >!  close!]]
            [secretary.core :as secretary]
            [reagent.core :as r]
            [chord.client :refer [ws-ch]]
            [datascript.core :as d]
            [goog.events :as events]
            [goog.history.EventType :as EventType])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn params [uri]
  (let [[uri-path query-string] (string/split (secretary/uri-without-prefix uri) #"\?")
        uri-path (secretary/uri-with-leading-slash uri-path)
        query-params (when query-string
                       {:query-params (secretary/decode-query-params query-string)})
        {:keys [_ params]} (secretary/locate-route uri-path)]
    (merge params query-params)))

(defprotocol IRouteHandler
  (render-fn [this])
  (start [this system])
  (stop [this deps]))


(deftype RouteHandler [render on-start on-stop]
  IRouteHandler

  (render-fn [this] render)

  (start [this system] (on-start system))

  (stop [this deps] (apply on-stop deps)))


(defn route [& args]
  (let [argmap (apply hash-map args)
        {:keys [start render stop] :or {start #() stop #()}} argmap]
    (->RouteHandler render start stop)))

(defrecord History [goog-history listener-key]
  component/Lifecycle

  (start [{:keys [router] :as this}]
    (.setEnabled goog-history true)
    (assoc this :listener-key (events/listen goog-history EventType/NAVIGATE (:route-fn router))))

  (stop [this]
    (.setEnabled goog-history false)
    (events/unlistenByKey (:listener-key this))
    this))

(defn new-history [goog-history]
    (map->History {:goog-history goog-history}))


(defprotocol IRootComponent
  (set-handler! [this handler-component]))


(defrecord RootComponent [mount-point goog-history handler-data database]
  component/Lifecycle

  (start [this]
    (let [handler-data (r/atom nil)
          root (fn []
                 (when-let [{:keys [render-fn params]} @handler-data]
                   (into [render-fn] params)))]
      (r/render [root] mount-point)
      (assoc this :handler-data handler-data)))

  (stop [this]
    (r/unmount-component-at-node mount-point))

  IRootComponent

  (set-handler! [this handler]
    (when (:route @(:handler-data this))
      (apply stop (concat [(:route @(:handler-data this))] (:params @(:handler-data this)))))
    (let [system {:params (params (.getToken goog-history))
                  :conn (get-in this [:database :conn])}]
      (reset! (:handler-data this) {:route handler
                                    :render-fn (render-fn handler)
                                    :params (start handler system)}))))


(defn new-root-component [mount-point goog-history]
    (map->RootComponent {:mount-point mount-point :goog-history goog-history}))


(defrecord Router [routes goog-history root-component]
  component/Lifecycle

  (start [this]
    (doseq [[pattern handler] routes]
      (secretary/add-route! pattern #(set-handler! root-component handler)))
    (secretary/dispatch! (.getToken goog-history))
    (assoc this :route-fn #(secretary/dispatch! (.-token %))))

  (stop [this]
    (secretary/reset-routes!)))


(defn new-router [routes goog-history]
  (map->Router {:routes routes :goog-history goog-history}))

(defrecord LettersChannel [address websocket-channel]
  component/Lifecycle

  (start [this]
    (let [ws-chan (atom nil)
          out-chan (chan)]
      (go
        (let [{:keys [ws-channel error]} (<! (ws-ch address))]
          (if-not error
            (do
              (reset! ws-chan ws-channel)
              (go-loop [msg (<! ws-channel)]
                (when msg
                  (>! out-chan msg)
                  (recur (<! ws-channel)))))
            (throw "connection failed"))))
      (-> this
          (assoc :websocket-channel ws-chan)
          (assoc :out out-chan))))

  (stop [this]
    (when @(:websocket-channel this) (close! @(:websocket-channel this)))
    (reset! (:websocket-channel this) nil)
    (close! (:out this))
    this))

(defn new-letters-channel [address]
  (map->LettersChannel {:address address}))

(defrecord Database [schema conn letters-channel]
  component/Lifecycle

  (start [this]
    (let [conn (d/create-conn schema)]
      (go-loop [msg (<! (:out letters-channel))]
        (when msg
          (d/transact! conn [{:message (name (:message msg))}])
          (recur (<! (:out letters-channel)))))
      (assoc this :conn conn)))

  (stop [this]))

(defn new-database [schema]
  (map->Database {:schema schema}))