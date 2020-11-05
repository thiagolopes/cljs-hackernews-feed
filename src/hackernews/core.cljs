(ns hackernews.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [goog.dom :as gdom]
   [goog.functions :refer [debounce]]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [rum.core :as rum]))

;; db
(defonce state (atom {:posts {} :ids []}))

;; constants
(def url-base "https://hacker-news.firebaseio.com/")
(def url-top-posts (str url-base "v0/topstories.json"))

;; fetch data
(defn url-post [post-id] (str url-base "v0/item/" (str post-id) ".json"))

(defn http-get [url]
  (http/get url {:with-credentials? false}))

(defn fetch-post [post-id]
  (go (let [response (<! (http-get (url-post post-id)))
            post (:body response)]
        (swap! state assoc-in [:posts post-id] post))))

(defn fetch-top-posts
  ([] (fetch-top-posts 10))
  ([n] (go (let [ids (take n (:body (<! (http-get url-top-posts))))]
        (swap! state assoc :ids ids)
        (doseq [id ids]
          (if (not (contains? (:posts @state) id))
            (fetch-post id)))))))

;; react components
(rum/defc list-of-posts < rum/reactive []
  [:ul.post-list
   (for [id (:ids (rum/react state))]
     (let [{:keys [title url by score kids]} (get-in @state [:posts id])]
       [:li.post-list__item {:key id}
        [:h2.post-list__item__title title]
        [:a.post-list__item__url {:href url} url]
        [:time.post-list__item__date "today"]
        [:p.post-list__item__author "👤 " by]
        [:p.post-list__item__comments "💬 " (count kids) " comments"]
        [:p.post-list__item__upvotes score]]))])

(rum/defc base-app []
  [:div
   [:div.header
    [:h1.header__title "Hacker News Feed"]
    [:h2.header__title "Using Clojurescript+React "]]
   [:main (list-of-posts)]])

;; main
(defn mount-app-element []
  (when-let [el (gdom/getElement "app")]
    (rum/mount (base-app) el)))

(defn ^:after-load on-reload []
  (mount-app-element))

(mount-app-element)
(fetch-top-posts)

;; infinity scroll
(defn document-height []
  (.. js/document -documentElement -offsetHeight))

(defn scroll-position []
  (+
   (.. js/document -documentElement -scrollTop)
   (.. js/window -innerHeight)))

(defn is-bottom? []
  (let [max-height (document-height)
        scroll-position (scroll-position)]
    (if (== scroll-position max-height)
      true
      false)))

(defn event-scroll []
  (if (is-bottom?)
    (fetch-top-posts (+ (count (:posts @state)) 10))))

(set! (. js/window -onscroll) (debounce event-scroll 1000))
