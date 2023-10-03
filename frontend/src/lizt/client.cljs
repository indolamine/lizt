(ns lizt.client
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]
            ["yjs" :as y]
            ["y-indexeddb" :as yidb]
            ["@liveblocks/client" :as lbc]
            ["@liveblocks/yjs$default" :as lbyp]
            ["@faker-js/faker" :refer [faker]]
            [clojure.edn :as edn]
            [cognitect.transit :as transit]
            [converge.api :as c]
            [converge.transit :as converge-transit]
            [ajax.core :as ajax]))


;; helpers
(defn rnd-id  []
  (+ (js/Date.now) (js/Math.random)))

(defn rnd-title []
  (let [wf (.-word faker)]
    (str/capitalize (str (.adverb wf) " " (.verb wf) " " (.noun wf)))))

(defn rnd-item []
  {:id (rnd-id)
   :title (rnd-title)})

;; state set-up
(def ui-state (r/atom {:title (str "Liszt " js/window.location.hash) :items []}))

(def lb-client (.createClient lbc #js {"publicApiKey" "pk_dev_YKUNleFTlW-PcPe-M2Fx9JdyXMJ1I_nP8Z1pxSE-UU8d0z0cypueDQggpnnm5wPH"}))
(def lb-room (.enter lb-client (str "lizt-" js/window.location.hash ) #js {"initialPresence" #js {}}))
(def y-doc (new y/Doc.))
(def y-provider (new lbyp lb-room y-doc))
(def yidb-provider (new yidb/IndexeddbPersistence (str "lizt-" js/window.location.hash) y-doc))
(def y-arr (. y-doc getArray "items"))
(. y-arr observe #(swap! ui-state assoc :items (map edn/read-string (js->clj (. y-arr toArray)))))

;; data helpers

(defn add-item! [i]
  (. y-arr push #js [(str i)]))

(defn index-of-item-by-id [id]
  (first
   (map first (filter #(= (:id (second %)) id)
                      (map-indexed vector (:items @ui-state))))))

(defn delete-item-by-id! [id]
  (. y-arr delete (index-of-item-by-id id) 1))


;; view helpers

(defn char-code-sum [s]
  (reduce + (map #(* 107 (- (.charCodeAt %) 90)) s))) ;;magic

(defn title->color [title]
  (str "hsl(" (char-code-sum title) " 100% 75%)" ))

(defn item-input [{:keys [title on-save on-stop]}]
  (let [val (r/atom title)]
    (fn []
      (let [stop (fn [_e]
                   (reset! val "")
                   (when on-stop (on-stop)))
            save (fn [e]
                   (let [v (-> @val str str/trim)]
                     (when-not (empty? v)
                       (on-save {:id (rnd-id) :title v}))
                     (stop e)))]
        [:div.input-group.mb-3 [:input.form-control
                                {:type "text"
                                 :value @val
                                 :placeholder "Add item"
                                 :on-change (fn [e] (reset! val (-> e .-target .-value)))
                                 :on-key-down (fn [e]
                                                (case (.-which e)
                                                  13 (save e)
                                                  27 (stop e)
                                                  nil))}]
         [:div.input-group-append
          [:button.btn.btn-outline-secondary {:on-click #(save %)} "Add" ]]]))))


(defn app []
  (let [{:keys [title items]} @ui-state]
    [:div.container
     [:h1 title]
     [:button.btn.btn-secondary.mb-3 {:on-click #(add-item! (rnd-item))} "Add random"]
     [:div [item-input {:on-save add-item! :placeholder "What/?"}] ]
     [:div [:ul.list-group
            (for [{:keys [title id]} items]
              [:li.list-group-item.px-2 {:key id
                                         :style {:background-color (title->color title)}}
               [:span.badge [:button.btn.btn-danger.btn-sm {:type "button" :on-click #(delete-item-by-id! id)} "X"] ]
               title])]]]))

(defn ^:export ^:dev/after-load run []
  (rdom/render [app] (js/document.getElementById "app")))

;; global browser behavior

(set! (.-onhashchange js/window) (fn [_] (js/location.reload)) )
