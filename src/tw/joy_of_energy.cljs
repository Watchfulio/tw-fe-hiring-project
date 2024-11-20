(ns ^:figwheel-hooks tw.joy-of-energy
  (:require
    ["chart.js"   :as chart-js]
    [cljs.math    :as math]
    [goog.object  :as gobj]
    [reagent.core :as r]
    [reagent.dom  :as r.dom]))

;; Data
;; ----------------------------------------------------------------------------

(defn fetch-readings
  ([]
   (fetch-readings 1200))
  ([length]
   (let [now      (.. js/Date now)
         hour     (* 1000 60 60)
         snapshot (fn [n]
                    {:time  (- now (* n hour))
                     :value (* (math/random) (+ 0.7 0.4))})]
     (map snapshot (range length)))))


(defn grouped-readings
  [readings]
  (->>
    (reduce
      (fn [acc {:keys [time value] :as _reading}]
        (let [date-ymdms (js/Date. time)
              time-ymd   (-> (js/Date.
                               (.. date-ymdms (getFullYear))
                               (.. date-ymdms (getMonth))
                               (.. date-ymdms (getDate)))
                             (js-invoke "getTime"))]
          (assoc acc time-ymd (+ (get acc time-ymd 0) value))))
      {}
      readings)
    (map #(hash-map :time (first %) :value (second %)))))


(defn sort-readings
  [readings]
  (sort-by :value > readings))


(defn format-label-date-dm
  [time]
  (let [date (js/Date. time)
        opts #js{:day "numeric" :month "numeric"}]
    (->
      (js/Intl.DateTimeFormat. "en-us" opts)
      (.. (format date)))))

;; Components
;; ----------------------------------------------------------------------------

(js-invoke
  chart-js/Chart
  "register"
  chart-js/BarController
  chart-js/BarElement
  chart-js/LineController
  chart-js/LineElement
  chart-js/PointElement
  chart-js/Filler
  chart-js/CategoryScale
  chart-js/LinearScale
  chart-js/LogarithmicScale
  chart-js/Title
  chart-js/Tooltip)


(defn chart
  "Given `data`, render a `chart.js` chart.

  ## Args

  * data - map?
  * opts - map? accepts keys
           :chart-ref - fn? accepts 1 arg of `ref` (atom?)"
  [data]
  (let [chart-ref   (atom nil)
        chart-state (atom nil)]
    (r/create-class
      {:component-did-mount #(when @chart-ref
                               (reset! chart-state (chart-js/Chart. @chart-ref
                                                                    data)))
       :component-did-update (fn [this _old-argv]
                               (let [next-data (-> (r/argv this)
                                                   rest
                                                   first)]
                                 (gobj/set
                                   (gobj/getValueByKeys @chart-state "data")
                                   "datasets"
                                   (gobj/getValueByKeys
                                     next-data
                                     "data"
                                     "datasets"))

                                 (js-invoke @chart-state "update")))
       :display-name   "Chart",
       :reagent-render (fn [_]
                         [:<>
                           [:h1
                             {:class "regular darkgray line-height-1 mb3"}
                             "Energy consumption"]

                           [:section
                             {:class "mb3"}
                             [:button
                               {:class "h5 inline-block shadow-2 pl2 pr2 pt1 pb1 roundedMore border-grey bg-blue white bold"}
                               "Last 30 Days"]]

                           [:section
                             {:class "chartHeight mb3"}
                             [:canvas {:id "usageChart" :ref #(reset! chart-ref %)}]]])})))


(defn energy-consumption-chart
  [{:keys [readings]}]
  (let [labels (map #(format-label-date-dm (:time %)) readings)
        values (map :value readings)]
    (set! (.. chart-js -Chart -defaults -font -size) "10px")
    [chart
      (clj->js
        {:type    "bar"
         :data    {:labels   labels
                   :datasets [{:label           "kWh usage"
                               :data            values
                               :fill            true
                               :borderColor     "rgb(75, 192, 192)"
                               :tension         0.1
                               :borderWidth     0.2
                               :backgroundColor "#5A8EDA"
                               :borderRadius    10}]}

         :options {:scales  {:y {:grid {:display false}}
                             :x {:grid {:display false}}}
                   :plugins {:legend {:display false}}
                   :maintainAspectRatio false}})]))


(defn device-section
  [{:keys [title usage]}]
  [:<>
    [:div
      {:class "shadow-2 roundedMore bg-super-light-grey mb1"}
      [:p
        {:class "darkgray pl2 pt1 pb1"}
        title]
      [:p
        {:class "h5 darkgray bold pl2 pb1 pt1 bg-very-light-grey"}
        usage]]])


(defn summary-section
  [{:keys [summary subtitle]}]
  [:<>
    [:h2
      {:class "h2 greyBlue"}
      summary]
    [:p
      {:class "darkgray mb2"}
      subtitle]])


(defn sidebar
  []
  [:<>
    [summary-section {:summary "⚡️ 1.4kW" :subtitle "Power draw"}]
    [summary-section {:summary "☀️️ 5.8kW"  :subtitle "Solar power production"}]
    [summary-section {:summary "🔌️ 4.4kW" :subtitle "Fed into grid"}]

    [:section {:class "h5 darkgray mb2"}
      [:h4 {:class "h4 mb1"}]
      [device-section {:title "Air Conditioner" :usage "0.3093kw"}]
      [device-section {:title "Wi-Fi Router"    :usage "0.0033kW"}]
      [device-section {:title "Humidifer"       :usage "0.0518kW"}]
      [device-section {:title "Smart TV"        :usage "0.1276kW"}]
      [device-section {:title "Diffuser"        :usage "0.0078kW"}]
      [device-section {:title "Refrigerator"    :usage "0.0923kW"}]]])


;; App
;; ----------------------------------------------------------------------------


(defn app []
  [:div
    {:class "background shadow-2 flex overflow-hidden"}
    [:aside
      {:class "p3 menuWidth overflow-auto"}
      [sidebar]]
    [:article
      {:class "bg-very-light-grey p3 flex-auto overflow-auto"}
      [energy-consumption-chart
        {:readings(->> (fetch-readings)
                       (grouped-readings)
                       (sort-by :time <)
                       (take 30))}]]])


(defn mount []
  (r.dom/render [app] (js/document.getElementById "root")))


(defn ^:after-load re-render []
  (mount))


(defonce start-up (do (mount) true))
