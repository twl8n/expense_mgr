- todo

* list-all by year, year+month

* category editor

* + multi-category tagging

+ fix list-all to merge erecs with list-all-cats on the :id of each rec

+ check/fix that map-selected is working to create multi selected menu, which really
should be some alternate UI, probably muliple single selection <select> elements

x update db, schema and data


x fix -main to call (ds). Now we can "lein run" and the app runs. When the app is running, point a web browser at:

http://localhost:8080/app?action=list-all

x track current year, insert not using using_year.

x -6-1 causes date to be blank.

x create report of sums by category

select * from (select (select name from category where id=entry.category) as category,sum(amount) as sum from entry group by category) as inner order by sum;

* normalize date from mm-dd to MM-DD require zero left pad.

* make entry id more obvious than "Edit id 21"

* list-all column sort

* list-all column totals

* reports by month, by year, by category.

* table entry add personal/business column

* row hover highlight row

* edit row when click anywhere in row

* row right click menu "edit,?"

* shortcut navigation keys: tab, shift-tab, return shift-return

* other date formats? date shortcuts?

* ? break up category "equipment" into big stuff with serial number, small stuff?

* Add page to insert/edit category data.

* create an app, a jar file, web menu to quit, app launches browser?

- Validate critical data, esp. foreign keys. Add constraint?

We don't want "11 " going into an integer field that should be 11.

That can cause 2 hours of confusion.

Both of these work in SQLite:

select entry.*,(select name from category where id='2 ') as category_name from entry where id=10;
select entry.*,(select name from category where id=2) as category_name from entry where id=10;

But the first one won't work when called from jdbc, although it returns no error message.

- x insert or update all recs from list-all page.

;; Nice. Works, returns new index.
(jdbc/insert! db "entry" {:date 1 :category 2 :amount 12.3 :mileage 4 :notes "foo"})
=>({:last_insert_rowid() 10})

;; Not such a useful return value, rows effected.
(jdbc/insert! db "entry" ["date","category","amount","mileage","notes"] ["1" "2" "3" "4" "5"])
=>(1)

;; OMG works, sth then db-do-prepared-return-keys.
;; https://github.com/clojure/java.jdbc
;;  sql-params is a vector containing a SQL string or PreparedStatement followed by parameters -- like other APIs in this library
;; sql-params = \[(sql-string|preparedstatement) vector \]
(def sth (jdbc/prepare-statement
          (jdbc/get-connection db)
          "insert into entry (date,category,amount,mileage,notes) values (?,?,?,?,?)"
          {:return-keys true}))
(jdbc/db-do-prepared-return-keys db [sth "1" "2" "3" "4" "5"])
=>{:last_insert_rowid() 6}

;; Works. Not necessary to prepare the sql, in spite of the name.
(jdbc/db-do-prepared-return-keys db ["insert into entry (date,category,amount,mileage,notes) values (?,?,?,?,?)" "1" "2" "3" "4" "5"])
=>{:last_insert_rowid() 7}

;; IllegalArgumentException db-spec org.sqlite.PrepStmt@4534175e is missing a required parameter  clojure.java.jdbc/get-connection (jdbc.clj:338)
(jdbc/db-do-prepared-return-keys sth ["1" "2" "3" "4" "5"])

(jdbc/db-do-prepared-return-keys db true ["1" "2" "3" "4" "5"] {})

;; SQLException [SQLITE_ERROR] SQL error or missing database (near "1": syntax error)  org.sqlite.DB.newSQLException (DB.java:383)
(jdbc/db-do-prepared-return-keys db "insert into entry (date,category,amount,mileage,notes) values (?,?,?,?,?)" ["1" "2" "3" "4" "5"] {})


(def sth (jdbc/prepare-statement
          (jdbc/get-connection db)
          "insert into entry (date,category,amount,mileage,notes) values (?,?,?,?,?)"
          {:return-keys true}))


(defn db-do-prepared-return-keys
  "Executes an (optionally parameterized) SQL prepared statement on the
  open database connection. The param-group is a seq of values for all of
  the parameters. transaction? can be ommitted and will default to true.
  Return the generated keys for the (single) update/insert.
  A PreparedStatement may be passed in, instead of a SQL string, in which
  case :return-keys MUST BE SET on that PreparedStatement!"
  ([db sql-params]
   (db-do-prepared-return-keys db true sql-params {}))
  ([db transaction? sql-params]
   (if (map? sql-params)
     (db-do-prepared-return-keys db true transaction? sql-params)
     (db-do-prepared-return-keys db transaction? sql-params {})))
  ([db transaction? sql-params opts]
   (let [opts (merge (when (map? db) db) opts)]
     (if-let [con (db-find-connection db)]
       (let [[sql & params] (if (sql-stmt? sql-params) (vector sql-params) (vec sql-params))]
         (if (instance? PreparedStatement sql)
           (db-do-execute-prepared-return-keys db sql params (assoc opts :transaction? transaction?))
           (with-open [^PreparedStatement stmt (prepare-statement con sql (assoc opts :return-keys true))]
             (db-do-execute-prepared-return-keys db stmt params (assoc opts :transaction? transaction?)))))
       (with-open [con (get-connection db)]
         (db-do-prepared-return-keys (add-connection db con) transaction? sql-params opts))))))
