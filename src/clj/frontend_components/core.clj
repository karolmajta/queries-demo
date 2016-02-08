(ns frontend-components.core
  (:require [clojure.core.async :refer [chan timeout <! go-loop]]
            [org.httpkit.server :refer [run-server with-channel open? send!]])
  (:gen-class))

(def characters (vec (map str (seq "abcdefghijklmnopqrstuvxyz"))))

(defn handler [req]
  (with-channel req channel
    (go-loop [c (rand-nth characters)]
      (when [open? channel]
        (send! channel c false)
        (<! (timeout 100))
        (recur (rand-nth characters))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (run-server handler {:port 9090}))
