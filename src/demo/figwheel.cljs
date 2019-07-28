(ns ^:figwheel-hooks demo.figwheel
  (:require
   [goog.dom :as gdom]
   [re-frame.core :as re-frame]
   [reitit.core :as r]
   [reitit.coercion.spec :as rss]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   [reagent.core :as reagent :refer [atom]]))

;;; Utils ;;;

(defn multiply [a b]
  (* a b))

;;; Events ;;;

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   {:current-route nil
    :counter       0}))

(re-frame/reg-event-fx
 ::navigate
 (fn [_ [_ route]]
   {::navigate! route}))

(re-frame/reg-event-db
 ::navigated
 (fn [db [_ new-match]]
   (let [old-match   (:current-route db)
         controllers (rfc/apply-controllers (:controllers old-match) new-match)]
     (assoc db :current-route (assoc new-match :controllers controllers)))))

(re-frame/reg-event-db
 ::inc-counter
 (fn [db]
   (update db :counter inc)))

(re-frame/reg-event-db
 ::dec-counter
 (fn [db]
   (update db :counter dec)))

;;; Subscriptions ;;;

(re-frame/reg-sub
 ::current-route
 (fn [db]
   (:current-route db)))

(re-frame/reg-sub
 ::counter-value
 (fn [db]
   (:counter db)))

(re-frame/reg-sub
 ::counter-text
 :<- [::counter-value]
 (fn [v]
   (str "Current counter value is " v)))

;;; Views ;;;

(defn home-page []
  [:div
   [:h1 "This is home page"]
   [:button
    ;; Dispatch navigate event that triggers a (side)effect.
    {:on-click #(re-frame/dispatch [::navigate ::sub-page2])}
    "Go to sub-page 2"]])

(defn counter-page []
  (let [counter-val @(re-frame/subscribe [::counter-value])
        counter-text @(re-frame/subscribe [::counter-text])]
    [:div
     [:h1 "This is counter page"]
     [:p counter-text]
     [:div
      [:button {:on-click #(re-frame/dispatch [::inc-counter])}
       "+"]
      [:button {:on-click #(re-frame/dispatch [::dec-counter])}
       "-"]]
     (into
      [:div]
      (for [_ (range (js/Math.abs counter-val))]
        [:img
         {:style {:width 250 :margin "0.5em"}
          :src (if (pos? counter-val)"img/dog.jpg" "img/cat.jpeg")}]))]))

(defn dummy-page []
  [:div
   [:h1 "This is a dummy page"]])

;;; Effects ;;;

;; Triggering navigation from events.
(re-frame/reg-fx
 ::navigate!
 (fn [k params query]
   (rfe/push-state k params query)))

;;; Routes ;;;

(defn href
  "Return relative url for given route. Url can be used in HTML links."
  ([k]
   (href k nil nil))
  ([k params]
   (href k params nil))
  ([k params query]
   (rfe/href k params query)))

(def routes
  (into
   ["/"
    [""
     {:name      ::home
      :view      home-page
      :link-text "Home"
      :controllers
      [{:start (fn [](js/console.log "Entering home page"))
        :stop  (fn [] (js/console.log "Leaving home page"))}]}]
    ["counter-page"
     {:name      ::counter-page
      :view      counter-page
      :link-text "Counter page"
      :controllers
      [{:start (fn [] (js/console.log "Entering Counter page"))
        :stop  (fn [] (js/console.log "Leaving Counter page"))}]}]]
   (for [n (range 5)]
     [(str "sub-page" n)
      {:name      (keyword (str "sub-page-" n))
       :view      dummy-page
       :link-text (str "Sub-page " n)
       :controllers
       [{:start (fn [] (js/console.log (str "Entering sub-page " n)))
         :stop  (fn [] (js/console.log (str "Leaving sub-page " n)))}]}])))

(defn on-navigate [new-match]
  (when new-match
    (re-frame/dispatch [::navigated new-match])))

(def router
  (rf/router
   routes
   {:data {:coercion rss/coercion}}))

(defn init-routes! []
  (js/console.log "initializing routes")
  (rfe/start!
   router
   on-navigate
   {:use-fragment true}))

(defn nav [{:keys [router current-route]}]
  [:ul
   (for [route-name (r/route-names router)
         :let       [route (r/match-by-name router route-name)
                     text (-> route :data :link-text)]]
     [:li {:key route-name}
      (when (= route-name (-> current-route :data :name))
        "> ")
      ;; Create a normal links that user can click
      [:a {:href (href route-name)} text]])])

(defn router-component [{:keys [router]}]
  (let [current-route @(re-frame/subscribe [::current-route])]
    [:div
     [nav {:router router :current-route current-route}]
     (when current-route
       [(-> current-route :data :view)])]))

(defn mount [el]
  (js/console.log "hello?")
  (re-frame/clear-subscription-cache!)
  (init-routes!) ;; Reset routes on figwheel reload
  (reagent/render-component
   [router-component
    {:router router}] el))

(defn get-app-element []
  (gdom/getElement "app"))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(defn init-app! []
  (re-frame/dispatch-sync [::initialize-db])
  (mount-app-element))

;; (init-app!)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element))
