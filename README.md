#### Curr

+ 2018-12-09 If params using_year and using_month are set and date is #"^\d$" then create yyyy-mm-$date
else date must be a full date, and if not then use now() as date.

curr-year and curr-month cannot store state in an atom because this is a web server, potentially multiuser,
although current design is single user. Let's not make things worse by being stupid.

#### Setup

```
cat schema.sql | sqlite3 expmgr.db
```

#### Running

This is untested:

```
lein uberjar
cd ..
mkdir my-em-prod
cd my-em-prod
../expense_mgr/deploy.sh
java -jar expense-mgr-standalone.jar
```

In a web browser:

http://localhost:8080/app?action=list-all

http://localhost:8080/app?action=list-limit&limit_date=2018-01-01

#### Dump existing table to CSV

```
.mode csv
.once category.csv
select * from category;
````

#### Read CSV into an existing table

This assumes that cagetory.csv does not have column names in the first line, and that table `category` already exists.

```sql
.mode csv
.import category.csv category
create table import2015 (
                     date text,
                     amount double,
                     notes text);
.mode csv
.import 2015-insert.sql import2015
insert into entry (date,amount,notes) select date,amount,notes from import2015;

select count(*)
from 
(
create table fix_date as 
 select entry.id,entry.date,ii.date,ii.amount,entry.amount,ii.notes,entry.notes
from entry, import2015 ii
where
strftime("%Y-%m",ii.date) = strftime("%Y-%m",entry.date)
and strftime("%d",entry.date) = '01'
-- and strftime("%Y-%m",ii.date) = '2015-11'
and ii.amount=entry.amount
 and ii.notes=entry.notes
 ;
-- group by entry.date,ii.date,ii.amount,entry.amount,ii.notes,entry.notes
order by ii.date);


update fix_date set date="date:1";


select count(*) from (
select date,(select date from fix_date fd where fd.id=entry.id) from entry where id in (select id from fix_date)
);

update entry set date=(select date from fix_date fd where fd.id=entry.id)
where exists (select date from fix_date fd where fd.id=entry.id);

select changes();


(select ii.date from import2015 ii where
strftime("%Y-%m",ii.date) = strftime("%Y-%m",entry.date)
and ii.amount=entry.amount
and ii.notes=entry.notes)
where strftime("%Y-%m",date) = '2015-11';

-- Very broken. Second column is always the same.
select date,(select date from fix_date fd where fd.id=id) from entry where id in (select id from fix_date);


```

-- Sample quick import record
-- 2015-08-24,46.41,exxon lovingston


#### todo

* "parsing limit_date: " "2018"
2019-04-06 10:37:47.254:WARN:oejs.AbstractHttpConnection:/app?action=set_limit_date&limit_date=2018&limit_month=Limit+Month
java.lang.IllegalArgumentException: Invalid format: "2018" is too short

* limit date 2016 does not default to 2016-01 but throws exception

"parsing limit_date: " "2016"
2018-12-29 13:21:33.632:WARN:oejs.AbstractHttpConnection:/app?action=set_limit_date&limit_date=2016&limit_month=Limit+Month
java.lang.IllegalArgumentException: Invalid format: "2016" is too short
...
	at expense_mgr.core$check_limit_date.invokeStatic(core.clj:419)

- deal with this:

http://localhost:8080/app?action=foo

"tp: " {"action" "foo"}
2018-12-29 12:47:54.453:WARN:oejs.AbstractHttpConnection:/app?action=foo
java.lang.NullPointerException
...
	at expense_mgr.core$is_good_date.invokeStatic(core.clj:148)

- Update check-limit-date to handle this:

"tp: " {"id" "", "action" "insert", "using_month" "11", "amount" "4.0", "insert" "insert", "date" "xx", "category" "2", "notes" "", "limit_date" "2016-11-02", "mileage" "", "using_year" "2016"}

2018-12-29 13:08:35.920:WARN:oejs.AbstractHttpConnection:/app?action=insert&limit_date=2016-11-02&using_year=2016&using_month=11&id=&date=xx&category=2&amount=4.0&mileage=&notes=&insert=insert
java.lang.NumberFormatException: For input string: "xx"
...
	at expense_mgr.core$check_limit_date.invokeStatic(core.clj:410)

"tp: " {"action" "set_limit_date", "limit_date" "2015-17", "limit_month" "Limit Month"}
"using ld: " "2015-17"
2018-12-29 10:23:04.079:WARN:oejs.AbstractHttpConnection:/app?action=set_limit_date&limit_date=2015-17&limit_month=Limit+Month
org.joda.time.IllegalFieldValueException: Cannot parse "2015-17": Value 17 for monthOfYear must be in the range [1,12]

x (working params date "or" wrong order) SQL date always becomes limit_date

http://localhost:8080/app?action=update-db&id=613&limit_date=2015-10-01&using_year=2015&date=2015-10-13&category=30&amount=17.08&mileage=&notes=gas&submit=Save

action: update-db params: {\"id\" \"613\", \"action\" \"update-db\", :limit-date \"2015-10-01\", :using-month \"10\", \"submit\" \"Save\", \"amount\" \"17.08\", :using-year \"2015\", \"date\" \"2015-10-01\", \"category\" \"30\", \"notes\" \"gas\", \"limit_date\" \"2015-10-01\", \"mileage\" \"\", \"using_year\" \"2015\"}"

* Change limit-date to year/month of edited record.

* Add *.html to resources/ so they are included in the uberjar

* Add a default route instead of 404, or change the 404 page.

x Bug

2018-12-28 21:45:23.910:WARN:oejs.AbstractHttpConnection:/app?action=update-db&id=659&limit_date=2015-02-28&using_year=2015&date=2015-02-27&category=38&amount=43.43&mileage=&notes=gas&submit=Save
java.lang.NumberFormatException: For input string: "2015-02-27"
	at java.lang.NumberFormatException.forInputString(NumberFormatException.java:65)
	at java.lang.Integer.parseInt(Integer.java:580)
...
	at expense_mgr.core$check_limit_date.invokeStatic(core.clj:390)
	at expense_mgr.core$check_limit_date.invoke(core.clj:385)
	at expense_mgr.core$handler.invokeStatic(core.clj:415)
	at expense_mgr.core$handler.invoke(core.clj:406)

* add unit tests for check-limit-date where rdate is various strings

* add limit_date={{limit-date}} to all links or generalize and send all config params every time.
Or set a cookie?

* + default to list-limit and keep limit-date set at all times, and carry limit-date around

* x list-all year+month

* allow date entry digits mmdd

* every request-action must be identical structure. There seem to be at least
two variants with either a list of records or [:all-recs (list of records)]. 

* list-all by year

* catgory UI maybe a cat field, comma separated, parsed by js and used to check checkboxes in real time.

* category editor, allow categories to be deleted (and when deleted, remove from table etocat)

* don't show deleted cats in UI. Modify SQL.

* categories need to be in consistent order in list-all and edit (show) pages.

* remove debug println's

* category report needs better SQL to distinguish what cats to group by, and which are secondary
  groups (like "personal" and "business")

x multi-category tagging

x fix list-all to merge erecs with list-all-cats on the :id of each rec

x check/fix that map-selected is working to create multi selected menu, which really
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
