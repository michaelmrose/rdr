(defproject rdr "0.1.0-SNAPSHOT"
  :description "a handy cli interface to searching and opening books from your calibre library or recently reads"
  :url "none"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.gnu.org/licenses/gpl-3.0"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.rpl/specter "1.1.2"]
                 [swiss-arrows "1.0.0"]
                 [org.clojure/data.json "0.2.6"]
                 [pandect "0.5.0"]
                 [me.raynes/fs "1.4.6"]
                 ]
  :main rdr.core
  :target-path "target/%s"
  :aot :all
  :profiles {:uberjar {:aot :all}})