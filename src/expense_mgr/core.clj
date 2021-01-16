(ns expense-mgr.core
  (:require [clojure.java.jdbc :as jdbc] ;; :refer :all]
            [clj-time.core :as time]
            [clj-time.format :as format]
            [clojure.tools.namespace.repl :as tnr]
            [clojure.string :as str]
            [clojure.pprint :refer :all]
            [clostache.parser :refer [render]]
            [ring.adapter.jetty :as ringa]
            [ring.util.response :as ringu]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]])
  (:gen-class))


(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "expmgr.db"
   })

(defn create-db []
  (try (jdbc/db-do-commands db
                       (jdbc/create-table-ddl :news
                                         [:date :text]
                                         [:url :text]
                                         [:title :text]
                                         [:body :text]))
       (catch Exception e (println e))))

;; outer is a map with a key :category_list, the value of which is a list of maps with key :id.
;; Make a set of the :id values and if that id exists in the inner map, assoc :selected with the inner map

;; This creates an inner map suitable for html <select>, or multi <select> now that we have 0..n categories
;; per entry.

;; Now that this is working, the let binding seem redundant. It might read better to simply inline
;; the set function.

(defn map-selected [outer inner]
  "Map inner and conditionally assoc :selected key, mapping the result via assoc into outer
  as :all-category. Add key :all-category to outer, and while setting :selected appropriately on each
  iteration of :all-category. Map assoc, map conditional assoc. And :checked with value checked."
  (map (fn [omap]
         (assoc omap :all-category
                (map (fn [imap] 
                       (let [catset (set (mapv :id (:category_list omap)))]
                         (if (contains? catset (:id imap)) 
                           (assoc imap :selected "selected" :checked "checked")
                           imap))) inner))) outer))

(def cat-report-sql
"select * from 
        (select 
        (select name from category where id=entry.category) as category,
        sum(amount) as sum 
        from entry 
        group by category) 
as inner order by sum")

(defn cat-report
  "The template expects fields 'category' and 'sum'."
  []
  (let [recs (jdbc/query db [cat-report-sql])
        total (jdbc/query db ["select 'Total' as category, sum(amount) as sum from entry"])]
    {:rec-list (concat recs total)}))

;; insert into etocat (eid,cid) (select id,category from entry);
;; create table ecat as select id,category from entry;

(def all-cats-sql
  "select name as category_name,category.id from entry, category, etocat
where 
entry.id=etocat.eid and
category.id=etocat.cid and entry.id=?
order by category.id")

(defn list-all-cats [eid]
  {:category_list (jdbc/query db [all-cats-sql eid])})

;; name from category where category.id=entry.category

;; Can use SQL to set col "selected" as true when entry.category = category.id
;; Or can use clojure.

(def show-sql
  "select entry.*,
(select name from category where category.id=entry.category) as category_name 
from entry 
where entry.id=?")


(defn show-conn [conn params]
  (let [id (get params "id")
        erecs (jdbc/query conn [show-sql id])
        full-recs (map (fn [rec] (merge rec (list-all-cats (:id rec)))) erecs)
        cats (jdbc/query conn ["select * from category order by name"])]
    (map-selected full-recs cats)))


;; name from category where category.id=entry.category
(defn show [params]
  (let [id (get params "id")
        erecs (jdbc/query db [show-sql id])
        full-recs (map (fn [rec] (merge rec (list-all-cats (:id rec)))) erecs)
        cats (jdbc/query db ["select * from category order by name"])]
    (map-selected full-recs cats)))

(defn choose [params]
  (let [title (params "title")]
    (when (not (nil? title))
      (jdbc/query db ["select * from entry where title like ? limit 1" (format "%%%s%%" title)]))))

(defn good-year
  "Arg must be a 4 digit string, all digits."
  [ystr]
  (some? (re-matches #"^\d{4}$" (str ystr))))

(defn good-month
  "Arg must be a 2 char str, leading zero as necessary."
  [mstr]
    (some? (re-matches #"0[1-9]|1[0-2]" (str mstr))))

;; using-year is a def that is an fn, and has a local, internal atom uy. Basically using-year is a closure. So
;; that safe and good programming practice, maybe.

;; Was using_ym or using-ym, but I don't see it being used as a function call.
(def curr-year-xx
  (let [ux (atom "2017")]
    (fn 
      ([] @ux)
      ([yy]
       (if (good-year yy)
         (reset! ux yy)
         @ux)))))

(def curr-month-xx
  (let [ux (atom "01")]
    (fn 
      ([] @ux)
      ([mm]
       (if (good-month mm)
         (reset! ux mm)
         @ux)))))

(defn truthy-string
  "True for non-blank strings."
  [arg]
  (if (= (str (type arg)) "class java.lang.String")
    (not (clojure.string/blank? arg))
    false))

(defn is-good-date
  [str]
  (if (truthy-string str)
    (let [[_ matched-year matched-month matched-day] (re-matches #"^(\d{4})-(\d{1,2})-(\d{1,2})$" str)]
      (if (and (seq matched-year) (seq matched-month) (seq matched-day))
        [matched-year matched-month matched-day]
        []))
    []))

(defn check-uy [cmap]
  "Expect empty string for missing map values, not nil, because nil breaks re-matches. Return year and month
  sets curr-year and curr-month."
  (let [date (:date cmap)
        uy (:using-year cmap)
        um (:using-month cmap)
        [_ myear mmonth _] (re-matches #"^(\d{4})-(\d{1,2})-(\d{1,2})$" date)]
    (cond (and (seq myear) (seq mmonth))
          (do
            (prn (format "check-uy good date: %s returning: %s %s" date myear mmonth))
            [myear mmonth])
          (and (seq uy) (seq um))
          [uy um]
          :else
          ["2018" "01"])))

;; We only accept full date or day-of-month as a short cut in the current year and month

(defn smarter-date
  "This is fine, but doesn't normalize dates. Probably better to tokenize dates, and reformat in a normal format."
  [sdmap]
  (let [date (:date sdmap)
        uy (:using-year sdmap)
        um (:using-month sdmap)
        full-match (re-matches #"^(\d{4})-\d{1,2}-\d{1,2}$" date)]
    (prn "sd uy " uy " um " um " date " date)
    (cond (some? full-match) date
          ;; Crazy ass left padding. Trivial in other languages.
          (re-matches #"^\d{1,2}$" date) (format "%s-%s-%s" uy um (second (re-matches #".*?(\d{1,2})" (str "00" date))))
          :else (format "%s-%s-01" uy um))))

(defn test-smarter-date []
  [(smarter-date {:date "-06-01" :using-year "2001"})
   (smarter-date {:date "06-01" :using-year "2001"})])

(defn update-db [params]
  "Update entry. Return a list of a single integer which is the number of records effected, which is what
  jdbc/execute!  returns. On error return list of zero."
  (let [id (params "id")
        date (params "date")
        category (flatten [(params "category")])
        amount (params "amount")
        mileage (params "mileage")
        notes (params "notes")]
    (cond (not (nil? (params "id")))
          (do
            (jdbc/execute! db 
                           ["update entry set date=?,amount=?,mileage=?,notes=? where id=?"
                            date amount mileage notes id])
            (jdbc/execute! db 
                           ["delete from etocat where eid=?" id])
            ;; Tricky. Lazy map doesn't work here. This is side-effect-y, so perhaps
            ;; for or doseq would be more appropriate. That said, we might want the return value of execute!.
            (mapv 
             (fn [cid]
               (jdbc/execute! db 
                              ["insert into etocat (eid,cid) values (?,?)" id cid])) category))
          :else (do
                  (prn "no id in params:" params)
                  '(0)))))

(defn pq [xx] (java.util.regex.Pattern/quote xx))

(defn cstr
  "Output pretty print of str. Unclear why I named this function cstr."
  [str] (str/replace (with-out-str (pprint str)) #"\n" "\n"))

(defn dev-init []
  (def myrec '({:id 1, :title "Hitachi Compact Impact Driver", :desc "The best tool I own", :stars nil, :isbn nil}))
  (def mytpl (slurp "edit.html")))

;; [string map] returning modified string
;; (seq) the map into a sequence of k v
;; This is the "render" of something like clostache.

(defn map-re
  "Replaced placeholders in the orig template with keys and values from the map remap. This is the functional 
equivalent of using regexes to change a string in place."
  [orig remap]
  (loop [ostr orig
         [[label value] & remainder] (seq remap)]
    (if (nil? label)
      ostr
      (recur (str/replace ostr (re-pattern (pq (str "{{" label "}}"))) (str value)) remainder))))

(def list-limit-sql
  "select entry.*,(select name from category where category.id=entry.category) as category_name 
from entry
where date>=date(?,'start of month') and date<date(date(?,'start of month'),'+1 month')
order by date desc")

(defn list-limit
  "List records for :limit-date."
  [params]
  (let [min-date (:limit-date params)
        max-date (:limit-date params)
        erecs (jdbc/query db [list-limit-sql min-date max-date])
        full-recs (map (fn [rec] (merge rec (list-all-cats (:id rec)))) erecs)
        cats (jdbc/query db ["select * from category order by name"])
        all-rec (assoc {:all-recs (map-selected full-recs cats)} :all-category cats)]
    all-rec))


(def list-all-sql
  "select entry.*,(select name from category where category.id=entry.category) as category_name 
from entry
order by date desc")

;; (map #(assoc % :all-category cats) recs)
;; (map #(if (= (:category %) (:id %)) (assoc % :selected 1) %)  [{:foo 1} {:foo 2}])

(defn list-all
  "List all records, for all dates."
  [params]
  (let [erecs (jdbc/query db [list-all-sql])
        full-recs (map (fn [rec] (merge rec (list-all-cats (:id rec)))) erecs)
        cats (jdbc/query db ["select * from category order by name"])
        all-rec (assoc {:all-recs (map-selected full-recs cats)} :all-category cats)]
    all-rec))

;; {:last_insert_rowid() 12} The key really is :last_insert_rowid() with parens. The clojure reader simply
;; can't grok a key with parens, so we have to use keyword.

(defn insert [params]
  "map of params => integer record id."
  (let [category (flatten [(params "category")])
        kmap (jdbc/db-do-prepared-return-keys
              db
              ["insert into entry (date,amount,mileage,notes) values (?,?,?,?)"
               (params "date")
               (params "amount")
               (params "mileage")
               (params "notes")])
        rowmap [{:id (get kmap (keyword "last_insert_rowid()"))}]
        id (:id (first rowmap))]
    (jdbc/execute! db 
                   ["delete from etocat where eid=?" id])
    ;; Tricky. Lazy map doesn't work here. This is side-effect-y, so perhaps
    ;; for or doseq would be more appropriate. That said, we might want the return value of execute!.
    (mapv 
     (fn [cid]
       (jdbc/execute! db 
                      ["insert into etocat (eid,cid) values (?,?)" id cid])) category)
    rowmap))


(defn fill-list-all
  "Fill in a list of all records. The regex must use (?s) so that newline matches .
Initialize with empty string, map-re on the body, and accumulate all the body strings."
  [rseq]
  (let [template (slurp "list-all.html")]
    (render template rseq)))

(defn render-any
  "Render rseq to the template file template-name."
  [rseq template-name]
  (let [template (slurp template-name)]
    (render template rseq)))

(defn edit
  "Map each key value in the record against placeholders in the template to create a web page."
  [record]
  (let [template (slurp "edit.html")
        body (render template record)]
    body))

(defn request-action
  [working-params action]
  (cond (= "show" action)
        (map #(assoc % :sys-msg (format "read id %s from db" (get working-params "id"))) (show working-params))
        (= "choose" action)
        (choose working-params)
        (= "update-db" action)
        (do 
          (update-db working-params)
          (list-limit working-params))
        (= "set_limit_date" action)
        (list-limit working-params)
        (= "list-limit" action)
        (list-limit working-params)
        (= "list-all" action)
        (list-all working-params)
        (= "insert" action)
        (do
          (insert working-params)
          (list-limit working-params))
        (= "catreport" action)
        (cat-report)
        :else
        {}))

;; Routing happens here. Note two (or) clauses. The first is a safety net, the second is the real thing.

(defn reply-action
  "Generate a response for some request. Params is working-params, which has injected params for the current request."
  [rmap action params]
  (prn (format "reply-action rmap count: %s action: %s params: %s" (count rmap) action params))
  (cond (or (nil? rmap)
            (nil? (some #{action} ["set_limit_date" "show" "list-all" "list-limit" "insert" "update-db" "catreport"])))
        ;; A redirect would make sense, maybe.
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (format
                "<html><body>Unknown command: %s You probably want: <a href=\"app?action=list-limit\">List limit</a></body</html>"
                action)}
        (= "show" action)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (edit (assoc (first rmap)
                            :using-year (:using-year params)
                            :using-month (:using-month params)
                            :limit-date (:limit-date params)))}
        (or (= "set_limit_date" action) (= "list-limit" action) (= "list-all" action) (= "insert" action) (= "update-db" action))
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (fill-list-all (assoc rmap
                                     :sys-msg (format "xlist all %s" params)
                                     :using-year (:using-year params)
                                     :using-month (:using-month params) 
                                     :limit-date (:limit-date params)))}
        (= "catreport" action)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (render-any (assoc rmap
                                  :sys-msg "Category report"
                                  :using-year (:using-year params)
                                  :using-month (:using-month params)) "catreport.html")}))

;; First string is the output format, which is also a supported input format.
;; Other strings are supported input formats.
(def multi-parser
  (format/formatter
   (time/default-time-zone)
   "YYYY-MM-dd" "YYYY/dd/MM" "YYYY MM dd" "YYYY-MM" "YYYY MM"))

;; (new java.util.Date "01 Jan 2018")
;; Proof that Java programmers are insane:
;; year-1900, month is zero based, day is one based.
;; (new java.util.Date 118 0 1) 
(defn check-limit-date
  "If param date is good then use it. 
  If params date is 2 digits then combine with limit_date. 
  If have limit_date then use it.
  Else returns today's date."
  [params]
  (let [rdate (get params "date")
        pld (get params "limit_date")
        generated-date (if (and (seq rdate) (= 2 (count rdate)))
                         (str/replace pld #"..$" (format "%02d" (Integer. rdate)))
                         nil)]
    (cond (seq (is-good-date rdate))
          rdate
          (seq generated-date)
          generated-date
          (seq pld)
          (do
            (prn "parsing limit_date: " pld)
            (format/unparse multi-parser (format/parse multi-parser pld)))
          :else
          (.format (java.text.SimpleDateFormat. "YYYY-MM-dd") (new java.util.Date)))))

;; expense-mgr.core=> 2018-12-10 21:28:40.850:WARN:oejs.AbstractHttpConnection:/app?
;; action=insert&limit_date=2017-01-22&using_year=&id=&date=24&category=3&amount=1.50&mileage=&notes=flkjf&insert=insert
;; java.util.FormatFlagsConversionMismatchException: Conversion = s, Flags = 0

;; todo: change params keys from strings to clj keywords.
;; (reduce-kv #(assoc %1 %2 (clojure.string/trim %3))  {} {:foo " stuff"})
;; (reduce-kv (fn [mm kk vv] (assoc mm kk (clojure.string/trim vv))) {} {:foo " stuff"})
(defn handler 
  "Expense link manager."
  [request]
  (if (empty? (:params request))
    nil
    (let [temp-params (reduce-kv #(assoc %1 %2 (clojure.string/trim %3))  {} (:params request))
          _ (prn "tp: " temp-params)
          action (get temp-params "action")
          ras  request
          nice-date (check-limit-date temp-params)
          [using-year using-month] (check-uy {:date nice-date
                                              :using-year (or (get temp-params "using_year") "")
                                              :using-month (or (get temp-params "using_month") "")})
          _ (prn "nice-date: " nice-date " limit-date: " (get temp-params "limit_date") " date: " (get temp-params "date") " uy: " using-year " um: " using-month)
          ;; Add :using-year, replace "date" value with a better date value
          working-params (merge temp-params
                                {:using-year using-year
                                 :using-month using-month
                                 :limit-date nice-date
                                 "date" nice-date})
          ;; rmap is a list of records from the db, will full category data
          rmap (request-action working-params action)]
      (prn "wp: " working-params)
      (reply-action rmap action working-params))))

(def app
  (wrap-multipart-params (wrap-params handler)))

;; Only if using compojure
;; (defroutes app 
;;   (GET "/action" [] (wrap-multipart-params (wrap-params handler))))

;; https://stackoverflow.com/questions/2706044/how-do-i-stop-jetty-server-in-clojure
;; example
;; (defonce server (run-jetty #'my-app {:port 8080 :join? false}))

;; Unclear how defonce and lein ring server headless will play together.
(defn ds []
  (defonce server (ringa/run-jetty app {:port 8080 :join? false})))

;; Need -main for 'lien run', but it is ignored by 'lein ring'.
(defn -main []
  (prn "-main starts")
  (ds)
  (prn "server: " server)
  (.start server))

;; https://stackoverflow.com/questions/39765943/clojure-java-jdbc-lazy-query
;; https://jdbc.postgresql.org/documentation/83/query.html#query-with-cursor
;; http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html#exception-handling-and-transaction-rollback
;; http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html#using-transactions

(defn ex-lazy-select
  []
  (jdbc/with-db-transaction [tx db] ;; originally connection
    (jdbc/query tx
                [(jdbc/prepare-statement (:connection tx)
                                         "select * from mytable"
                                         {:fetch-size 10})]
                {:result-set-fn (fn [result-set] result-set)})))

(defn demo-autocommit
  "Demo looping SQL without a transaction. Every execute will auto-commit, which is time consuming. This
  function takes 62x longer than doing these queries inside a single transaction."
  []
  (jdbc/execute! db ["delete from entry where title like 'demo transaction%'"])
  (loop [nseq (range 10000)]
    (let [num (first nseq)
          remainder (rest nseq)]
      (if (nil? num)
        nil
        (do
          (jdbc/execute! db ["insert into entry (title,stars) values (?,?)" (str "demo transaction" num) num])
          (recur remainder))))))

(defn demo-transaction
  "Demo looping SQL inside a transaction. This seems to lack an explicit commit, which makes it tricky to
commit every X SQL queries. Use doall or something to un-lazy inside with-db-transaction, if you need the
query results.

http://pesterhazy.karmafish.net/presumably/2015-05-25-getting-started-with-clojure-jdbc-and-sqlite.html"
  []
  (jdbc/with-db-transaction [dbh db]
    (jdbc/execute! dbh ["delete from entry where title like 'demo transaction%'"])
    (loop [nseq (range 10000)]
      (let [num (first nseq)
            remainder (rest nseq)]
        (if (nil? num)
          nil
          (do
            (jdbc/execute! dbh ["insert into entry (title,stars) values (?,?)" (str "demo transaction" num) num])
          (recur remainder)))))))


(defn makefresh []
  (.stop server)
  (tnr/refresh)
  (ds)
  (.start server)
  )

