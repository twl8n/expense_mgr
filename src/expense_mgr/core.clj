(ns expense-mgr.core
  (:require [clojure.java.jdbc :as jdbc] ;; :refer :all]
            [clojure.tools.namespace.repl :as tns]
            [clojure.string :as str]
            [clojure.pprint :refer :all]
            [clostache.parser :refer [render]]
            [ring.adapter.jetty :as ringa]
            [ring.util.response :as ringu]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]])
  (:gen-class))


;; use destructuring to allow a 2 arg function to work in comp
;; Not a great example since 2 arg fun really is 1 arg with destructuring
;; maybe partial with a true 2 arg func.
(def fx (comp (fn [[foo bar]] (format "a: %s b: %s" foo bar)) (fn foo [xx yy] (prn xx yy) [xx yy])))
;; (fx 1 3)
;; 1 3
;; "a: 1 b: 3"
;; quick-web.core=> 


;; A true 2 arg fn2 called via partial apply where the arg is a vector with 2 items.

(defn fn2 [foo bar] (format "a: %s b: %s" foo bar))
(def fx (comp  (partial apply fn2) (fn foo [xx yy] (prn xx yy) [xx yy])))
;; quick-web.core=> (fx 1 3)
;; 1 3
;; "a: 1 b: 3"
;; quick-web.core=> 

(defn mya
  "Loop only for side effect. The str/replace is done, but the value is only printed, not saved. stracc and
  rex are always passed back in the recur, and only the index is modified."
  []
  (loop [stracc "xxxa yyya yb" 
         rex [#"x" #"y"]
         index 0]
    (if (> index 100)
      (println "hit: " index)
      (recur 
       (let [cs (str/replace stracc (first rex) "zz")]
         (println index "is string: " cs)
         stracc)
       rex
       (inc index)))))

(defn myb
  "Same speed as (myz). Call the body in a let binding so we can return before the index is incremented an extra time. Return with
  index 1 unlike (mya) and (myz) which return with index 2. Loop over regex seq rex (consuming rex), modifying string
  accumulator stracc. Use an index."
  []
  (loop [stracc "xxxa yyya yb" 
         rex [#"x" #"y"]
         index 0]
    (let [cs (str/replace stracc (first rex) "zz")
          rexr (rest rex)]
      (println index "is string: " cs)
      (if (empty? rexr)
        (do
          ;; This print raises the timing on 10 invocations from 33 to 47 ms.
          (println "Final index: " index)
          cs)
        (recur cs 
               rexr
               (inc index))))))
  
  (defn myz
  "Loop over regex seq rex (consuming rex), modifying string accumulator stracc. Also inc an index so we can print debug info."
  []
  (loop [stracc "xxxa yyya yb" 
         rex [#"x" #"y"]
         index 0]
    (if (empty? rex)
        (do
          ;; This print raises the timing on 10 invocations from 33 to 47 ms.
          (println "Final index: " index)
          stracc)
      (recur (let [cs (str/replace stracc (first rex) "zz")]
               (println index "is string: " cs)
               cs)
             (rest rex) 
             (inc index)))))

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

(defn map-selected [outer inner]
  "map inner and conditionally create :selected key, mapping the result into outer as :all-category."
  (map (fn [omap]
         (assoc omap :all-category
                (map (fn [imap] 
                       (if 
                           (= (:category omap) (:id imap)) 
                         (assoc imap :selected "selected")
                         imap)) inner))) outer))

;; Can use SQL to set col "selected" as true when entry.category = category.id
;; Or can use clojure.

;; name from category where category.id=entry.category
(defn show [params]
  (let [id (get params "id")
        recs (jdbc/query
              db 
              ["select entry.*,(select name from category where category.id=entry.category) as category_name from entry where entry.id=?" id])
        cats (jdbc/query
              db 
              ["select * from category order by name"])
        all-rec (map-selected recs cats)]
    all-rec))


(defn choose [params]
  (let [title (params "title")]
    (when (not (nil? title))
      (jdbc/query db ["select * from entry where title like ? limit 1" (format "%%%s%%" title)]))))

(defn update-db [params]
  (let [id (params "id")
        date (params "date")
        category (params "category")
        amount (params "amount")
        mileage (params "mileage")
        notes (params "notes")]
    (cond (not (nil? (params "id")))
          (do
            (prn ["update entry set date=?,category=?,amount=?,mileage=?,notes=? where id=?"
                  date category amount mileage notes id])
            (jdbc/execute! db 
                           ["update entry set date=?,category=?,amount=?,mileage=?,notes=? where id=?"
                            date category amount mileage notes id]))
          :else (do (prn "no id in params:" params) 0))))

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


(def list-all-sql
  "select entry.*,(select name from category where category.id=entry.category) as category_name 
from entry
order by entry.id")

    ;; (map #(assoc % :all-category cats) recs)
    ;;(map #(if (= (:category %) (:id %)) (assoc % :selected 1) %)  [{:foo 1} {:foo 2}])

(defn list-all [params]
  (let [recs (jdbc/query db [list-all-sql])
        cats (jdbc/query db ["select * from category order by name"])
        all-rec (assoc {:all-recs (map-selected recs cats)} :all-category cats)]
    (prn "list-all recs: " recs)
    all-rec))

;; {:last_insert_rowid() 12} The key really is :last_insert_rowid() with parens. The reader simply can't grok
;; a key with parens, so we have to use keyword.

(defn insert [params]
  "map of params => integer record id."
  (let [kmap (jdbc/db-do-prepared-return-keys
              db
              ["insert into entry (date,category,amount,mileage,notes) values (?,?,?,?,?)"
               (params "date")
               (params "category")
               (params "amount")
               (params "mileage")
               (params "notes")])]
    [{:id (get kmap (keyword "last_insert_rowid()"))}]))

;; (let [[_ pre body post] (re-matches #"(.*?)\{\{for\}\}(.*?)\{\{end\}\}(.*)$"
;; "pre{{for}}middle{{end}}post")]
;; {:pre pre :body body :post post})
;; {:pre "pre", :body "middle", :post "post"}

(defn map-re-fill-list-all
  "Fill in a list of all records. The regex must use (?s) so that newline matches .
Initialize with empty string, map-re on the body, and accumulate all the body strings."
  [rseq]
  (let [template (slurp "list-all.html")
        [all pre body post] (re-matches #"(?s)^(.*?)\{\{for\}\}(.*?)\{\{end\}\}(.*)$" template)]
    (str (map-re pre {:_msg ["List all from db"]})
         (loop [full ""
                remap rseq]
           (prn full)
           (if (empty? remap)
               full
             (recur (str full (map-re body (first remap))) (rest remap))))
         post)))

(defn fill-list-all
  "Fill in a list of all records. The regex must use (?s) so that newline matches .
Initialize with empty string, map-re on the body, and accumulate all the body strings."
  [rseq]
  (let [template (slurp "list-all.html")]
    (render template rseq)))

(defn edit
  "Map each key value in the record against placeholders in the template to create a web page."
  [record]
  (let [template (slurp "edit.html")
        body (render template record)]
    body))

(defn handler 
  "Expense link manager."
  [request]
  (let [params (:params request)
        action (params "action")
        ras  request
        rmap (cond (= "show" action)
                   (map #(assoc % :sys-msg (format "read %s from db" (get params "id"))) (show params))
                   (= "choose" action)
                   (choose params)
                   (= "update-db" action)
                   (do 
                     (update-db params)
                     ;;(map #(assoc % :sys-msg "updated") (show params))
                     (list-all params))
                   (= "list-all" action)
                   (list-all params)
                   (= "insert" action)
                   (list-all (first (insert params))))]
    (cond (some? rmap)
          (cond (= "show" action)
                {:status 200
                 :headers {"Content-Type" "text/html"}
                 :body (edit (first rmap))}
                (or (= "list-all" action) (= "insert" action) (= "update-db" action))
                {:status 200
                 :headers {"Content-Type" "text/html"}
                 :body (fill-list-all (assoc rmap :sys-msg "list all"))})
          :else
          ;; A redirect would make sense, maybe.
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body "<html><body>Unknown command. You probably want: <a href=\"app?action=list-all\">List all</a></body</html>"}
          )))

(def app
  (wrap-multipart-params (wrap-params handler)))

;; https://stackoverflow.com/questions/2706044/how-do-i-stop-jetty-server-in-clojure
;; example
;; (defonce server (run-jetty #'my-app {:port 8080 :join? false}))

;; Unclear how defonce and lein ring server headless will play together.
(defn ds []
  (defonce server (ringa/run-jetty app {:port 8080 :join? false})))

;; Need -main for 'lien run', but it is ignored by 'lein ring'.
;; (defn -main []
;;   (ringa/run-jetty app {:port 8080}))

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
  (tns/refresh)
  (ds)
  (.start server))

