(ns lizt.client
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [converge.api :as c]
            [converge.transit :as converge-transit]
            [ajax.core :as ajax]))


;; helpers
(defn rnd-id  []
  (+ (js/Date.now) (js/Math.random)))

(defn rnd-title []
  (str "Item - " (inc (rand-int 1000))))

(defn rnd-item []
  {:id (rnd-id)
   :title (rnd-title)})

;; state set-up

(defonce ui-state (r/atom {}))

(defonce distributed-state (r/atom nil))

(defonce last-seen-clock (r/atom nil))

;; initial fetch from server and helpers
(def response-format
  (ajax/transit-response-format {:handlers (merge converge-transit/read-handlers)}))

(def request-format
  (ajax/transit-request-format {:handlers (merge converge-transit/write-handlers)}))

(defn patch-state! [{:keys [patch ts]}]
  (c/merge! @distributed-state patch)
  (reset! last-seen-clock ts))

(defn sync-with-server! []
  (ajax/POST "/lizt/1/sync"
             {:format request-format
              :response-format response-format
              :params {:ts @last-seen-clock :patch (c/patch-from-clock @distributed-state @last-seen-clock)}
              :error-handler prn
              :handler patch-state!}))

(defn ref->state! [res]
  (let [cref (:ref res)
        update-ui-state (fn [cref]
                          (reset! ui-state @cref)
                          (reset! last-seen-clock (c/clock cref)))]
    (add-watch cref :to-reagent (fn [_ c _ _] (update-ui-state c)))
    (update-ui-state cref)
    (reset! distributed-state cref)
    (js/setInterval sync-with-server! 1000)))

(defn fetch-from-server! []
  (ajax/GET "/lizt/1/ref"
            {:response-format response-format
             :error-handler prn
             :handler ref->state!}))

;; data helpers

(defn update-items! [f]
  (swap! @distributed-state update-in [:items] f))

(defn add-item! [i]
  (update-items! #(conj % i)))

(defn delete-item-by-id! [id]
  (update-items! (fn [is] (vec (remove #(= id (:id %)) is)))))

;; view helpers

(defn title->color [title]
  (str "hsl(" (subs (str title) 7) " 100% 75%)" ))


(defn app []
  (let [{:keys [title items]} @ui-state]
    [:div
     [:h1 title]
     [:button.btn.btn-primary {:on-click
                               #(add-item! (rnd-item))}
      "Add new"]
     [:button.btn.btn-light {:on-click sync-with-server!}
      "Sync"]
     [:ul.list-group
      (for [{:keys [title id]} items]
        [:li.list-group-item {:key id
                              :style
                              {:background-color (title->color title)}}
         [:button.btn.btn-light.btn-sm {:type "button" :on-click #(delete-item-by-id! id)} "X"]
         title])
      ]]))

(defn ^:export ^:dev/after-load run []
  (fetch-from-server!)
  (rdom/render [app] (js/document.getElementById "app")))
