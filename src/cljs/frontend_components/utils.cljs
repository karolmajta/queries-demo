(ns frontend-components.utils
  (:require [datascript.core :as d]))

(defprotocol IReactiveQuery
  (unlisten! [this conn])
  (ratom [this conn]))

(defrecord ReactiveQuery [conn query ratom*]
  IReactiveQuery
  (unlisten! [this conn] )
  (ratom [this conn] ratom*))

