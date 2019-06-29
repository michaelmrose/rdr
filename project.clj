(defproject rdr "0.1.0-SNAPSHOT"
  :description "A handy cli interface to searching and opening books from your calibre library or recently read titles from your library."
  :url "none"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.gnu.org/licenses/gpl-3.0"}
  :plugins [[io.taylorwood/lein-native-image "0.3.0"]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.rpl/specter "1.1.2"]
                 [swiss-arrows "1.0.0"]
                 [org.clojure/data.json "0.2.6"]
                 [pandect "0.5.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/tools.cli "0.4.2"]]
  :main rdr.core
  :cljfmt {:indents {if-let* [[:block 1]]}}
  :target-path "target/"
  :aot :all
  :native-image {:name     "rdr"
                 :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                 :opts     ["--report-unsupported-elements-at-runtime"
                            "--initialize-at-build-time"
                            "--allow-incomplete-classpath"
                            "-Dgraal.CompilerConfiguration=economy"
                            "--no-server"]})
