(ns rdr.core
  (:require
   [clojure.java.shell :as ex]
   [clojure.string :as string]
   [rdr.utils :refer :all]
   [rdr.calibre :as calibre]
   [clojure.tools.cli :as cli]
   [me.raynes.fs :as fs])
  (:gen-class)
  ;; (:refer-clojure)
)

(set! *warn-on-reflection* true)
(declare opts)
(def help-text
  "Usage:
  -p [list]     => list of formats in order of preference eg pdf,epub,mobi
  -S [options]  => save options to disk
  -k [number]   => number of recent reads to keep
  -q [query]    => pass query with the same syntax as calibredb or calibregui accepts
  -r            => filter the most recent 30 distinct books opened via rdr via rofi or dmenu
  -l            => open the last book read
  -o [file]     => open with default reader and record in recent reads if part of a calibre library
  a query here  => same as -q a query here")

(defn format-book-data [book]
  (str (:title book) " by " (:authors book)))

(defn save-book-to-recent-reads!
  "saves book info map to ~/.config/booksclj/recent.edn keeping only the most recent n entries 
   where n is either 30 or the value defined by --keep"
  [book]
  (if-let* [recent-config-file (get-configuration-file-path "recent")
            current (map read-string (string/split-lines (slurp recent-config-file)))
            combined (take (:keep opts) (distinct-by #(select-keys % [:id :library])  (flatten [book current])))]
           (spit recent-config-file (string/join "\n" combined))
           (println "saving to recent list")))

(defn open-ebook!
  "Pass book to save-book-to-recent-reads!
   then open the most preferred format defined by -p anconfiguration"
  [book command]
  (if-let* [preferred (calibre/select-preferred-ebook-format book opts)]
           (do (future (save-book-to-recent-reads! book))
               (:out (ex/sh command preferred)))))

(defn open-ebook-file! [file command]
  (if-let [book (calibre/filename-to-metadata file opts)]
    (open-ebook! book command) (ex/sh command file)))

(defn list-recent-reads! []
  (let [recent (get-configuration-file-path "recent")]
    (map read-string (string/split-lines (slurp recent)))))

(defn open-last-book
  "Open most recent entry from recent reads"
  [command]
  (if-let* [book (first (list-recent-reads!))
            fixed (calibre/correct-ebook-metadata-if-database-changed book opts)]
           (open-ebook! fixed command)))

(defn pick-from-recent-reads
  "Read recent reads and use pick-from-selection to open"
  [select-fn command]
  (if-let* [recent (list-recent-reads!)
            sel (select-by recent select-fn format-book-data)
            fixed (calibre/correct-ebook-metadata-if-database-changed sel opts)]
           (open-ebook! fixed command)))

(defn print-help "Print help info" []
  (println help-text))

(defn query-and-open [query select-fn command]
  (if-let* [res (calibre/query-string-to-vector-of-maps query opts)
            sel (select-by res select-fn format-book-data)]
           (open-ebook! sel command)))

(defn query-or-open [value select-fn command]
  (if (fs/file? value)
    (open-ebook-file! value command)
    (query-and-open value select-fn command)))

(defn print-results-of-query [query]
  (doall (map println (map format-book-data
                           (calibre/query-string-to-vector-of-maps query opts)))))

(def cli-options
  [["-h" "--help"]
   ["-l" "--last"]
   ["-L" "--library PATH"
    :parse-fn ensure-path-string-ends-in-slash]
   ["-r" "--recent"]
   ["-q" "--query"]
   ["-o" "--open"]
   ["-k" "--keep NUMBER"]
   ["-p" "--preferred FORMATS"
    :parse-fn #(string/split % #",")]
   ["-P" "--print"]
   ["-S" "--save"]])

(def default-options
  {:keep 30
   :preferred [".pdf" ".epub" ".mobi"] :reader {:default "xdg-open"}})

(defn -main
  "Parse arguments and decide what action to take."
  [& args]
  (let [command "xdg-open"
        parsed (cli/parse-opts args cli-options)
        arguments (string/join " " (:arguments parsed))]

    (def opts (merge default-options (get-saved-configuration) (:options parsed)))

    (cond
      (:help opts) (print-help)
      (:save opts) (save-configuration! opts)
      (:recent opts) (pick-from-recent-reads rofi-select command)
      (:last opts) (open-last-book command)
      (:open opts) (open-ebook-file! arguments command)
      (:query opts) (query-and-open arguments rofi-select command)
      (:print opts) (print-results-of-query arguments)
      :else (query-or-open arguments rofi-select command)))

  ;; process takes several seconds to properly terminate if we don't exit manually
  ;; yet obviously we don't want to kill the repl every time we run test main


  (if-not (is-in-repl?)
    (shutdown-agents)))
