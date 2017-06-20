(defproject expense_mgr "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-ring "0.8.10"]
            [lein-cljsbuild "1.0.5"]]
  :cljsbuild {:builds
              {:min {:source-paths ["src"]
                     :compiler {:output-to "out/main.js"
                                :optimizations :advanced}}}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 ;; [cljstache "2.0.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ring "1.5.0"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]]
  ;; You need a :ring with lein ring or lein ring server-headless
  ;; However, you must send the request through the function that wraps the handler with
  ;; with wrap-params, and any other wrap-* decorators.
  ;; :ring {:handler expense-mgr.core/app}

  ;; Note hyphen, expense-mgr even though our path is expense_mgr
  :main ^:skip-aot expense-mgr.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
