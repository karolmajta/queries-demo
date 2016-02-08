(ns frontend-components.query
  (:require [reagent.core :as r]
            [com.stuartsierra.component :as component]
            [datascript.core :as d]))

(defrecord ReactiveQuery [listener-key conn query args through ratom]
  component/Lifecycle

  (start [this]
    (let [current-value ((:through this) (apply d/q (concat [(:query this) @(:conn this)] (:args this))))]
      (reset! (:ratom this) current-value)
      (d/listen! (:conn this) (:listener-key this)
        (fn [tx-report]
          (reset! (:ratom this) ((:through this) (apply d/q (concat [(:query this) (:db-after tx-report)] (:args this))))))))
    this)

  (stop [this]
    (d/unlisten! (:conn this) (:listener-key this))
    this))

(defn new-query [query-name conn q args through]
  (let [listener-key (gensym query-name)]
    (map->ReactiveQuery {:listener-key listener-key
                         :conn conn
                         :query q
                         :args args
                         :through through
                         :ratom (r/atom nil)})))