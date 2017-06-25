- insert or update all recs from list-all page.


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
