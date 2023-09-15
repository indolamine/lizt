(ns lizt.core
  (:require [converge.api :as convergent]
            [converge.transit :as converge-transit]
            [cognitect.transit :as transit]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]))


(def server-lizt (convergent/ref {:title "A distributed lizt"
                                  :items [{:id 324234
                                           :title "Item - 1"}]}))
;; helpers
(defn ok [body] {:status 200 :body body})

;; interceptors
(def my-transit-json-body
  (http/transit-body-interceptor
   ::my-transit-json-body
   "application/transit+json;charset=UTF-8"
   :json
   {:handlers (merge transit/default-write-handlers
                     converge-transit/write-handlers)}))

(def my-transit-json-body-params
  (body-params/body-params (body-params/default-parser-map :transit-options [{:handlers (merge transit/default-read-handlers
                                                                                               converge-transit/read-handlers)}])))

(def lizt-interceptor
  {:name :lizt-interceptor
   :enter
   (fn [context]
     (-> context
         (update :request assoc :lizt server-lizt)))})

;; controllers

(def lizt-ref
  "If there is a lizt in the request, retrieves its ref and adds it as an ok response"
  {:name :lizt-ref
   :enter
   (fn [{:keys [request] :as context}]
     (if-let [lizt (:lizt request)]
       (let [response (ok {:ref lizt})]
         (assoc context :response response))
       context))})

(def lizt-sync
  "This endpoint will facilitate syncing.
  It expects a map with two keys:
  - :ts is the timestamp since which the client would like to have a patch to update its own state
  - :patch is the patch of the client that will need to be applied to our server version"
  {:name :lizt-sync
   :enter
   (fn [{:keys [request] :as context}]
     (if-let [lizt (:lizt request)]
       (let [{:keys [patch ts]} (:transit-params request)
             _ (convergent/merge! lizt patch)
             response (ok {:patch (convergent/patch-from-clock lizt ts)
                           :ts (convergent/clock lizt)})]

         (assoc context :response response))
       context))})

(def routes
  #{["/lizt/ref" :get [my-transit-json-body lizt-interceptor lizt-ref] :route-name :lizt-ref]
    ["/lizt/sync" :post [my-transit-json-body my-transit-json-body-params lizt-interceptor lizt-sync] :route-name :lizt-sync]
    })

(defn start []
  (-> {::http/routes #(route/expand-routes (deref (var routes)))
       ::http/port 8890
       ::http/type :jetty
       ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}
       ::http/resource-path "/public"
       ::http/join? false}
      http/create-server
      http/start))

(defn -main [& args]
  (start))

(defonce server (atom nil))

(defn dev-start [] (reset! server (start)))

(defn dev-stop [] ((::http/stop-fn @server)))

(comment

  ;;start server
  (dev-start)

  ;;stop server
  (dev-stop)

  ;;add some items
  (doseq [i (range 120 480 30)]
    (-> server-lizt
        (swap! update-in [:items] conj {:id (+ 1780000 i) :title (str "Item - " i) :status :unchecked})))

  ;;drop all items
  (-> server-lizt
      (swap! update-in [:items] (partial filterv (constantly false))))

  ;;change title
  (-> server-lizt
      (swap! update :title clojure.string/capitalize))

  ,)
